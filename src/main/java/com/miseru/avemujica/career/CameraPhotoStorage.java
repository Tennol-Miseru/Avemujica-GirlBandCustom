package com.miseru.avemujica.career;

import com.miseru.avemujica.network.CameraPublishPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-owned, bounded, world-local PNG store. Names come from validated IDs, never paths. */
public final class CameraPhotoStorage {
    private CameraPhotoStorage() { }
    public static boolean validId(String id) { return id != null && id.matches("[0-9a-f]{32}"); }
    private static Path ownerDir(MinecraftServer server, java.util.UUID owner) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("avemujica_camera")
                .resolve(owner.toString());
    }
    private static Path file(MinecraftServer server, java.util.UUID owner, String id) {
        if (!validId(id)) throw new IllegalArgumentException("Invalid photo ID");
        return ownerDir(server, owner).resolve(id + ".png");
    }
    public static boolean validPng(byte[] bytes) {
        if (bytes == null || bytes.length < 48 || bytes.length > CameraPublishPayload.MAX_PNG_BYTES) return false;
        byte[] signature = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
        for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return false;
        // Bound dimensions before invoking an image decoder to avoid PNG decompression bombs.
        int w = ((bytes[16] & 255) << 24) | ((bytes[17] & 255) << 16) | ((bytes[18] & 255) << 8) | (bytes[19] & 255);
        int h = ((bytes[20] & 255) << 24) | ((bytes[21] & 255) << 16) | ((bytes[22] & 255) << 8) | (bytes[23] & 255);
        if (w < 64 || h < 32 || w > 640 || h > 384 || (long)w * h > 230400L) return false;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            return img != null && img.getWidth() == w && img.getHeight() == h;
        } catch (IOException | RuntimeException ex) { return false; }
    }
    public static void save(MinecraftServer server, java.util.UUID owner, String id, byte[] bytes) throws IOException {
        if (!validId(id) || !validPng(bytes)) throw new IOException("无效的 PNG 或图片体积超限");
        Path path = file(server, owner, id);
        Files.createDirectories(path.getParent());
        // Prevent duplicate IDs overwriting previously published pictures.
        Files.write(path, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
    }
    public static byte[] read(MinecraftServer server, java.util.UUID owner, String id) throws IOException {
        Path path = file(server, owner, id);
        if (!Files.isRegularFile(path) || Files.size(path) > CameraPublishPayload.MAX_PNG_BYTES) return null;
        return Files.readAllBytes(path);
    }
}
