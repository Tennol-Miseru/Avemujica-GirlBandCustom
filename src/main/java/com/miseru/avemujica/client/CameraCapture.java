package com.miseru.avemujica.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Client-only camera shutter. A GUI-free rendered frame is captured before editing the post. */
public final class CameraCapture {
    private CameraCapture() { }
    private static int shutterTicks = -1;
    private static final int MAX_BYTES = 262144;
    public static void register() { ClientTickEvents.END_CLIENT_TICK.register(CameraCapture::tick); }
    public static void clear() {
        shutterTicks = -1;
        PhotoHudVisibility.end(PhotoHudVisibility.Capture.CAMERA);
    }
    public static void openDrafts() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) mc.setScreen(new CameraDraftScreen());
    }
    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        // Hide the HUD *before* the waiting frames, not on the screenshot
        // tick: the framebuffer would otherwise still contain the hotbar/UI.
        shutterTicks = 6;
        PhotoHudVisibility.begin(PhotoHudVisibility.Capture.CAMERA);
    }
    private static void tick(Minecraft mc) {
        if (shutterTicks < 0) return;
        if (mc.player == null || mc.level == null) { clear(); return; }
        if (mc.screen != null) { clear(); return; }
        if (--shutterTicks > 0) return;
        shutterTicks = -1;
        String id = UUID.randomUUID().toString().replace("-", "");
        Path path = draftFile(id);
        try {
            Files.createDirectories(path.getParent());
            try (NativeImage original = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                int w = original.getWidth(), h = original.getHeight();
                int cutWidth = Math.min(w, (int)((long)h * 512 / 226));
                int cutHeight = Math.min(h, (int)((long)w * 226 / 512));
                if (cutWidth < 64 || cutHeight < 32) throw new IOException("窗口太小，无法拍照");
                boolean saved = false;
                for (int width : new int[]{512, 448, 384, 320, 256}) {
                    int outWidth = Math.min(width, cutWidth);
                    int outHeight = Math.max(32, outWidth * 226 / 512);
                    try (NativeImage image = new NativeImage(outWidth, outHeight, false)) {
                        original.resizeSubRectTo((w-cutWidth)/2, (h-cutHeight)/2,
                                cutWidth, cutHeight, image);
                        image.writeToFile(path);
                    }
                    if (Files.size(path) <= MAX_BYTES) { saved = true; break; }
                }
                if (!saved) throw new IOException("照片超过 256 KiB 的上传限制");
            }
            // Restore the user's HUD setting before opening the editor.
            PhotoHudVisibility.end(PhotoHudVisibility.Capture.CAMERA);
            mc.setScreen(new CameraComposeScreen(id, path));
        } catch (IOException | RuntimeException ex) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            mc.player.displayClientMessage(Component.literal("[Mujica] 拍照失败：" + ex.getMessage()), false);
        } finally {
            PhotoHudVisibility.end(PhotoHudVisibility.Capture.CAMERA);
        }
    }
    public static Path draftFile(String photoId) {
        if (!photoId.matches("[0-9a-f]{32}")) throw new IllegalArgumentException("invalid ID");
        return FabricLoader.getInstance().getGameDir().resolve("screenshots")
                .resolve("avemujica_tours").resolve("camera_drafts").resolve(photoId + ".png");
    }
}
