package com.miseru.avemujica.client;

import com.miseru.avemujica.registry.ModRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.miseru.avemujica.network.MidiSongListPayload;
import com.miseru.avemujica.network.TourArchivePayload;
import com.miseru.avemujica.network.CameraPhotoDataPayload;
import com.miseru.avemujica.network.MasqueradeSkyPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.miseru.avemujica.network.MidiInstrumentSettingsPayload;
import com.miseru.avemujica.network.GuitarPedalboardPayload;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.gui.screens.MenuScreens;

public final class AveMujicaClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        // Rebuild the user's optional overlay before Minecraft lists resource packs.
        // It is deliberately NOT force-enabled; each client opts in once in Resource Packs.
        try {
            CustomAssetImporter.install(FabricLoader.getInstance().getGameDir());
        } catch (Exception e) {
            System.err.println("[AveMujica] 自定义素材导入失败（默认素材不受影响）: " + e);
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(literal("avemujicaassets")
                        .executes(context -> {
                            context.getSource().sendFeedback(Component.literal(
                                    "自定义素材目录：" + CustomAssetImporter.inputDir(FabricLoader.getInstance().getGameDir())
                                    + "；将资源包 AveMujica_Custom_Assets 在设置中启用，然后运行 /avemujicaassets reload"));
                            return 1;
                        })
                        .then(literal("reload").executes(context -> {
                            try {
                                CustomAssetImporter.Result result = CustomAssetImporter.install(FabricLoader.getInstance().getGameDir());
                                Minecraft.getInstance().reloadResourcePacks();
                                context.getSource().sendFeedback(Component.literal(result.summary()));
                                context.getSource().sendFeedback(Component.literal("资源包已请求刷新；确认 AveMujica_Custom_Assets 处于启用状态"));
                                for (String warning : result.warnings().stream().limit(5).toList())
                                    context.getSource().sendFeedback(Component.literal("跳过：" + warning));
                            } catch (Exception e) {
                                context.getSource().sendError(Component.literal("素材导入失败：" + e.getMessage()));
                            }
                            return 1;
                        }))));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MasqueradeSkyState.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TourPhotoCapture.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CameraCapture.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PhotoPosterTextures.clear());
        ClientPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.CharacterCustomizationStatePayload.TYPE,
                (packet, context) -> context.client().execute(() -> {
                    if (context.client().screen instanceof CharacterCustomizationScreen screen) screen.update(packet);
                    else if (packet.open()) context.client().setScreen(new CharacterCustomizationScreen(packet));
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.MemberInstrumentStatePayload.TYPE,
                (packet, context) -> context.client().execute(() -> {
                    if (context.client().screen instanceof CharacterCustomizationScreen screen) screen.updateInstruments(packet);
                    else if (context.client().screen instanceof InstrumentColorScreen screen) screen.updateInstruments(packet);
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.InstrumentColorStatePayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().screen instanceof InstrumentColorScreen screen) screen.update(payload);
                    else if (payload.open()) context.client().setScreen(new InstrumentColorScreen(payload));
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.OctopusShopStatePayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().screen instanceof OctopusShopScreen screen) screen.update(payload);
                    else if (payload.open()) context.client().setScreen(new OctopusShopScreen(payload));
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.MerchConsignmentStatePayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().screen instanceof MerchConsignmentScreen screen) screen.update(payload);
                    else if (payload.open()) context.client().setScreen(new MerchConsignmentScreen(payload));
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.PosterOpenPayload.TYPE,
                (packet, context) -> context.client().execute(() -> context.client().setScreen(
                        new PhotoPosterPickerScreen(packet))));
        ClientPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.PosterPhotoPayload.TYPE,
                (packet, context) -> context.client().execute(() -> PhotoPosterTextures.receive(packet)));
        CameraCapture.register();
        ClientPlayNetworking.registerGlobalReceiver(CameraPhotoDataPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (TourPhotoCapture.receiveManual(payload.photoId(), payload.png())) {
                        if (context.client().screen instanceof TourArchiveScreen archive)
                            archive.photoReceived(payload.photoId());
                        if (context.client().screen instanceof PhotoPosterPickerScreen picker)
                            picker.photoReceived(payload.photoId());
                    }
                }));
        TourPhotoCapture.register();
        ClientPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.TourPhotoPayload.TYPE, (payload, context) ->
                context.client().execute(() -> TourPhotoCapture.queue(payload)));
        ClientPlayNetworking.registerGlobalReceiver(MasqueradeSkyPayload.TYPE, (payload, context) ->
                context.client().execute(() -> MasqueradeSkyState.set(payload.active())));
        ClientPlayNetworking.registerGlobalReceiver(TourArchivePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof TourArchiveScreen archive) archive.updateArchive(payload);
                    else context.client().setScreen(new TourArchiveScreen(payload));
                }));
        ClientPlayNetworking.registerGlobalReceiver(MidiSongListPayload.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(
                        new MidiSongPickerScreen(payload.memberId(), payload.songs()))));
        ClientPlayNetworking.registerGlobalReceiver(MidiInstrumentSettingsPayload.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(
                        new MidiInstrumentScreen(payload.roles(), payload.presets(), payload.volumes()))));
        ClientPlayNetworking.registerGlobalReceiver(GuitarPedalboardPayload.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(
                        new GuitarPedalboardScreen(payload.roles(), payload.presets(), payload.mixes()))));
        MenuScreens.register(ModRegistry.SAKIKO_CONSOLE_MENU, SakikoConsoleScreen::new);
        MenuScreens.register(ModRegistry.BAND_MANAGEMENT_MENU, BandManagementScreen::new);
        MenuScreens.register(ModRegistry.MEMBER_INTERACTION_MENU, MemberInteractionScreen::new);
        MenuScreens.register(ModRegistry.BAND_DEBUG_MENU, BandDebugScreen::new);
        MenuScreens.register(ModRegistry.POSE_DEBUG_MENU, PoseDebugScreen::new);
        // Preserve transparent second-layer pixels of the user-provided 64x64 character skins.
        BlockRenderLayerMap.INSTANCE.putBlock(ModRegistry.MERCH_DOLL_OBLIVIONIS, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModRegistry.MERCH_DOLL_DOLORIS, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModRegistry.MERCH_DOLL_MORTIS, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModRegistry.MERCH_DOLL_TIMORIS, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModRegistry.MERCH_DOLL_AMORIS, RenderType.cutout());
        MemberNoteParticle.registerAll();
        BlockEntityRenderers.register(ModRegistry.STAGE_LEVEL2_LOGO_ENTITY, StageLogoRenderer::new);
        BlockEntityRenderers.register(ModRegistry.PHOTO_POSTER_ENTITY, PhotoPosterRenderer::new);
        StageBeamRenderer.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> StageBeamRenderer.reset());
        EntityRendererRegistry.register(ModRegistry.BAND_MEMBER,BandMemberRenderer::new);
        EntityRendererRegistry.register(ModRegistry.NOTE_BOLT,ThrownItemRenderer::new);
        EntityRendererRegistry.register(ModRegistry.RIFLE_ROUND,ThrownItemRenderer::new);
    }
}
