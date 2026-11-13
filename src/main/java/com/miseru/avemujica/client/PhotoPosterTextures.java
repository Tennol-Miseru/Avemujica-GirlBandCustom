package com.miseru.avemujica.client;

import com.miseru.avemujica.career.CameraPhotoStorage;
import com.miseru.avemujica.network.PosterPhotoPayload;
import com.miseru.avemujica.network.PosterRequestPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

/** Bounded GPU cache for the poster textures. Photo bytes never enter block NBT. */
public final class PhotoPosterTextures {
    private PhotoPosterTextures() { }
    private record Key(UUID owner, String photoId) { }
    public record PhotoTexture(ResourceLocation id, int width, int height) { }
    private static final LinkedHashMap<Key, PhotoTexture> CACHE = new LinkedHashMap<>(32, .75F, true);
    private static final Map<Key, Long> LAST_REQUEST = new HashMap<>();
    private static final int MAX_TEXTURES = 48;

    public static PhotoTexture find(UUID owner, String id) {
        return CACHE.get(new Key(owner, id));
    }
    public static void request(BlockPos pos, UUID owner, String id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || owner == null || !CameraPhotoStorage.validId(id)) return;
        Key key = new Key(owner, id);
        if (CACHE.containsKey(key) || !ClientPlayNetworking.canSend(PosterRequestPayload.TYPE)) return;
        long now = minecraft.level.getGameTime();
        long last = LAST_REQUEST.getOrDefault(key, Long.MIN_VALUE / 2);
        if (now >= last && now - last < 200) return;
        LAST_REQUEST.put(key, now);
        ClientPlayNetworking.send(new PosterRequestPayload(pos));
    }
    public static void receive(PosterPhotoPayload packet) {
        if (!CameraPhotoStorage.validId(packet.photoId()) || !CameraPhotoStorage.validPng(packet.png())) return;
        Key key = new Key(packet.owner(), packet.photoId());
        LAST_REQUEST.remove(key);
        if (CACHE.containsKey(key)) return;
        // Upload the original photo pixels, not the legacy 200x240 PORTRAIT MAT.
        // The mat was why all landscape photos got stretched on wide models.
        try (ByteArrayInputStream input = new ByteArrayInputStream(packet.png())) {
            NativeImage image = NativeImage.read(input);
            int width=image.getWidth(), height=image.getHeight();
            if (width<64 || height<32 || width>640 || height>384) {
                image.close(); return;
            }
            ResourceLocation texture;
            try {
                texture = Minecraft.getInstance().getTextureManager().register(
                        "avemujica_poster", new DynamicTexture(image));
            } catch (RuntimeException ex) { image.close(); throw ex; }
            CACHE.put(key, new PhotoTexture(texture,width,height));
            while (CACHE.size()>MAX_TEXTURES) {
                Iterator<Map.Entry<Key,PhotoTexture>> it=CACHE.entrySet().iterator();
                Map.Entry<Key,PhotoTexture> old=it.next();
                Minecraft.getInstance().getTextureManager().release(old.getValue().id());
                it.remove();
            }
        } catch (Exception ex) {
            System.err.println("[AveMujica] 照片立牌贴图读取失败: " + ex.getMessage());
        }
    }
    public static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (PhotoTexture value : CACHE.values()) textures.release(value.id());
        CACHE.clear();
        LAST_REQUEST.clear();
    }
}
