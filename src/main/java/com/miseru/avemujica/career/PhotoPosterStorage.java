package com.miseru.avemujica.career;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.Arrays;

/** World-local, immutable poster copy for unpublished camera drafts. */
public final class PhotoPosterStorage {
    private PhotoPosterStorage() { }
    private static Path file(MinecraftServer server, UUID owner, String id) {
        if (!CameraPhotoStorage.validId(id)) throw new IllegalArgumentException("Invalid poster photo ID");
        return server.getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve("avemujica_poster_photos").resolve(owner.toString()).resolve(id + ".png");
    }
    public static void store(MinecraftServer server, UUID owner, String id, byte[] png) throws IOException {
        if (!CameraPhotoStorage.validPng(png)) throw new IOException("不支持的 PNG 图片");
        Path target = file(server, owner, id);
        Files.createDirectories(target.getParent());
        if (Files.isRegularFile(target)) {
            if (Arrays.equals(Files.readAllBytes(target), png)) return;
            throw new IOException("照片 ID 已使用，不能覆盖其他照片");
        }
        Files.write(target, png, StandardOpenOption.CREATE_NEW);
    }
    public static byte[] read(MinecraftServer server, UUID owner, String id) throws IOException {
        Path path = file(server, owner, id);
        if (!Files.isRegularFile(path) || Files.size(path) > 262144) return null;
        byte[] png = Files.readAllBytes(path);
        return CameraPhotoStorage.validPng(png) ? png : null;
    }
}
