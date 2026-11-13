package com.miseru.avemujica;

import com.miseru.avemujica.career.BandManagement;
import com.miseru.avemujica.career.TourAccount;
import com.miseru.avemujica.network.TourArchivePayload;
import com.miseru.avemujica.network.TourPinPayload;
import com.miseru.avemujica.career.VillageAudience;
import com.miseru.avemujica.career.MasqueradeConcert;
import com.miseru.avemujica.effect.MasqueradeEffect;
import com.miseru.avemujica.team.OctopusSummons;
import com.miseru.avemujica.team.PlayerBandRoster;
import com.miseru.avemujica.music.MidiBandPlayer;
import com.miseru.avemujica.music.MidiInstrumentSettings;
import com.miseru.avemujica.music.MortisSampleSounds;
import com.miseru.avemujica.music.StageConcertSounds;
import com.miseru.avemujica.music.GuitarPedalboard;
import com.miseru.avemujica.network.GuitarPedalboardPayload;
import com.miseru.avemujica.network.MidiInstrumentSettingsPayload;
import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.HostileTargeting;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.item.SummonMemberItem;
import com.miseru.avemujica.registry.ModRegistry;
import com.miseru.avemujica.particle.MemberNoteParticles;
import com.miseru.avemujica.voice.VoiceLibrary;
import com.miseru.avemujica.voice.VoiceDirector;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.miseru.avemujica.career.CameraPosts;
import com.miseru.avemujica.network.CameraPublishPayload;
import com.miseru.avemujica.network.CameraPhotoRequestPayload;
import com.miseru.avemujica.network.CameraPhotoDataPayload;
import com.miseru.avemujica.network.MidiSongListPayload;
import com.miseru.avemujica.network.MasqueradeSkyPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class AveMujicaMod implements ModInitializer {
    public static final String MODID = "avemujica";
    private static final SuggestionProvider<CommandSourceStack> MEMBER_SUGGESTIONS =
            (ctx,builder) -> { for(MemberRole role:MemberRole.values()) builder.suggest(role.id); builder.suggest("all"); return builder.buildFuture(); };
    @Override public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(com.miseru.avemujica.network.MemberInstrumentChoicePayload.TYPE,
                com.miseru.avemujica.network.MemberInstrumentChoicePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.miseru.avemujica.network.MemberInstrumentStatePayload.TYPE,
                com.miseru.avemujica.network.MemberInstrumentStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.MemberInstrumentChoicePayload.TYPE,
                (packet, context) -> context.server().execute(() ->
                        com.miseru.avemujica.career.MemberInstruments.choose(context.player(), packet)));
        PayloadTypeRegistry.playC2S().register(com.miseru.avemujica.network.CharacterDailyActionPayload.TYPE,
                com.miseru.avemujica.network.CharacterDailyActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(com.miseru.avemujica.network.CharacterSummonChoicePayload.TYPE,
                com.miseru.avemujica.network.CharacterSummonChoicePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.miseru.avemujica.network.CharacterCustomizationStatePayload.TYPE,
                com.miseru.avemujica.network.CharacterCustomizationStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.CharacterDailyActionPayload.TYPE,
                (packet, context) -> context.server().execute(() ->
                        com.miseru.avemujica.career.CharacterCustomization.choose(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.CharacterSummonChoicePayload.TYPE,
                (packet, context) -> context.server().execute(() ->
                        com.miseru.avemujica.career.CharacterCustomization.chooseSummon(context.player(), packet)));
        PayloadTypeRegistry.playC2S().register(com.miseru.avemujica.network.InstrumentColorChoosePayload.TYPE,
                com.miseru.avemujica.network.InstrumentColorChoosePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.miseru.avemujica.network.InstrumentColorStatePayload.TYPE,
                com.miseru.avemujica.network.InstrumentColorStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.InstrumentColorChoosePayload.TYPE,
                (packet, context) -> context.server().execute(() ->
                        com.miseru.avemujica.career.InstrumentColors.choose(context.player(), packet)));
        PayloadTypeRegistry.playC2S().register(com.miseru.avemujica.network.OctopusShopBuyPayload.TYPE,
                com.miseru.avemujica.network.OctopusShopBuyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.miseru.avemujica.network.OctopusShopStatePayload.TYPE,
                com.miseru.avemujica.network.OctopusShopStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.OctopusShopBuyPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        com.miseru.avemujica.career.OctopusShop.buy(context.player(), payload)));
        PayloadTypeRegistry.playC2S().register(com.miseru.avemujica.network.MerchConsignmentActionPayload.TYPE,
                com.miseru.avemujica.network.MerchConsignmentActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.miseru.avemujica.network.MerchConsignmentStatePayload.TYPE,
                com.miseru.avemujica.network.MerchConsignmentStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.MerchConsignmentActionPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        com.miseru.avemujica.career.MerchConsignment.action(context.player(), payload)));
        PayloadTypeRegistry.playC2S().register(TourPinPayload.TYPE, TourPinPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TourPinPayload.TYPE, (packet, context) ->
                context.server().execute(() -> TourAccount.pin(context.player(), packet)));
        PayloadTypeRegistry.playC2S().register(CameraPublishPayload.TYPE, CameraPublishPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CameraPhotoRequestPayload.TYPE, CameraPhotoRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CameraPhotoDataPayload.TYPE, CameraPhotoDataPayload.CODEC);
        // Poster photo selection, server-verified binding and on-demand client rendering.
        PayloadTypeRegistry.playC2S().register(com.miseru.avemujica.network.PosterConfigurePayload.TYPE,
                com.miseru.avemujica.network.PosterConfigurePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(com.miseru.avemujica.network.PosterChoosePayload.TYPE,
                com.miseru.avemujica.network.PosterChoosePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(com.miseru.avemujica.network.PosterRequestPayload.TYPE,
                com.miseru.avemujica.network.PosterRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.miseru.avemujica.network.PosterOpenPayload.TYPE,
                com.miseru.avemujica.network.PosterOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.miseru.avemujica.network.PosterPhotoPayload.TYPE,
                com.miseru.avemujica.network.PosterPhotoPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.PosterConfigurePayload.TYPE,
                (packet, context) -> context.server().execute(() ->
                        com.miseru.avemujica.career.PhotoPosterService.configure(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.PosterChoosePayload.TYPE,
                (packet, context) -> context.server().execute(() ->
                        com.miseru.avemujica.career.PhotoPosterService.choose(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(com.miseru.avemujica.network.PosterRequestPayload.TYPE,
                (packet, context) -> context.server().execute(() ->
                        com.miseru.avemujica.career.PhotoPosterService.request(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(CameraPublishPayload.TYPE, (packet, context) ->
                context.server().execute(() -> CameraPosts.publish(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(CameraPhotoRequestPayload.TYPE, (packet, context) ->
                context.server().execute(() -> CameraPosts.request(context.player(), packet)));
        PayloadTypeRegistry.playS2C().register(MasqueradeSkyPayload.TYPE, MasqueradeSkyPayload.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                MasqueradeConcert.playerDisconnected(handler.getPlayer().getUUID()));
        PayloadTypeRegistry.playS2C().register(MidiSongListPayload.TYPE, MidiSongListPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TourArchivePayload.TYPE, TourArchivePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.miseru.avemujica.network.TourPhotoPayload.TYPE,
                com.miseru.avemujica.network.TourPhotoPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MidiInstrumentSettingsPayload.TYPE, MidiInstrumentSettingsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GuitarPedalboardPayload.TYPE, GuitarPedalboardPayload.CODEC);
        ModRegistry.register();
        MortisSampleSounds.initialize();
        StageConcertSounds.initialize();
        MemberNoteParticles.register();
        MasqueradeEffect.register();
        VillageAudience.register();
        VoiceLibrary.register();
        HostileTargeting.register();
        PlayerBandRoster.register();
        FabricDefaultAttributeRegistry.register(ModRegistry.BAND_MEMBER, BandMemberEntity.attributes());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("avemujica")
                .then(Commands.literal("instrument_colors")
                    .executes(ctx -> {
                        com.miseru.avemujica.career.InstrumentColors.open(ctx.getSource().getPlayerOrException());
                        return 1;
                    }))
                .then(Commands.literal("summon").requires(s->s.hasPermission(2))
                    .then(Commands.argument("member", StringArgumentType.word()).suggests(MEMBER_SUGGESTIONS)
                        .executes(ctx -> summon(ctx.getSource(),StringArgumentType.getString(ctx,"member")))))
                .then(Commands.literal("voice").requires(s -> s.hasPermission(2))
                    .then(Commands.literal("test")
                        .then(Commands.argument("member", StringArgumentType.word()).suggests(MEMBER_SUGGESTIONS)
                            .then(Commands.argument("id", IntegerArgumentType.integer(1, 999))
                                .executes(ctx -> VoiceDirector.test(ctx.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(ctx,"member"), IntegerArgumentType.getInteger(ctx,"id")))))))
                .then(Commands.literal("midi")
                    .then(Commands.literal("guitar")
                        .executes(ctx -> GuitarPedalboard.open(ctx.getSource()))
                        .then(Commands.literal("ui").executes(ctx -> GuitarPedalboard.open(ctx.getSource())))
                        .then(Commands.literal("reset").executes(ctx -> GuitarPedalboard.reset(ctx.getSource())))
                        .then(Commands.literal("preview")
                            .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((ctx,b) -> { b.suggest("doloris_guitar"); b.suggest("mortis"); return b.buildFuture(); })
                                .executes(ctx -> GuitarPedalboard.preview(ctx.getSource(), StringArgumentType.getString(ctx,"role")))))
                        .then(Commands.literal("mix")
                            .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((ctx,b) -> { b.suggest("doloris_guitar"); b.suggest("mortis"); return b.buildFuture(); })
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                    .executes(ctx -> GuitarPedalboard.setMix(ctx.getSource(),
                                            StringArgumentType.getString(ctx,"role"), IntegerArgumentType.getInteger(ctx,"percent"))))))
                        .then(Commands.literal("set")
                            .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((ctx,b) -> { b.suggest("doloris_guitar"); b.suggest("mortis"); return b.buildFuture(); })
                                .then(Commands.argument("preset", StringArgumentType.word())
                                    .suggests((ctx,b) -> {for (String id : GuitarPedalboard.PRESETS) b.suggest(id); return b.buildFuture();})
                                    .executes(ctx -> GuitarPedalboard.set(ctx.getSource(),
                                            StringArgumentType.getString(ctx,"role"), StringArgumentType.getString(ctx,"preset")))))))
                    .then(Commands.literal("instrument")
                        .executes(ctx -> MidiInstrumentSettings.open(ctx.getSource()))
                        .then(Commands.literal("ui").executes(ctx -> MidiInstrumentSettings.open(ctx.getSource())))
                        .then(Commands.literal("reset").executes(ctx -> MidiInstrumentSettings.reset(ctx.getSource())))
                        .then(Commands.literal("volume")
                            .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((ctx,b) -> { for (MemberRole r : MemberRole.values()) b.suggest(r.id); b.suggest("doloris_guitar"); return b.buildFuture(); })
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 200))
                                    .executes(ctx -> MidiInstrumentSettings.setVolume(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "role"), IntegerArgumentType.getInteger(ctx, "percent"))))))
                        .then(Commands.literal("preview")
                            .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((ctx,b) -> { for (MemberRole r : MemberRole.values()) b.suggest(r.id); b.suggest("doloris_guitar"); return b.buildFuture(); })
                                .executes(ctx -> MidiInstrumentSettings.preview(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "role")))))
                        .then(Commands.literal("set")
                            .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((ctx,b) -> {for (MemberRole r : MemberRole.values()) b.suggest(r.id); b.suggest("doloris_guitar"); return b.buildFuture();})
                                .then(Commands.argument("preset", StringArgumentType.word())
                                    .suggests((ctx,b) -> {for (String preset : MidiInstrumentSettings.PRESETS) b.suggest(preset); return b.buildFuture();})
                                    .executes(ctx -> MidiInstrumentSettings.set(ctx.getSource(),
                                            StringArgumentType.getString(ctx,"role"), StringArgumentType.getString(ctx,"preset")))))))
                    .then(Commands.literal("browse")
                        .executes(ctx -> MidiBandPlayer.browse(ctx.getSource(), -1))
                        .then(Commands.argument("entityId", IntegerArgumentType.integer(1))
                            .executes(ctx -> MidiBandPlayer.browse(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "entityId")))))
                    .then(Commands.literal("browse_team")
                        .then(Commands.argument("entityId", IntegerArgumentType.integer(1))
                            .executes(ctx -> MidiBandPlayer.browseTeam(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "entityId")))))
                    .then(Commands.literal("list").executes(ctx -> MidiBandPlayer.list(ctx.getSource())))
                    .then(Commands.literal("status").executes(ctx -> MidiBandPlayer.status(ctx.getSource())))
                    .then(Commands.literal("default_info").executes(ctx -> MidiBandPlayer.defaultInfo(ctx.getSource())))
                    .then(Commands.literal("select_default").executes(ctx -> MidiBandPlayer.selectBundledDefault(ctx.getSource())))
                    .then(Commands.literal("play")
                        .executes(ctx -> MidiBandPlayer.play(ctx.getSource(), "default"))
                        .then(Commands.argument("file",StringArgumentType.string())
                        .suggests(MidiBandPlayer.suggestions())
                        .executes(ctx -> MidiBandPlayer.play(ctx.getSource(), StringArgumentType.getString(ctx,"file")))))
                    .then(Commands.literal("play_team")
                        .then(Commands.argument("entityId", IntegerArgumentType.integer(1))
                            .executes(ctx -> MidiBandPlayer.playTeam(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "entityId"), "default"))
                            .then(Commands.argument("file", StringArgumentType.string())
                                .suggests(MidiBandPlayer.suggestions())
                                .executes(ctx -> MidiBandPlayer.playTeam(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "entityId"),
                                        StringArgumentType.getString(ctx, "file"))))))
                    .then(Commands.literal("play_member")
                        .then(Commands.argument("entityId", IntegerArgumentType.integer(1))
                            .executes(ctx -> MidiBandPlayer.playMember(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "entityId"), "default"))
                            .then(Commands.argument("file", StringArgumentType.string())
                                .suggests(MidiBandPlayer.suggestions())
                                .executes(ctx -> MidiBandPlayer.playMember(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "entityId"), StringArgumentType.getString(ctx, "file"))))))
                    .then(Commands.literal("stop").executes(ctx -> MidiBandPlayer.stop(ctx.getSource()))))
                .then(Commands.literal("customize")
                    .executes(ctx -> { com.miseru.avemujica.career.CharacterCustomization.open(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("account")
                    .executes(ctx -> { TourAccount.open(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("manage")
                    .executes(ctx -> {BandManagement.openMenu(ctx.getSource().getPlayerOrException()); return 1;})
                    .then(Commands.argument("action",StringArgumentType.word())
                        .suggests((ctx,b) -> {for (String s:new String[]{"status","recruit","rehearse","rehearse_enhanced","show","tour","claim","rewards","promote","sound","lights"}) b.suggest(s);return b.buildFuture();})
                        .executes(ctx-> BandManagement.command(ctx.getSource().getPlayerOrException(),StringArgumentType.getString(ctx,"action")))))));
        ServerTickEvents.END_SERVER_TICK.register(OctopusSummons::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(BandManagement::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(MasqueradeConcert::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(MidiBandPlayer::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(GuitarPedalboard::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(VoiceDirector::serverTick);
    }
    private static int summon(CommandSourceStack source,String name) {
        ServerLevel level=source.getLevel(); Vec3 center=source.getPosition(); ServerPlayer owner=source.getPlayer();
        if ("all".equalsIgnoreCase(name)) {
            for(int i=0;i<MemberRole.values().length;i++) {
                double angle=(i-2)*Math.PI/5;
                MemberRole role = MemberRole.values()[i];
                Vec3 destination = center.add(Math.cos(angle)*2.5,.2,Math.sin(angle)*2.5);
                BandMemberEntity npc = owner != null
                        ? PlayerBandRoster.summonOrRecall(level, destination, role, owner.getUUID())
                        : SummonMemberItem.spawn(level, destination, role);
                if(npc!=null && owner!=null) {
                    VoiceDirector.feature(npc, VoiceDirector.Feature.SUMMON);
                }
            }
            source.sendSuccess(()->Component.literal("已尝试召集全队；处于复活冷却的队员需等待。"), true);return 5;
        }
        for(MemberRole role:MemberRole.values()) if(role.id.equalsIgnoreCase(name)) {
            BandMemberEntity npc = owner != null
                    ? PlayerBandRoster.summonOrRecall(level, center, role, owner.getUUID())
                    : SummonMemberItem.spawn(level, center, role);
            if(npc!=null&&owner!=null) {
                VoiceDirector.feature(npc, VoiceDirector.Feature.SUMMON);
            }
            if (npc == null) {
                if (owner != null && !PlayerBandRoster.summonEnabled(level, owner.getUUID(), role)) {
                    source.sendFailure(Component.literal(role.display + "已在角色自定义中设置为暂不召集。"));
                    return 0;
                }
                int wait = owner == null ? 0 : PlayerBandRoster.revivalRemainingSeconds(level, owner.getUUID(), role);
                if (wait > 0) source.sendFailure(Component.literal(role.display + "的复活冷却还剩 " + wait + " 秒。"));
                else source.sendFailure(Component.literal("未能召集 " + role.display + "。"));
                return 0;
            }
            source.sendSuccess(()->Component.literal("已召集："+role.display),true);return 1;
        }
        source.sendFailure(Component.literal("Unknown member: doloris/mortis/timoris/amoris/oblivionis/all"));return 0;
    }
}
