package com.miseru.avemujica.client;

import com.miseru.avemujica.career.TourPost;
import com.miseru.avemujica.network.TourPhotoPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.miseru.avemujica.network.CameraPhotoRequestPayload;
import com.miseru.avemujica.network.CameraPublishPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Saves owner-only screenshots on the local PC, never uploads files to a server.
 * World/server data still preserves the procedural postcard fallback. */
public final class TourPhotoCapture {
    private TourPhotoCapture() { }
    private static TourPhotoPayload pending;
    private static int countdown;
    private static int remainingTicks;
    private static boolean menuPrompted;
    private static final int MAX_WAIT_TICKS = 600; // wait up to 30 sec for the player to leave a menu
    private static final int IMAGE_WIDTH = 512, IMAGE_HEIGHT = 226;
    private static final int MAX_SAVED_WIDTH = 1600;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(TourPhotoCapture::tick);
    }
    public static void queue(TourPhotoPayload payload) {
        clear();
        pending = payload;
        countdown = 8; // let the game render several clear HUD-free frames
        remainingTicks = MAX_WAIT_TICKS;
        menuPrompted = false;
    }
    /** A player can retake the photo for an archived concert while near its site. */
    public static boolean retake(TourPost post) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.level.dimension() != Level.OVERWORLD) return false;
        double dx = minecraft.player.getX() - post.x();
        double dz = minecraft.player.getZ() - post.z();
        if (dx * dx + dz * dz > 42D * 42D) return false;
        queue(new TourPhotoPayload(post.number(), post.photoSeed(), post.x(), post.y(), post.z()));
        return true;
    }
    public static void clear() {
        pending = null; countdown = 0; remainingTicks = 0; menuPrompted = false;
        PhotoHudVisibility.end(PhotoHudVisibility.Capture.TOUR);
    }

    public static Path file(TourPost post) {
        return post.manual() ? manualFile(post.photoId()) : file(post.number(), post.photoSeed());
    }
    public static Path manualFile(String photoId) {
        if (photoId == null || !photoId.matches("[0-9a-f]{32}")) throw new IllegalArgumentException("invalid photo ID");
        return FabricLoader.getInstance().getGameDir().resolve("screenshots")
                .resolve("avemujica_tours").resolve("camera_posts").resolve(photoId + ".png");
    }
    /** Photos are stored by the owner server; retrieve them on other clients only when needed. */
    public static void requestManual(TourPost post) {
        if (!post.manual()) return;
        if (ClientPlayNetworking.canSend(CameraPhotoRequestPayload.TYPE))
            ClientPlayNetworking.send(new CameraPhotoRequestPayload(post.photoId()));
    }
    public static boolean receiveManual(String photoId, byte[] png) {
        if (photoId == null || !photoId.matches("[0-9a-f]{32}") || png == null
                || png.length < 48 || png.length > CameraPublishPayload.MAX_PNG_BYTES) return false;
        byte[] signature = {(byte)137,80,78,71,13,10,26,10};
        for (int i=0;i<8;i++) if (png[i] != signature[i]) return false;
        try {
            Path path = manualFile(photoId);
            Files.createDirectories(path.getParent());
            Files.write(path, png);
            return true;
        } catch (IOException ex) { return false; }
    }
    public static Path file(int number, int seed) {
        String filename = "tour_" + number + "_" + Integer.toUnsignedString(seed, 16) + ".png";
        return FabricLoader.getInstance().getGameDir().resolve("screenshots")
                .resolve("avemujica_tours").resolve(filename);
    }

    private static void tick(Minecraft minecraft) {
        if (pending == null) return;
        // Deliberately wait rather than permanently dropping the photo when a GUI
        // was open at show completion (the main cause of missing real snapshots).
        if (minecraft.player == null || minecraft.level == null
                || minecraft.level.dimension() != Level.OVERWORLD) { clear(); return; }
        if (--remainingTicks <= 0) {
            minecraft.player.displayClientMessage(Component.literal(
                    "[Mujica] 巡演照片未拍摄：请到演出地点打开官方账号，点击「重拍现场」。"), false);
            clear(); return;
        }
        TourPhotoPayload data = pending;
        double dx = minecraft.player.getX() - data.x();
        double dz = minecraft.player.getZ() - data.z();
        if (dx * dx + dz * dz > 42D * 42D) {
            minecraft.player.displayClientMessage(Component.literal(
                    "[Mujica] 离演出场地太远，暂未拍摄。回到现场可从相册重拍。"), false);
            clear(); return;
        }
        if (minecraft.screen != null) {
            countdown = 8;
            // If a menu is opened mid-countdown, never leave the menu hidden.
            PhotoHudVisibility.end(PhotoHudVisibility.Capture.TOUR);
            if (!menuPrompted) {
                minecraft.player.displayClientMessage(Component.literal(
                        "[Mujica] 巡演结束！关闭菜单并面向舞台，将自动保存真实现场照片。"), false);
                menuPrompted = true;
            }
            return;
        }
        if (!PhotoHudVisibility.active(PhotoHudVisibility.Capture.TOUR)) {
            PhotoHudVisibility.begin(PhotoHudVisibility.Capture.TOUR);
            countdown = 8;
            return;
        }
        if (--countdown > 0) return;
        Path output = file(data.number(), data.photoSeed());
        try {
            Files.createDirectories(output.getParent());
            // Screenshot from the actual rendered game framebuffer, not a generated
            // illustration. Prefer native pixel resolution; only downscale oversized
            // images to at most 1600 px to prevent disk/resource exhaustion.
            try (NativeImage original = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                int width = original.getWidth(), height = original.getHeight();
                int cutWidth = Math.min(width, (int)((long)height * IMAGE_WIDTH / IMAGE_HEIGHT));
                int cutHeight = Math.min(height, (int)((long)width * IMAGE_HEIGHT / IMAGE_WIDTH));
                if (cutWidth < 64 || cutHeight < 32) throw new IOException("画面尺寸过小");
                int savedWidth = Math.min(cutWidth, MAX_SAVED_WIDTH);
                int savedHeight = Math.max(1, savedWidth * IMAGE_HEIGHT / IMAGE_WIDTH);
                try (NativeImage picture = new NativeImage(savedWidth, savedHeight, false)) {
                    original.resizeSubRectTo((width - cutWidth) / 2, (height - cutHeight) / 2,
                            cutWidth, cutHeight, picture);
                    picture.writeToFile(output);
                }
            }
            minecraft.player.displayClientMessage(Component.literal(
                    "[Mujica] 真实巡演照片已保存：screenshots/avemujica_tours/"), false);
        } catch (IOException | RuntimeException ex) {
            System.err.println("[AveMujica] 巡演截图失败，保留历史记录与纪念卡：" + ex);
            minecraft.player.displayClientMessage(Component.literal(
                    "[Mujica] 截图失败，可从官方账号返回现场重试：" + ex.getMessage()), false);
        } finally {
            // Also restore HUD after exceptions, disconnected sessions and
            // failed writes. clear() is idempotent.
            clear();
        }
    }

    /** A single visible local photo texture, released whenever the album changes post/closes. */
    public static final class Preview implements AutoCloseable {
        private final TextureManager manager;
        private final ResourceLocation id;
        private final int imageWidth, imageHeight;
        private Preview(TextureManager manager, ResourceLocation id, int imageWidth, int imageHeight) {
            this.manager = manager; this.id = id;
            this.imageWidth = imageWidth; this.imageHeight = imageHeight;
        }
        public static Preview open(TourPost post) {
            return open(file(post));
        }
        public static Preview open(Path path) {
            if (!Files.isRegularFile(path)) return null;
            try (var input = Files.newInputStream(path)) {
                NativeImage image = NativeImage.read(input);
                TextureManager manager = Minecraft.getInstance().getTextureManager();
                int w = image.getWidth(), h = image.getHeight();
                if (w < 16 || h < 16 || w > 8192 || h > 8192) {
                    image.close(); return null;
                }
                ResourceLocation id;
                try { id = manager.register("avemujica_tour", new DynamicTexture(image)); }
                catch (RuntimeException ex) { image.close(); throw ex; }
                return new Preview(manager, id, w, h);
            } catch (Exception ex) {
                System.err.println("[AveMujica] 巡演照片读取失败，改用纪念卡：" + ex);
                return null;
            }
        }
        public void render(GuiGraphics graphics, int x, int y, int w, int h) {
            // The 11-argument overload separates on-screen size from texture-region size.
            // The old 9-argument call treated w/h as source UV extents and tiled big photos.
            int drawWidth = Math.min(w, Math.max(1, Math.round(h * (imageWidth / (float)imageHeight))));
            int drawHeight = Math.min(h, Math.max(1, Math.round(w * (imageHeight / (float)imageWidth))));
            // Preserve aspect ratio inside the target rectangle.
            if (drawWidth == w) drawHeight = Math.min(h, Math.round(w * (imageHeight / (float)imageWidth)));
            else drawWidth = Math.min(w, Math.round(h * (imageWidth / (float)imageHeight)));
            graphics.blit(id, x + (w - drawWidth) / 2, y + (h - drawHeight) / 2,
                    drawWidth, drawHeight, 0F, 0F, imageWidth, imageHeight, imageWidth, imageHeight);
        }
        public int imageWidth() { return imageWidth; }
        public int imageHeight() { return imageHeight; }
        /** Draw the EXACT selected part of the source, not a stretched 200x240 mat. */
        public void renderCrop(GuiGraphics g,int x,int y,int w,int h,PosterCrop.Region region) {
            int u=Math.max(0,Math.min(imageWidth-1,Math.round(region.u0()*imageWidth)));
            int v=Math.max(0,Math.min(imageHeight-1,Math.round(region.v0()*imageHeight)));
            int uw=Math.max(1,Math.min(imageWidth-u,Math.round(region.width()*imageWidth)));
            int vh=Math.max(1,Math.min(imageHeight-v,Math.round(region.height()*imageHeight)));
            g.blit(id,x,y,w,h,(float)u,(float)v,uw,vh,imageWidth,imageHeight);
        }
        @Override public void close() { manager.release(id); }
    }
}
