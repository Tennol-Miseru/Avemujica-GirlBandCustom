package com.miseru.avemujica.career;

import com.miseru.avemujica.network.CameraPhotoDataPayload;
import com.miseru.avemujica.network.CameraPhotoRequestPayload;
import com.miseru.avemujica.network.CameraPublishPayload;
import com.miseru.avemujica.registry.ModRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.io.IOException;

/** Validated, player-owned manual feed posts; no tour-count, reward or fan inflation. */
public final class CameraPosts {
    private CameraPosts() { }
    private static boolean holdsCamera(ServerPlayer player) {
        return player.getMainHandItem().is(ModRegistry.BLUE_OCTOPUS)
                || player.getOffhandItem().is(ModRegistry.BLUE_OCTOPUS)
                || player.getMainHandItem().is(ModRegistry.CAMERA)
                || player.getOffhandItem().is(ModRegistry.CAMERA);
    }
    private static String cleanCaption(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length() && out.length() < 120; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') out.append(' ');
            else if (!Character.isISOControl(c)) out.append(c);
        }
        return out.toString().trim();
    }
    public static void publish(ServerPlayer player, CameraPublishPayload request) {
        if (!holdsCamera(player)) {
            player.sendSystemMessage(Component.literal("[Mujica] 发布照片时请手持蓝色小章鱼。")); return;
        }
        BandCareerData data = BandCareerData.get(player.serverLevel());
        BandCareerData.Career career = data.career(player.getUUID());
        long tick = player.serverLevel().getGameTime();
        if (tick >= career.lastCameraPostTick && tick - career.lastCameraPostTick < 100L) {
            player.sendSystemMessage(Component.literal("[Mujica] 请稍后再发布下一张照片。")); return;
        }
        if (!CameraPhotoStorage.validId(request.photoId())
                || !CameraPhotoStorage.validPng(request.png())) {
            player.sendSystemMessage(Component.literal("[Mujica] 上传失败：照片格式或文件大小不符合要求。")); return;
        }
        if (career.tourPosts.stream().anyMatch(p -> p.manual() && p.photoId().equals(request.photoId()))) {
            player.sendSystemMessage(Component.literal("[Mujica] 这张照片已经发布。")); return;
        }
        String caption = cleanCaption(request.caption());
        if (caption.isBlank()) caption = "记录今天的风景。";
        try { CameraPhotoStorage.save(player.getServer(), player.getUUID(), request.photoId(), request.png()); }
        catch (IOException ex) {
            player.sendSystemMessage(Component.literal("[Mujica] 照片保存失败：" + ex.getMessage())); return;
        }
        String biome = player.serverLevel().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.location().toString()).orElse("minecraft:plains");
        long seed = request.photoId().hashCode() * 341873128712L ^ player.getUUID().getLeastSignificantBits();
        int likes = Math.max(1, TourEngagement.likes(career.fans, 65, 0, 0, seed));
        career.cameraPostCount++;
        career.lastCameraPostTick = tick;
        career.publish(new TourPost(career.cameraPostCount, player.serverLevel().getDayTime() / 24000L,
                biome, player.serverLevel().dimension().location().toString(),
                player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                0, 0, 0, 0, 0, 0, likes, 0, (int)seed,
                true, caption, request.photoId()));
        data.setDirty();
        player.sendSystemMessage(Component.literal("[Mujica] 照片已发布到官方账号，获得 " + likes + " 个赞。"));
        TourAccount.open(player);
    }
    public static void request(ServerPlayer player, CameraPhotoRequestPayload request) {
        if (!CameraPhotoStorage.validId(request.photoId())) return;
        BandCareerData.Career career = BandCareerData.get(player.serverLevel()).career(player.getUUID());
        if (career.tourPosts.stream().noneMatch(p -> p.manual() && p.photoId().equals(request.photoId()))) return;
        try {
            byte[] data = CameraPhotoStorage.read(player.getServer(), player.getUUID(), request.photoId());
            if (data != null) ServerPlayNetworking.send(player, new CameraPhotoDataPayload(request.photoId(), data));
        } catch (IOException ignored) { }
    }
}
