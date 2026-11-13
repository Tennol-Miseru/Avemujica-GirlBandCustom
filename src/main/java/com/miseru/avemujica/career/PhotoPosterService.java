package com.miseru.avemujica.career;

import com.miseru.avemujica.block.PhotoPosterBlockEntity;
import com.miseru.avemujica.block.PhotoPosterLayout;
import com.miseru.avemujica.network.PosterConfigurePayload;
import com.miseru.avemujica.network.PosterChoosePayload;
import com.miseru.avemujica.network.PosterPhotoPayload;
import com.miseru.avemujica.network.PosterRequestPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.io.IOException;

/** Validates writes and read requests on the server, never trusts a client-supplied block owner. */
public final class PhotoPosterService {
    private PhotoPosterService() { }
    private static PhotoPosterBlockEntity nearbyPoster(ServerPlayer player, BlockPos pos, double range) {
        if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > range * range
                || !player.serverLevel().hasChunkAt(pos)) return null;
        return player.serverLevel().getBlockEntity(pos) instanceof PhotoPosterBlockEntity poster ? poster : null;
    }
    /** Resize or re-crop an existing photograph without uploading it again. */
    public static void configure(ServerPlayer player, PosterConfigurePayload payload) {
        PhotoPosterBlockEntity poster=nearbyPoster(player,payload.pos(),8);
        if (poster==null || !poster.canEdit(player.getUUID())) return;
        if (!valid(payload.width(),payload.height(),payload.zoom(),payload.centerX(),payload.centerY())) return;
        if (!PhotoPosterLayout.resize(player.serverLevel(),payload.pos(),poster,payload.width(),payload.height())) {
            player.sendSystemMessage(Component.literal("[Mujica] 立牌需要空余空间，调整尺寸失败。")); return;
        }
        poster.configure(payload.width(),payload.height(),payload.zoom(),payload.centerX(),payload.centerY());
        player.sendSystemMessage(Component.literal("[Mujica] 立牌尺寸和照片裁切已更新。"));
    }
    private static boolean valid(int w,int h,float zoom,float cx,float cy) {
        return w>=1 && w<=4 && h>=1 && h<=4 && Float.isFinite(zoom) && zoom>=1 && zoom<=4
                && Float.isFinite(cx) && cx>=0 && cx<=1 && Float.isFinite(cy) && cy>=0 && cy<=1;
    }
    public static void choose(ServerPlayer player, PosterChoosePayload payload) {
        PhotoPosterBlockEntity poster = nearbyPoster(player, payload.pos(), 8);
        if (poster == null) return;
        if (!poster.canEdit(player.getUUID())) {
            player.sendSystemMessage(Component.literal("[Mujica] 只能修改自己设置的海报。")); return;
        }
        if (!valid(payload.width(),payload.height(),payload.zoom(),payload.centerX(),payload.centerY())) return;
        if (!PhotoPosterLayout.canResize(player.serverLevel(),payload.pos(),poster,payload.width(),payload.height())) {
            player.sendSystemMessage(Component.literal("[Mujica] 立牌周围空间不足，请清出位置后重试。")); return;
        }
        if (!payload.photoId().isEmpty() && !CameraPhotoStorage.validId(payload.photoId())) return;
        try {
            // Validate image before mutating the structure.
            if (!payload.photoId().isEmpty() && payload.published()) {
                // A published ID must belong to the sending player and be in their feed.
                boolean ownerPost = BandCareerData.get(player.serverLevel()).career(player.getUUID())
                        .tourPosts.stream().anyMatch(p -> p.manual() && payload.photoId().equals(p.photoId()));
                if (!ownerPost || payload.png().length != 0 || CameraPhotoStorage.read(
                        player.getServer(), player.getUUID(), payload.photoId()) == null) return;
            } else if (!payload.photoId().isEmpty()) {
                // Camera drafts are local-only. Store a bounded immutable world-local copy.
                PhotoPosterStorage.store(player.getServer(), player.getUUID(), payload.photoId(), payload.png());
            } else if (payload.png().length != 0) return;
            if (!PhotoPosterLayout.resize(player.serverLevel(),payload.pos(),poster,payload.width(),payload.height())) {
                player.sendSystemMessage(Component.literal("[Mujica] 立牌需要空余空间，调整尺寸失败。")); return;
            }
            poster.configure(payload.width(),payload.height(),payload.zoom(),payload.centerX(),payload.centerY());
            if (payload.photoId().isEmpty()) {
                poster.clear(player.getUUID());
                player.sendSystemMessage(Component.literal("[Mujica] 已恢复默认海报。"));
            } else {
                poster.assign(player.getUUID(), payload.photoId(), payload.published());
                player.sendSystemMessage(Component.literal("[Mujica] 已设置照片立牌。"));
            }
        } catch (IOException ex) {
            player.sendSystemMessage(Component.literal("[Mujica] 海报照片无法保存：" + ex.getMessage()));
        }
    }
    public static void request(ServerPlayer player, PosterRequestPayload payload) {
        PhotoPosterBlockEntity poster = nearbyPoster(player, payload.pos(), 96);
        if (poster == null || !poster.hasPhoto()) return;
        try {
            byte[] bytes = poster.published()
                    ? CameraPhotoStorage.read(player.getServer(), poster.owner(), poster.photoId())
                    : PhotoPosterStorage.read(player.getServer(), poster.owner(), poster.photoId());
            if (bytes != null) ServerPlayNetworking.send(player,
                    new PosterPhotoPayload(poster.owner(), poster.photoId(), bytes));
        } catch (IOException ignored) { }
    }
}
