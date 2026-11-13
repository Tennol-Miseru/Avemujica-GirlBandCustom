package com.miseru.avemujica.voice;

import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.entity.TeamMode;
import com.miseru.avemujica.voice.VoiceLibrary.Cue;
import com.miseru.avemujica.voice.VoiceLibrary.DailyCue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-only voice sequencing. Multiple members never speak over each other in one band.
 * Queue is bounded and transient; reloading a world does not persist stale announcements.
 * Casual lines reuse only semantically compatible existing assets; leaderboard
 * claims are never used without an actual leaderboard. */
public final class VoiceDirector {
    private VoiceDirector() {}
    public enum Context { IDLE, DAILY_ACTION, REST, REHEARSAL_START, PERFORMANCE_START, PERFORMANCE_PHRASE, GREETING, SHOP }
    // A queued sound is fully resolved at enqueue time so old and new voice banks share
    // the same sequencing/cooldown rules without a second independent voice scheduler.
    private record Pending(UUID memberId, MemberRole role, SoundEvent sound,
                           ResourceKey<Level> dimension, int durationTicks, long expiresAt) {}
    private static final class Group {
        private final ArrayDeque<Pending> waiting = new ArrayDeque<>();
        private long nextPlayAt;
        private long nextAmbientAt;
        private MemberRole lastAmbientRole;
        private long lastAmbientAt;
        private DailyCue lastDailyCue;
        private final EnumMap<MemberRole, Long> lastFeatureByRole = new EnumMap<>(MemberRole.class);
        private long lastFeatureAt;
        private Feature lastFeature;
        private String lastFeatureClip;
    }
    private static final Map<UUID, Group> GROUPS = new HashMap<>();
    private static final MemberRole[] SPEAKING_ORDER = {
            MemberRole.OBLIVIONIS, MemberRole.DOLORIS, MemberRole.MORTIS,
            MemberRole.TIMORIS, MemberRole.AMORIS};

    private static void enqueue(UUID owner, BandMemberEntity musician, SoundEvent event,
                                int durationTicks, long now) {
        if (owner == null || musician == null || !musician.isAlive() || event == null) return;
        Group group = GROUPS.computeIfAbsent(owner, k -> new Group());
        if (group.waiting.size() >= 10) return; // Bounded queue: never wipe earlier results.
        group.waiting.addLast(new Pending(musician.getUUID(), musician.role(), event,
                musician.level().dimension(), durationTicks, now + 900L));
    }

    private static void add(UUID owner, BandMemberEntity musician, Cue cue, long now) {
        if (musician == null || cue == null) return;
        enqueue(owner, musician, VoiceLibrary.sound(musician.role(), cue),
                VoiceLibrary.durationTicks(musician.role(), cue), now);
    }

    private static void addDaily(UUID owner, BandMemberEntity musician, DailyCue cue, long now) {
        if (musician == null || cue == null) return;
        enqueue(owner, musician, VoiceLibrary.dailySound(musician.role(), cue),
                VoiceLibrary.dailyDurationTicks(musician.role(), cue), now);
    }

    /** Intentional gameplay-event cues. Each clip is selected by its actual subtitle,
     * not by a misleading identifier (BATTLE_* are leaderboard/result dialogue). */
    public enum Feature {
        SUMMON, FOLLOW, REHEARSAL, PERFORMANCE, REST, STANDBY, FREE,
        INDIVIDUAL_STANDBY, PATROL, GUARD, INDIVIDUAL_REST,
        SOLO, HEALED, COMBAT_UNLOCK, COMBAT_ON, COMBAT_OFF,
        SHOW_START, TOUR_START, PROMOTION, SOUND_UPGRADE, LIGHT_UPGRADE,
        CLAIM_REWARD, SONG_SELECTED
    }
    private record Choice(Cue old, DailyCue daily) { }
    private static Choice old(Cue cue) { return new Choice(cue, null); }
    private static Choice daily(DailyCue cue) { return new Choice(null, cue); }
    private static Choice one(ServerLevel world, Choice... choices) {
        return choices.length == 0 ? null : choices[world.getRandom().nextInt(choices.length)];
    }

    /** Return null instead of an unrelated line. All choices have been checked
     * against DAILY_VOICE_INDEX_v089.csv and VOICE_INDEX.csv. */
    private static Choice featureChoice(MemberRole role, Feature feature, ServerLevel world, BandMemberEntity npc) {
        long day = world.getDayTime() % 24000L;
        return switch (feature) {
            case SUMMON -> switch (role) {
                case OBLIVIONIS, MORTIS, TIMORIS, AMORIS -> daily(DailyCue.GREETING);
                // Uika's greeting explicitly says "good morning".
                case DOLORIS -> day < 6500L ? daily(DailyCue.GREETING) : null;
            };
            case FOLLOW, GUARD -> switch (role) {
                case OBLIVIONIS -> one(world, old(Cue.SPECIAL_TRAINING), daily(DailyCue.COMMON_3));
                case TIMORIS -> old(Cue.LEVEL_UP); // "受教了" / acknowledged
                case AMORIS -> old(Cue.SKILL_UP); // "理所当然" / confident
                case MORTIS, DOLORIS -> null;
            };
            case REHEARSAL, SOLO -> switch (role) {
                case OBLIVIONIS -> one(world, old(Cue.SPECIAL_TRAINING), daily(DailyCue.COMMON_2));
                case DOLORIS -> daily(DailyCue.LIVE_PREP_2); // "全力去唱歌"
                case MORTIS -> one(world, old(Cue.SKILL_UP), old(Cue.AWAKENING));
                case TIMORIS -> daily(DailyCue.COMMON_3); // "喜欢检查音响器材"
                case AMORIS -> daily(DailyCue.LIVE_PREP_1);
            };
            case PERFORMANCE, SHOW_START, TOUR_START -> switch (role) {
                case OBLIVIONIS -> one(world, daily(DailyCue.LIVE_PREP_2), daily(DailyCue.LIVE_PREP_1));
                case DOLORIS -> one(world, daily(DailyCue.LIVE_PREP_1), daily(DailyCue.LIVE_PREP_2));
                case MORTIS -> daily(DailyCue.LIVE_PREP_2);
                case TIMORIS -> one(world, daily(DailyCue.LIVE_PREP_1), daily(DailyCue.LIVE_PREP_2));
                case AMORIS -> one(world, daily(DailyCue.LIVE_PREP_1), daily(DailyCue.LIVE_PREP_2));
            };
            case REST, INDIVIDUAL_REST -> switch (role) {
                case OBLIVIONIS -> daily(DailyCue.COMMON_1); // reading + tea
                case MORTIS -> day > 12000L ? daily(DailyCue.LIVE_PREP_1) : old(Cue.SCORE_C);
                case TIMORIS -> day > 12500L && day < 23500L && npc.recentlyPerformed() ? old(Cue.LIVE_CLEAR) : null;
                case AMORIS -> npc.recentlyPerformed() ? old(Cue.LIVE_FC) : null;
                case DOLORIS -> npc.recentlyPerformed() ? old(Cue.SCORE_C) : null;
            };
            case STANDBY, INDIVIDUAL_STANDBY -> switch (role) {
                case OBLIVIONIS -> old(Cue.SPECIAL_TRAINING); // "不要大意，继续前进吧"
                default -> null; // Existing growth remarks do not acknowledge standby.
            };
            case PATROL -> switch (role) {
                case OBLIVIONIS -> old(Cue.SPECIAL_TRAINING); // "不要大意，继续前进吧"
                case TIMORIS -> old(Cue.SPECIAL_TRAINING); // "就保持这个状态继续吧"
                default -> null; // Growth remarks are not patrol commands.
            };
            case FREE -> switch (role) {
                case OBLIVIONIS -> daily(DailyCue.COMMON_3);
                case MORTIS -> daily(DailyCue.COMMON_2);
                case TIMORIS -> daily(DailyCue.COMMON_1);
                case AMORIS -> daily(DailyCue.COMMON_2);
                case DOLORIS -> daily(DailyCue.COMMON_2);
            };
            case HEALED -> switch (role) {
                case OBLIVIONIS -> null; // "感谢祝福" does not mean fed/healed
                case MORTIS -> old(Cue.LEVEL_UP); // "太好了"
                case TIMORIS -> null;
                case AMORIS -> old(Cue.LEVEL_UP); // "好耶"
                case DOLORIS -> null;
            };
            case COMBAT_UNLOCK -> switch (role) {
                case OBLIVIONIS -> one(world, old(Cue.SKILL_UP), daily(DailyCue.RANK_GAIN));
                case DOLORIS -> one(world, old(Cue.LEVEL_UP), daily(DailyCue.LEVEL_GAIN));
                case TIMORIS -> one(world, old(Cue.SKILL_UP), old(Cue.AWAKENING));
                default -> null;
            };
            case COMBAT_ON -> switch (role) {
                case OBLIVIONIS -> one(world, old(Cue.SPECIAL_TRAINING), daily(DailyCue.COMMON_3));
                case DOLORIS -> null; // "传达给更多人" is not a chainsaw-mode line.
                case TIMORIS -> old(Cue.SPECIAL_TRAINING);
                default -> null;
            };
            case COMBAT_OFF -> switch (role) {
                case OBLIVIONIS -> old(Cue.SPECIAL_TRAINING); // "不要大意"
                case DOLORIS -> null; // No suitable deactivation line in her bank.
                case TIMORIS -> null; // The level-up thanks line is not a mode-off response.
                default -> null;
            };
            case PROMOTION -> switch (role) {
                // These are actual messages about popularity / reaching an audience.
                case DOLORIS -> old(Cue.SKILL_UP);
                case OBLIVIONIS -> daily(DailyCue.COMMON_2); // composing/directing work
                default -> null;
            };
            case SOUND_UPGRADE -> role == MemberRole.TIMORIS ? daily(DailyCue.COMMON_3) : null;
            case LIGHT_UPGRADE -> null; // No clip actually mentions lighting.
            case SONG_SELECTED -> switch (role) {
                case DOLORIS -> daily(DailyCue.LIVE_PREP_2);
                case MORTIS -> old(Cue.SKILL_UP);
                case TIMORIS -> daily(DailyCue.COMMON_3);
                case AMORIS -> daily(DailyCue.LIVE_PREP_1);
                case OBLIVIONIS -> daily(DailyCue.LIVE_PREP_2);
            };
            case CLAIM_REWARD -> switch (role) {
                case TIMORIS -> null; // Her exchange line asks what to buy, not about rewards.
                case AMORIS -> old(Cue.LEVEL_UP); // "好耶"
                case MORTIS -> old(Cue.LEVEL_UP); // "太好了"
                case OBLIVIONIS -> old(Cue.LEVEL_UP); // "哼哼，做到了"
                case DOLORIS -> old(Cue.AWAKENING); // "哼哼，做到了"
            };
        };
    }

    /** Add an explicitly requested feature reaction only after a successful
     * server-side action. Group and role cooldowns prevent voice spam. */
    public static boolean feature(BandMemberEntity npc, Feature feature) {
        if (!(npc.level() instanceof ServerLevel world) || !npc.isAlive() || npc.manager() == null) return false;
        UUID owner = npc.manager();
        long now = world.getGameTime();
        Group group = GROUPS.computeIfAbsent(owner, unused -> new Group());
        if (group.waiting.size() >= 5 || group.nextPlayAt > now + 240L) return false;
        if (feature != Feature.SUMMON && group.lastFeature == feature && now - group.lastFeatureAt < 20L * 12L) return false;
        if (now - group.lastFeatureByRole.getOrDefault(npc.role(), -100000L) < 20L * 9L) return false;
        Choice selection = featureChoice(npc.role(), feature, world, npc);
        if (selection == null) return false;
        String clipId = npc.role() + ":" + (selection.old() != null ? "o" + selection.old() : "d" + selection.daily());
        // Choose an alternate when the exact same sentence was used recently.
        if (clipId.equals(group.lastFeatureClip) && feature != Feature.SUMMON) {
            if (world.getRandom().nextBoolean()) return false;
        }
        if (selection.old() != null) add(owner, npc, selection.old(), now);
        else addDaily(owner, npc, selection.daily(), now);
        group.lastFeatureAt = now;
        group.lastFeature = feature;
        group.lastFeatureClip = clipId;
        group.lastFeatureByRole.put(npc.role(), now);
        group.nextAmbientAt = Math.max(group.nextAmbientAt, now + 20L * 14L);
        return true;
    }

    /** The director selects one relevant bandmate response, not five simultaneous
     * acknowledgements. Sakiko leads FOLLOW/STANDBY, other roles rotate for shows. */
    public static void teamOrder(ServerLevel world, EnumMap<MemberRole, BandMemberEntity> members, TeamMode mode) {
        if (members.isEmpty()) return;
        Feature event = switch (mode) {
            case FOLLOW -> Feature.FOLLOW;
            case REHEARSAL -> Feature.REHEARSAL;
            case PERFORMANCE -> Feature.PERFORMANCE;
            case REST -> Feature.REST;
            case STANDBY -> Feature.STANDBY;
            case FREE -> Feature.FREE;
            case NONE -> null;
        };
        if (event == null) return;
        if ((mode == TeamMode.FOLLOW || mode == TeamMode.STANDBY)
                && featureMember(members, MemberRole.OBLIVIONIS, event)) return;
        int start = world.getRandom().nextInt(SPEAKING_ORDER.length);
        for (int i = 0; i < SPEAKING_ORDER.length; i++) {
            if (featureMember(members, SPEAKING_ORDER[(start + i) % SPEAKING_ORDER.length], event)) return;
        }
    }

    private static boolean featureMember(EnumMap<MemberRole, BandMemberEntity> members, MemberRole role, Feature event) {
        BandMemberEntity member = members.get(role);
        return member != null && feature(member, event);
    }

    /** Speaker selection for economy actions: prefer the member whose actual line
     * matches the operation; never force an irrelevant result/competition clip. */
    public static void economyAction(ServerLevel world, EnumMap<MemberRole, BandMemberEntity> members, Feature event) {
        if (members.isEmpty()) return;
        if (event == Feature.SOUND_UPGRADE && featureMember(members, MemberRole.TIMORIS, event)) return;
        MemberRole[] preferred = event == Feature.PROMOTION
                ? new MemberRole[]{MemberRole.DOLORIS, MemberRole.OBLIVIONIS}
                : SPEAKING_ORDER;
        int start = world.getRandom().nextInt(preferred.length);
        for (int i = 0; i < preferred.length; i++) {
            if (featureMember(members, preferred[(i + start) % preferred.length], event)) return;
        }
    }

    /**
     * These 80 assets were recorded for growth/live/results, not everyday NPC
     * conversations. Only reuse a line in a new context when its actual spoken
     * wording still makes sense. No fabricated greetings or competition claims.
     */
    private static Cue contextualCue(MemberRole role, Context context, int action) {
        return switch (context) {
            case IDLE -> switch (role) {
                case DOLORIS -> Cue.SCORE_B;      // "感觉相当不错"
                case MORTIS -> Cue.SPECIAL_TRAINING; // "嗯，感觉不错"
                case TIMORIS -> Cue.SKILL_UP;     // "还算不错吧"
                case AMORIS -> Cue.LEVEL_UP;      // "好耶"
                case OBLIVIONIS -> Cue.SPECIAL_TRAINING; // "不要大意，继续前进"
            };
            case DAILY_ACTION -> switch (role) {
                case DOLORIS, MORTIS, TIMORIS, AMORIS, OBLIVIONIS -> null;
                // DAILY_ACTION must only use precisely matched daily clips.
            };
            case REST -> switch (role) {
                case DOLORIS -> Cue.SCORE_C;      // "嗯，下次要更加努力"
                case MORTIS -> Cue.SCORE_C;       // "身体好沉……好累"
                case TIMORIS -> Cue.LIVE_CLEAR;   // "今宵就到此为止" (night only)
                case AMORIS -> Cue.LIVE_FC;       // "呼～出了好多汗" (after playing)
                case OBLIVIONIS -> Cue.LIVE_CLEAR; // "祝各位好梦" (night only)
            };
            case REHEARSAL_START -> Cue.SPECIAL_TRAINING;
            case PERFORMANCE_START -> switch (role) {
                case DOLORIS, MORTIS -> Cue.SKILL_UP; // "传达" / "好好地表现"
                case TIMORIS, OBLIVIONIS -> Cue.SPECIAL_TRAINING;
                case AMORIS -> Cue.LEVEL_UP;      // "好耶"
            };
            case PERFORMANCE_PHRASE -> role == MemberRole.AMORIS ? Cue.LEVEL_UP : null;
                // A short "好耶" works on a drum fill; score/growth verdicts do not.

            case GREETING, SHOP -> null; // No suitable lines in the original score bank.
        };
    }

    /** Match exact daily speech to context. Inappropriate lines remain available
     * for /avemujica voice test but are NEVER forced into unrelated animations. */
    private static DailyCue selectDaily(BandMemberEntity npc, Context ctx, ServerLevel world) {
        MemberRole role = npc.role();
        int action = npc.personalityAction();
        long day = world.getDayTime() % 24000L;
        switch (ctx) {
            case GREETING -> {
                // Uika explicitly says good morning; Nyamu says start the day.
                if (role == MemberRole.DOLORIS && day >= 6500) return null;
                if (role == MemberRole.AMORIS && day >= 14000) return null;
                return DailyCue.GREETING;
            }
            case SHOP -> { return DailyCue.SHOP; }
            case REHEARSAL_START -> {
                return switch (role) {
                    case DOLORIS -> DailyCue.LIVE_PREP_2;   // "今天全力唱歌"
                    case MORTIS -> DailyCue.COMMON_3;     // guitar in hand
                    case TIMORIS -> DailyCue.COMMON_3;    // checking audio gear
                    case AMORIS -> DailyCue.LIVE_PREP_1;  // preparations finished
                    case OBLIVIONIS -> DailyCue.COMMON_2; // composing music
                };
            }
            case PERFORMANCE_START -> { return DailyCue.LIVE_PREP_2; }
            case REST -> {
                return switch (role) {
                    case MORTIS -> day >= 12000 && day <= 23000 ? DailyCue.LIVE_PREP_1 : null; // sleep early
                    case OBLIVIONIS -> DailyCue.COMMON_1;  // reading / tea
                    default -> null;
                };
            }
            case DAILY_ACTION -> {
                return switch (role) {
                    case DOLORIS -> action == 15 ? DailyCue.LIVE_PREP_2
                            : action == 26 ? DailyCue.COMMON_1 : null;
                    // The lyric-writing line is reserved for showing lyrics on her phone.
                    // No existing clip explicitly narrates her feelings for Sakiko.
                    case MORTIS -> (action == 1 || action == 9) ? DailyCue.COMMON_3 : null;
                    // "Bitter melon watering" is not a flower observation.
                    case TIMORIS -> action == 2 ? DailyCue.COMMON_2 : null;
                    case AMORIS -> action == 12 ? DailyCue.COMMON_1 : null;
                    case OBLIVIONIS -> action == 6 ? DailyCue.COMMON_1 :
                            action == 5 ? DailyCue.COMMON_2 : null;
                    // Composing/musical writing is different from conducting a beat.
                };
            }
            case IDLE -> {
                return switch (role) {
                    case DOLORIS -> world.random.nextBoolean() ? DailyCue.COMMON_1 : DailyCue.COMMON_2;
                    case MORTIS -> nearFarmland(npc, world) && world.random.nextInt(3) == 0
                            ? DailyCue.COMMON_1 : world.random.nextBoolean() ? DailyCue.COMMON_2 : DailyCue.COMMON_3;
                    case TIMORIS -> DailyCue.COMMON_1;   // personal clothing preference
                    case AMORIS -> world.random.nextBoolean() ? DailyCue.COMMON_2 : DailyCue.COMMON_3;
                    case OBLIVIONIS -> world.random.nextInt(4) == 0
                            ? DailyCue.COMMON_3 : DailyCue.COMMON_2;
                };
            }
            case PERFORMANCE_PHRASE -> { return null; } // older short rhythm cues remain preferable
        }
        return null;
    }

    private static boolean nearFarmland(BandMemberEntity npc, ServerLevel world) {
        BlockPos origin = npc.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -1, -3), origin.offset(3, 1, 3))) {
            if (world.hasChunkAt(pos) && world.getBlockState(pos).is(Blocks.FARMLAND)) return true;
        }
        return false;
    }

    /** Casual lines are optional, owner-local, mutually exclusive with scored result queues. */
    public static boolean ambient(BandMemberEntity musician, Context context) {
        if (!(musician.level() instanceof ServerLevel level) || !musician.isAlive()) return false;
        UUID owner = musician.manager();
        if (owner == null) return false;
        long now = level.getGameTime();
        Group group = GROUPS.computeIfAbsent(owner, k -> new Group());
        if (now < group.nextAmbientAt || now < group.nextPlayAt || !group.waiting.isEmpty()) return false;
        if (group.lastAmbientRole == musician.role() && now - group.lastAmbientAt < 20L * 75L) return false;
        if (context == Context.IDLE && level.getRandom().nextInt(3) != 0) return false;
        if (context == Context.DAILY_ACTION && level.getRandom().nextInt(2) != 0) return false;
        if (context == Context.PERFORMANCE_PHRASE && level.getRandom().nextInt(4) != 0) return false;
        if (context == Context.SHOP && level.getRandom().nextInt(3) != 0) return false;

        DailyCue daily = selectDaily(musician, context, level);
        if (daily == null && (context == Context.GREETING || context == Context.SHOP
                || context == Context.DAILY_ACTION)) return false;
        Cue old = null;
        if (daily == null) {
            // Reuse only semantically safe old lines, and never during irrelevant scenes.
            if (context == Context.REST) {
                if (musician.role() == MemberRole.OBLIVIONIS || musician.role() == MemberRole.MORTIS) return false;
                long day = level.getDayTime() % 24000L;
                if (musician.role() == MemberRole.TIMORIS && (day < 12500L || day > 23500L)) return false;
                if ((musician.role() == MemberRole.AMORIS || musician.role() == MemberRole.TIMORIS)
                        && !musician.recentlyPerformed()) return false;
            }
            old = contextualCue(musician.role(), context, musician.personalityAction());
            if (old == null) return false;
        }
        if (daily != null) {
            // Avoid exact duplicate lines from the same member in two adjacent sessions.
            if (context == Context.IDLE && group.lastDailyCue == daily && level.getRandom().nextInt(3) != 0) return false;
            addDaily(owner, musician, daily, now);
            group.lastDailyCue = daily;
        } else add(owner, musician, old, now);

        group.lastAmbientRole = musician.role();
        group.lastAmbientAt = now;
        group.nextAmbientAt = now + 20L * switch (context) {
            case GREETING -> 52 + level.getRandom().nextInt(20);
            case SHOP -> 44 + level.getRandom().nextInt(20);
            case PERFORMANCE_PHRASE -> 64 + level.getRandom().nextInt(24);
            case REHEARSAL_START, PERFORMANCE_START -> 20 + level.getRandom().nextInt(13);
            default -> 39 + level.getRandom().nextInt(35);
        };
        return true;
    }

    /** One contextual shop/exchange line when the owner opens the business interface. */
    public static void managementOpened(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<BandMemberEntity> candidates = level.getEntitiesOfClass(BandMemberEntity.class,
                player.getBoundingBox().inflate(10D), npc -> npc.isAlive()
                        && player.getUUID().equals(npc.manager()) && !npc.isPerforming());
        if (candidates.isEmpty()) return;
        // Rotate through present members rather than always picking Sakiko.
        int slot = level.getRandom().nextInt(candidates.size());
        ambient(candidates.get(slot), Context.SHOP);
    }

    /** Queue a single level-up reaction after a *real* increase in band level. */
    public static void bandLevelUp(ServerLevel level, UUID owner,
                                   EnumMap<MemberRole, BandMemberEntity> members, int levelBefore, int levelAfter) {
        if (levelAfter <= levelBefore || members.isEmpty()) return;
        MemberRole[] roles = SPEAKING_ORDER;
        BandMemberEntity member = members.get(roles[Math.floorMod(levelAfter, roles.length)]);
        if (member == null) member = members.values().iterator().next();
        addDaily(owner, member, DailyCue.LEVEL_GAIN, level.getGameTime());
    }

    private static BandMemberEntity find(EnumMap<MemberRole, BandMemberEntity> members, MemberRole role) {
        return members.get(role);
    }

    /** After a paid rehearsal, let each present member react once, in sequence. */
    public static void rehearsal(ServerLevel level, UUID owner,
                                  EnumMap<MemberRole, BandMemberEntity> members,
                                  EnumMap<MemberRole, Integer> previousRanks) {
        long now = level.getGameTime();
        for (MemberRole role : SPEAKING_ORDER) {
            BandMemberEntity member = find(members, role);
            if (member == null) continue;
            int rank = member.trainingRank();
            if (rank <= previousRanks.getOrDefault(role, rank)) continue;
            if (rank == 15) add(owner, member, Cue.AWAKENING, now);
            else if (rank > 0 && rank % 5 == 0) add(owner, member, Cue.LEVEL_UP, now);
            else addDaily(owner, member, DailyCue.RANK_GAIN, now);
        }
    }

    /** Match original Result_Score_* labels to the economy's actual 0–100 score. */
    private static Cue scoreCue(int score) {
        if (score >= 95) return Cue.SCORE_SS;
        if (score >= 85) return Cue.SCORE_S;
        if (score >= 70) return Cue.SCORE_A;
        if (score >= 55) return Cue.SCORE_B;
        if (score >= 40) return Cue.SCORE_C;
        return Cue.SCORE_D;
    }

    /** Natural MIDI completion is different from the scored career show: one
     * genuine curtain-call line, never an invented score or combo claim.
     * Invoked once per completed timeline, not after a user STOP or a looping song. */
    public static void midiFinished(ServerLevel level, UUID owner,
                                    EnumMap<MemberRole, UUID> performers) {
        if (owner == null || performers.isEmpty()) return;
        MemberRole[] suitable = {MemberRole.MORTIS, MemberRole.AMORIS,
                MemberRole.DOLORIS, MemberRole.TIMORIS, MemberRole.OBLIVIONIS};
        long now = level.getGameTime();
        int start = (int) Math.floorMod(now / 40L, suitable.length);
        for (int i = 0; i < suitable.length; i++) {
            MemberRole role = suitable[(start + i) % suitable.length];
            UUID id = performers.get(role);
            if (id == null || !(level.getEntity(id) instanceof BandMemberEntity musician)
                    || !musician.isAlive() || !owner.equals(musician.manager())) continue;
            Group group = GROUPS.computeIfAbsent(owner, key -> new Group());
            group.waiting.clear(); // End cue should not trail obsolete mid-show smalltalk.
            group.nextPlayAt = Math.min(group.nextPlayAt, now + 8L);
            // "Curtain falls", "What a performance", "we worked hard" — no FC/AP claims.
            add(owner, musician, role == MemberRole.AMORIS
                    ? Cue.LIVE_FC : Cue.LIVE_CLEAR, now);
            group.nextAmbientAt = Math.max(group.nextAmbientAt, now + 20L * 14L);
            return;
        }
    }

    /** After a finished show, one clear/quality remark and two score remarks.
     * FC/AP lines are used as *quality tiers*, not claimed as actual combo verification. */
    public static void performanceResult(ServerLevel level, UUID owner,
                                         EnumMap<MemberRole, BandMemberEntity> members,
                                         int score, int completedShows) {
        if (owner == null || members.isEmpty()) return;
        Group group = GROUPS.computeIfAbsent(owner, k -> new Group());
        group.waiting.clear(); // result commentary has priority over stale smalltalk
        long now = level.getGameTime();
        int n = Math.floorMod(completedShows, SPEAKING_ORDER.length);
        Cue finish = score >= 95 ? Cue.LIVE_AP : score >= 85 ? Cue.LIVE_FC : Cue.LIVE_CLEAR;
        add(owner, find(members, SPEAKING_ORDER[n]), finish, now);
        Cue scoreLine = scoreCue(score);
        add(owner, find(members, SPEAKING_ORDER[(n + 1) % 5]), scoreLine, now);
        add(owner, find(members, SPEAKING_ORDER[(n + 2) % 5]), scoreLine, now);
    }

    public static void serverTick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        if (now % 4L != 0L || GROUPS.isEmpty()) return;
        var iter = GROUPS.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<UUID, Group> entry = iter.next();
            Group group = entry.getValue();
            if (group.waiting.isEmpty()) {
                // Keep the group's voice cooldown after the queue runs dry;
                // otherwise every later NPC call would recreate an uncooldowned group.
                if (now > Math.max(group.nextAmbientAt, group.nextPlayAt) + 1200L) iter.remove();
                continue;
            }
            if (now < group.nextPlayAt) continue;
            while (!group.waiting.isEmpty()) {
                Pending pending = group.waiting.removeFirst();
                if (now > pending.expiresAt()) continue;
                ServerLevel source = server.getLevel(pending.dimension());
                if (source == null) continue;
                Entity entity = source.getEntity(pending.memberId());
                if (!(entity instanceof BandMemberEntity member) || !member.isAlive()
                        || member.role() != pending.role() || !entry.getKey().equals(member.manager())) continue;
                source.playSound(null, member.blockPosition(), pending.sound(), SoundSource.VOICE, .72F, 1.0F);
                group.nextPlayAt = now + pending.durationTicks() + 16L;
                break;
            }
            // Preserve the cooldown and last speaker after the final queued clip.
        }
    }

    /** Op-only diagnostics for testing that a clip is wired to its correct member. */
    public static int test(ServerPlayer player, String roleName, int clipId) {
        MemberRole role = null;
        for (MemberRole member : MemberRole.values()) if (member.id.equals(roleName)) role = member;
        if (role == null) {
            player.sendSystemMessage(Component.literal("Unknown member. Use doloris/mortis/timoris/amoris/oblivionis."));
            return 0;
        }
        Cue cue = VoiceLibrary.cueBySourceId(role, clipId);
        DailyCue daily = VoiceLibrary.dailyCueBySourceId(role, clipId);
        if (cue == null && daily == null) {
            player.sendSystemMessage(Component.literal("Voice number " + clipId + " does not belong to " + role.id + "."));
            return 0;
        }
        SoundEvent sound = cue != null ? VoiceLibrary.sound(role, cue) : VoiceLibrary.dailySound(role, daily);
        player.serverLevel().playSound(null, player.blockPosition(), sound, SoundSource.VOICE, 1.0F, 1.0F);
        player.sendSystemMessage(Component.literal("Voice preview: " + role.id + "/" + clipId + " (" + (cue != null ? cue : daily) + ")"));
        return 1;
    }
}
