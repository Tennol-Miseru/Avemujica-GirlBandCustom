package com.miseru.avemujica.music;

import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.career.BandManagement;
import com.miseru.avemujica.stage.StageAcoustics;
import com.miseru.avemujica.career.BandCareerData;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.entity.MemberInstrument;
import com.miseru.avemujica.entity.TeamMode;
import com.miseru.avemujica.entity.TeamOrders;
import com.miseru.avemujica.voice.VoiceDirector;
import com.miseru.avemujica.network.MidiSongListPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/** Server-driven Standard MIDI File sequencer with note-block attacks and length-aware imported MIDI backing. */
public final class MidiBandPlayer {
    private MidiBandPlayer() {}
    private static final Path SONG_DIR = FabricLoader.getInstance().getConfigDir().resolve("avemujica").resolve("midi");
    // Large arrangements can have multiple MIDI events per note (note-on, note-off,
    // program changes, CC, etc.). Keep independent limits and bounded audio output.
    private static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;
    private static final int MAX_EVENTS = 3_000_000;
    private static final int MAX_NOTES = 1_000_000;
    private static final int MAX_DURATION_TICKS = 20 * 60 * 60 * 2;
    private static final int MAX_TRACKS = 256;
    private static final int MAX_NOTES_PER_SERVER_TICK = 128; // Audio safety budget; excess notes are deferred, never discarded.
    // Player-configurable timbres. Uika voice defaults to harp, independent Uika guitar to soft electric; Mutsumi unchanged.
    // MIDI tracks/pitches are unchanged: settings affect SOUND only.
    // Explicit MIDI selection takes precedence; automatic performances are per owner.
    private static Playback playback;
    private static final Map<UUID, Playback> automatic = new HashMap<>();
    // Independent combat playback must NEVER be treated as a stage performance:
    // that would stop pathfinding, lock facing, or show Sakiko's keyboard in battle.
    private static final Map<UUID, CombatSession> combat = new HashMap<>();
    private static final int COMBAT_MEMORY_TICKS = 20 * 6;
    private static Song builtInSong;
    // An explicit /midi stop suppresses auto restart until the next stage/team command.
    private static final Set<UUID> suppressed = new HashSet<>();
    // Quiet performance is distinct from not performing: keep the band on stage
    // after an explicit STOP, without resurrecting the old synthetic note ladder.
    private static final Set<UUID> silentOwners = new HashSet<>();
    private static final Map<UUID, Set<UUID>> silentMembers = new HashMap<>();
    private static final String DEFAULT_RESOURCE = "/assets/avemujica/midi/killkiss_default.mid";
    private static final String DEFAULT_NAME = "KillKiss（模组内置）";
    private static final int AUTO_TEAM = 1;
    private static final int AUTO_SHOW = 2;
    private static final int AUTO_SOLO = 3;
    private static final int AUTO_COMBAT = 4;

    /** One reusable cursor per track: no massive event-copy/sort list in memory. */
    private static final class TrackCursor {
        final Track track;
        final int trackOrder;
        int index;
        MidiEvent event;

        TrackCursor(Track track, int trackOrder) {
            this.track = track;
            this.trackOrder = trackOrder;
            this.event = track.get(0);
        }

        boolean advance() {
            index++;
            if (index >= track.size()) return false;
            event = track.get(index);
            return true;
        }
    }
    // Imported MIDI Voice tracks remain assignable to Uika, with fixed HARP.
    // Only her separate electric guitar voice supports alternative OGG presets.
    // Program is captured at NOTE_ON so Sakiko can play keys and strings/pads differently.
    private record Note(int serverTick, int channel, int pitch, int velocity,
                        int program, MemberRole part, boolean vocal, int durationTicks) {
        Note(int tick, int channel, int pitch, int velocity, int program,
             MemberRole part, boolean vocal) {
            this(tick, channel, pitch, velocity, program, part, vocal, 5);
        }
        Note withDuration(int ticks) {
            return new Note(serverTick, channel, pitch, velocity, program, part, vocal,
                    Math.max(1, Math.min(1200, ticks)));
        }
    }
    private record Song(String name, List<Note> notes, int lengthTicks, boolean bundled) {}

    /** The authored MIDI clock plays only while this owner is fighting; never modifies team orders. */
    private static final class CombatSession {
        final Playback music;
        long activeUntil;
        CombatSession(Playback music, long activeUntil) {
            this.music = music;
            this.activeUntil = activeUntil;
        }
    }

    // Repeating quiet note-block samples approximate held tones when MIDI NOTE_OFF
    // occurs later than the onset. This does not synthesize a continuous waveform.
    private static final class HeldTone {
        final UUID musician;
        final MemberRole part;
        final int pitch;
        final SoundEvent sample;
        final float samplePitch;
        final float gain;
        final int initialMixerPercent;
        final int endTick;
        final int interval;
        int nextTick;

        HeldTone(UUID musician, MemberRole part, int pitch, SoundEvent sample,
                 float samplePitch, float gain, int initialMixerPercent,
                 int now, int endTick, int interval) {
            this.musician = musician;
            this.part = part;
            this.pitch = pitch;
            this.sample = sample;
            this.samplePitch = samplePitch;
            this.gain = gain;
            this.initialMixerPercent = initialMixerPercent;
            this.endTick = endTick;
            this.interval = interval;
            this.nextTick = now + interval;
        }
    }

    private static final class Playback {
        final Song song;
        final UUID owner;
        final ServerLevel level;
        final Vec3 stage;
        boolean levelTwoStage;
        final EnumMap<MemberRole, UUID> members;
        final UUID soloMember;
        final int autoSource; // 0 explicit, 1 team rehearsal/show, 2 ticketed show/tour
        TeamMode teamOrder = TeamMode.NONE;
        UUID teamController;
        int elapsed = 0;
        int index = 0;
        // Voice-labelled MIDI notes use the built-in HARP regardless of saved presets.
        // Per-tick acoustic budgets prevent dense imported MIDI chords from
        // turning into dozens of loud simultaneous Minecraft note-block samples.
        final int[] soundedPerRole = new int[MemberRole.values().length];
        int soundedVocals = 0;
        int soundedKeyboardLead = 0;
        final List<HeldTone> heldTones = new ArrayList<>();
        Playback(Song song, UUID owner, ServerLevel level, Vec3 stage,
                 EnumMap<MemberRole, UUID> members, UUID soloMember, int autoSource) {
            this.song = song;
            this.owner = owner;
            this.level = level;
            this.stage = stage;
            this.members = new EnumMap<>(members);
            this.soloMember = soloMember;
            this.autoSource = autoSource;
            // The stage may be behind the player when choosing a song. Resolve
            // the acoustic profile from the *performers* as well, not only the
            // player who opened the MIDI picker.
            refreshStageAcoustics();
        }

        /** Refresh only once per second; never scan blocks on every MIDI note. */
        void refreshStageAcoustics() {
            if (StageAcoustics.nearLevelTwo(level, stage)) {
                levelTwoStage = true;
                return;
            }
            levelTwoStage = false;
            for (UUID id : members.values()) {
                if (!(level.getEntity(id) instanceof BandMemberEntity member)
                        || !member.isAlive() || !owner.equals(member.manager())) continue;
                if (StageAcoustics.nearLevelTwo(level, member.position())) {
                    levelTwoStage = true;
                    return;
                }
            }
        }
        Playback(Song song, ServerPlayer player, EnumMap<MemberRole, UUID> members, UUID soloMember) {
            this(song, player.getUUID(), player.serverLevel(), player.position(), members, soloMember, 0);
        }
    }

    /** Bundled MIDI is read from inside the jar; no config-folder installation required. */
    private static Song defaultSong() throws IOException, InvalidMidiDataException {
        if (builtInSong == null) {
            try (InputStream stream = MidiBandPlayer.class.getResourceAsStream(DEFAULT_RESOURCE)) {
                if (stream == null) throw new IOException("Bundled KILLKiSS MIDI resource is missing");
                // Buffered, mark/reset-capable stream: Java MIDI readers may probe
                // multiple providers and some jar resource streams cannot reset.
                builtInSong = read(MidiSystem.getSequence(new java.io.BufferedInputStream(stream)),
                        DEFAULT_NAME, true);
            }
            if (builtInSong.notes.isEmpty()) throw new IOException("Bundled MIDI has no playable notes");
        }
        return builtInSong;
    }

    public static void allowAutomaticForOwner(UUID owner) {
        if (owner != null) {
            suppressed.remove(owner);
            silentOwners.remove(owner);
            silentMembers.remove(owner);
        }
    }

    private static boolean manualOverrides(UUID owner) {
        return playback != null && playback.owner.equals(owner);
    }

    private static boolean musicalOrder(TeamMode mode) {
        return mode == TeamMode.REHEARSAL || mode == TeamMode.PERFORMANCE;
    }

    /** Recover a saved team order when any participating member loads, even without Sakiko. */
    public static void ensureDefaultForTeam(BandMemberEntity member) {
        if (!(member.level() instanceof ServerLevel level) || member.manager() == null
                || !musicalOrder(member.teamMode())) return;
        UUID owner = member.manager();
        if (manualOverrides(owner) || suppressed.contains(owner)
                || BandManagement.isPerforming(level, owner, member.blockPosition())) return;
        Playback current = automatic.get(owner);
        if (current != null && (current.autoSource != AUTO_TEAM || current.level != level
                || current.teamOrder != member.teamMode()
                || !java.util.Objects.equals(current.teamController, member.teamLeader()))) return;
        if (current != null && member.getUUID().equals(current.members.get(member.role()))) return;
        EnumMap<MemberRole, BandMemberEntity> ensemble = new EnumMap<>(MemberRole.class);
        for (BandMemberEntity npc : level.getEntitiesOfClass(BandMemberEntity.class,
                member.getBoundingBox().inflate(48D, 16D, 48D),
                npc -> TeamOrders.canCommand(level, owner, npc) && npc.teamMode() == member.teamMode()
                        && java.util.Objects.equals(member.teamLeader(), npc.teamLeader()))) {
            ensemble.putIfAbsent(npc.role(), npc);
        }
        Vec3 stage = member.teamStageCenter() == null ? member.position() : member.teamStageCenter();
        ensureDefaultForTeam(level, owner, stage, ensemble);
    }

    /** Start the selected MIDI with the actual members who received the order. */
    public static void ensureDefaultForTeam(ServerLevel level, UUID owner, Vec3 stage,
                                             EnumMap<MemberRole, BandMemberEntity> ensemble) {
        if (ensemble.isEmpty() || manualOverrides(owner) || suppressed.contains(owner)
                || BandManagement.isPerforming(level, owner, BlockPos.containing(stage))) return;
        var first = ensemble.values().iterator().next();
        if (!musicalOrder(first.teamMode())) return;
        EnumMap<MemberRole, UUID> members = new EnumMap<>(MemberRole.class);
        ensemble.forEach((role, member) -> {
            if (member.teamMode() == first.teamMode()
                    && java.util.Objects.equals(first.teamLeader(), member.teamLeader()))
                members.put(role, member.getUUID());
        });
        Playback current = automatic.get(owner);
        if (current != null && current.autoSource == AUTO_TEAM && current.level == level
                && current.teamOrder == first.teamMode()
                && java.util.Objects.equals(current.teamController, first.teamLeader())) {
            // Late arrivals join at the current beat, without restarting the song.
            current.members.putAll(members);
            return;
        }
        startAutomatic(owner, level, stage, members, AUTO_TEAM);
        Playback started = automatic.get(owner);
        if (started != null) {
            started.teamOrder = first.teamMode();
            started.teamController = first.teamLeader();
        }
    }

    private static boolean activeTeamMember(Playback p, BandMemberEntity member) {
        return TeamOrders.canCommand(p.level, p.owner, member) && member.teamMode() == p.teamOrder
                && java.util.Objects.equals(p.teamController, member.teamLeader());
    }

    /**
     * Called only after a real attack was initiated. The selected team MIDI is
     * interpreted on its ORIGINAL note-on timeline, not stepped one pitch per
     * attack (which would destroy chords, rests and tempo). The attack cooldown,
     * projectiles and damage stay independent of the score.
     *
     * This map is intentionally separate from automatic performance playback:
     * isPlayingFor() must never stage-lock a member simply for fighting.
     * Returns false if the selected score cannot be started, allowing the old
     * combat note to remain as a safe fallback.
     */
    public static boolean combatAttack(BandMemberEntity attacker) {
        if (!(attacker.level() instanceof ServerLevel level) || !attacker.isAlive()
                || attacker.manager() == null || attacker.isPerforming() || attacker.isSoloing()
                || attacker.teamMode() == TeamMode.REHEARSAL
                || attacker.teamMode() == TeamMode.PERFORMANCE) return false;
        UUID owner = attacker.manager();
        if (suppressed.contains(owner) || manualOverrides(owner) || automatic.containsKey(owner)
                || BandManagement.isPerforming(level, owner, attacker.blockPosition())) return false;
        long now = level.getGameTime();
        CombatSession session = combat.get(owner);
        if (session != null && session.music.level != level) {
            combat.remove(owner);
            session = null;
        }
        if (session == null) {
            EnumMap<MemberRole, UUID> members = combatMembers(level, owner, attacker);
            try {
                Song selected = selectedTeamSong(level, owner);
                if (selected.notes.isEmpty()) return false;
                Playback music = new Playback(selected, owner, level, attacker.position(),
                        members, null, AUTO_COMBAT);
                // MIDI files can have several seconds of introductory silence.
                // Start at the first real note while preserving all relative timing.
                music.elapsed = selected.notes.get(0).serverTick;
                session = new CombatSession(music, now + COMBAT_MEMORY_TICKS);
                combat.put(owner, session);
            } catch (IOException | InvalidMidiDataException | RuntimeException ex) {
                // Malformed imported scores must never escape the entity attack tick.
                System.err.println("[AveMujica/MIDI] Cannot start combat MIDI: " + ex);
                return false;
            }
        }
        // Different members attacking extend the SAME synchronized performance.
        session.activeUntil = now + COMBAT_MEMORY_TICKS;
        return true;
    }

    private static EnumMap<MemberRole, UUID> combatMembers(ServerLevel level, UUID owner,
                                                            BandMemberEntity attacker) {
        EnumMap<MemberRole, UUID> members = new EnumMap<>(MemberRole.class);
        for (BandMemberEntity candidate : level.getEntitiesOfClass(BandMemberEntity.class,
                attacker.getBoundingBox().inflate(32D, 12D, 32D),
                npc -> npc.isAlive() && owner.equals(npc.manager())
                        && !npc.isPerforming() && !npc.isSoloing()
                        && npc.teamMode() != TeamMode.REHEARSAL
                        && npc.teamMode() != TeamMode.PERFORMANCE)) {
            members.putIfAbsent(candidate.role(), candidate.getUUID());
        }
        // Never suppress the attacking musician if its neighbors are unloaded.
        members.put(attacker.role(), attacker.getUUID());
        return members;
    }

    /** Ticketed concerts and village tours use the song, but stop at the settlement timer. */
    public static void ensureDefaultForShow(ServerLevel level, UUID owner, Vec3 stage,
                                            EnumMap<MemberRole, BandMemberEntity> ensemble) {
        if (owner == null || manualOverrides(owner) || suppressed.contains(owner) || ensemble.isEmpty()) return;
        Playback current = automatic.get(owner);
        if (current != null && current.autoSource == AUTO_SHOW && current.level == level) return;
        EnumMap<MemberRole, UUID> members = new EnumMap<>(MemberRole.class);
        ensemble.forEach((role, member) -> members.put(role, member.getUUID()));
        startAutomatic(owner, level, stage, members, AUTO_SHOW);
    }

    /** Twelve-second interactive solo also uses the default song unless one was selected. */
    public static void ensureDefaultForSolo(BandMemberEntity member) {
        if (!(member.level() instanceof ServerLevel level) || member.manager() == null
                || !member.isSoloing() || manualOverrides(member.manager())
                || suppressed.contains(member.manager())) return;
        UUID owner = member.manager();
        Playback current = automatic.get(owner);
        if (current != null && current.autoSource == AUTO_SOLO &&
                member.getUUID().equals(current.soloMember)) return;
        EnumMap<MemberRole, UUID> one = new EnumMap<>(MemberRole.class);
        one.put(member.role(), member.getUUID());
        try {
            combat.remove(owner);
            automatic.put(owner, new Playback(defaultSong(), owner, level, member.position(),
                    one, member.getUUID(), AUTO_SOLO));
        } catch (IOException | InvalidMidiDataException e) {
            automatic.remove(owner);
            System.err.println("[Ave Mujica] Cannot load solo MIDI: " + e.getMessage());
        }
    }

    /** Auto rehearsal, concert and tour all read the owner's ONE saved team song. */
    private static Song selectedTeamSong(ServerLevel level, UUID owner)
            throws IOException, InvalidMidiDataException {
        BandCareerData save = BandCareerData.get(level);
        BandCareerData.Career career = save.career(owner);
        String selected = career.selectedMidi;
        if (selected == null || selected.equalsIgnoreCase("default") || selected.equalsIgnoreCase("killkiss")
                || isLegacyKiLLKiSSAlias(selected)) {
            // Older save files may have selected a former KiLLKiSS filename.
            // Preserve other custom songs while migrating only known song-title aliases.
            if (selected != null && !selected.equalsIgnoreCase("default")) {
                career.selectedMidi = "default";
                save.setDirty();
            }
            return defaultSong();
        }
        try {
            if (!validName(selected)) throw new IOException("Invalid saved MIDI filename");
            Path path = SONG_DIR.resolve(selected).normalize();
            if (!path.startsWith(SONG_DIR) || !Files.isRegularFile(path))
                throw new IOException("MIDI is missing");
            if (!path.toRealPath().startsWith(SONG_DIR.toRealPath()))
                throw new IOException("MIDI outside song folder");
            if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("MIDI file exceeds size limit");
            Song song = read(path, selected);
            if (song.notes.isEmpty()) throw new IOException("Empty MIDI song");
            return song;
        } catch (IOException | InvalidMidiDataException ex) {
            // An accidentally deleted MIDI must not break village shows or rehearsals.
            career.selectedMidi = "default";
            save.setDirty();
            System.err.println("[Ave Mujica] Team MIDI '" + selected + "' unavailable; restoring KILLKiSS: " + ex);
            return defaultSong();
        }
    }

    private static boolean isLegacyKiLLKiSSAlias(String name) {
        if (name == null) return false;
        String n = name.strip().toLowerCase(Locale.ROOT).replace(" ", "");
        // Only filenames that explicitly identify the original song; a custom
        // song with unrelated title keeps its saved selection.
        return n.equals("killkiss.mid") || n.equals("killkiss.midi")
                || n.equals("avemujica-killkiss.mid")
                || n.equals("avemujica-killkiss.midi")
                || n.equals("ave_mujica-killkiss.mid")
                || n.equals("ave mujica - killkiss.mid")
                || n.equals("ave mujica - killkiss.midi");
    }

    private static void startAutomatic(UUID owner, ServerLevel level, Vec3 stage,
                                       EnumMap<MemberRole, UUID> members, int source) {
        combat.remove(owner); // show/rehearsal has priority; never double the MIDI.
        try {
            automatic.put(owner, new Playback(selectedTeamSong(level, owner), owner, level, stage, members, null, source));
        } catch (IOException | InvalidMidiDataException e) {
            automatic.remove(owner);
            // A missing bundled resource must not crash or block ordinary fallback rehearsal.
            System.err.println("[Ave Mujica] Cannot load bundled MIDI: " + e.getMessage());
        }
    }

    private static boolean automaticValid(Playback p, MinecraftServer server) {
        if (p.level.getServer() != server) return false;
        if (p.autoSource == AUTO_SHOW) {
            return BandManagement.isPerforming(p.level, p.owner, BlockPos.containing(p.stage));
        }
        if (p.autoSource == AUTO_SOLO) {
            return p.soloMember != null && p.level.getEntity(p.soloMember) instanceof BandMemberEntity member
                    && member.isAlive() && member.isSoloing();
        }
        return p.members.values().stream().anyMatch(id ->
                p.level.getEntity(id) instanceof BandMemberEntity member && activeTeamMember(p, member));
    }

    public static SuggestionProvider<CommandSourceStack> suggestions() {
        return (context,builder) -> {
            builder.suggest("default");
            for(String name : names()) builder.suggest(name.contains(" ") ? "\"" + name + "\"" : name);
            return builder.buildFuture();
        };
    }

    public static Path songDirectory() { return SONG_DIR; }

    private static List<String> names() {
        try {
            Files.createDirectories(SONG_DIR);
            try (Stream<Path> files = Files.list(SONG_DIR)) {
                return files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String n = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".mid") || n.endsWith(".midi");
                    })
                    .map(path -> path.getFileName().toString())
                    .filter(MidiBandPlayer::validName)
                    .sorted(String.CASE_INSENSITIVE_ORDER).limit(128).toList();
            }
        } catch (IOException ignored) { return List.of(); }
    }

    /** Opens an actual, paged song-picker GUI using filenames supplied by the logical server. */
    public static int browse(CommandSourceStack src, int memberId) {
        ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (Exception e) { src.sendFailure(Component.literal("Only players can browse MIDI.")); return 0; }
        if (memberId < 0) {
            if (!BandManagement.canUseMidi(player)) {
                src.sendFailure(Component.literal("靠近自己的乐队成员即可选择全队 MIDI。"));
                return 0;
            }
        } else if (!(player.level().getEntity(memberId) instanceof BandMemberEntity npc)
                || !npc.isAlive() || player.distanceToSqr(npc) > 64D || !npc.canBeManagedBy(player)) {
            src.sendFailure(Component.literal("The band member must be nearby and assigned to you."));
            return 0;
        }
        List<String> songs = new ArrayList<>();
        songs.add("default");
        songs.addAll(names());
        ServerPlayNetworking.send(player, new MidiSongListPayload(memberId, List.copyOf(songs)));
        return songs.size();
    }

    /** Team picker opened via Sakiko's personal GUI; no management desk required. */
    public static int browseTeam(CommandSourceStack src, int entityId) {
        ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (Exception ex) { src.sendFailure(Component.literal("Only players may select team songs.")); return 0; }
        if (!isOwnedNearbySakiko(player, entityId)) {
            src.sendFailure(Component.literal("请在祥子身边操作全队选曲。"));
            return 0;
        }
        List<String> songs = new ArrayList<>();
        songs.add("default");
        songs.addAll(names());
        // -1 is the mobile band picker. Values <= -2 identify
        // Sakiko's team picker without changing the existing packet format.
        ServerPlayNetworking.send(player, new MidiSongListPayload(-entityId - 2, List.copyOf(songs)));
        return songs.size();
    }

    private static boolean isOwnedNearbySakiko(ServerPlayer player, int entityId) {
        return player.level().getEntity(entityId) instanceof BandMemberEntity sakiko
                && sakiko.role() == MemberRole.OBLIVIONIS && player.distanceToSqr(sakiko) <= 64D
                && TeamOrders.canCommand(player.serverLevel(), player.getUUID(), sakiko);
    }

    public static int list(CommandSourceStack src) {
        List<String> tracks=names();
        src.sendSuccess(() -> Component.literal("[Ave Mujica] Default: " + DEFAULT_NAME
                + "  (use /avemujica midi play default)"), false);
        src.sendSuccess(() -> Component.literal("[Ave Mujica] Custom MIDI: " + SONG_DIR + " ("+ tracks.size()+" files)"),false);
        for(String name:tracks) src.sendSuccess(() -> Component.literal("  - " + name),false);
        if(tracks.isEmpty()) src.sendSuccess(() -> Component.literal("Copy .mid files into the folder and use /avemujica midi play <file>."),false);
        return tracks.size();
    }
    /** Verifies exactly which KiLLKiSS is packaged and which song a world has selected. */
    public static int defaultInfo(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("[Ave Mujica] Built-in: " + DEFAULT_NAME), false);
        try (InputStream resource = MidiBandPlayer.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (resource == null) throw new IOException("Resource missing: " + DEFAULT_RESOURCE);
            byte[] bytes = resource.readAllBytes();
            String sha = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            src.sendSuccess(() -> Component.literal("Actual bundled MIDI SHA-256: " + sha), false);
        } catch (Exception ex) {
            src.sendFailure(Component.literal("Bundled MIDI verification failed: " + ex.getMessage()));
            return 0;
        }
        try {
            ServerPlayer player = src.getPlayerOrException();
            String selected = BandCareerData.get(player.serverLevel()).career(player.getUUID()).selectedMidi;
            src.sendSuccess(() -> Component.literal("Saved team MIDI: " + selected
                    + " (play 'default' to choose bundled translation)"), false);
        } catch (Exception ignored) { }
        return 1;
    }

    /** Force the saved owner-wide selection back to the NEW bundled MIDI. */
    public static int selectBundledDefault(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            BandCareerData data = BandCareerData.get(player.serverLevel());
            data.career(player.getUUID()).selectedMidi = "default";
            data.setDirty();
            // Stop the previous auto player: the next rehearsal/show tick opens
            // the newly selected file. Explicit manual playback is unchanged.
            automatic.remove(player.getUUID());
            combat.remove(player.getUUID());
            src.sendSuccess(() -> Component.literal("已切换乐队默认曲目：" + DEFAULT_NAME
                    + "。现有播放请重新开始，确保载入新 MIDI。"), false);
            return 1;
        } catch (Exception ex) {
            src.sendFailure(Component.literal("此操作需要玩家身份。"));
            return 0;
        }
    }

    public static int status(CommandSourceStack src) {
        if (playback == null) {
            if (!automatic.isEmpty()) {
                Playback auto = automatic.values().iterator().next();
                src.sendSuccess(() -> Component.literal("[Ave Mujica] Automatic " + auto.song.name
                        + " : " + auto.elapsed/20 + "s / " + auto.song.lengthTicks/20 + "s"
                        + (auto.levelTwoStage ? " · 二级舞台扩音(112格)" : " · 普通传播范围")), false);
                return 1;
            }
            if (!combat.isEmpty()) {
                CombatSession session = combat.values().iterator().next();
                Playback battle = session.music;
                src.sendSuccess(() -> Component.literal("[Ave Mujica] 战斗 MIDI：" + battle.song.name
                        + " · " + battle.elapsed / 20 + "s / " + battle.song.lengthTicks / 20
                        + "s（原曲速度，结束战斗后自动停止）"), false);
                return 1;
            }
            src.sendSuccess(() -> Component.literal("[Ave Mujica] MIDI player idle."),false);
            return 0;
        }
        Playback p=playback;
        src.sendSuccess(() -> Component.literal("[Ave Mujica] " + p.song.name + " : " + p.elapsed/20 + "s / " + p.song.lengthTicks/20 + "s ("+p.index+"/"+p.song.notes.size()+" notes)"
                + (p.levelTwoStage ? " · 二级舞台扩音(112格)" : " · 普通传播范围")),false);
        return 1;
    }
    /** Silent stage is a persistent pose until a fresh play/team command, not a song. */
    public static boolean isSilentFor(BandMemberEntity member) {
        UUID owner = member.manager();
        return owner != null && silentOwners.contains(owner) &&
                (member.teamMode() == TeamMode.REHEARSAL || member.teamMode() == TeamMode.PERFORMANCE
                        || silentMembers.getOrDefault(owner, Set.of()).contains(member.getUUID())
                        || (member.level() instanceof ServerLevel level &&
                            BandManagement.isPerforming(level, owner, member.blockPosition())));
    }

    public static boolean isSilentForOwner(UUID owner) {
        return owner != null && silentOwners.contains(owner);
    }

    private static void rememberQuietMembers(Playback previous) {
        // Stopping a personal solo must end the stage pose instead of putting
        // its only performer into a persistent "silent concert" state.
        if (previous == null || previous.soloMember != null || previous.autoSource == AUTO_SOLO) return;
        silentMembers.computeIfAbsent(previous.owner, k -> new HashSet<>()).addAll(previous.members.values());
    }

    /** Manual STOP must cancel solo timers even when the fallback 12-second
     * solo is running without an active MIDI Playback object.
     */
    private static void cancelSoloForOwner(ServerPlayer player) {
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof BandMemberEntity member && member.isAlive()
                        && player.getUUID().equals(member.manager()) && member.isSoloing()) {
                    member.cancelSolo();
                    Set<UUID> oldQuiet = silentMembers.get(player.getUUID());
                    if (oldQuiet != null) oldQuiet.remove(member.getUUID());
                }
            }
        }
    }

    public static int stop(CommandSourceStack src) {
        ServerPlayer requestingPlayer = src.getEntity() instanceof ServerPlayer player ? player : null;
        UUID requester = requestingPlayer == null ? null : requestingPlayer.getUUID();
        boolean admin = src.hasPermission(2);
        if (requester == null && !admin) {
            src.sendFailure(Component.literal("只有乐队主人或管理员能停止 MIDI。"));
            return 0;
        }
        // Stop only the requesting owner's music; another player's playback must
        // never prevent them from using the Stop button on their own band.
        if (requester != null) {
            if (playback != null && playback.owner.equals(requester)) {
                rememberQuietMembers(playback);
                playback = null;
            }
            Playback oldAuto = automatic.remove(requester);
            combat.remove(requester);
            rememberQuietMembers(oldAuto);
            cancelSoloForOwner(requestingPlayer);
            // Also permit STOP when the fallback rhythm is playing without MIDI.
            silentOwners.add(requester);
            suppressed.add(requester);
            src.sendSuccess(() -> Component.literal("[Ave Mujica] 已停止 MIDI：乐队原地静默待机，重新选曲或下达演奏指令即可恢复。")
                    .withStyle(ChatFormatting.GOLD), false);
            return 1;
        }
        // Console operator can silence all active owners.
        if (playback != null) {
            silentOwners.add(playback.owner);
            suppressed.add(playback.owner);
            rememberQuietMembers(playback);
            playback = null;
        }
        for (Playback old : automatic.values()) {
            silentOwners.add(old.owner);
            suppressed.add(old.owner);
            rememberQuietMembers(old);
        }
        automatic.clear();
        combat.clear();
        src.sendSuccess(() -> Component.literal("[Ave Mujica] 所有当前 MIDI 已静音。"), true);
        return 1;
    }

    /** Called after the owner orders another whole-band behavior. */
    public static boolean stopForOwner(UUID owner) {
        boolean stopped = automatic.remove(owner) != null;
        if (playback != null && playback.owner.equals(owner)) {
            playback = null;
            stopped = true;
        }
        return stopped;
    }

    private static boolean validName(String name) {
        // User-provided basename only: no path traversal / absolute paths / hidden files.
        return name.length() >= 1 && name.length() <= 100 && !name.startsWith(".") &&
            name.chars().noneMatch(c -> c < 32 || c == '/' || c == '\\' || c == ':') &&
            !name.contains("..") &&
            (name.toLowerCase(Locale.ROOT).endsWith(".mid") || name.toLowerCase(Locale.ROOT).endsWith(".midi"));
    }

    public static int play(CommandSourceStack src, String filename) {
        return playInternal(src, filename, null);
    }

    /** Authenticated whole-band selection directly from Sakiko's personal menu. */
    public static int playTeam(CommandSourceStack src, int entityId, String filename) {
        ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (Exception ex) { src.sendFailure(Component.literal("Only players may direct the band.")); return 0; }
        if (!isOwnedNearbySakiko(player, entityId)) {
            src.sendFailure(Component.literal("请在祥子身边开始全队演奏。"));
            return 0;
        }
        return playInternal(src, filename, null);
    }

    public static int playMember(CommandSourceStack src, int entityId, String filename) {
        ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (Exception e) { src.sendFailure(Component.literal("Only players may direct musicians.")); return 0; }
        if (!(player.level().getEntity(entityId) instanceof BandMemberEntity npc)
                || !npc.isAlive() || player.distanceToSqr(npc) > 64D || !npc.canBeManagedBy(player)) {
            src.sendFailure(Component.literal("You must be within 8 blocks of your band member."));
            return 0;
        }
        return playInternal(src, filename, npc);
    }

    private static int playInternal(CommandSourceStack src, String filename,
                                    BandMemberEntity soloMember) {
        ServerPlayer player;
        try { player=src.getPlayerOrException(); }
        catch (Exception e) { src.sendFailure(Component.literal("MIDI playback must be started by a player at the stage.")); return 0; }
        EnumMap<MemberRole, UUID> members = new EnumMap<>(MemberRole.class);
        if (soloMember == null) {
            TeamOrders.nearbyOwned(player).forEach((role, member) -> members.put(role, member.getUUID()));
            if (members.isEmpty()) {
                src.sendFailure(Component.literal("需要至少一名属于你的乐队成员在附近才能启动全队 MIDI。"));
                return 0;
            }
        } else members.put(soloMember.role(), soloMember.getUUID());
        if(playback!=null && !src.hasPermission(2) && !player.getUUID().equals(playback.owner)) {
            src.sendFailure(Component.literal("Another band's MIDI is already playing."));return 0;
        }
        boolean bundled = filename.equalsIgnoreCase("default") || filename.equalsIgnoreCase("killkiss");
        if (!bundled && !validName(filename)) {
            src.sendFailure(Component.literal("Invalid MIDI name (try 'default' or use a file from the MIDI folder)."));
            return 0;
        }
        try {
            Song song;
            if (bundled) {
                song = defaultSong();
            } else {
                Path path = SONG_DIR.resolve(filename).normalize();
                if (!path.startsWith(SONG_DIR) || !Files.isRegularFile(path)) {
                    src.sendFailure(Component.literal("MIDI file not found: " + filename + " (try /avemujica midi list)"));
                    return 0;
                }
                if (!path.toRealPath().startsWith(SONG_DIR.toRealPath()))
                    throw new IOException("MIDI path resolves outside the song folder");
                if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("MIDI file exceeds 32 MiB limit");
                song = read(path, filename);
            }
            if (song.notes.isEmpty()) throw new IOException("No playable notes in MIDI");
            // A team song is a PLAYER-owned choice shared by concerts, village tours
            // and rehearsals. A solo request never overwrites the ensemble selection.
            if (soloMember == null) {
                BandCareerData data = BandCareerData.get(player.serverLevel());
                data.career(player.getUUID()).selectedMidi = bundled ? "default" : filename;
                data.setDirty();
            }
            // An explicit solo song replaces the 12-second fallback solo, not just
            // its audio. Otherwise the old solo timer can requeue KiLLKiSS after
            // the chosen song stops, or leave the GUI looking permanently busy.
            if (soloMember != null) soloMember.cancelSolo();
            // Explicit selection wins; automatic performance is resumed only after it ends.
            automatic.remove(player.getUUID());
            combat.remove(player.getUUID());
            allowAutomaticForOwner(player.getUUID());
            playback = new Playback(song, player, members,
                    soloMember == null ? null : soloMember.getUUID());
            Vec3 formation = BandManagement.preferredFormationCenter(player.serverLevel(),
                    player.blockPosition(), player.position());
            for (UUID id : members.values()) {
                if (player.serverLevel().getEntity(id) instanceof BandMemberEntity member) {
                    // Do not overwrite formal stage marks for team rehearsals / concerts.
                    if (soloMember == null && !member.isPerforming())
                        member.assignStagePosition(formation);
                }
            }
            // Only an actual successful play request triggers a song-selection line.
            // Automatic looping never calls this path, so it cannot spam greetings.
            if (soloMember != null) VoiceDirector.feature(soloMember, VoiceDirector.Feature.SONG_SELECTED);
            else {
                EnumMap<MemberRole, BandMemberEntity> speakers = new EnumMap<>(MemberRole.class);
                for (var entry : members.entrySet()) {
                    if (player.serverLevel().getEntity(entry.getValue()) instanceof BandMemberEntity speaker
                            && speaker.isAlive() && player.getUUID().equals(speaker.manager()))
                        speakers.put(entry.getKey(), speaker);
                }
                VoiceDirector.economyAction(player.serverLevel(), speakers, VoiceDirector.Feature.SONG_SELECTED);
            }
            src.sendSuccess(() -> Component.literal("[Ave Mujica] Playing " + song.name + " ("
                    + song.notes.size() + " notes, " + (song.lengthTicks / 20) + "s; "
                    + members.size() + " nearby band members)."), true);
            return 1;
        } catch (IOException | InvalidMidiDataException ex) {
            System.err.println("[AveMujica/MIDI] Failed to load song: " + filename);
            ex.printStackTrace(System.err);
            src.sendFailure(Component.literal("MIDI 读取失败：" + ex.getMessage()
                    + "；请检查日志 latest.log。"));
            return 0;
        } catch (RuntimeException ex) {
            // A malformed import or a stage/NPC integration error must not escape
            // the Brigadier command callback and shut down the integrated server.
            // Do not swallow JVM Errors (OOM/LinkageError): those require diagnosis.
            System.err.println("[AveMujica/MIDI] MIDI selection/runtime error: " + filename);
            ex.printStackTrace(System.err);
            src.sendFailure(Component.literal("MIDI 启动失败，已阻止崩溃；请把 latest.log 中 [AveMujica/MIDI] 后的错误发给开发者。"));
            return 0;
        }
    }

    /**
     * Route every melodic GM family to a real member. A track label takes priority
     * when it names an instrument; unlike channel-parity routing it never sends
     * guitar parts to Umiri simply because of the MIDI channel number.
     */
    private static MemberRole partFor(int channel, int program, String trackName) {
        if (channel == 9) return MemberRole.AMORIS;
        String label = trackName == null ? "" : trackName.toLowerCase(Locale.ROOT);
        if (containsAny(label, "drum", "perc", "kit", "打击", "鼓组")) return MemberRole.AMORIS;
        // A bowed contrabass is an orchestral string, not Umiri's electric bass.
        // Name matching must run BEFORE the generic "bass" substring.
        if (containsAny(label, "contrabass", "contra bass", "kontrabass", "double bass",
                "doublebass", "string bass", "orchestral bass", "bowed bass", "upright strings",
                "upright bass", "violin", "viola", "cello", "violoncello",
                "小提琴", "大提琴", "低音提琴", "低音提琴组"))
            return MemberRole.OBLIVIONIS;
        // GM 44 (zero-based 43) is CONTRABASS, part of the orchestral string family.
        // Imported tracks named simply 'Bass' must not override an explicit GM bowed-string patch.
        if (program == 43 || (program >= 40 && program <= 51
                && containsAny(label, "bass", "贝斯", "低音吉他")))
            return MemberRole.OBLIVIONIS;
        if (containsAny(label, "bass", "贝斯", "低音吉他")) return MemberRole.TIMORIS;
        if (containsAny(label, "vocal", "voice", "choir", "sing", "主唱", "人声")) return MemberRole.DOLORIS;
        if (containsAny(label, "guitar", "gtr", "吉他")) {
            // Explicitly named Uika guitar stays distinct from Mortis even if both use GM electric programs.
            if (containsAny(label, "uika", "doloris", "初华", "rhythm", "acoustic", "伴奏", "节奏")) return MemberRole.DOLORIS;
            return MemberRole.MORTIS;
        }
        if (containsAny(label, "string", "orchestra", "synth", "pad", "piano", "keyboard",
                "organ", "弦乐", "管弦", "合成", "键盘", "钢琴")) return MemberRole.OBLIVIONIS;
        // GM 0-based program families. Every valid MIDI program is handled.
        if (program >= 32 && program <= 39) return MemberRole.TIMORIS; // real bass
        if (program >= 24 && program <= 31) return MemberRole.MORTIS; // guitars, default lead
        if (program >= 52 && program <= 55) return MemberRole.DOLORIS; // choir/voice
        if (program >= 64 && program <= 79) return MemberRole.DOLORIS; // reeds/flutes -> wordless lead
        if (program >= 40 && program <= 51) return MemberRole.OBLIVIONIS; // strings
        if (program >= 80 && program <= 103) return MemberRole.OBLIVIONIS; // synth/pad/effects
        if (program >= 112 && program <= 119) return MemberRole.AMORIS; // pitched percussion
        if (program >= 104 && program <= 111) return MemberRole.MORTIS; // plucked world instruments
        if (program >= 120 && program <= 127) return MemberRole.AMORIS; // percussion/FX
        return MemberRole.OBLIVIONIS; // keys, organs, brass & all remaining GM programs
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    private static boolean isVocalPart(int program, String label) {
        return (program >= 52 && program <= 55) || (program >= 64 && program <= 79)
                || containsAny(label.toLowerCase(Locale.ROOT), "voice", "vocal", "choir", "主唱", "人声");
    }

    private static String[] readTrackNames(Track[] tracks) {
        String[] names = new String[tracks.length];
        for (int i = 0; i < tracks.length; i++) {
            names[i] = "";
            for (int j = 0; j < Math.min(64, tracks[i].size()); j++) {
                MidiMessage msg = tracks[i].get(j).getMessage();
                if (msg instanceof MetaMessage meta && meta.getType() == 0x03) {
                    names[i] = new String(meta.getData(), java.nio.charset.StandardCharsets.UTF_8);
                    break;
                }
            }
        }
        return names;
    }

    private static Song read(Path path, String name) throws IOException, InvalidMidiDataException {
        return read(MidiSystem.getSequence(path.toFile()), name, false);
    }

    private static Song read(Sequence sequence, String name, boolean bundled) throws IOException {
        Track[] tracks = sequence.getTracks();
        String[] trackNames = readTrackNames(tracks);
        if (tracks.length > MAX_TRACKS) throw new IOException("More than " + MAX_TRACKS + " MIDI tracks");
        if (sequence.getDivisionType() != Sequence.PPQ)
            throw new IOException("Only PPQ MIDI time division is supported");
        int resolution = sequence.getResolution();
        if (resolution <= 0) throw new IOException("Invalid PPQ resolution");

        // Each javax.sound.midi.Track is already ordered by MIDI tick. A small
        // priority queue (at most MAX_TRACKS heads) merges them without copying
        // millions of NOTE_OFF/CC events into another list just to sort them.
        PriorityQueue<TrackCursor> heads = new PriorityQueue<>(
                Comparator.comparingLong((TrackCursor c) -> c.event.getTick())
                        .thenComparingInt(c -> c.trackOrder));
        long eventCount = 0L;
        for (int trackNumber = 0; trackNumber < tracks.length; trackNumber++) {
            Track track = tracks[trackNumber];
            eventCount += track.size();
            if (eventCount > MAX_EVENTS)
                throw new IOException("More than " + MAX_EVENTS + " MIDI events");
            if (track.size() > 0) heads.add(new TrackCursor(track, trackNumber));
        }
        List<Note> notes = new ArrayList<>();
        // Match each NOTE_OFF to its original track/channel/pitch NOTE_ON;
        // CC64 pedal release holds the voice until the pedal is lifted.
        MidiNoteTiming lengths = new MidiNoteTiming((index, duration) ->
                notes.set(index, notes.get(index).withDuration(duration)));
        int[] programs = new int[16];
        long previous = 0, tempo = 500_000L; // default 120 BPM
        double microseconds = 0D;
        while (!heads.isEmpty()) {
            TrackCursor cursor = heads.poll();
            MidiEvent event = cursor.event;
            long tick = event.getTick();
            microseconds += (tick - previous) * (double) tempo / resolution;
            previous = tick;
            if (microseconds > MAX_DURATION_TICKS * 50_000D)
                throw new IOException("MIDI exceeds 2 hour limit");
            MidiMessage message = event.getMessage();
            if (message instanceof MetaMessage meta && meta.getType() == 0x51
                    && meta.getData().length == 3) {
                byte[] bytes = meta.getData();
                int newTempo = ((bytes[0] & 255) << 16) | ((bytes[1] & 255) << 8) | (bytes[2] & 255);
                if (newTempo > 0) tempo = newTempo;
            }
            if (message instanceof ShortMessage shortMsg) {
                int channel = shortMsg.getChannel();
                if (shortMsg.getCommand() == ShortMessage.PROGRAM_CHANGE)
                    programs[channel] = shortMsg.getData1();
                int cmd = shortMsg.getCommand();
                if (cmd == ShortMessage.CONTROL_CHANGE) {
                    lengths.control(channel, shortMsg.getData1(), shortMsg.getData2(), microseconds / 50_000D);
                }
                if (cmd == ShortMessage.NOTE_OFF || (cmd == ShortMessage.NOTE_ON && shortMsg.getData2() == 0)) {
                    lengths.noteOff(cursor.trackOrder, channel, shortMsg.getData1(), microseconds / 50_000D);
                }
                if (cmd == ShortMessage.NOTE_ON && shortMsg.getData2() > 0) {
                    if (notes.size() >= MAX_NOTES)
                        throw new IOException("More than " + MAX_NOTES + " playable MIDI notes");
                    // Route bundled MIDI by its real track names / GM programs,
                    // exactly as custom MIDI. Older KILLKiSS editions used a
                    // different channel layout; fixed channel positions would
                    // misassign the user-transcribed distorted guitar to Sakiko.
                    MemberRole role = partFor(channel, programs[channel], trackNames[cursor.trackOrder]);
                    boolean vocalTrack = role == MemberRole.DOLORIS
                            && isVocalPart(programs[channel], trackNames[cursor.trackOrder]);
                    int onset = (int) Math.round(microseconds / 50_000D);
                    lengths.noteOn(cursor.trackOrder, channel, shortMsg.getData1(),
                            notes.size(), microseconds / 50_000D);
                    notes.add(new Note(onset, channel, shortMsg.getData1(),
                            shortMsg.getData2(), programs[channel], role, vocalTrack));
                }
            }
            if (cursor.advance()) heads.add(cursor);
        }
        lengths.finish(microseconds / 50_000D);
        // Keep ALL source MIDI notes, including Uika's voice melody. For the
        // bundled song only, double that original voice onto Sakiko's synth;
        // avoid copying Mutsumi's dense distorted-guitar chords.
        boolean hasUikaPart = notes.stream().anyMatch(n -> n.part == MemberRole.DOLORIS);
        List<Note> additions = new ArrayList<>();
        if (bundled) {
            // Keep the guitar's established triad thinning and sound untouched.
            // Previous keyboard support borrowed distorted-guitar chord tones;
            // this bundled arrangement instead follows Uika's explicit voice
            // melody, avoiding competing synthetic harmony runs.
            retainBundledGuitarChordVoices(notes);
            // The supplied 10-track KiLLKiSS has just one electric guitar part.
            // Derive a distinctly sparser rhythm accompaniment for Uika without
            // stealing/modifying Mutsumi's lead or changing the authored MIDI.
            addBundledUikaRhythmGuitar(notes, additions);
            addBundledUikaMelodyForSakiko(notes, additions);
        }
        if (!hasUikaPart && !bundled) {
            int melody = findMelodyGuideChannel(notes);
            if (melody >= 0) {
                Map<Integer, Note> chosenByTick = new java.util.LinkedHashMap<>();
                for (Note n : notes) {
                    if (n.channel != melody || n.vocal) continue;
                    Note chosen = chosenByTick.get(n.serverTick);
                    // General imports without explicit vocal tracks get a light
                    // upper melody guide. Bundled KiLLKiSS now uses its own
                    // dedicated keyboard-support pass above, not a copied vocal line.
                    if (chosen == null || n.pitch > chosen.pitch)
                        chosenByTick.put(n.serverTick, n);
                }
                int lastGuitarTick = -100;
                for (Note chosen : chosenByTick.values()) {
                    if (notes.size() + additions.size() >= MAX_NOTES) break;
                    if (chosen.serverTick - lastGuitarTick < 3) continue;
                    if (chosen.pitch < 53) continue;
                    additions.add(new Note(chosen.serverTick, -1, chosen.pitch,
                            Math.min(95, chosen.velocity), 27, MemberRole.DOLORIS, false));
                    lastGuitarTick = chosen.serverTick;
                }
            }
        }
        notes.addAll(additions);
        if (!additions.isEmpty()) notes.sort(Comparator.comparingInt(Note::serverTick));
        int last=notes.isEmpty()?0:notes.get(notes.size()-1).serverTick;
        // Long held end-of-song notes must finish before an automatic loop.
        int lastRelease = 0;
        for (Note note : notes) {
            int end = note.serverTick + Math.min(1200, note.durationTicks);
            if (end > lastRelease) lastRelease = end;
        }
        // Keep the authored KiLLKiSS loop timing exactly as before.
        int length = bundled ? last + 20 : Math.min(MAX_DURATION_TICKS,
                Math.max(last + 20, lastRelease + 6));
        return new Song(name,List.copyOf(notes),length,bundled);
    }

    /**
     * Voice a single overloaded distorted-guitar staff as playable triads.
     * This only changes the in-memory arrangement of bundled KiLLKiSS:
     * notes remain untouched in the original resource and other MIDI imports
     * continue playing with their exact original chords.
     */
    /**
     * Only the bundled KiLLKiSS uses a shared lead line. Sakiko doubles Uika's
     * original VOICE note onsets at the SAME pitch and tick (not Mutsumi's guitar
     * chords). Channel -2 marks this as an in-memory synthesizer layer: the
     * supplied MIDI file, external MIDI routing and original voice remain intact.
     */
    private static void addBundledUikaMelodyForSakiko(List<Note> notes, List<Note> additions) {
        for (Note voice : notes) {
            if (voice.part != MemberRole.DOLORIS || !voice.vocal || voice.channel < 0) continue;
            if (notes.size() + additions.size() >= MAX_NOTES) break;
            additions.add(new Note(voice.serverTick, -2, voice.pitch,
                    Math.min(88, Math.max(52, voice.velocity - 18)), 81,
                    MemberRole.OBLIVIONIS, false, voice.durationTicks));
        }
    }

    /** The virtual -3 channel is a separate Uika rhythm part, never Mortis's track. */
    private static void addBundledUikaRhythmGuitar(List<Note> notes, List<Note> additions) {
        // If a later bundled MIDI has a real Doloris guitar track, use it directly.
        if (notes.stream().anyMatch(n -> n.part == MemberRole.DOLORIS && !n.vocal && n.channel >= 0)) return;
        List<UikaRhythmArrangement.Event> guitar = new ArrayList<>();
        for (Note n : notes) {
            if (isBundledGuitarNote(n))
                guitar.add(new UikaRhythmArrangement.Event(n.serverTick, n.pitch, n.velocity));
        }
        for (UikaRhythmArrangement.Event hit : UikaRhythmArrangement.derive(guitar)) {
            if (notes.size() + additions.size() >= MAX_NOTES) break;
            additions.add(new Note(hit.tick(), -3, hit.pitch(), hit.velocity(), 27,
                    MemberRole.DOLORIS, false));
        }
    }

    private static void retainBundledGuitarChordVoices(List<Note> notes) {
        Map<Integer, List<Note>> chordOnsets = new java.util.LinkedHashMap<>();
        for (Note note : notes) {
            if (isBundledGuitarNote(note)) {
                chordOnsets.computeIfAbsent(note.serverTick, key -> new ArrayList<>()).add(note);
            }
        }
        Set<Note> voices = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (List<Note> chord : chordOnsets.values()) {
            chord.sort(Comparator.comparingInt(Note::pitch));
            if (chord.size() <= 3) {
                voices.addAll(chord);
            } else {
                // Low root, central harmonic note, and high definition note.
                voices.add(chord.get(0));
                voices.add(chord.get((chord.size() - 1) / 2));
                voices.add(chord.get(chord.size() - 1));
            }
        }
        notes.removeIf(note -> isBundledGuitarNote(note) && !voices.contains(note));
    }

    /** Recognize the source GM electric-guitar part, independent of export channel.
     * The new authored MIDI puts it on channel 4 / program 30 instead of 3 / 29.
     * Called only for the bundled arrangement, never for external MIDI files. */
    private static boolean isBundledGuitarNote(Note note) {
        return note.part == MemberRole.MORTIS && note.channel >= 0
                && note.channel != 9 && note.program >= 24 && note.program <= 31;
    }

    private static int findMelodyGuideChannel(List<Note> notes) {
        int[] count = new int[16];
        double[] total = new double[16];
        int[] overlapping = new int[16];
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        for (Note note : notes) if (note.channel >= 0 && note.channel != 9 && !note.vocal) {
            count[note.channel]++;
            total[note.channel] += note.pitch;
            long onset = (((long)note.channel) << 32) | (note.serverTick & 0xffffffffL);
            if (!seen.add(onset)) overlapping[note.channel]++;
        }
        int bestChannel = -1;
        double bestScore = -999D;
        for (int channel = 0; channel < 16; channel++) {
            // A thick piano chord / dual-melody channel is NOT a single vocal
            // guide. Duplicating it onto Uika would muddy an otherwise good MIDI.
            if (channel == 9 || count[channel] < 12 || overlapping[channel] > count[channel] * .18D) continue;
            double mean = total[channel] / count[channel];
            if (mean < 57D) continue;
            double score = mean + Math.min(7D, Math.log1p(count[channel]));
            if (score > bestScore) { bestScore = score; bestChannel = channel; }
        }
        return bestChannel;
    }

    private static boolean includes(Playback p, BandMemberEntity member) {
        return p != null && member.level() == p.level && (p.soloMember != null
                    ? p.soloMember.equals(member.getUUID())
                    : member.manager() != null && member.manager().equals(p.owner)
                        && p.members.containsValue(member.getUUID())
                        && (p.autoSource != AUTO_TEAM || activeTeamMember(p, member)));
    }

    public static boolean hasMusicFor(UUID owner) {
        // Do not include combat here: ticketed shows and stage-lock checks must
        // remain unaffected by an independently playing battle score.
        return owner != null && (manualOverrides(owner) || automatic.containsKey(owner));
    }

    public static boolean isPlayingFor(BandMemberEntity member) {
        if (includes(playback, member)) return true;
        if (member.manager() == null || manualOverrides(member.manager())) return false;
        return includes(automatic.get(member.manager()), member);
    }

    /** Advances one MIDI timeline by one Minecraft game tick. */
    private static boolean advance(Playback p) {
        // A manual MIDI may start while the owner is standing offstage, before
        // the band has reached its stage marks. An immutable startup flag made
        // the ENTIRE song use 16-block vanilla note sounds despite later stage
        // arrival. Re-evaluate the venue at a bounded cadence for every song.
        if (p.elapsed % 20 == 0) p.refreshStageAcoustics();
        java.util.Arrays.fill(p.soundedPerRole, 0);
        p.soundedVocals = 0;
        p.soundedKeyboardLead = 0;
        int emitted = 0;
        // Leave overflow in the queue for the next tick. Never consume a note
        // without emitting it; the previous post-increment silently lost notes.
        while (emitted < MAX_NOTES_PER_SERVER_TICK && p.index < p.song.notes.size()
                && p.song.notes.get(p.index).serverTick <= p.elapsed) {
            emit(p, p.song.notes.get(p.index));
            p.index++;
            emitted++;
        }
        playHeldTones(p);
        p.elapsed++;
        return p.elapsed > p.song.lengthTicks && p.index >= p.song.notes.size();
    }

    /** Return -1 on recoverable playback failure, 0 while running, 1 at end. */
    private static int guardedAdvance(Playback p) {
        try {
            return advance(p) ? 1 : 0;
        } catch (RuntimeException ex) {
            System.err.println("[AveMujica/MIDI] Playback error in " + p.song.name
                    + " at game tick " + p.elapsed + " (note index " + p.index + ")");
            ex.printStackTrace(System.err);
            ServerPlayer owner = p.level.getServer().getPlayerList().getPlayer(p.owner);
            if (owner != null) owner.sendSystemMessage(Component.literal(
                    "[Ave Mujica] MIDI 播放已安全中止；请提交 latest.log 中 [AveMujica/MIDI] 的异常信息。"));
            return -1;
        }
    }

    public static void serverTick(MinecraftServer server) {
        if (playback != null) {
            Playback manual = playback;
            if (server.getPlayerList().getPlayer(manual.owner) == null || manual.level.getServer() != server) {
                playback = null;
            } else {
                int result = guardedAdvance(manual);
                if (result < 0) {
                    playback = null; // abort only the broken MIDI session
                    suppressed.add(manual.owner);
                    silentOwners.add(manual.owner);
                    rememberQuietMembers(manual);
                } else if (result > 0) {
                ServerPlayer owner = server.getPlayerList().getPlayer(manual.owner);
                if (owner != null) owner.sendSystemMessage(Component.literal("[Ave Mujica] MIDI finished: " + manual.song.name + "；乐队进入静默演奏待机。"));
                VoiceDirector.midiFinished(manual.level, manual.owner, manual.members);
                rememberQuietMembers(manual);
                silentOwners.add(manual.owner);
                suppressed.add(manual.owner);
                playback = null;
                }
            }
        }
        Iterator<Map.Entry<UUID, Playback>> it = automatic.entrySet().iterator();
        while (it.hasNext()) {
            Playback auto = it.next().getValue();
            if (manualOverrides(auto.owner) || !automaticValid(auto, server)) {
                it.remove();
            } else {
                int result = guardedAdvance(auto);
                if (result < 0) {
                    it.remove();
                    suppressed.add(auto.owner);
                    silentOwners.add(auto.owner);
                    rememberQuietMembers(auto);
                } else if (result > 0) {
                if (auto.autoSource == AUTO_TEAM) {
                    // Indefinite band rehearsal/performance; loop while the order remains active.
                    auto.elapsed = 0;
                    auto.index = 0;
                    java.util.Arrays.fill(auto.soundedPerRole, 0);
                    auto.soundedVocals = 0;
                    auto.soundedKeyboardLead = 0;
                    auto.heldTones.clear(); // Never leak sustained notes across a song loop.
                } else {
                    // Ticketed performances finish against their independent show
                    // timer; remain silent rather than falling back to synthetic notes.
                    if (auto.autoSource == AUTO_SOLO) {
                        VoiceDirector.midiFinished(auto.level, auto.owner, auto.members);
                    }
                    if (auto.autoSource == AUTO_SHOW) {
                        rememberQuietMembers(auto);
                        silentOwners.add(auto.owner);
                        suppressed.add(auto.owner);
                    }
                    it.remove();
                }
                }
            }
        }
        // Combat MIDI has its own lifetime, original tempo, and saved team score.
        // It does not set performing flags or overwrite stage/team playback.
        Iterator<Map.Entry<UUID, CombatSession>> combatIt = combat.entrySet().iterator();
        while (combatIt.hasNext()) {
            CombatSession session = combatIt.next().getValue();
            Playback music = session.music;
            if (music.level.getServer() != server || music.level.getGameTime() >= session.activeUntil
                    || manualOverrides(music.owner) || automatic.containsKey(music.owner)
                    || suppressed.contains(music.owner)) {
                combatIt.remove();
                continue;
            }
            // Update the visible ensemble without allocating a new song or
            // resetting the beat when another bandmate joins the fight.
            if (music.elapsed % 20 == 0) {
                BandMemberEntity anchor = null;
                for (UUID id : music.members.values()) {
                    if (music.level.getEntity(id) instanceof BandMemberEntity npc && npc.isAlive()
                            && music.owner.equals(npc.manager()) && !npc.isPerforming()
                            && npc.teamMode() != TeamMode.REHEARSAL
                            && npc.teamMode() != TeamMode.PERFORMANCE) {
                        anchor = npc;
                        break;
                    }
                }
                if (anchor == null) {
                    combatIt.remove();
                    continue;
                }
                music.members.clear();
                music.members.putAll(combatMembers(music.level, music.owner, anchor));
            }
            int result = guardedAdvance(music);
            if (result < 0) {
                // Never set silentOwners here: that would interfere with
                // stage performances or the next concert after a MIDI error.
                combatIt.remove();
            } else if (result > 0) {
                music.elapsed = music.song.notes.get(0).serverTick;
                music.index = 0;
                music.heldTones.clear();
                java.util.Arrays.fill(music.soundedPerRole, 0);
                music.soundedVocals = 0;
                music.soundedKeyboardLead = 0;
            }
        }
    }

    /** Count notes at the same authored onset for a single instrument voice.
     * Scan a bounded neighborhood to stay safe even for hostile million-note files.
     * Vocal notes, Uika guitar, and Sakiko's copied melodic lead stay separate.
     */
    private static int simultaneousNotes(Playback p, Note note) {
        int count = 1;
        int group = note.part == MemberRole.DOLORIS ? (note.vocal ? 1 : 2)
                : note.part == MemberRole.OBLIVIONIS && note.channel == -2 ? 3 : 0;
        List<Note> notes = p.song.notes;
        for (int d : new int[]{-1, 1}) {
            int i = p.index + d;
            int examined = 0;
            while (i >= 0 && i < notes.size() && examined++ < 64) {
                Note n = notes.get(i);
                if (n.serverTick != note.serverTick) break;
                int ng = n.part == MemberRole.DOLORIS ? (n.vocal ? 1 : 2)
                        : n.part == MemberRole.OBLIVIONIS && n.channel == -2 ? 3 : 0;
                if (n.part == note.part && ng == group) count++;
                i += d;
            }
        }
        return Math.min(24, count);
    }

    private static void emit(Playback p, Note note) {
        // Solo sessions play only that member's own MIDI track, not every part on one instrument.
        if (p.soloMember != null && !p.members.containsKey(note.part)) return;
        UUID musician = p.soloMember != null ? p.soloMember : p.members.get(note.part);
        // Absent / unloaded / dead performer: never create a phantom instrument at
        // the stage center. The shared ensemble timeline is not restarted here.
        if (musician == null || !(p.level.getEntity(musician) instanceof BandMemberEntity npc)
                || !npc.isAlive() || !p.owner.equals(npc.manager())) return;
        if (p.autoSource == AUTO_TEAM && !activeTeamMember(p, npc)) return;
        if (p.autoSource == 0 && p.soloMember == null && !TeamOrders.canCommand(p.level, p.owner, npc)) return;
        if (p.autoSource == AUTO_COMBAT && (npc.isPerforming() || npc.isSoloing()
                || npc.teamMode() == TeamMode.REHEARSAL
                || npc.teamMode() == TeamMode.PERFORMANCE)) return;
        boolean uikaVoice = note.part == MemberRole.DOLORIS && note.vocal;
        boolean uikaGuitar = note.part == MemberRole.DOLORIS && !note.vocal;
        if (uikaGuitar && !npc.usesPerformanceInstrument()) return;
        MemberInstrument physical = npc.instrumentType();
        int playedPitch = uikaVoice ? note.pitch
                : physical.notePitch(note.pitch, note.channel == 9 || note.part == MemberRole.AMORIS);
        Vec3 position = npc.position().add(0, npc.getBbHeight() * 0.75, 0);
        // The vocal and accompaniment channels may share pitch. Only the
        // matching channel can release a sustained note.
        if (!uikaGuitar) p.heldTones.removeIf(h -> h.musician.equals(musician) && h.pitch == note.pitch
                && h.part == note.part);
        // MIDI velocities and percussion note numbers drive the player animations.
        // Voice/harp triggers NO guitar impulses. Only a Doloris guitar note
        // can display the guitar and strumming arms; this also works in solo MIDI.
        if (uikaVoice) npc.singNote(playedPitch, note.velocity);
        else if (uikaGuitar && (p.autoSource != AUTO_COMBAT || !npc.alternateCombat()))
            npc.playUikaGuitarNote(playedPitch, note.velocity);
        else npc.playNote(playedPitch, note.velocity);
        // No fixed role-based NOTE_ON suppression: it muted real chord notes,
        // transients and drum accents and made the original MIDI sound thin.
        // Every note reaches the vanilla sound engine; simultaneous notes get
        // gently attenuated instead of disappearing. The global 128/tick CPU
        // budget still defers overflow rather than dropping MIDI events.
        if (note.velocity <= 0) return;
        boolean copiedUikaLead = p.song.bundled && note.part == MemberRole.OBLIVIONIS
                && note.channel == -2 && note.program == 81;
        boolean violinBacking = p.song.bundled && note.part == MemberRole.OBLIVIONIS
                && note.channel == 0 && note.program == 40;
        int chordSize;
        if (copiedUikaLead) {
            // Keep the lead separate from Sakiko's quieter orchestral backing.
            chordSize = p.soundedKeyboardLead++;
        } else if (note.vocal) {
            chordSize = p.soundedVocals++;
        } else {
            chordSize = p.soundedPerRole[note.part.ordinal()]++;
        }
        BandCareerData.Career ownerCareer = BandCareerData.get(p.level).career(p.owner);
        // Vocal and guitar parts have independent presets. Human voice uses
        // HARP by default; custom singing OGG is opt-in, never a guitar sample.
        String instrument = uikaGuitar
                ? MidiInstrumentSettings.forUikaGuitar(ownerCareer)
                : MidiInstrumentSettings.forRole(ownerCareer, note.part);
        boolean drums = !uikaVoice && (instrument.equals("drums")
                || (instrument.equals("auto") && physical == MemberInstrument.DRUMS));
        SoundEvent sound = drums ? vanillaDrum(playedPitch) : copiedUikaLead && instrument.equals("auto")
                ? SoundEvents.NOTE_BLOCK_PLING.value() // clear but restrained synthesizer melody
                : (instrument.equals("auto") ? vanillaSound(note)
                : MidiInstrumentSettings.sound(instrument, uikaGuitar, uikaVoice));
        // Vanilla note-block samples have a fixed base pitch. Octave-fold only
        // beyond the +/-12 semitone playable range, avoiding extreme squeals.
        MemberRole soundProfile = uikaVoice ? MemberRole.DOLORIS : physical.pose;
        int root = switch (soundProfile) {
            case TIMORIS -> 38;     // KILLKiSS finger bass: pitches 26-40, do NOT shift up 2 octaves
            case MORTIS -> 45;      // 7-string guitar (33-59)
            case DOLORIS -> 57; // preserve Uika's accompaniment register, not an octave jump
            case OBLIVIONIS -> isSynthStrings(note.program) ? 72 : 69;
            default -> 60;
        };
        int key = playedPitch;
        while (key < root - 12) key += 12;
        while (key > root + 12) key -= 12;
        float pitch = drums ? 1F
                : (float) Math.pow(2.0, (key - root) / 12.0);
        float dynamics = Math.max(0F, Math.min(1F, note.velocity / 127F));
        float volume = switch (note.part) {
            case OBLIVIONIS -> isSynthStrings(note.program) ? 0.54F + 0.34F * dynamics
                    : 0.56F + 0.36F * dynamics;
            // Preserve Uika's established velocity response while her default
            // instrument now uses HARP instead of guitar.
            case DOLORIS -> 0.45F + 0.38F * dynamics;
            case MORTIS -> 0.45F + 0.38F * dynamics;
            case TIMORIS -> 0.50F + 0.40F * dynamics;
            case AMORIS -> 0.48F + 0.42F * dynamics;
        };
        // Real simultaneous notes are normalized as a GROUP, not by emission
        // order. This keeps the second line of a duet from becoming quieter
        // just because its NOTE_ON happened to be visited later in the file.
        int simultaneous = simultaneousNotes(p, note);
        // Soft polyphony normalization instead of dropping notes. A two-note
        // chord retains nearly all of its impact; giant imported chords remain
        // bounded in loudness without removing their musical information.
        // Drums need their own punch: simultaneous kick/snare/hat are a groove,
        // not a polyphonic chord to compress as heavily as melodic instruments.
        // Kept separate from guitar/synth volume to preserve the existing mix.
        if (drums) {
            float drumPresence = switch (playedPitch) {
                case 35, 36 -> 1.62F;             // kick
                case 38, 40 -> 1.72F;             // snare
                case 42, 44, 46 -> 1.39F;         // closed / pedal / open hat
                case 49, 51, 57, 59 -> 1.44F;     // cymbals
                default -> 1.48F;                 // toms and remaining percussion
            };
            volume = Math.min(1.50F, volume * drumPresence);
            volume *= 1F / (1F + Math.max(0, simultaneous - 1) * 0.040F);
        } else {
            volume *= 1F / (1F + Math.max(0, simultaneous - 1) * 0.09F);
        }
        // Bundled transcription has an overly dense distorted guitar chord
        // staff. Lower its masking level, not its established vanilla guitar +
        // electric BIT timbre; unrelated MIDI and pedalboard settings are intact.
        if (p.song.bundled && isBundledGuitarNote(note))
            volume *= .68F;
        // No competing bright violin melody: its original track is now a soft
        // string bed while the copied Uika notes carry Sakiko's melodic line.
        if (violinBacking) volume *= .34F;
        if (copiedUikaLead) volume *= .58F;
        // Previous 0.39 multiplier rendered Uika's in-memory guitar inaudible.
        // Use a separate guitar-only level, keeping the voice and Mortis untouched.
        if (uikaGuitar) {
            if (note.channel == -1) volume *= 1.05F;
            if (note.channel == -3) volume *= 1.12F;
            volume = Math.min(1.12F, Math.max(0.66F, volume * 1.18F));
        }
        // Named Voice/Vocal notes retain their original melody on the selected
        // member instrument (new default for Uika: HARP).
        // Keep the actual Voice/Vocal melody clear above guitars and drums.
        // This is a voice-only gain; her guitar and all six user faders are unchanged.
        if (uikaVoice) volume *= 1.40F;
        // Very short imported MIDI notes should not ring as loudly as a held
        // lead tone. Bundled arrangements keep their established dynamics.
        if (!p.song.bundled && note.durationTicks <= 2 && !drums)
            volume *= .80F;
        // Apply owner mix to all MIDI timbres, effects and held-note layers.
        String volumeChannel = uikaGuitar ? MidiInstrumentSettings.UIKA_GUITAR_ID : note.part.id;
        int mixerPercent = MidiInstrumentSettings.volumePercent(ownerCareer, volumeChannel);
        volume *= mixerPercent / 100F;
        if (p.autoSource == AUTO_COMBAT) volume *= .42F;
        if (volume <= 0F) return;
        // Only the LARGE physical stage amplifies the server-side audio.
        // Multiplying after the user mix preserves mute and all six faders.
        volume = StageAcoustics.concertVolume(volume, p.levelTwoStage);
        BlockPos block = BlockPos.containing(position);
        // OGG samples are a separate opt-in instrument preset. The previous
        // pedalboard presets/settings stay saved and work again whenever the
        // musician selects one of the existing electric/guitar presets.
        // Guitar pedalboard is opt-in. If bypassed, run the exact v1.1.7
        // sound logic below unchanged (including Mutsumi's current Bit mix).
        // A configured guitar pedalboard must not silently turn Uika's new HARP
        // default back into a guitar. Keep Mutsumi's existing pedal path intact.
        boolean guitarTimbre = instrument.equals("guitar") || instrument.equals("electric")
                || instrument.equals("electric_soft");
        boolean pedalPlayed = guitarTimbre && physical.guitars() && GuitarPedalboard.isGuitar(note.part)
                && GuitarPedalboard.play(ownerCareer, note.part, note.vocal, p.level, block, volume, pitch, p.levelTwoStage);
        if (!pedalPlayed) p.level.playSound(null, block, StageConcertSounds.forStage(sound, p.levelTwoStage),
                SoundSource.RECORDS, volume, pitch);
        // Soft re-attacks follow NOTE_OFF timing without repeating guitar picking
        // animations or layering noisy OGG/electric/drum samples.
        queueHeldTone(p, note, musician, instrument, sound, pitch, volume, mixerPercent, pedalPlayed);
        // No bundled OGG sustain pads: long notes are handled solely by
        // queueHeldTone/playHeldTones using the selected vanilla note-block sound.
        // Mutsumi only: restrained electronic pick attack on separated accents.
        // The existing GUITAR is still the dominant sound; electronic layers
        // cannot take over Uika's part or any other member's MIDI events.
        // Electric guitar is distinctly audible on *every* guitar note, not
        // an almost silent transient that might not trigger anywhere in a song.
        // Keep GUITAR as the main pluck; add a fixed same-pitch vanilla BIT layer.
        if (!pedalPlayed && (instrument.equals("electric") || instrument.equals("electric_soft"))) {
            // Mortis's established blend is untouched; Uika gets a firmer electric attack.
            float electricMix = uikaGuitar
                    ? (instrument.equals("electric") ? 0.62F : 0.38F)
                    : (instrument.equals("electric") ? 0.66F : 0.32F);
            p.level.playSound(null, block, StageConcertSounds.forStage(SoundEvents.NOTE_BLOCK_BIT.value(), p.levelTwoStage),
                    SoundSource.RECORDS, Math.min(p.levelTwoStage
                            ? StageAcoustics.MAX_CONCERT_VOLUME : 1F, volume * electricMix), pitch);
        }
        // Voice-only solos still show Uika's colored notes above her head,
        // without any guitar model or picking animation.
        if (note.channel != 9 && ((note.part == MemberRole.DOLORIS && note.vocal)
                ? p.soundedVocals % 3 == 0 : p.index % 8 == 0))
            com.miseru.avemujica.particle.MemberNoteParticles.emit(p.level, npc.role(), position.x, position.y + 0.25, position.z,
                    1, 0.10, 0.08, 0.10, 0.0);
    }

    /** Only clearly held, non-percussive sounds receive restrained repeated samples. */
    private static void queueHeldTone(Playback p, Note note, UUID musician,
                                      String instrument, SoundEvent sound,
                                      float pitch, float volume, int mixerPercent, boolean pedalPlayed) {
        if (pedalPlayed || note.durationTicks < 12 || p.heldTones.size() >= 24
                || instrument.equals("drums") || instrument.startsWith("ogg_") || note.part == MemberRole.AMORIS
                || note.part == MemberRole.TIMORIS || note.part == MemberRole.MORTIS
                || (note.part == MemberRole.DOLORIS && !note.vocal)) return;
        boolean voice = note.part == MemberRole.DOLORIS && note.vocal;
        boolean suitable = voice || instrument.equals("harp") || instrument.equals("flute")
                || (note.part == MemberRole.OBLIVIONIS && isSynthStrings(note.program));
        if (!suitable) return;
        // Five Minecraft ticks between low-level HARP attacks. Near silence is
        // better than a loud tremolo, particularly in polyphonic vocal melodies.
        int interval = voice ? 5 : 6;
        int endTick = note.serverTick + Math.min(400, note.durationTicks);
        if (p.elapsed + interval >= endTick) return;
        // More audible held vocal notes, still subject to NOTE_OFF and the live
        // Doloris volume fader. Do not change non-vocal sustain balance.
        float gain = volume * (voice ? .43F : .27F);
        p.heldTones.add(new HeldTone(musician, note.part, note.pitch,
                sound, pitch, gain, mixerPercent, p.elapsed, endTick, interval));
    }

    private static void playHeldTones(Playback p) {
        int emitted = 0;
        Iterator<HeldTone> iterator = p.heldTones.iterator();
        while (iterator.hasNext()) {
            HeldTone h = iterator.next();
            if (p.elapsed >= h.endTick || !(p.level.getEntity(h.musician) instanceof BandMemberEntity npc)
                    || !npc.isAlive() || !p.owner.equals(npc.manager())
                    || (p.autoSource == AUTO_TEAM && !activeTeamMember(p, npc))) {
                iterator.remove();
                continue;
            }
            if (p.elapsed < h.nextTick || emitted >= 12) continue;
            // Envelope fades toward the actual NOTE_OFF, avoiding a hard cutoff.
            float release = Math.min(1F, (h.endTick - p.elapsed) / 8F);
            // The six channel faders must remain effective even on an already-sounding
            // sustained note; muting must stop the next repeat immediately.
            int currentMixer = MidiInstrumentSettings.volumePercent(
                    BandCareerData.get(p.level).career(p.owner), h.part.id);
            float volume = h.initialMixerPercent > 0
                    ? h.gain * release * currentMixer / h.initialMixerPercent : 0F;
            if (volume > .01F) {
                p.level.playSound(null, BlockPos.containing(npc.position()),
                        StageConcertSounds.forStage(h.sample, p.levelTwoStage),
                        SoundSource.RECORDS, volume, h.samplePitch);
                emitted++;
            }
            h.nextTick = p.elapsed + h.interval;
        }
    }

    private static SoundEvent vanillaDrum(int pitch) {
        return switch (pitch) {
            case 35, 36, 41, 43, 45 -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
            case 38, 40, 37, 39 -> SoundEvents.NOTE_BLOCK_SNARE.value();
            case 42, 44, 46, 49, 51, 57, 59 -> SoundEvents.NOTE_BLOCK_HAT.value();
            default -> SoundEvents.NOTE_BLOCK_HAT.value();
        };
    }

    /** Multiple original note-block timbres, chosen from MIDI GM program. */
    private static SoundEvent vanillaSound(Note note) {
        if (note.part == MemberRole.DOLORIS && note.vocal) return SoundEvents.NOTE_BLOCK_HARP.value(); // default vocal fallback only; opt-in OGG overrides above
        if (note.part == MemberRole.AMORIS) return vanillaDrum(note.pitch);
        return switch (note.part) {

            case OBLIVIONIS -> sakikoSynthSound(note.program);
            case DOLORIS -> SoundEvents.NOTE_BLOCK_HARP.value(); // Never route voice to an OGG guitar
            case MORTIS -> SoundEvents.NOTE_BLOCK_GUITAR.value();  // Mutsumi: lead base + gentle electric attack
            case TIMORIS -> SoundEvents.NOTE_BLOCK_BASS.value();
            case AMORIS -> SoundEvents.NOTE_BLOCK_SNARE.value();
        };
    }

    private static SoundEvent sakikoSynthSound(int program) {
        if (program >= 0 && program <= 7) return SoundEvents.NOTE_BLOCK_PLING.value();
        if (program >= 8 && program <= 15) return SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
        if (program >= 16 && program <= 23) return SoundEvents.NOTE_BLOCK_BIT.value();
        if (program >= 40 && program <= 51) return SoundEvents.NOTE_BLOCK_HARP.value(); // GM strings 40-51, including String Ensemble 1 at 48
        if (program >= 80 && program <= 87) return SoundEvents.NOTE_BLOCK_BIT.value();
        if (program >= 88 && program <= 95) return SoundEvents.NOTE_BLOCK_CHIME.value();
        if (program >= 96 && program <= 103) return SoundEvents.NOTE_BLOCK_BELL.value();
        if (program >= 56 && program <= 63) return SoundEvents.NOTE_BLOCK_DIDGERIDOO.value();
        return SoundEvents.NOTE_BLOCK_HARP.value();
    }

    private static boolean isSynthStrings(int program) {
        return (program >= 40 && program <= 51) || (program >= 88 && program <= 95);
    }

}
