package com.miseru.avemujica.entity;

import com.miseru.avemujica.registry.ModRegistry;
import com.miseru.avemujica.team.PlayerBandRoster;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import com.miseru.avemujica.voice.VoiceDirector;
import com.miseru.avemujica.music.MidiBandPlayer;

import com.miseru.avemujica.career.BandManagement;
import com.miseru.avemujica.career.BandCareerData;
import com.miseru.avemujica.menu.MemberInteractionMenu;
import com.miseru.avemujica.menu.PoseDebugMenu;
import com.miseru.avemujica.menu.PoseDebugActions;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import java.util.UUID;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.util.Mth;

import java.util.EnumSet;
import java.util.List;
import org.joml.Vector3f;

/** Friendly musicians: roles, live performances, direct commands and personal downtime. */
public class BandMemberEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> ROLE =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TEAM_MODE =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TASK_MODE =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CONCERT =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> PERFORMING =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DRUM_SEATED =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.BOOLEAN);
    // Keyboard is only visible once Sakiko has reached her fixed stage heading.
    private static final EntityDataAccessor<Boolean> KEYBOARD_LOCKED =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.BOOLEAN);
    // Sequence data is server-owned and synchronized by Minecraft's entity tracker.
    // Each note-on event causes a short pose impulse on the client.
    private static final EntityDataAccessor<Integer> VOCAL_SEQUENCE =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> NOTE_SEQUENCE =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    // The vocal voice part must not trigger guitar strumming or summon a visible guitar.
    // Synchronized for dedicated-server players: client rendering follows actual guitar notes.
    private static final EntityDataAccessor<Boolean> UIKA_GUITAR_ACTIVE =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> NOTE_PITCH =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> NOTE_VELOCITY =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MIDI_MODE =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SILENT_STAGE =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.BOOLEAN);
    // Server-controlled daily activities (client sync drives body poses and visual props).
    // 0 idle, 1 Mutsumi seated, 2 Umiri snack, 3 Nyamu photos,
    // 4 Uika greeting Sakiko, 5 Sakiko considering the next rehearsal.
    // 6 Sakiko reviewing music; old 7 stick-conducting was retired in v1.1.0.
    // 8 Mutsumi inspecting flowers, 9 quietly tuning guitar;
    // 10 Umiri stretching, 11 drinking, 19 checking injured teammate;
    // 12 Nyamu fixing her hair, 13 organizing group selfie, 14 reacting to photo;
    // 15 Uika warming up vocals, 16 giving a flower to Sakiko;
    // 17 Sakiko reacting to Uika, 18 briefly shielding from rain.
    // 20 Sakiko drinking coffee, 21 Mutsumi holding cucumber,
    // 22 Mutsumi gifting cucumber, 23 Uika drinking coffee,
    // 24 a teammate receiving Mutsumi's cucumber gift.
    // 25 Uika quietly watching Sakiko work (one-sided; never interrupts her).
    // 26 Uika sharing song lyrics on her phone; 27 Sakiko looking over the score.
    // 28 Uika checking on injured Sakiko; 29 Sakiko reassuring Uika.
    // 30/31 hand-hold, 32/33 shoulder lean, 34/35 embrace,
    // 36/37 coffee handoff, 38/39 flower gift.
    // 40 refined Sakiko pose, 41 gentle Uika stage pose,
    // 42 quiet Mutsumi pose, 43 cool Umiri pose, 44 playful Nyamu pose.
    // Both members share the same time-phased choreography, synchronized to clients.
    // 30/31: hand holding; 32/33: shoulder lean; 34/35: hug;
    // 36/37: coffee handoff; 38/39: flower gift.
    private static final EntityDataAccessor<Integer> DUO_PHASE =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PERSONALITY =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRAINING_RANK =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    // Instrument finish is owner-controlled visual state, synchronized for all observers.
    private static final EntityDataAccessor<Integer> INSTRUMENT_COLOR =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    // Second style is available only for Sakiko, Uika and Umiri. Saved in existing entity NBT.
    private static final EntityDataAccessor<Boolean> ALT_UNLOCKED =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ALT_ACTIVE =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.BOOLEAN);
    // Combat attacks are separate from MIDI notes: client animation cannot miss a melee hit.
    private static final EntityDataAccessor<Integer> ATTACK_SEQUENCE =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    // Sakiko's death temporarily drives Uika into her chainsaw second combat style.
    private static final EntityDataAccessor<Boolean> UIKA_RAGE_ACTIVE =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PERFORMANCE_INSTRUMENT =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> INSTRUMENT_ENABLED =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int UIKA_RAGE_DURATION_TICKS = 15 * 20;
    public static final int ALT_REQUIRED_TRAINING = 5;
    public static final int ALT_UNLOCK_EMERALDS = 8;
    private int clientAttackSequence = -1;
    private float clientAttackStartAge = -1000F;
    private static final int GROUP_RADIUS = 15;
    private static final int ATTACK_RADIUS = 17;
    private UUID teamLeader;
    private Vec3 teamStageCenter;
    private boolean teamOptOut;
    private UUID manager;
    public UUID manager() { return manager; }
    public void setManager(UUID id) { this.manager = id; }
    private int cooldown = 20;
    private int chorusCooldown = 160;
    // Transient client-side interpolation only (never stored or accessed by the server).
    private int clientLastVocalSequence = -1;
    private float clientLastVocalAge = -1000F;
    private int clientLastNoteSequence = -1;
    private float clientLastNoteAge = -1000F;
    private Vec3 stageSpot;
    // Navigation retries are transient; the requested stage centre is already stored in NBT.
    private Vec3 stageFormationCenter;
    // The controller may not be available when NBT is first read on chunk load.
    // Reconfirm its tier and direction once the world data becomes available.
    private boolean assignedPhysicalStage;
    private boolean assignedLevelTwo;
    private net.minecraft.core.Direction assignedStageFront = net.minecraft.core.Direction.SOUTH;
    private long stageLastProgressTick;
    // When the keyboardist is stuck outside the exact mark, settle at the nearest safe
    // grounded location instead of performing forever with an invisible instrument.
    private int keyboardApproachStallTicks;
    // Transient navigation phase; route is rebuilt from the current position
    // after a world reload, never serialized as permanent character state.
    private int galleryApproachPhase;
    // Do not replace A* every 12 ticks: let it traverse each real gallery riser.
    // Alternate the three-block-wide stairs only after a genuine stalled route.
    private int galleryLane;
    private Vec3 galleryPathGoal;
    private long galleryLastPathRequestTick;
    private double galleryBestWaypointDistanceSq = Double.MAX_VALUE;
    private long galleryLastWaypointProgressTick;
    // Stable arrival hysteresis for the two raised level-two performers.
    // Without it, path retries flip DRUM_SEATED / KEYBOARD_LOCKED repeatedly,
    // making the rendered keyboard and full drum set disappear mid-song.
    private int galleryStableTicks;
    private double stageBestDistanceSq = Double.MAX_VALUE;
    // Stage heading must not follow LookAtPlayerGoal, random glances or limb animation.
    // It is captured only after walking to a formation point (solo: immediately).
    private Float performanceFacingYaw;
    private long nextFlourishTick;
    private long flourishEndTick;
    private static final EntityDataAccessor<Integer> FLOURISH_KIND =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLOURISH_SEQUENCE =
            SynchedEntityData.defineId(BandMemberEntity.class, EntityDataSerializers.INT);
    private int clientFlourishSeq = -1;
    private float clientFlourishStartAge = -999F;
    private Vec3 dutyCenter;
    private BlockPos seatTarget;
    private long seatSearchUntil;
    private long activityUntil;
    // Transient manual pose preview. Takes precedence over ambient actions and wandering.
    private long debugPoseUntil;
    // Transient ambient voice cadence; never requires saved-data migration.
    private long nextIdleVoiceTick;
    private long nextStagePhraseTick;
    private int lastVoicePersonality;
    private boolean lastVoiceResting;
    // Personal proximity edge detector: greet once on approach, never every idle tick.
    private boolean ownerWasClose;
    private long nextGreetingAllowed;
    private long nextPersonalityTick;
    private long nextProteinSelfEatTick = 0L;
    private long nextHeartTick;
    private int lastSpontaneousAction = -1;
    private BlockPos flowerToWatch;
    private UUID socialFocus;
    private long duoStartTick = -1L;
    // A visit to Sakiko is a bounded social trip, not a permanent follow order.
    private UUID sakikoVisitTarget;
    private long sakikoVisitUntil;
    private long nextSakikoVisitTick;
    // Short-lived invitation: Sakiko waits for Uika rather than starting a new
    // idle animation or wandering away while Uika walks to her.
    private UUID expectedUikaVisitor;
    private long uikaVisitorUntil;
    // Used only to avoid playing post-show lines after unrelated rest orders.
    private long lastPerformanceEndTick = -100000L;
    private int lastNoteTick = -1;
    // Extend the guitar pose across normal rhythm gaps; voice-only performances never set it.
    private long uikaGuitarPoseUntil = -1L;
    private int lastNoteVelocity = -1;
    private long soloUntil = 0L;
    // Persistent duration; the synchronized boolean is used by client renderers.
    private int uikaRageTicksRemaining = 0;
    private long soloCooldownUntil = 0L;

    public BandMemberEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 55D)
                .add(Attributes.MOVEMENT_SPEED, 0.29D)
                .add(Attributes.FOLLOW_RANGE, 48D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.35D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROLE, 0);
        builder.define(TASK_MODE, 0);
        builder.define(TEAM_MODE, 0);
        builder.define(CONCERT, false);
        builder.define(PERFORMING, false);
        builder.define(DRUM_SEATED, false);
        builder.define(KEYBOARD_LOCKED, false);
        builder.define(VOCAL_SEQUENCE, 0);
        builder.define(NOTE_SEQUENCE, 0);
        builder.define(UIKA_GUITAR_ACTIVE, false);
        builder.define(NOTE_PITCH, 60);
        builder.define(NOTE_VELOCITY, 0);
        builder.define(MIDI_MODE, false);
        builder.define(SILENT_STAGE, false);
        builder.define(FLOURISH_KIND, 0);
        builder.define(FLOURISH_SEQUENCE, 0);
        builder.define(PERSONALITY, 0);
        builder.define(DUO_PHASE, 0);
        builder.define(TRAINING_RANK, 0);
        builder.define(INSTRUMENT_COLOR, 0);
        builder.define(ALT_UNLOCKED, false);
        builder.define(ALT_ACTIVE, false);
        builder.define(ATTACK_SEQUENCE, 0);
        builder.define(UIKA_RAGE_ACTIVE, false);
        builder.define(PERFORMANCE_INSTRUMENT, -1);
        builder.define(INSTRUMENT_ENABLED, true);
    }

    public MemberRole role() {
        return MemberRole.values()[Mth.clamp(entityData.get(ROLE), 0, MemberRole.values().length - 1)];
    }

    public int trainingRank() { return entityData.get(TRAINING_RANK); }
    public int instrumentColor() { return com.miseru.avemujica.career.InstrumentPalette.safe(entityData.get(INSTRUMENT_COLOR)); }
    /** Called server-side when the owner's SavedData finish changes. */
    public void setInstrumentColor(int color) { entityData.set(INSTRUMENT_COLOR,
            com.miseru.avemujica.career.InstrumentPalette.safe(color)); }

    public MemberInstrument instrumentType() {
        return MemberInstrument.at(entityData.get(PERFORMANCE_INSTRUMENT), role());
    }

    public boolean usesPerformanceInstrument() { return entityData.get(INSTRUMENT_ENABLED); }

    public void applyPerformanceInstrument(MemberInstrument instrument, boolean enabled) {
        enabled = role() != MemberRole.DOLORIS || enabled;
        boolean changed = instrumentType() != instrument || usesPerformanceInstrument() != enabled;
        entityData.set(PERFORMANCE_INSTRUMENT, instrument.ordinal());
        entityData.set(INSTRUMENT_ENABLED, enabled);
        if (changed) {
            entityData.set(DRUM_SEATED, false);
            entityData.set(KEYBOARD_LOCKED, false);
            entityData.set(UIKA_GUITAR_ACTIVE, false);
            entityData.set(NOTE_VELOCITY, 0);
            uikaGuitarPoseUntil = -1L;
        }
        if (enabled ? !getMainHandItem().is(instrument.heldItem()) : !getMainHandItem().isEmpty())
            setItemSlot(EquipmentSlot.MAINHAND, enabled ? new ItemStack(instrument.heldItem()) : ItemStack.EMPTY);
        setDropChance(EquipmentSlot.MAINHAND, 0F);
    }

    public boolean hasSecondCombatStyle() {
        return role() == MemberRole.OBLIVIONIS || role() == MemberRole.DOLORIS || role() == MemberRole.TIMORIS;
    }
    public boolean alternateUnlocked() { return hasSecondCombatStyle() && entityData.get(ALT_UNLOCKED); }
    public boolean isUikaRaging() { return role() == MemberRole.DOLORIS && entityData.get(UIKA_RAGE_ACTIVE); }
    // Rage overrides the combat style temporarily, without unlocking or changing the saved choice.
    public boolean alternateCombat() {
        return isUikaRaging() || (alternateUnlocked() && entityData.get(ALT_ACTIVE));
    }
    public boolean toggleAlternateCombat(ServerPlayer player) {
        if (!canBeManagedBy(player) || !alternateUnlocked()) return false;
        entityData.set(ALT_ACTIVE, !entityData.get(ALT_ACTIVE));
        PlayerBandRoster.snapshot(this, false);
        cooldown = 10;
        getNavigation().stop();
        return true;
    }
    /** Only a player's real inventory funds unlocks; creative mode is exempt. */
    public boolean unlockAlternateCombat(ServerPlayer player) {
        if (!hasSecondCombatStyle() || alternateUnlocked() || !canBeManagedBy(player)
                || trainingRank() < ALT_REQUIRED_TRAINING) return false;
        if (!player.getAbilities().instabuild) {
            int count = 0;
            for (ItemStack stack : player.getInventory().items) if (stack.is(Items.EMERALD)) count += stack.getCount();
            if (count < ALT_UNLOCK_EMERALDS) return false;
            int remaining = ALT_UNLOCK_EMERALDS;
            for (ItemStack stack : player.getInventory().items) {
                if (remaining <= 0) break;
                if (!stack.is(Items.EMERALD)) continue;
                int take = Math.min(stack.getCount(), remaining);
                stack.shrink(take);
                remaining -= take;
            }
            player.getInventory().setChanged();
        }
        entityData.set(ALT_UNLOCKED, true);
        entityData.set(ALT_ACTIVE, true);
        PlayerBandRoster.snapshot(this, false);
        cooldown = 10;
        return true;
    }
    public float clientAttackAge(float age) {
        int sequence = entityData.get(ATTACK_SEQUENCE);
        if (clientAttackSequence != sequence) {
            clientAttackSequence = sequence;
            clientAttackStartAge = age;
        }
        return age - clientAttackStartAge;
    }
    private void markCombatStrike() {
        entityData.set(ATTACK_SEQUENCE, entityData.get(ATTACK_SEQUENCE) + 1);
    }

    /** Start the 15-second, owner-specific berserk state when Sakiko is defeated. */
    private void startUikaRage(ServerLevel server, Monster attacker) {
        if (role() != MemberRole.DOLORIS || !isAlive()) return;
        uikaRageTicksRemaining = UIKA_RAGE_DURATION_TICKS;
        entityData.set(UIKA_RAGE_ACTIVE, true);
        // Resistance V, regeneration II, speed II and strength II for 15 seconds.
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, UIKA_RAGE_DURATION_TICKS, 4));
        addEffect(new MobEffectInstance(MobEffects.REGENERATION, UIKA_RAGE_DURATION_TICKS, 1));
        addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, UIKA_RAGE_DURATION_TICKS, 1));
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, UIKA_RAGE_DURATION_TICKS, 1));
        if (attacker != null && attacker.isAlive()) setTarget(attacker);
        setPersonality(0);
        getNavigation().stop();
        cooldown = 2;
        server.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getEyeY() + .25D, getZ(),
                12, .35D, .35D, .35D, .08D);
    }

    private void tickUikaRage(ServerLevel server) {
        if (!isUikaRaging()) return;
        if (uikaRageTicksRemaining <= 0) {
            entityData.set(UIKA_RAGE_ACTIVE, false);
            return;
        }
        // Continuous but bounded particles: no client-side duplicate spawning.
        if (tickCount % 3 == 0)
            server.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getEyeY() + .2D, getZ(),
                    3, .23D, .25D, .23D, .025D);
        if (--uikaRageTicksRemaining == 0) entityData.set(UIKA_RAGE_ACTIVE, false);
    }

    private void triggerUikaRageOnSakikoDeath(DamageSource source) {
        if (role() != MemberRole.OBLIVIONIS || manager == null
                || !(level() instanceof ServerLevel server) || !PlayerBandRoster.isCurrent(this)) return;
        Monster attacker = source.getEntity() instanceof Monster monster ? monster : null;
        for (Entity entity : server.getAllEntities()) {
            if (!(entity instanceof BandMemberEntity uika) || !uika.isAlive()
                    || uika.role() != MemberRole.DOLORIS || !manager.equals(uika.manager())
                    || !PlayerBandRoster.isCurrent(uika)) continue;
            uika.startUikaRage(server, attacker);
            ServerPlayer owner = server.getServer().getPlayerList().getPlayer(manager);
            if (owner != null && owner.level() == server)
                owner.displayClientMessage(Component.literal("Saki酱Saki酱Saki酱Saki酱Saki酱Saki酱！"), false);
            break;
        }
    }

    /** Real persistent growth: 55 HP + 3 per training rank, reaching 100 HP at Lv.15. */
    public void trainFromRehearsal() {
        if (level().isClientSide) return;
        int rank = Math.min(15, trainingRank() + 1);
        if (rank != trainingRank()) entityData.set(TRAINING_RANK, rank);
        updateTrainedHealth();
        heal(6F);
        PlayerBandRoster.snapshot(this, false);
    }

    /** Debug-only progression setter: callers MUST validate creative/op permission. */
    public void debugSetProgress(int rank, boolean unlocked, boolean enabled) {
        if (level().isClientSide) return;
        entityData.set(TRAINING_RANK, Mth.clamp(rank, 0, 15));
        boolean allow = hasSecondCombatStyle() && unlocked;
        entityData.set(ALT_UNLOCKED, allow);
        entityData.set(ALT_ACTIVE, allow && enabled);
        updateTrainedHealth();
        setHealth(getMaxHealth());
        cooldown = 10;
        PlayerBandRoster.snapshot(this, false);
    }

    public double attackTrainingMultiplier() { return 1D + trainingRank() * .06D; }

    private void updateTrainedHealth() {
        var attribute = getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) return;
        float oldMax = getMaxHealth();
        attribute.setBaseValue(55D + trainingRank() * 3D);
        if (getMaxHealth() > oldMax) setHealth(Math.min(getMaxHealth(), getHealth() + (getMaxHealth() - oldMax)));
        else if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
    }

    public void setRole(MemberRole role) {
        entityData.set(ROLE, role.ordinal());
        setCustomName(Component.literal(role.display));
        setCustomNameVisible(true);
        applyPerformanceInstrument(MemberInstrument.defaultFor(role), true);
        // Visual performance instruments are now rendered by a custom client layer.
        // Keep real equipment simple: no mirrored off-hand drumstick.
        setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        setDropChance(EquipmentSlot.MAINHAND, 0F);
        cooldown = role.attackInterval;
    }

    /** Team orders override task execution, but do not destroy personal-task preferences. */
    public int task() {
        return switch (teamMode()) {
            case NONE -> entityData.get(TASK_MODE);
            case REST -> 3;
            default -> 0;
        };
    }
    public TeamMode teamMode() { return TeamMode.fromId(entityData.get(TEAM_MODE)); }
    public UUID teamLeader() { return teamLeader; }
    public Vec3 teamStageCenter() { return teamStageCenter; }
    public boolean teamOptOut() { return teamOptOut; }
    public boolean isStationary() { return task() != 1 && teamMode() != TeamMode.FREE; }
    public int personalityAction() { return entityData.get(PERSONALITY); }
    /** 0 approach, 1 contact, 2 visible response, 3 release. */
    public int duoPhase() { return entityData.get(DUO_PHASE); }
    public boolean recentlyPerformed() {
        return level().getGameTime() - lastPerformanceEndTick < 20L * 180L;
    }

    /** Called by authenticated server-side band controllers. */
    public void applyTeamOrder(TeamMode order, UUID leaderId, Vec3 center) {
        if (level().isClientSide) return;
        teamLeader = leaderId;
        teamOptOut = false;
        entityData.set(TEAM_MODE, order.ordinal());
        teamStageCenter = (order == TeamMode.REHEARSAL || order == TeamMode.PERFORMANCE) ? center : null;
        stageSpot = null;
        stageFormationCenter = null;
        if (teamStageCenter != null) assignStagePosition(teamStageCenter);
        performanceFacingYaw = null;
        soloUntil = 0L;
        dutyCenter = position();
        setTarget(null);
        getNavigation().stop();
        seatTarget = null;
        flowerToWatch = null;
        socialFocus = null;
        sakikoVisitTarget = null;
        expectedUikaVisitor = null;
        nextSakikoVisitTick = level().getGameTime() + 120L;
        activityUntil = 0L;
        debugPoseUntil = 0L;
        setPersonality(0);
        nextPersonalityTick = level().getGameTime() + 80L;
        PlayerBandRoster.snapshot(this, false);
    }

    /** Follow a moving player's trailing formation, not the previous generic follow goal. */
    private void updateTeamFollow(ServerLevel server) {
        // A short two-person interaction takes precedence over follower formation.
        // Otherwise the 12-tick follow update immediately overwrites Uika's path
        // to Sakiko, and can pull Sakiko away as Uika reaches her.
        if (debugPoseUntil > server.getGameTime() || sakikoVisitTarget != null || isDuoAction(personalityAction())
                || (expectedUikaVisitor != null && server.getGameTime() < uikaVisitorUntil)) return;
        if (tickCount % 12 != 0) return;
        if (manager == null) { getNavigation().stop(); return; }
        ServerPlayer owner = server.getServer().getPlayerList().getPlayer(manager);
        if (owner == null || owner.level() != server || owner.isSpectator()) {
            getNavigation().stop(); return;
        }
        double side = switch (role()) {
            case OBLIVIONIS -> 0D;
            case DOLORIS -> -1.7D;
            case MORTIS -> -3.3D;
            case TIMORIS -> 1.7D;
            case AMORIS -> 3.3D;
        };
        double back = switch (role()) {
            case OBLIVIONIS -> 2.2D;
            case DOLORIS, TIMORIS -> 3.9D;
            case MORTIS, AMORIS -> 5.4D;
        };
        double radians = Math.toRadians(owner.getYRot());
        // Minecraft player's forward horizontal vector: (-sin(yaw), cos(yaw)).
        double fx = -Math.sin(radians), fz = Math.cos(radians);
        double rx = Math.cos(radians), rz = Math.sin(radians);
        double x = owner.getX() - fx * back + rx * side;
        double z = owner.getZ() - fz * back + rz * side;
        double distance = position().distanceToSqr(x, owner.getY(), z);
        if (distance > 2.6D * 2.6D) {
            getNavigation().moveTo(x, owner.getY(), z, distance > 100D ? 1.30D : 1.08D);
        } else getNavigation().stop();
    }

    private static boolean isDuoAction(int action) { return action >= 30 && action <= 39; }

    private void setPersonality(int action) {
        if (personalityAction() != action) entityData.set(PERSONALITY, action);
        if (!isDuoAction(action)) {
            duoStartTick = -1L;
            if (entityData.get(DUO_PHASE) != 0) entityData.set(DUO_PHASE, 0);
        }
    }
    public void assignTask(int task) {
        int next = Mth.clamp(task, 0, 3);
        entityData.set(TASK_MODE, next);
        if (teamMode() != TeamMode.NONE) {
            entityData.set(TEAM_MODE, TeamMode.NONE.ordinal());
            teamLeader = null;
            teamStageCenter = null;
            stageSpot = null;
            stageFormationCenter = null;
            teamOptOut = true;
        }
        dutyCenter = position();
        setTarget(null);
        getNavigation().stop();
        seatTarget = null;
        flowerToWatch = null;
        socialFocus = null;
        sakikoVisitTarget = null;
        expectedUikaVisitor = null;
        nextSakikoVisitTick = level().getGameTime() + 120L;
        activityUntil = 0L;
        debugPoseUntil = 0L;
        setPersonality(0);
        // A new order takes precedence over all spontaneous behavior.
        nextPersonalityTick = level().getGameTime() + 80L;
        PlayerBandRoster.snapshot(this, false);
    }
    public boolean canBeManagedBy(ServerPlayer player) {
        return manager == null || manager.equals(player.getUUID());
    }
    /** Only feeding dialogue and healing are affected by these preferences.
     *  AMORIS has no preferred food: every edible item receives normal healing.
     *  Daily ambient poses and character gifts are separate features.
     */
    public net.minecraft.world.item.Item preferredFood() {
        return switch (role()) {
            case DOLORIS -> ModRegistry.DONUT;
            case OBLIVIONIS -> ModRegistry.COFFEE;
            case TIMORIS -> ModRegistry.PROTEIN_BAR;
            case MORTIS -> ModRegistry.CUCUMBER;
            case AMORIS -> Items.AIR;
        };
    }

    /** Any edible item can be handed to any member outside combat/performances.
     *  Favorites only improve the amount healed and change the response text.
     */
    public boolean feedFrom(ServerPlayer player) {
        if (!canBeManagedBy(player) || isPerforming() || getTarget() != null) return false;
        ItemStack food = player.getMainHandItem();
        FoodProperties properties = food.get(DataComponents.FOOD);
        if (properties == null || food.isEmpty()) return false;
        boolean favorite = preferredFood() != Items.AIR && food.is(preferredFood());
        float healed = 0F;
        if (getHealth() < getMaxHealth()) {
            float before = getHealth();
            heal(Math.max(2F, properties.nutrition() * (favorite ? 3F : 1.8F)));
            healed = getHealth() - before;
        }
        if (!player.getAbilities().instabuild) food.shrink(1);
        String comment = switch (role()) {
            case OBLIVIONIS -> favorite ? "祥子：嗯……咖啡不错。谢谢。" : "祥子：多谢，感觉好多了。";
            case DOLORIS -> favorite ? "初华：甜甜圈真好吃，谢谢你！" : "初华：谢谢，恢复精神了。";
            case MORTIS -> favorite ? "睦：……黄瓜很好吃。谢谢。" : "睦：……谢谢。";
            case TIMORIS -> favorite ? "海铃：蛋白棒正合适，谢谢。" : "海铃：谢谢，补充得不错。";
            case AMORIS -> "喵梦：那我就收下啦，谢谢！";
        };
        player.displayClientMessage(Component.literal(comment
                + (healed > 0F ? "（恢复 " + Math.round(healed) + " 点生命）" : "")), false);
        PlayerBandRoster.snapshot(this, false);
        return true;
    }

    /** Independent idle self-heal: Umiri can still replenish with a protein bar
     *  between fights, regardless of the player's hand-feeding action.
     */
    private void tickTimorisProtein(ServerLevel server) {
        if (role() != MemberRole.TIMORIS || tickCount % 20 != 0 || isPerforming()
                || getTarget() != null || !isAlive() || getHealth() >= getMaxHealth()) return;
        long now = server.getGameTime();
        if (now < nextProteinSelfEatTick || tickCount - getLastHurtByMobTimestamp() < 160) return;
        if (!server.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(10D), Monster::isAlive).isEmpty()) return;
        float before = getHealth();
        heal(7F);
        nextProteinSelfEatTick = now + 20L * 14L;
        if (onGround() && !isMidiPerforming() && spontaneousActionsEnabled())
            startDailyAction(2, now, 35, 12, 200, 100);
        if (manager != null) {
            ServerPlayer owner = server.getServer().getPlayerList().getPlayer(manager);
            if (owner != null && owner.serverLevel() == server && owner.distanceToSqr(this) <= 100D)
                owner.displayClientMessage(Component.literal("海铃拿出一根蛋白棒补充体力（+" + Math.round(getHealth()-before) + " 生命）。"), true);
        }
        PlayerBandRoster.snapshot(this, false);
    }

    /** Genuine item gifts, deliberately rare and opt-out per character. */
    private void tickDailyGift(ServerLevel server) {
        if (manager == null || role() == MemberRole.OBLIVIONIS || tickCount % 20 != 0
                || isPerforming() || getTarget() != null || isUikaRaging()
                || personalityAction() != 0 || !isAlive()) return;
        ServerPlayer owner = server.getServer().getPlayerList().getPlayer(manager);
        if (owner == null || owner.serverLevel() != server || owner.distanceToSqr(this) > 8D*8D) return;
        BandCareerData careers = BandCareerData.get(server);
        BandCareerData.Career career = careers.career(manager);
        String roleId = role().id;
        if (!career.giftsEnabled(roleId)) return;
        long now = server.getGameTime();
        Long last = career.lastDailyGiftTick.get(roleId);
        if (last == null || last > now) {
            career.lastDailyGiftTick.put(roleId, now);
            careers.setDirty();
            return;
        }
        // At least 30 active world minutes per role; then ~15 minutes more on average.
        if (now - last < 20L * 60L * 30L || random.nextInt(900) != 0) return;
        // Gifts are independent of the feeding preference table.
        // Preserve pre-existing character gifts without affecting feeding bonuses.
        net.minecraft.world.item.Item giftItem = switch (role()) {
            case DOLORIS -> ModRegistry.COFFEE;
            case MORTIS -> ModRegistry.CUCUMBER;
            case TIMORIS -> ModRegistry.PROTEIN_BAR;
            case AMORIS -> ModRegistry.DONUT;
            case OBLIVIONIS -> Items.AIR;
        };
        ItemStack gift = new ItemStack(giftItem);
        if (gift.isEmpty()) return;
        if (!owner.getInventory().add(gift) && !gift.isEmpty()) owner.drop(gift, false);
        career.lastDailyGiftTick.put(roleId, now);
        careers.setDirty();
        String comment = switch (role()) {
            case DOLORIS -> "初华：给你带了一杯咖啡，稍微休息一下吧。";
            case MORTIS -> "睦：……这个黄瓜，送给你。";
            case TIMORIS -> "海铃：这根蛋白棒给你，记得补充体力。";
            case AMORIS -> "喵梦：给你甜甜圈！别忘了谢谢我哦！";
            default -> "";
        };
        owner.displayClientMessage(Component.literal(comment), false);
    }

    public boolean beginSolo() {
        long now = level().getGameTime();
        if (isPerforming() || now < soloCooldownUntil) return false;
        soloUntil = now + 20L * 12L;
        soloCooldownUntil = soloUntil + 20L * 3L;
        getNavigation().stop();
        return true;
    }

    /** Called when the owner stops MIDI manually. Cancel the NPC-side solo timer too,
     * otherwise PERFORMING stays true for the original twelve seconds and the
     * individual MIDI picker appears locked after audio has already stopped.
     * Does not alter team-stage, concert or village-tour orders.
     */
    public void cancelSolo() {
        if (level().isClientSide) return;
        soloUntil = 0L;
        soloCooldownUntil = 0L;
    }
    public boolean concertActive() { return entityData.get(CONCERT); }
    public boolean isPerforming() { return entityData.get(PERFORMING); }
    public boolean isDrumSeated() { return entityData.get(DRUM_SEATED); }
    public boolean isKeyboardLocked() { return entityData.get(KEYBOARD_LOCKED); }
    public boolean isSoloing() { return soloUntil > level().getGameTime(); }
    public boolean isMidiPerforming() { return entityData.get(MIDI_MODE); }
    /** The actual electric-guitar channel, never Uika's Voice/HARP channel. */
    public boolean isUikaGuitarActive() { return role() == MemberRole.DOLORIS && usesPerformanceInstrument() && entityData.get(UIKA_GUITAR_ACTIVE); }
    public boolean isSilentPerforming() { return entityData.get(SILENT_STAGE); }
    public int playedPitch() { return entityData.get(NOTE_PITCH); }
    public int playedVelocity() { return entityData.get(NOTE_VELOCITY); }
    public int playedSequence() { return entityData.get(NOTE_SEQUENCE); }
    public int flourishKind() { return entityData.get(FLOURISH_KIND); }

    /** Local fractional tick age; server note events and flourishes arrive via SynchedEntityData. */
    public float clientFlourishAge(float ageInTicks) {
        int seq = entityData.get(FLOURISH_SEQUENCE);
        if (clientFlourishSeq != seq) {
            clientFlourishSeq = seq;
            clientFlourishStartAge = ageInTicks;
        }
        return ageInTicks - clientFlourishStartAge;
    }


    /** Age in ticks since the latest SERVER-authored note, interpolated in client render time. */
    public float clientNoteAge(float renderAge) {
        int sequence = playedSequence();
        if (sequence != clientLastNoteSequence) {
            clientLastNoteSequence = sequence;
            clientLastNoteAge = renderAge;
        }
        return renderAge - clientLastNoteAge;
    }

    /** Voice notes animate only the singer's head; they NEVER trigger guitar note impulses. */
    public void singNote(int pitch, int velocity) {
        if (level().isClientSide || !isAlive() || role() != MemberRole.DOLORIS) return;
        entityData.set(VOCAL_SEQUENCE, entityData.get(VOCAL_SEQUENCE) + 1);
    }

    /** Called ONLY for routed Doloris guitar notes, not for vocal/harp notes. */
    public void playUikaGuitarNote(int pitch, int velocity) {
        if (level().isClientSide || !isAlive() || role() != MemberRole.DOLORIS) return;
        if (!usesPerformanceInstrument()) return;
        uikaGuitarPoseUntil = level().getGameTime() + 34L;
        if (!entityData.get(UIKA_GUITAR_ACTIVE)) entityData.set(UIKA_GUITAR_ACTIVE, true);
        playNote(pitch, velocity);
    }

    public float clientVocalAge(float renderAge) {
        int seq = entityData.get(VOCAL_SEQUENCE);
        if (seq != clientLastVocalSequence) {
            clientLastVocalSequence = seq;
            clientLastVocalAge = renderAge;
        }
        return renderAge - clientLastVocalAge;
    }

    private net.minecraft.sounds.SoundEvent instrumentSound() {
        // Fallback rehearsal/solo notes use the same vanilla instruments as MIDI.
        return instrumentType().pose.sound;
    }

    private void playFallbackInstrument(ServerLevel server, int note, float volume, float pitch, boolean wide) {
        var instrument = instrumentType();
        var sample = instrument == MemberInstrument.DRUMS ? switch (note) {
            case 36 -> net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASEDRUM.value();
            case 38 -> net.minecraft.sounds.SoundEvents.NOTE_BLOCK_SNARE.value();
            default -> net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value();
        } : instrumentSound();
        server.playSound(null, blockPosition(), com.miseru.avemujica.music.StageConcertSounds.forStage(sample, wide),
                SoundSource.RECORDS, volume, instrument == MemberInstrument.DRUMS ? 1F : pitch);
        if (instrument == MemberInstrument.ELECTRIC) {
            var bit = net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value();
            server.playSound(null, blockPosition(), com.miseru.avemujica.music.StageConcertSounds.forStage(bit, wide),
                    SoundSource.RECORDS, volume * .66F, pitch);
        }
    }

    /** Fired for real MIDI note-on events, or for the fallback scripted concert rhythm. */
    public void playNote(int pitch, int velocity) {
        if (level().isClientSide || !isAlive()) return;
        int speed = Mth.clamp(velocity, 1, 127);
        // Several notes of a chord can occur on the same tick: keep the strongest
        // impulse but do not spam multiple data packets for the same musician.
        if (tickCount == lastNoteTick) {
            // A MIDI chord can have several note-ons in the same game tick.
            // The audio layer plays each note, but the character should strum
            // only once: later stronger notes only update that beat's intensity.
            if (speed > lastNoteVelocity) {
                lastNoteVelocity = speed;
                entityData.set(NOTE_PITCH, Mth.clamp(pitch, 0, 127));
                entityData.set(NOTE_VELOCITY, speed);
            }
            return;
        }
        lastNoteTick = tickCount;
        lastNoteVelocity = speed;
        entityData.set(NOTE_PITCH, Mth.clamp(pitch, 0, 127));
        entityData.set(NOTE_VELOCITY, speed);
        entityData.set(NOTE_SEQUENCE, entityData.get(NOTE_SEQUENCE) + 1);
        if (isPerforming() && level() instanceof ServerLevel server
                && server.getGameTime() >= nextStagePhraseTick) {
            VoiceDirector.ambient(this, VoiceDirector.Context.PERFORMANCE_PHRASE);
            nextStagePhraseTick = server.getGameTime() + 20L * (35 + random.nextInt(25));
        }
    }

    /** Keep the five positions consistent for team rehearsal, MIDI and live concerts.
     * A tier-two stage has two raised galleries and three front-row marks.
     * The rear marks are unobstructed: tier-two stages no longer pre-place
     * keyboard/drum-kit props that would duplicate the musicians' instruments.
     */
    public void assignStagePosition(Vec3 stageCenter) {
        stageFormationCenter = stageCenter;
        BlockPos controller = BlockPos.containing(stageCenter).below();
        var state = level().getBlockState(controller);
        boolean levelTwo = state.is(ModRegistry.TOUR_STAGE_LEVEL2);
        net.minecraft.core.Direction front = stageFront();
        assignedPhysicalStage = ModRegistry.isTourStage(state);
        assignedLevelTwo = levelTwo;
        assignedStageFront = front;
        com.miseru.avemujica.stage.StageFormation.Mark mark =
                com.miseru.avemujica.stage.StageFormation.rotated(role(), levelTwo, front);
        stageSpot = stageCenter.add(mark.x(), mark.y(), mark.z());
        performanceFacingYaw = front.toYRot();
        stageBestDistanceSq = Double.MAX_VALUE;
        stageLastProgressTick = level().getGameTime();
        keyboardApproachStallTicks = 0;
        galleryApproachPhase = 0;
        galleryLane = 0;
        galleryPathGoal = null;
        galleryLastPathRequestTick = 0L;
        galleryBestWaypointDistanceSq = Double.MAX_VALUE;
        galleryLastWaypointProgressTick = level().getGameTime();
        galleryStableTicks = 0;
        getNavigation().stop();
    }

    /** Resolve orientation from the controller directly below the saved formation center. */
    private net.minecraft.core.Direction stageFront() {
        if (stageFormationCenter != null) {
            BlockPos controller = BlockPos.containing(stageFormationCenter).below();
            var state = level().getBlockState(controller);
            if (ModRegistry.isTourStage(state))
                return state.getValue(com.miseru.avemujica.block.TourStageBlock.FACING);
        }
        return net.minecraft.core.Direction.SOUTH;
    }

    private boolean stageFootingClear(ServerLevel level, Vec3 mark) {
        BlockPos feet = BlockPos.containing(mark);
        if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.below())) return false;
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) return false;
        // Our gallery ramp contains lower half-slabs. Standing at the top of
        // one means the feet BLOCK is non-empty even though the space above the
        // slab's top surface is free. Check this exact stage-owned shape first.
        var feetState = level.getBlockState(feet);
        if (feetState.is(ModRegistry.STAGE_PART)
                && (feetState.getValue(com.miseru.avemujica.block.StagePartBlock.PART)
                        == com.miseru.avemujica.block.StagePart.LV2_STAIR_HALF
                    || feetState.getValue(com.miseru.avemujica.block.StagePartBlock.PART)
                        .name().startsWith("LV2_STAIR_"))
                && mark.y - feet.getY() >= .48D) {
            return level.getBlockState(feet.above())
                    .getCollisionShape(level, feet.above()).isEmpty();
        }
        if (!feetState.getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return false;
        return !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    /** Find a nearby safe mark if a player has put a block directly on our usual spot.
     * Search around the assigned mark, not around the other four musicians. */
    private void resolveBlockedStageSpot(ServerLevel level) {
        if (stageSpot == null || stageFootingClear(level, stageSpot)) return;
        Vec3 original = stageSpot;
        final int[][] offsets = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,-1},{1,-1},{-1,1},
                                 {2,0},{-2,0},{0,2},{0,-2}};
        for (int[] offset : offsets) {
            Vec3 option = original.add(offset[0], 0D, offset[1]);
            if (stageFootingClear(level, option)) {
                stageSpot = option;
                stageBestDistanceSq = Double.MAX_VALUE;
                stageLastProgressTick = level.getGameTime();
                return;
            }
        }
        // Do not move to unsafe blocks or treat a blocked mark as reached.
        getNavigation().stop();
    }

    private boolean isAlreadyOnStageDeck(ServerLevel level) {
        BlockPos floor = blockPosition().below();
        return ModRegistry.isTourStage(level.getBlockState(floor))
                || level.getBlockState(floor).is(ModRegistry.STAGE_PART);
    }

    /** The stage may be one or two blocks above its designated center. The old
     * three-dimensional radius rejected correct horizontal marks on elevated
     * stage decks and hid Sakiko's keyboard for an entire performance. */
    private boolean reachedStageMark() {
        if (stageSpot == null) return true; // Solo / no physical stage.
        double dx = getX() - stageSpot.x;
        double dz = getZ() - stageSpot.z;
        // On a real stage the Y coordinate is exact. In particular, do not
        // accept standing on the LOWER deck as arrival at a Y+2 gallery mark.
        // Off-stage/solo fallback keeps the previous lenient tolerance.
        boolean physicalStage = stageFormationCenter != null
                && ModRegistry.isTourStage(level().getBlockState(
                        BlockPos.containing(stageFormationCenter).below()));
        return dx * dx + dz * dz <= (physicalStage ? 0.81D : 2.25D)
                && Math.abs(getY() - stageSpot.y) <= (physicalStage ? 0.85D : 2.5D);
    }

    /**
     * A stable, real gallery foothold, not merely a nearby 3-D distance.
     * Both feet must be on the upper platform and near the role's OWN mark;
     * a member stuck on the bottom staircase cannot pass this test.
     */
    private boolean nearOwnGallery(ServerLevel level) {
        if (!assignedPhysicalStage || !assignedLevelTwo || stageSpot == null
                || (role() != MemberRole.OBLIVIONIS && role() != MemberRole.AMORIS)
                || !onGround() || !isAlreadyOnStageDeck(level)) return false;
        double dx = getX() - stageSpot.x;
        double dz = getZ() - stageSpot.z;
        return com.miseru.avemujica.stage.GalleryInstrumentStability.onGallery(
                true, true, onGround(), isAlreadyOnStageDeck(level),
                dx, getY() - stageSpot.y, dz);
    }

    /**
     * Once settled on the gallery, a single failed A* path / fractional foot
     * correction must not instantly unequip the instruments. On leaving the
     * actual gallery, clear the latch immediately (no floating kit on stairs).
     */
    private boolean updateGalleryStability(ServerLevel level) {
        boolean galleryFooting = nearOwnGallery(level);
        galleryStableTicks = com.miseru.avemujica.stage.GalleryInstrumentStability.update(
                galleryStableTicks, galleryFooting, getDeltaMovement().horizontalDistanceSqr());
        return com.miseru.avemujica.stage.GalleryInstrumentStability.ready(
                false, galleryFooting, galleryStableTicks);
    }

    /**
     * A* targets only standable FLOOR nodes. The stair blocks between those
     * nodes are walked naturally instead of treating half-solid stair spaces
     * as destinations. This also respects all four rotated stage orientations.
     */
    private Vec3 galleryWaypoint(int side, com.miseru.avemujica.stage.GalleryApproachRoute.Step step) {
        int localX = side * (12 + galleryLane);
        int[] xz = com.miseru.avemujica.stage.StageStructure.rotate(
                localX, (int) step.localZ(), stageFront());
        return stageFormationCenter.add(xz[0], step.elevation(), xz[1]);
    }

    private void resetGalleryGoal(ServerLevel level) {
        galleryPathGoal = null;
        galleryBestWaypointDistanceSq = Double.MAX_VALUE;
        galleryLastWaypointProgressTick = level.getGameTime();
    }

    /**
     * Main deck (z=3) -> walk UP both real stair blocks -> upper landing (z=0)
     * -> the permanent rear performance mark (z=-4). No teleports and no
     * repeated micro-targets inside a stair's collision shape.
     */
    private boolean followGalleryRoute(ServerLevel level) {
        if (stageFormationCenter == null || !assignedPhysicalStage || !assignedLevelTwo
                || (role() != MemberRole.OBLIVIONIS && role() != MemberRole.AMORIS)) return false;
        if (!isAlreadyOnStageDeck(level)) return false;

        long now = level.getGameTime();
        int side = role() == MemberRole.OBLIVIONIS ? -1 : 1;
        double relativeY = getY() - stageFormationCenter.y;
        if (galleryApproachPhase == 0)
            galleryApproachPhase = com.miseru.avemujica.stage.GalleryApproachRoute.APPROACH;
        int adjustedPhase = com.miseru.avemujica.stage.GalleryApproachRoute.adjustForHeight(
                galleryApproachPhase, relativeY);
        if (adjustedPhase != galleryApproachPhase) {
            galleryApproachPhase = adjustedPhase;
            resetGalleryGoal(level);
        }

        Vec3 next = galleryApproachPhase
                == com.miseru.avemujica.stage.GalleryApproachRoute.PERFORMANCE_MARK
                ? stageSpot : galleryWaypoint(side,
                        com.miseru.avemujica.stage.GalleryApproachRoute.step(galleryApproachPhase));
        // Reaching the main-deck approach node advances to the upper LANDING,
        // not to a collision-filled half-step. Arrival on the upper floor then
        // advances to the actual mark. Do not silently accept a lower floor.
        if (galleryApproachPhase == com.miseru.avemujica.stage.GalleryApproachRoute.APPROACH
                && com.miseru.avemujica.stage.GalleryApproachRoute.reached(
                        getX() - next.x, getY() - next.y, getZ() - next.z)) {
            galleryApproachPhase = com.miseru.avemujica.stage.GalleryApproachRoute.LANDING;
            resetGalleryGoal(level);
            next = galleryWaypoint(side,
                    com.miseru.avemujica.stage.GalleryApproachRoute.step(galleryApproachPhase));
        }
        if (galleryApproachPhase == com.miseru.avemujica.stage.GalleryApproachRoute.LANDING
                && relativeY >= 1.70D
                && com.miseru.avemujica.stage.GalleryApproachRoute.reached(
                        getX() - next.x, getY() - next.y, getZ() - next.z)) {
            galleryApproachPhase = com.miseru.avemujica.stage.GalleryApproachRoute.PERFORMANCE_MARK;
            resetGalleryGoal(level);
            next = stageSpot;
        }

        // Only check safe, solid upper/low deck nodes. If this specific lane is
        // blocked, change to one of the other two central stair lanes.
        if (!stageFootingClear(level, next)
                && galleryApproachPhase != com.miseru.avemujica.stage.GalleryApproachRoute.PERFORMANCE_MARK) {
            boolean found = false;
            for (int alternate : new int[]{0, -1, 1}) {
                galleryLane = alternate;
                Vec3 candidate = galleryWaypoint(side,
                        com.miseru.avemujica.stage.GalleryApproachRoute.step(galleryApproachPhase));
                if (stageFootingClear(level, candidate)) {
                    next = candidate;
                    found = true;
                    resetGalleryGoal(level);
                    break;
                }
            }
            if (!found) return true; // never try to navigate directly through a gallery wall
        }
        if (!stageFootingClear(level, next)) return true;

        double distance = position().distanceToSqr(next);
        boolean goalChanged = galleryPathGoal == null || galleryPathGoal.distanceToSqr(next) > .02D;
        if (goalChanged) {
            resetGalleryGoal(level);
            galleryPathGoal = next;
        }
        if (distance + .08D < galleryBestWaypointDistanceSq) {
            galleryBestWaypointDistanceSq = distance;
            galleryLastWaypointProgressTick = now;
        }
        if (now - galleryLastWaypointProgressTick > 100L
                && galleryApproachPhase != com.miseru.avemujica.stage.GalleryApproachRoute.PERFORMANCE_MARK) {
            // A blocked lane is retried only after genuine lack of progress.
            // Avoid oscillating every few ticks on the same staircase.
            galleryLane = galleryLane == 0 ? -1 : galleryLane == -1 ? 1 : 0;
            resetGalleryGoal(level);
            next = galleryWaypoint(side,
                    com.miseru.avemujica.stage.GalleryApproachRoute.step(galleryApproachPhase));
            getNavigation().stop();
            if (!stageFootingClear(level, next)) return true;
            galleryPathGoal = next;
            goalChanged = true;
        }
        // Let A* complete its route rather than discarding it every 12 ticks.
        // An exhausted/failed path can be retried without moving the target.
        if (goalChanged || getNavigation().isDone()
                || now - galleryLastPathRequestTick > 100L) {
            getNavigation().moveTo(next.x, next.y, next.z, 1.18D);
            galleryLastPathRequestTick = now;
            galleryPathGoal = next;
        }
        return true;
    }

    private void updateStageNavigation(ServerLevel level) {
        if (stageSpot == null) return; // Solo can perform at its current location.
        double remaining = position().distanceToSqr(stageSpot);
        if (reachedStageMark() || (galleryStableTicks >= com.miseru.avemujica.stage.GalleryInstrumentStability.SETTLE_TICKS
                && nearOwnGallery(level))) {
            if (!getNavigation().isDone()) getNavigation().stop();
            return;
        }
        if (tickCount % 12 != 0) return;
        resolveBlockedStageSpot(level);
        if (!stageFootingClear(level, stageSpot)) return;
        remaining = position().distanceToSqr(stageSpot);
        if (remaining + .18D < stageBestDistanceSq) {
            stageBestDistanceSq = remaining;
            stageLastProgressTick = level.getGameTime();
        }
        if (remaining > 72D * 72D) return; // Do not drag far-away NPCs across the world.

        // The packed tour-stage deck stands a full block above the terrain and
        // only its +Z frontage has the half-height access stairs. Approach those
        // stairs first instead of repeatedly running into the solid stage wall.
        if (stageFormationCenter != null
                && ModRegistry.isTourStage(level.getBlockState(BlockPos.containing(stageFormationCenter).below()))
                && !isAlreadyOnStageDeck(level)) {
            boolean levelTwo = level.getBlockState(BlockPos.containing(stageFormationCenter).below())
                    .is(ModRegistry.TOUR_STAGE_LEVEL2);
            int[] entrance = com.miseru.avemujica.stage.StageStructure.rotate(0, levelTwo ? 9 : 5, stageFront());
            Vec3 steps = stageFormationCenter.add(entrance[0], -.5D, entrance[1]);
            if (position().distanceToSqr(steps) > 2.8D) {
                getNavigation().moveTo(steps.x, steps.y, steps.z, 1.20D);
                return;
            }
        }
        // Rehearsal and concert members on the high galleries first use the
        // same visible physical risers players use. Otherwise A* tries to
        // climb the gallery wall or circle a static instrument prop forever.
        if (!followGalleryRoute(level))
            getNavigation().moveTo(stageSpot.x, stageSpot.y, stageSpot.z, 1.15D);
        // Restart a stale path without breaking blocks or teleporting the NPC.
        boolean onGalleryRoute = assignedLevelTwo && (role() == MemberRole.OBLIVIONIS
                || role() == MemberRole.AMORIS) && isAlreadyOnStageDeck(level);
        long stallLimit = 100L;
        if (!onGalleryRoute && level.getGameTime() - stageLastProgressTick > stallLimit) {
            getNavigation().stop();
            stageBestDistanceSq = Double.MAX_VALUE;
            stageLastProgressTick = level.getGameTime();
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.75D, 0.015F) {
            @Override public boolean canUse() { return (task() == 1 || teamMode() == TeamMode.FREE) && !isPerforming()
                    && debugPoseUntil <= level().getGameTime()
                    && (dutyCenter == null || position().distanceToSqr(dutyCenter) < 144D) && super.canUse(); }
            @Override public boolean canContinueToUse() { return (task() == 1 || teamMode() == TeamMode.FREE)
                    && !isPerforming() && debugPoseUntil <= level().getGameTime()
                    && super.canContinueToUse(); }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 9F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // All members defend themselves and seek hostile monsters, not just when
        // individually assigned the old guard task. Neutral mobs stay untouched.
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Monster.class, 10, true, false,
                monster -> monster.isAlive() && !(monster instanceof NeutralMob)
                        && distanceToSqr(monster) <= ATTACK_RADIUS * ATTACK_RADIUS));
    }

    /**
     * Off-stage team follow/rest/standby is social time too. Rehearsal and live
     * performance remain exclusive, as do explicit individual work/combat orders.
     * This is not permission to socialize while walking: visit selection checks
     * the recipient's idle navigation and follow is suspended during the visit.
     */
    /** Disables only spontaneous daily gestures/social scenes, not combat, concerts or player commands. */
    private boolean spontaneousActionsEnabled() {
        return manager == null || !(level() instanceof ServerLevel server)
                || BandCareerData.get(server).career(manager).dailyActionsEnabled(role().id);
    }

    private boolean maySocialize() {
        if (!spontaneousActionsEnabled()) return false;
        TeamMode mode = teamMode();
        // A current off-stage team order overrides a leftover individual task.
        // For a truly independent member (NONE), only the idle task is social.
        boolean offDuty = mode != TeamMode.NONE || task() == 0;
        return !isPerforming() && mode != TeamMode.REHEARSAL && mode != TeamMode.PERFORMANCE
                && offDuty && (getTarget() == null || !getTarget().isAlive())
                && (onGround() || (role() == MemberRole.DOLORIS && sakikoVisitTarget != null))
                && !isInWaterOrBubble();
    }

    private BandMemberEntity nearestBandmate(ServerLevel server, double distance, boolean injuredOnly) {
        if (manager == null) return null;
        return server.getEntitiesOfClass(BandMemberEntity.class,
                getBoundingBox().inflate(distance), other -> other != this && other.isAlive()
                        && Objects.equals(other.manager(), manager) && !other.isPerforming()
                        && (!injuredOnly || other.getHealth() < other.getMaxHealth() * .65F))
                .stream().min((a, b) -> Double.compare(distanceToSqr(a), distanceToSqr(b)))
                .orElse(null);
    }

    private int nearbyAvailableBandmates(ServerLevel server, double distance) {
        if (manager == null) return 0;
        return server.getEntitiesOfClass(BandMemberEntity.class, getBoundingBox().inflate(distance),
                other -> other != this && other.isAlive() && Objects.equals(other.manager(), manager)
                        && other.maySocialize() && other.personalityAction() == 0
                        && other.getNavigation().isDone()).size();
    }

    private BlockPos nearbyFlower(ServerLevel server) {
        // Do not select flowers behind the hill and then stare at empty space.
        // Only inspect flowers within visible conversational distance of the NPC.
        BlockPos origin = blockPosition();
        BlockPos best = null;
        double closest = 3.0D * 3.0D;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos sample = origin.offset(dx, dy, dz);
                double distance = position().distanceToSqr(sample.getX() + .5D,
                        sample.getY() + .4D, sample.getZ() + .5D);
                if (distance >= closest || !server.hasChunkAt(sample)
                        || !server.getBlockState(sample).is(BlockTags.FLOWERS)) continue;
                closest = distance;
                best = sample.immutable();
            }
        return best;
    }

    /** Single brief peer animation, accepted only if the recipient is free. */
    private boolean inviteReaction(int response, BandMemberEntity source, long now, int duration) {
        // Receiving a photo/flower invitation is not a random idle action.  A member
        // may accept even during the normal idle cooldown, but not during another
        // activity, locomotion, player order, or combat.
        if (!maySocialize() || personalityAction() != 0 || !getNavigation().isDone()) return false;
        if (source == this || !Objects.equals(manager, source.manager())) return false;
        if (distanceToSqr(source) > 11D * 11D) return false;
        startDailyAction(response, now, duration, 1, 290, 260);
        socialFocus = source.getUUID();
        return true;
    }

    private void invitePhoto(ServerLevel server, long now) {
        if (manager == null) return;
        int invited = 0;
        for (BandMemberEntity other : server.getEntitiesOfClass(BandMemberEntity.class,
                getBoundingBox().inflate(8D), e -> e != this && e.isAlive()
                        && Objects.equals(e.manager(), manager))) {
            if (other.inviteReaction(14, this, now, 42) && ++invited == 3) break;
        }
    }

    /** Mutsumi occasionally *offers* a cucumber to an available nearby bandmate.
     * This is a paired ambient gesture, not a source of free item stacks or healing.
     * Player-provided cucumber items are still consumed only through explicit feeding.
     */
    private boolean tryCucumberGift(ServerLevel server, long now) {
        if (role() != MemberRole.MORTIS || manager == null) return false;
        for (BandMemberEntity other : server.getEntitiesOfClass(BandMemberEntity.class,
                getBoundingBox().inflate(3D), e -> e != this && e.isAlive()
                        && Objects.equals(e.manager(), manager)
                        && e.maySocialize() && e.personalityAction() == 0
                        && e.getNavigation().isDone() && distanceToSqr(e) < 3D * 3D)) {
            if (!getSensing().hasLineOfSight(other)) continue;
            if (!other.inviteReaction(24, this, now, 58)) continue;
            startDailyAction(22, now, 58, 1, 350, 420);
            socialFocus = other.getUUID();
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    (getX() + other.getX()) / 2D,
                    (getEyeY() + other.getEyeY()) / 2D,
                    (getZ() + other.getZ()) / 2D,
                    2, .12D, .12D, .12D, .01D);
            return true;
        }
        return false;
    }

    /** Start an actual two-person scene, never a pair of unrelated idle animations.
     * Both members must already have arrived close enough for a visible interaction.
     */
    private boolean startUikaSakikoDuo(ServerLevel server, BandMemberEntity sakiko, long now) {
        if (role() != MemberRole.DOLORIS || sakiko.role() != MemberRole.OBLIVIONIS
                || !Objects.equals(manager, sakiko.manager())
                || !maySocialize() || !sakiko.maySocialize() || !onGround()
                || personalityAction() != 0 || sakiko.personalityAction() != 0
                || !sakiko.getNavigation().isDone()
                || distanceToSqr(sakiko) > 1.85D * 1.85D
                || !getSensing().hasLineOfSight(sakiko)) return false;
        // Deliberately make the hug and the hand-holding common, not rare easter eggs.
        int choice = random.nextInt(10);
        int uikaAction = choice == 9 ? 38 : choice < 3 ? 30 : choice < 5 ? 32
                : choice < 7 ? 34 : 36;
        int sakikoAction = uikaAction + 1;
        int duration = 84;
        // Accept on the server before changing Uika: either the entire pair starts or neither does.
        if (!sakiko.inviteReaction(sakikoAction, this, now, duration)) return false;
        sakiko.expectedUikaVisitor = null;
        startDailyAction(uikaAction, now, duration, 1, 290, 180);
        this.socialFocus = sakiko.getUUID();
        this.duoStartTick = now;
        this.entityData.set(DUO_PHASE, 0);
        sakiko.duoStartTick = now;
        sakiko.entityData.set(DUO_PHASE, 0);
        // Both look at each other rather than following random idle/head-look goals.
        facePartner(sakiko);
        sakiko.facePartner(this);
        nextSakikoVisitTick = now + 340L + random.nextInt(280);
        return true;
    }

    private void facePartner(BandMemberEntity companion) {
        double dx = companion.getX() - getX();
        double dz = companion.getZ() - getZ();
        if (dx * dx + dz * dz < .09D) return;
        float yaw = (float) (Mth.atan2(dz, dx) * (180D / Math.PI)) - 90F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        getLookControl().setLookAt(companion, 35F, 25F);
    }

    /** Reciprocal scene validation prevents a single NPC continuing the pose after
     * the partner is moved, unloaded, ordered, attacked, or reassigned to another player.
     */
    private boolean tickDuo(ServerLevel server, long now) {
        if (!isDuoAction(personalityAction())) return true;
        var entity = socialFocus == null ? null : server.getEntity(socialFocus);
        if (!(entity instanceof BandMemberEntity other)
                || !other.isAlive() || !other.maySocialize()
                || !Objects.equals(manager, other.manager())
                || other.socialFocus == null || !other.socialFocus.equals(getUUID())
                || !isDuoAction(other.personalityAction())
                || ((personalityAction() ^ other.personalityAction()) != 1)
                || distanceToSqr(other) > 2.55D * 2.55D
                || now < duoStartTick || duoStartTick < 0L) {
            setPersonality(0);
            socialFocus = null;
            activityUntil = 0L;
            nextPersonalityTick = now + 180L;
            return false;
        }
        long elapsed = now - duoStartTick;
        int phase = elapsed < 13 ? 0 : elapsed < 31 ? 1 : elapsed < 61 ? 2 : 3;
        if (entityData.get(DUO_PHASE) != phase) entityData.set(DUO_PHASE, phase);
        getNavigation().stop();
        facePartner(other);
        // One shared tactile sound cue at the moment of contact; never a repeated chime.
        if ((personalityAction() & 1) == 0 && elapsed == 13) {
            server.playSound(null, blockPosition(), SoundEvents.WOOL_PLACE,
                    SoundSource.NEUTRAL, .17F, 1.35F);
        }
        return true;
    }

    private void tickPersonalityEffects(ServerLevel server, long now) {
        int action = personalityAction();
        if (tickCount % 20 == 0) {
            if (action == 8 && flowerToWatch != null) {
                getLookControl().setLookAt(flowerToWatch.getX() + .5D,
                        flowerToWatch.getY() + .35D, flowerToWatch.getZ() + .5D);
            } else if (socialFocus != null) {
                var companion = server.getEntity(socialFocus);
                if (companion != null && companion.isAlive()
                        && distanceToSqr(companion) < 12D * 12D)
                    getLookControl().setLookAt(companion, 25F, 18F);
            }
        }
        if ((action == 2 || action == 21)
                && tickCount % 22 == 0) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getEyeY() - .18D,
                    getZ(), 1, .12D, .12D, .12D, 0D);
            server.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EAT,
                    SoundSource.NEUTRAL, .24F, 1.1F);
        } else if ((action == 20 || action == 23) && tickCount % 27 == 0) {
            server.sendParticles(ParticleTypes.CLOUD, getX(), getEyeY() + .05D,
                    getZ(), 1, .05D, .06D, .05D, .01D);
            server.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_DRINK,
                    SoundSource.NEUTRAL, .20F, .95F);
        } else if ((action == 3 || action == 13) && tickCount % 34 == 0) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getEyeY() + .12D,
                    getZ() - .4D, 2, .13D, .12D, .13D, .01D);
            server.playSound(null, blockPosition(),
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                    SoundSource.NEUTRAL, .17F, 1.4F);
        } else if (action == 7 && tickCount % 18 == 0) {
            com.miseru.avemujica.particle.MemberNoteParticles.emit(server, role(), getX(), getEyeY() + .26D,
                    getZ(), 1, .22D, .08D, .22D, .015D);
            server.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(),
                    SoundSource.NEUTRAL, .23F, 1.0F);
        } else if (action == 9 && instrumentType().strings() && tickCount % 27 == 0) {
            server.playSound(null, blockPosition(), instrumentSound(),
                    SoundSource.NEUTRAL, .20F, 0.7F + random.nextFloat() * .3F);
        } else if (action == 15 && tickCount % 29 == 0) {
            com.miseru.avemujica.particle.MemberNoteParticles.emit(server, role(), getX(), getEyeY() + .15D,
                    getZ(), 1, .15D, .13D, .15D, .01D);
            server.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_GUITAR.value(),
                    SoundSource.NEUTRAL, .15F, .92F + random.nextFloat() * .24F);
        } else if (action >= 40 && action <= 44 && tickCount % 24 == 0) {
            com.miseru.avemujica.particle.MemberNoteParticles.emit(server, role(), getX(), getEyeY() + .18D,
                    getZ(), 1, .10D, .10D, .10D, .002D);
            server.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.NEUTRAL, .10F, 1.1F + random.nextFloat() * .2F);
        }
    }

    /**
     * Reserve a brief quiet moment for Uika to reach Sakiko. A reservation
     * never teleports either member, is not persisted, and expires naturally.
     */
    private void reserveForUika(BandMemberEntity uika, long until) {
        if (role() != MemberRole.OBLIVIONIS) return;
        expectedUikaVisitor = uika.getUUID();
        uikaVisitorUntil = until;
        getNavigation().stop();
    }

    private boolean waitForUika(ServerLevel server, long now) {
        if (role() != MemberRole.OBLIVIONIS || expectedUikaVisitor == null) return false;
        var entity = server.getEntity(expectedUikaVisitor);
        if (now >= uikaVisitorUntil || !maySocialize() || personalityAction() != 0
                || !(entity instanceof BandMemberEntity visitor)
                || !visitor.isAlive() || visitor.role() != MemberRole.DOLORIS
                || !Objects.equals(manager, visitor.manager())
                || !getUUID().equals(visitor.sakikoVisitTarget)
                || distanceToSqr(visitor) > 12D * 12D) {
            expectedUikaVisitor = null;
            return false;
        }
        getNavigation().stop();
        getLookControl().setLookAt(visitor, 28F, 18F);
        return true;
    }

    /** Visible, reciprocal encounters instead of two independent ambient poses.
     * Uika approaches on foot; only after reaching hand-to-hand distance can
     * the pair start a synchronized hand-hold, shoulder lean, embrace or handoff.
     */
    private boolean updateUikaVisit(ServerLevel server, long now) {
        if (role() != MemberRole.DOLORIS || !maySocialize()) return false;
        if (sakikoVisitTarget != null) {
            var entity = server.getEntity(sakikoVisitTarget);
            if (!(entity instanceof BandMemberEntity sakiko) || !sakiko.isAlive()
                    || sakiko.role() != MemberRole.OBLIVIONIS
                    || !Objects.equals(sakiko.manager(), manager)
                    || !sakiko.maySocialize() || sakiko.personalityAction() != 0
                    || distanceToSqr(sakiko) > 12D * 12D || now >= sakikoVisitUntil) {
                if (entity instanceof BandMemberEntity previous
                        && getUUID().equals(previous.expectedUikaVisitor))
                    previous.expectedUikaVisitor = null;
                sakikoVisitTarget = null;
                getNavigation().stop();
                nextPersonalityTick = now + 130L;
                nextSakikoVisitTick = Math.max(nextSakikoVisitTick, now + 150L);
                return false;
            }
            double d2 = distanceToSqr(sakiko);
            if (d2 > 1.65D * 1.65D) {
                // Path to the partner, not an invisible 3.5-block social trigger.
                if (tickCount % 14 == 0) getNavigation().moveTo(sakiko, .96D);
                return true;
            }
            sakikoVisitTarget = null;
            getNavigation().stop();
            if (startUikaSakikoDuo(server, sakiko, now)) return true;
            nextPersonalityTick = now + 150L;
            nextSakikoVisitTick = now + 250L;
            return false;
        }
        // Relationship visits have their own timer. They must not be blocked by
        // the unrelated long idle-action cooldown once both are available.
        if (now < nextSakikoVisitTick || tickCount % 20 != 0) return false;
        BandMemberEntity sakiko = nearestSakiko(server);
        if (sakiko == null || !sakiko.maySocialize()
                || sakiko.personalityAction() != 0 || !sakiko.getNavigation().isDone()
                || distanceToSqr(sakiko) > 10D * 10D) return false;
        // Approach is likely when close, occasional when further apart.
        if (distanceToSqr(sakiko) > 3D * 3D && random.nextBoolean()) return false;
        sakikoVisitTarget = sakiko.getUUID();
        sakikoVisitUntil = now + 200L; // 10 seconds for a real path, not teleportation
        nextSakikoVisitTick = now + 300L + random.nextInt(180);
        sakiko.reserveForUika(this, sakikoVisitUntil);
        return updateUikaVisit(server, now);
    }

    /** Return true while daily behavior currently controls navigation or posture. */
    private boolean updatePersonality(ServerLevel server, boolean onStage) {
        long now = server.getGameTime();
        // A debugger-driven pose is a deliberate player instruction, not an ambient
        // action competing for a random cooldown. Stop random AI until preview ends.
        // A real show or combat immediately cancels the preview instead.
        if (debugPoseUntil != 0L) {
            if (now < debugPoseUntil && !onStage
                    && (getTarget() == null || !getTarget().isAlive()) && isAlive()) {
                getNavigation().stop();
                return true;
            }
            debugPoseUntil = 0L;
            setPersonality(0);
            activityUntil = 0L;
            nextPersonalityTick = now + 120L;
        }
        if (!maySocialize() || onStage) {
            expectedUikaVisitor = null;
            if (personalityAction() != 0 || seatTarget != null || sakikoVisitTarget != null) {
                setPersonality(0);
                seatTarget = null;
                flowerToWatch = null;
                socialFocus = null;
                if (sakikoVisitTarget != null) getNavigation().stop();
                sakikoVisitTarget = null;
                activityUntil = 0L;
                nextPersonalityTick = now + 100L;
            }
            return false;
        }
        if (personalityAction() != 0 && now >= activityUntil) {
            setPersonality(0);
            seatTarget = null;
            flowerToWatch = null;
            socialFocus = null;
            nextPersonalityTick = now + 160L + random.nextInt(280);
        }
        // Active actions are immutable for their short lifetime; no new random draw may
        // replace them. Only performance, hostile danger or an order can cancel them.
        if (personalityAction() != 0) {
            // A pair cannot become an independent solo pose if the other half disappears.
            if (isDuoAction(personalityAction()) && !tickDuo(server, now)) return false;
            boolean invalidSeat = personalityAction() == 1 && (seatTarget == null
                    || !isUsableSeat(server, seatTarget, false));
            boolean missingFlower = personalityAction() == 8 && (flowerToWatch == null
                    || !server.hasChunkAt(flowerToWatch)
                    || !server.getBlockState(flowerToWatch).is(BlockTags.FLOWERS));
            if (invalidSeat || missingFlower) {
                setPersonality(0);
                seatTarget = null;
                flowerToWatch = null;
                socialFocus = null;
                nextPersonalityTick = now + 180L;
            } else {
                getNavigation().stop();
                tickPersonalityEffects(server, now);
                return true;
            }
        }

        if (updateUikaVisit(server, now)) return true;
        if (waitForUika(server, now)) return true;

        // Mutsumi moves to genuine stair/chair geometry before playing a seated pose.
        if (role() == MemberRole.MORTIS && seatTarget != null) {
            if (now > seatSearchUntil || !isUsableSeat(server, seatTarget, false)) {
                seatTarget = null;
                nextPersonalityTick = now + 210L;
            } else {
                Vec3 destination = new Vec3(seatTarget.getX() + .5D,
                        seatTarget.getY() + 1D, seatTarget.getZ() + .5D);
                if (position().distanceToSqr(destination) < 2.0D) {
                    getNavigation().stop();
                    moveTo(destination.x, destination.y, destination.z, getYRot(), getXRot());
                    startDailyAction(1, now, 125, 125, 360, 320);
                } else if (tickCount % 20 == 0) {
                    getNavigation().moveTo(destination.x, destination.y, destination.z, .78D);
                }
                return true;
            }
        }
        if (now < nextPersonalityTick || tickCount % 20 != 0) return false;

        // A rare, environment-triggered response is preferred to random idle behavior.
        if (server.isRainingAt(blockPosition().above()) && random.nextInt(5) == 0) {
            startDailyAction(18, now, 35, 25, 260, 360);
            return true;
        }

        switch (role()) {
            case OBLIVIONIS -> {
                int roll = random.nextInt(9);
                int action;
                if (roll == 0) action = 40;
                else if (roll <= 2) action = 20;
                else if (lastSpontaneousAction == 6) action = 5; // Reflect after reading.
                else if (lastSpontaneousAction == 5) action = 6; // Pick up the score again.
                else action = random.nextBoolean() ? 5 : 6;
                startDailyAction(action, now,
                        action == 40 ? 60 : 55,
                        action == 40 ? 42 : 35,
                        action == 40 ? 360 : 300,
                        action == 40 ? 420 : 400);
            }
            case MORTIS -> {
                // Cucumber giving takes priority when another bandmate is available.
                // Paired animations are cosmetic: no items are duplicated or created.
                if (random.nextInt(4) == 0 && tryCucumberGift(server, now)) return true;
                BlockPos bloom = nearbyFlower(server);
                int roll = random.nextInt(10);
                if (roll == 0) {
                    startDailyAction(42, now, 58, 40, 360, 420);
                } else if (roll <= 2) {
                    startDailyAction(21, now, 38, 24, 350, 360);
                } else if (bloom != null && random.nextBoolean()) {
                    startDailyAction(8, now, 50, 45, 350, 420);
                    flowerToWatch = bloom;
                } else if (lastSpontaneousAction != 9 && random.nextBoolean()) {
                    startDailyAction(9, now, 48, 40, 330, 300);
                } else {
                    BlockPos seat = findNearbySeat(server);
                    if (seat != null) {
                        seatTarget = seat;
                        seatSearchUntil = now + 180L;
                        nextPersonalityTick = now + 380L;
                        return true;
                    }
                    startDailyAction(9, now, 38, 30, 380, 300);
                }
            }
            case TIMORIS -> {
                BandMemberEntity hurtFriend = nearestBandmate(server, 8D, true);
                if (hurtFriend != null && random.nextInt(3) == 0) {
                    startDailyAction(19, now, 40, 25, 340, 360);
                    socialFocus = hurtFriend.getUUID();
                } else {
                    int action;
                    int roll = random.nextInt(8);
                    if (roll == 0) action = 43;
                    else action = lastSpontaneousAction == 2 ? 10
                            : lastSpontaneousAction == 10 ? 11 : random.nextInt(3) == 0 ? 2 : 10 + random.nextInt(2);
                    startDailyAction(action, now,
                            action == 43 ? 56 : 46,
                            action == 43 ? 42 : 36,
                            action == 43 ? 340 : 310,
                            action == 43 ? 390 : 350);
                }
            }
            case AMORIS -> {
                int action;
                if (random.nextInt(18) == 0) action = 45; // Rare donut snack, not a food requirement.
                else if (random.nextInt(7) == 0) action = 44;
                else action = nearbyAvailableBandmates(server, 8D) >= 2 && random.nextInt(3) == 0 ? 13
                        : lastSpontaneousAction == 3 ? 12 : (random.nextBoolean() ? 3 : 12);
                startDailyAction(action, now,
                        action == 44 ? 54 : action == 45 ? 38 : 50,
                        action == 44 ? 40 : action == 45 ? 28 : 38,
                        action == 44 ? 300 : 260,
                        action == 44 ? 380 : 350);
                if (action == 13) invitePhoto(server, now);
            }
            case DOLORIS -> {
                int roll = random.nextInt(8);
                if (roll == 0) startDailyAction(41, now, 56, 42, 320, 410);
                else if (roll <= 2) startDailyAction(23, now, 38, 26, 320, 370); // coffee
                else startDailyAction(15, now, 44, 40, 290, 390);
            }
        }
        return personalityAction() != 0;
    }

    /** Cancel a synchronized duet atomically before taking over one participant. */
    private void cancelSocialForPreview(ServerLevel server, long now) {
        if (socialFocus != null && isDuoAction(personalityAction())) {
            var paired = server.getEntity(socialFocus);
            if (paired instanceof BandMemberEntity partner && partner.socialFocus != null
                    && partner.socialFocus.equals(getUUID())
                    && Objects.equals(manager, partner.manager())) {
                partner.setPersonality(0);
                partner.socialFocus = null;
                partner.duoStartTick = -1L;
                partner.activityUntil = 0L;
                partner.nextPersonalityTick = now + 120L;
                partner.getNavigation().stop();
            }
        }
        if (sakikoVisitTarget != null) {
            var visited = server.getEntity(sakikoVisitTarget);
            if (visited instanceof BandMemberEntity sakiko
                    && getUUID().equals(sakiko.expectedUikaVisitor))
                sakiko.expectedUikaVisitor = null;
        }
        if (expectedUikaVisitor != null) {
            var visiting = server.getEntity(expectedUikaVisitor);
            if (visiting instanceof BandMemberEntity uika
                    && getUUID().equals(uika.sakikoVisitTarget)) {
                uika.sakikoVisitTarget = null;
                uika.getNavigation().stop();
                uika.nextSakikoVisitTick = now + 180L;
            }
        }
        socialFocus = null;
        sakikoVisitTarget = null;
        expectedUikaVisitor = null;
        flowerToWatch = null;
        seatTarget = null;
    }

    /** Five-second manual preview, preempting passive actions and reciprocal duets. */
    public boolean debugTriggerPose(ServerPlayer player, int action) {
        if (!(player.hasPermissions(2) || player.getAbilities().instabuild)
                || !canBeManagedBy(player) || !isAlive()
                || isPerforming() || isSoloing()
                || teamMode() == TeamMode.REHEARSAL || teamMode() == TeamMode.PERFORMANCE
                || MidiBandPlayer.isPlayingFor(this) || MidiBandPlayer.isSilentFor(this)
                || (getTarget() != null && getTarget().isAlive())
                || isInWaterOrBubble() || !onGround()) return false;
        boolean allowed = false;
        for (int slot = 0; slot < PoseDebugActions.SLOT_COUNT; slot++) {
            if (PoseDebugActions.action(role(), slot) == action) { allowed = true; break; }
        }
        if (!allowed) return false;
        long now = level().getGameTime();
        if (level() instanceof ServerLevel server) cancelSocialForPreview(server, now);
        getNavigation().stop();
        setPersonality(action);
        lastSpontaneousAction = action;
        activityUntil = now + 100L;
        debugPoseUntil = now + 100L;
        nextPersonalityTick = now + 240L;
        return true;
    }

    /** Stop manual and ordinary solo poses, not active concerts or real combat. */
    public boolean debugResetPose(ServerPlayer player) {
        if (!(player.hasPermissions(2) || player.getAbilities().instabuild)
                || !canBeManagedBy(player) || !isAlive()
                || isPerforming() || MidiBandPlayer.isPlayingFor(this)
                || (getTarget() != null && getTarget().isAlive())) return false;
        long now = level().getGameTime();
        if (level() instanceof ServerLevel server) cancelSocialForPreview(server, now);
        debugPoseUntil = 0L;
        setPersonality(0);
        activityUntil = 0L;
        nextPersonalityTick = now + 180L;
        getNavigation().stop();
        return true;
    }

    private void startDailyAction(int action, long now, int minDuration,
                                  int extraDuration, int minGap, int extraGap) {
        setPersonality(action);
        lastSpontaneousAction = action;
        activityUntil = now + minDuration + random.nextInt(Math.max(1, extraDuration));
        nextPersonalityTick = activityUntil + minGap + random.nextInt(Math.max(1, extraGap));
        getNavigation().stop();
    }

    private BandMemberEntity nearestSakiko(ServerLevel server) {
        return server.getEntitiesOfClass(BandMemberEntity.class,
                getBoundingBox().inflate(11D), other -> other != this
                        && other.isAlive() && other.role() == MemberRole.OBLIVIONIS
                        && Objects.equals(other.manager(), manager)
                        && other.maySocialize())
                .stream().min((a, b) -> Double.compare(distanceToSqr(a), distanceToSqr(b)))
                .orElse(null);
    }

    private BlockPos findNearbySeat(ServerLevel server) {
        BlockPos origin = blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) for (int y = -1; y <= 2; y++) {
            BlockPos candidate = origin.offset(x, y, z);
            double dist = candidate.distSqr(origin);
            if (dist >= bestDist || dist < .5D || !isUsableSeat(server, candidate, true)) continue;
            best = candidate.immutable();
            bestDist = dist;
        }
        return best;
    }

    private boolean isUsableSeat(ServerLevel server, BlockPos spot, boolean checkOtherMembers) {
        if (!server.isLoaded(spot)) return false;
        BlockState state = server.getBlockState(spot);
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (!(state.getBlock() instanceof StairBlock) && !name.endsWith("_chair")
                && !name.equals("chair") && !name.endsWith("_stool")) return false;
        if (!server.getBlockState(spot.above()).getCollisionShape(server, spot.above()).isEmpty()
                || !server.getBlockState(spot.above(2)).getCollisionShape(server, spot.above(2)).isEmpty()) return false;
        if (checkOtherMembers && !server.getEntitiesOfClass(BandMemberEntity.class,
                new AABB(spot).inflate(.8D), member -> member != this && member.isAlive()).isEmpty()) return false;
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        if (!PlayerBandRoster.observe(this)) return;
        tickUikaRage(server);
        tickTimorisProtein(server);
        tickDailyGift(server);
        if (task() == 1 && dutyCenter == null) dutyCenter = position();
        if (tickCount % 40 == 0 && (teamMode() == TeamMode.REHEARSAL || teamMode() == TeamMode.PERFORMANCE))
            MidiBandPlayer.ensureDefaultForTeam(this);
        if (isSoloing()) MidiBandPlayer.ensureDefaultForSolo(this);
        // Recover formation after a chunk unload/rejoin during a paid show.
        // Only the owning player's current show may provide this position.
        if (stageSpot == null && manager != null && tickCount % 40 == 0
                && teamMode() != TeamMode.REHEARSAL && teamMode() != TeamMode.PERFORMANCE) {
            Vec3 resumedStage = BandManagement.activeShowFormation(server, manager, position());
            if (resumedStage != null) assignStagePosition(resumedStage);
        }
        boolean midiPerforming = MidiBandPlayer.isPlayingFor(this);
        boolean silentStage = MidiBandPlayer.isSilentFor(this) && !midiPerforming;
        boolean teamStage = teamMode() == TeamMode.PERFORMANCE || teamMode() == TeamMode.REHEARSAL;
        boolean onStage = teamStage || midiPerforming || silentStage
                || BandManagement.isPerforming(server, manager, blockPosition())
                || soloUntil > server.getGameTime();
        // During rage, Uika must be free to chase enemies instead of being stage-locked.
        if (isUikaRaging()) onStage = false;
        if (entityData.get(SILENT_STAGE) != (onStage && silentStage))
            entityData.set(SILENT_STAGE, onStage && silentStage);
        // Derived from the real stage state; do not conflate manual hold-position with performance.
        if (entityData.get(PERFORMING) != onStage) {
            entityData.set(PERFORMING, onStage);
            if (onStage) {
                VoiceDirector.ambient(this, teamMode() == TeamMode.REHEARSAL
                        ? VoiceDirector.Context.REHEARSAL_START
                        : VoiceDirector.Context.PERFORMANCE_START);
                nextStagePhraseTick = server.getGameTime() + 20L * (26 + random.nextInt(18));
            }
            performanceFacingYaw = null;
            keyboardApproachStallTicks = 0;
            nextFlourishTick = server.getGameTime() + 100L + random.nextInt(100);
            flourishEndTick = 0L;
            entityData.set(FLOURISH_KIND, 0);
            if (!onStage) {
                lastPerformanceEndTick = server.getGameTime();
                stageSpot = null;
                getNavigation().stop();
            }
        }
        if (entityData.get(MIDI_MODE) != midiPerforming) entityData.set(MIDI_MODE, midiPerforming);
        if (role() == MemberRole.DOLORIS && entityData.get(UIKA_GUITAR_ACTIVE)
                && (silentStage || !usesPerformanceInstrument() || server.getGameTime() >= uikaGuitarPoseUntil)) {
            entityData.set(UIKA_GUITAR_ACTIVE, false);
        }
        if (onStage) {
            // After NBT restoration the controller may load later than this
            // NPC. Correct the cached marks once the real stage is visible,
            // but never repeatedly reset a valid obstruction-avoidance mark.
            if (stageFormationCenter != null && tickCount % 40 == 0) {
                BlockPos centerBlock = BlockPos.containing(stageFormationCenter).below();
                var controllerState = server.getBlockState(centerBlock);
                if (ModRegistry.isTourStage(controllerState)) {
                    boolean tierTwoNow = controllerState.is(ModRegistry.TOUR_STAGE_LEVEL2);
                    var facingNow = controllerState.getValue(
                            com.miseru.avemujica.block.TourStageBlock.FACING);
                    if (!assignedPhysicalStage || tierTwoNow != assignedLevelTwo
                            || facingNow != assignedStageFront) {
                        assignStagePosition(stageFormationCenter);
                    }
                }
            }
            // Remain on stage; ranged defense can still fire without chasing.
            updateStageNavigation(server);
            // Let pathfinding orient members until they reach the mark, then lock.
            // Keyboard/drum roles should always face the audience once they enter a stage state,
            // otherwise their large anchored instruments visibly spin with every look update.
            boolean atMark = reachedStageMark();
            boolean galleryPerformer = assignedPhysicalStage && assignedLevelTwo && stageSpot != null
                    && (role() == MemberRole.OBLIVIONIS || role() == MemberRole.AMORIS);
            updateGalleryStability(server);
            // Exact mark remains preferred, but an adjacent, quiet upper-deck
            // position is a legitimate performance spot when A* cannot find
            // the last few pixels of path. Never accept the lower deck.
            boolean performanceReady = galleryPerformer
                    ? com.miseru.avemujica.stage.GalleryInstrumentStability.ready(
                            atMark, nearOwnGallery(server), galleryStableTicks)
                    : atMark;
            // A blocked/pathless destination must not hide the keyboard forever.
            // Only settle if Sakiko has stopped on solid ground for 30 ticks;
            // walking toward a reachable mark still uses normal navigation.
            boolean builtStage = stageFormationCenter != null
                    && ModRegistry.isTourStage(server.getBlockState(
                            BlockPos.containing(stageFormationCenter).below()));
            if (usesPerformanceInstrument() && instrumentType() == MemberInstrument.KEYBOARD && !builtStage && !atMark && onGround()
                    && getDeltaMovement().horizontalDistanceSqr() < .0016D) {
                keyboardApproachStallTicks++;
                if (keyboardApproachStallTicks >= 30) {
                    getNavigation().stop();
                    stageSpot = position(); // Temporary performance mark, not saved as a world edit.
                    atMark = true;
                    performanceReady = true;
                    keyboardApproachStallTicks = 0;
                }
            } else {
                keyboardApproachStallTicks = 0;
            }
            // The drummer's stool and kit share one synced state. Do not
            // require Navigation.isDone(): a reissued path is common even when
            // Nyamu is visibly standing still on her own gallery mark.
            boolean drumReady = usesPerformanceInstrument() && instrumentType() == MemberInstrument.DRUMS && performanceReady
                    && onGround() && getDeltaMovement().horizontalDistanceSqr() < .025D;
            if (entityData.get(DRUM_SEATED) != drumReady)
                entityData.set(DRUM_SEATED, drumReady);
            boolean keyboardReady = usesPerformanceInstrument() && instrumentType() == MemberInstrument.KEYBOARD && performanceReady;
            if (entityData.get(KEYBOARD_LOCKED) != keyboardReady)
                entityData.set(KEYBOARD_LOCKED, keyboardReady);
            // Before reaching the mark, Sakiko walks normally without hauling
            // a free-floating keyboard through every pathfinding rotation.
            // At the mark the keyboard appears and her facing is fixed.
            // Do not forcibly set keyboard/drummer yaw while pathfinder is steering.
            // Doing so made them repeatedly turn away from their path and get stuck.
            if (performanceReady) {
                if (performanceFacingYaw == null) performanceFacingYaw = stageSpot == null ? getYRot() : stageFront().toYRot();
                getNavigation().stop();
                // This runs AFTER super.tick(), overriding LookAtPlayerGoal / RandomLookAroundGoal.
                setYRot(performanceFacingYaw);
                yBodyRot = performanceFacingYaw;
                yHeadRot = performanceFacingYaw;
                // Stop prior frame interpolation causing the body to swivel
                // while a fixed keyboard remains in front of the musician.
                if (keyboardReady || drumReady) {
                    yBodyRotO = performanceFacingYaw;
                    yHeadRotO = performanceFacingYaw;
                }
                setXRot(0F);
            }
            if (!silentStage && teamMode() != TeamMode.REHEARSAL) updateFlourishes(server, atMark);
            else if (entityData.get(FLOURISH_KIND) != 0) entityData.set(FLOURISH_KIND, 0);
            if (!silentStage) emitThemeSpotlight(server, atMark);
            // Non-MIDI concerts have scripted rhythm; when MIDI is active, real
            // note-on events drive all performance impulses instead.
            int beat = switch (instrumentType().pose) {
                case AMORIS -> 7;
                case TIMORIS -> 10;
                case DOLORIS, MORTIS -> 9;
                case OBLIVIONIS -> 8;
            };
            if (teamMode() == TeamMode.REHEARSAL) beat = Math.round(beat * 1.55F);
            if (!silentStage && tickCount % beat == 0) {
                if (!midiPerforming && usesPerformanceInstrument()) {
                    boolean drums = instrumentType() == MemberInstrument.DRUMS;
                    int note = drums
                            ? ((tickCount / beat) % 4 == 0 ? 36 : ((tickCount / beat) % 2 == 0 ? 38 : 42))
                            : 56 + (tickCount / beat + role().ordinal() * 3) % 12;
                    if (role() == MemberRole.DOLORIS) playUikaGuitarNote(note, drums ? 110 : 80);
                    else playNote(note, drums ? 110 : 80);
                    com.miseru.avemujica.particle.MemberNoteParticles.emit(server, role(), getX(), getEyeY() + .3, getZ(),
                            teamMode() == TeamMode.REHEARSAL ? 1 : 2, .35, .2, .35, .03);
                    float scriptedLevel = teamMode() == TeamMode.REHEARSAL ? .14F : .35F;
                    scriptedLevel = com.miseru.avemujica.stage.StageAcoustics.scriptedVolume(
                            scriptedLevel, assignedPhysicalStage && assignedLevelTwo,
                            teamMode() == TeamMode.REHEARSAL);
                    // Scripted rehearsal/concert rhythm is audible without MIDI, but it
                    // still belongs to the musician's saved mixer channel. Applying the
                    // fader after stage amplification preserves a true 0% mute.
                    int mixerPercent = manager == null ? 100
                            : com.miseru.avemujica.music.MidiInstrumentSettings.volumePercent(
                                    BandCareerData.get(server).career(manager), role() == MemberRole.DOLORIS
                                            ? com.miseru.avemujica.music.MidiInstrumentSettings.UIKA_GUITAR_ID : role().id);
                    scriptedLevel *= mixerPercent / 100F;
                    if (assignedPhysicalStage && assignedLevelTwo)
                        scriptedLevel = Math.min(com.miseru.avemujica.stage.StageAcoustics.MAX_CONCERT_VOLUME,
                                scriptedLevel);
                    if (scriptedLevel > 0F) {
                        playFallbackInstrument(server, note, scriptedLevel,
                                .78F + ((tickCount / beat + role().ordinal()) % 5) * .14F,
                                assignedPhysicalStage && assignedLevelTwo);
                    }
                }
            }
        }
        if (!onStage && entityData.get(DRUM_SEATED)) entityData.set(DRUM_SEATED, false);
        if (!onStage) {
            galleryStableTicks = 0;
            keyboardApproachStallTicks = 0;
            if (entityData.get(KEYBOARD_LOCKED)) entityData.set(KEYBOARD_LOCKED, false);
        }
        if (teamMode() == TeamMode.FOLLOW && !onStage) updateTeamFollow(server);
        if (role() == MemberRole.OBLIVIONIS && teamMode() != TeamMode.NONE && tickCount % 80 == 0)
            TeamOrders.refresh(this);
        boolean personalityBusy = updatePersonality(server, onStage);
        // When both are genuinely off-duty, Uika naturally turns towards Sakiko.
        // No movement, extra sounds or persistent target is created by this glance.
        if (role() == MemberRole.DOLORIS && !personalityBusy && maySocialize()
                && tickCount % 45 == 0 && random.nextInt(3) != 0) {
            BandMemberEntity sakiko = nearestSakiko(server);
            if (sakiko != null && distanceToSqr(sakiko) < 8D * 8D
                    && getSensing().hasLineOfSight(sakiko))
                getLookControl().setLookAt(sakiko, 24F, 18F);
        }
        // Greeting is event-driven (owner enters 9 blocks after leaving 14 blocks).
        if (manager != null && tickCount % 40 == 0) {
            ServerPlayer ownerPlayer = server.getServer().getPlayerList().getPlayer(manager);
            boolean near = ownerPlayer != null && ownerPlayer.level() == server
                    && distanceToSqr(ownerPlayer) < 9D * 9D;
            boolean far = ownerPlayer == null || ownerPlayer.level() != server
                    || distanceToSqr(ownerPlayer) > 14D * 14D;
            if (!onStage && teamMode() != TeamMode.REST && task() != 3
                    && near && !ownerWasClose && server.getGameTime() >= nextGreetingAllowed) {
                VoiceDirector.ambient(this, VoiceDirector.Context.GREETING);
                nextGreetingAllowed = server.getGameTime() + 20L * 150L;
            }
            if (near) ownerWasClose = true;
            else if (far) ownerWasClose = false;
        }
        boolean resting = !onStage && (teamMode() == TeamMode.REST || task() == 3);
        if (resting && !lastVoiceResting) {
            VoiceDirector.ambient(this, VoiceDirector.Context.REST);
        }
        lastVoiceResting = resting;
        int currentAction = personalityAction();
        if (!onStage && !resting && currentAction > 0 && currentAction != lastVoicePersonality
                && server.getGameTime() >= nextIdleVoiceTick) {
            VoiceDirector.ambient(this, VoiceDirector.Context.DAILY_ACTION);
            nextIdleVoiceTick = server.getGameTime() + 20L * (32 + random.nextInt(27));
        }
        lastVoicePersonality = currentAction;
        if (!onStage && !resting && getTarget() == null && !personalityBusy && currentAction == 0
                && getNavigation().isDone() && getDeltaMovement().horizontalDistanceSqr() < .003D
                && server.getGameTime() >= nextIdleVoiceTick) {
            VoiceDirector.ambient(this, VoiceDirector.Context.IDLE);
            nextIdleVoiceTick = server.getGameTime() + 20L * (36 + random.nextInt(35));
        }
        if (tickCount % 20 == 0) {
            entityData.set(CONCERT, groupIsComplete(server));
            if (task() == 1 && dutyCenter != null && !onStage
                    && position().distanceToSqr(dutyCenter) > 144D)
                getNavigation().moveTo(dutyCenter.x, dutyCenter.y, dutyCenter.z, 1.05D);
            if (task() != 1 && teamMode() != TeamMode.FREE && teamMode() != TeamMode.FOLLOW
                    && !onStage && !personalityBusy && !chasingWithChainsaw(server)) getNavigation().stop();
            if (teamMode() == TeamMode.FREE && dutyCenter != null
                    && position().distanceToSqr(dutyCenter) > 144D)
                getNavigation().moveTo(dutyCenter.x, dutyCenter.y, dutyCenter.z, 0.85D);
            if (task() == 3 && !onStage && tickCount % 40 == 0 && getHealth() < getMaxHealth()) heal(1F);
        }
        if (task() != 1 && teamMode() != TeamMode.FREE && teamMode() != TeamMode.FOLLOW
                && !onStage && !personalityBusy && !chasingWithChainsaw(server)) getNavigation().stop();
        if (--cooldown <= 0) {
            cooldown = alternateCombat() ? switch (role()) {
                case OBLIVIONIS -> 17;
                case DOLORIS -> 8;
                case TIMORIS -> 12;
                default -> role().attackInterval;
            } : Math.max(9, role().attackInterval - (concertActive() ? 6 : 0));
            attackNearbyMonster(server);
        }
        if (--chorusCooldown <= 0) {
            chorusCooldown = 180;
            if (role() == MemberRole.OBLIVIONIS && concertActive() && hasHostileThreat(server, 12D)) performChorus(server);
        }
    }

    /** Rare, role-specific stage accents. Never generates extra music notes or movement. */
    private void emitThemeSpotlight(ServerLevel server, boolean atMark) {
        if (assignedPhysicalStage && assignedLevelTwo) return; // real client beams
        if (!atMark) return;
        int interval = teamMode() == TeamMode.REHEARSAL ? 16 : 8;
        if (tickCount % interval != 0) return;
        MemberRole role = role();
        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f(role.colorR(), role.colorG(), role.colorB()),
                teamMode() == TeamMode.REHEARSAL ? 1.0F : 1.45F);
        double x = getX();
        double y = getY() + 2.1D;
        double z = getZ();
        // soft overhead wash
        server.sendParticles(dust, x, y + 0.6D, z,
                teamMode() == TeamMode.REHEARSAL ? 4 : 8,
                0.35D, 0.9D, 0.35D, 0.01D);
        // vertical beam feel
        for (int i = 0; i < 3; i++) {
            server.sendParticles(dust, x, y + i * 0.55D, z,
                    teamMode() == TeamMode.REHEARSAL ? 2 : 3,
                    0.10D, 0.05D, 0.10D, 0.0D);
        }
        // active sparkle around flourish moments
        if (entityData.get(FLOURISH_KIND) != 0) {
            server.sendParticles(dust, x, getEyeY() + 0.25D, z,
                    5, 0.28D, 0.34D, 0.28D, 0.01D);
        }
    }

    private void updateFlourishes(ServerLevel server, boolean atMark) {
        long now = server.getGameTime();
        if (!atMark) {
            if (entityData.get(FLOURISH_KIND) != 0) entityData.set(FLOURISH_KIND, 0);
            return;
        }
        if (entityData.get(FLOURISH_KIND) != 0 && now >= flourishEndTick) {
            entityData.set(FLOURISH_KIND, 0);
        }
        if (now < nextFlourishTick || entityData.get(FLOURISH_KIND) != 0) return;
        // Each accent lasts about 1–1.5 seconds, followed by a randomized 8–16s gap.
        entityData.set(FLOURISH_KIND, random.nextBoolean() ? 1 : 2);
        entityData.set(FLOURISH_SEQUENCE, entityData.get(FLOURISH_SEQUENCE) + 1);
        flourishEndTick = now + 26L;
        nextFlourishTick = flourishEndTick + 160L + random.nextInt(160);
    }

    /** Neutrals are not provoked by default, although retaliation still works. */
    private boolean hasHostileThreat(ServerLevel server, double radius) {
        return !server.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(radius),
                mob -> mob.isAlive() && !(mob instanceof NeutralMob) && hasLineOfSight(mob)).isEmpty();
    }

    private boolean chasingWithChainsaw(ServerLevel server) {
        if (!alternateCombat() || role() != MemberRole.DOLORIS || isPerforming()) return false;
        return !server.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(12D),
                mob -> mob.isAlive() && !(mob instanceof NeutralMob) && hasLineOfSight(mob)).isEmpty();
    }

    private void attackNearbyMonster(ServerLevel server) {
        // A mob that actually attacked us is a valid retaliation target, even if
        // it is normally neutral. Otherwise, do not provoke neutral creatures.
        Monster target = getTarget() instanceof Monster attacker && attacker.isAlive()
                && distanceToSqr(attacker) <= ATTACK_RADIUS * ATTACK_RADIUS
                && hasLineOfSight(attacker) ? attacker : null;
        if (target == null) {
            target = server.getEntitiesOfClass(Monster.class,
                    getBoundingBox().inflate(ATTACK_RADIUS),
                    mob -> mob.isAlive() && !(mob instanceof NeutralMob) && hasLineOfSight(mob))
                    .stream().min((a,b) -> Double.compare(distanceToSqr(a), distanceToSqr(b))).orElse(null);
        }
        if (target == null) return;
        if (personalityAction() != 0 || seatTarget != null) {
            setPersonality(0);
            seatTarget = null;
            flowerToWatch = null;
            socialFocus = null;
            nextPersonalityTick = server.getGameTime() + 140L;
        }
        getLookControl().setLookAt(target, 40.0F, 35.0F);
        if (alternateCombat()) {
            if (role() == MemberRole.OBLIVIONIS && distanceToSqr(target) <= 3.1D * 3.1D) {
                strikeMelee(server, target, 7.5F, 0.9D, false);
                return;
            }
            if (role() == MemberRole.DOLORIS) {
                if (distanceToSqr(target) > 2.5D * 2.5D) {
                    // Never run off the stage or into the audience during a show.
                    if (!isPerforming() && !isInWaterOrBubble() && distanceToSqr(target) < 14D * 14D)
                        getNavigation().moveTo(target, 1.23D);
                    return;
                }
                getNavigation().stop();
                strikeMelee(server, target, 4.8F, 0.36D, true);
                return;
            }
            if (role() == MemberRole.TIMORIS) {
                RifleRoundEntity bullet = new RifleRoundEntity(server, this);
                bullet.setPos(getX(), getEyeY() - .17D, getZ());
                Vec3 shot = target.position().add(0D, target.getBbHeight() * .57D, 0D)
                        .subtract(bullet.position());
                bullet.shoot(shot.x, shot.y, shot.z, 2.6F, .25F);
                server.addFreshEntity(bullet);
                markCombatStrike();
                MidiBandPlayer.combatAttack(this); // selected MIDI provides battle notes; keep rifle SFX
                server.playSound(null, blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, .64F, 1.25F);
                server.sendParticles(ParticleTypes.SMOKE, getX(), getEyeY() - .12D, getZ(),
                        5, .14D, .12D, .14D, .028D);
                return;
            }
        }
        if (alternateCombat() && role() == MemberRole.OBLIVIONIS) markCombatStrike();
        NoteBoltEntity bolt = new NoteBoltEntity(server, this);
        bolt.setPos(getX(), getEyeY() - 0.12D, getZ());
        Vec3 aim = target.position().add(0D, target.getBbHeight() * 0.55D, 0D).subtract(bolt.position());
        bolt.shoot(aim.x, aim.y, aim.z, 1.22F, 0.3F);
        server.addFreshEntity(bolt);
        // Attacks cause damage on their own cooldown; music follows the owner's
        // selected MIDI timeline with the original notes, chords and rests.
        // Do not layer the old fixed-pitch attack over an active MIDI song.
        if (!MidiBandPlayer.isPlayingFor(this) && !MidiBandPlayer.combatAttack(this)
                && usesPerformanceInstrument()) {
            int pitch = instrumentType().notePitch(60 + role().ordinal() * 3, false);
            playNote(pitch, 110);
            playFallbackInstrument(server, pitch, concertActive() ? .85F : .55F,
                    .82F + ((tickCount / 10 + role().ordinal() * 2) % 8) * .09F, false);
            com.miseru.avemujica.particle.MemberNoteParticles.emit(server, role(), getX(), getEyeY() + .1, getZ(),
                    concertActive() ? 7 : 3, .35, .2, .35, .12);
        }
    }

    private void strikeMelee(ServerLevel server, Monster enemy, float baseDamage,
                             double knockback, boolean chainsaw) {
        double trained = BandCareerData.get(server).combatMultiplier(manager);
        float damage = (float)(baseDamage * trained * attackTrainingMultiplier());
        // Custom melee deals direct damage; unlike vanilla doHurtTarget, it does not
        // automatically apply the Strength potion. Account for Strength II here.
        if (chainsaw && isUikaRaging()) {
            MobEffectInstance strength = getEffect(MobEffects.DAMAGE_BOOST);
            if (strength != null) damage += 3F * (strength.getAmplifier() + 1);
        }
        if (!enemy.hurt(damageSources().mobAttack(this), damage)) return;
        enemy.knockback(knockback, getX() - enemy.getX(), getZ() - enemy.getZ());
        markCombatStrike();
        MidiBandPlayer.combatAttack(this); // keep melee/chain SFX; soundtrack is role-routed MIDI
        if (chainsaw) {
            server.playSound(null, blockPosition(), SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.HOSTILE,
                    .75F, .78F + random.nextFloat() * .18F);
            server.sendParticles(ParticleTypes.CRIT, enemy.getX(), enemy.getY() + .9D, enemy.getZ(),
                    9, .4D, .5D, .4D, .02D);
        } else {
            server.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, .8F, 1.1F);
            server.sendParticles(ParticleTypes.SWEEP_ATTACK, enemy.getX(), enemy.getY() + .85D,
                    enemy.getZ(), 1, .1D, .05D, .1D, 0D);
        }
    }

    private boolean groupIsComplete(ServerLevel level) {
        EnumSet<MemberRole> roles = EnumSet.noneOf(MemberRole.class);
        for (BandMemberEntity e : level.getEntitiesOfClass(BandMemberEntity.class,
                getBoundingBox().inflate(GROUP_RADIUS), e -> e.isAlive() && Objects.equals(e.manager(), this.manager))) {
            roles.add(e.role());
        }
        return roles.size() == MemberRole.values().length;
    }

    private void performChorus(ServerLevel server) {
        // Only Oblivionis leads the burst. No teamwide multipliers per tick.
        List<Monster> enemies = server.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(12), Monster::isAlive);
        for (Monster mob : enemies) {
            double training = BandCareerData.get(server).combatMultiplier(manager);
            mob.hurt(damageSources().mobAttack(this), (float)(8.0F * training * attackTrainingMultiplier()));
            mob.knockback(2.4D, getX() - mob.getX(), getZ() - mob.getZ());
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 65, 1));
        }
        for (int i = 0; i < 36; i++) {
            double angle = i * (Math.PI * 2D / 36D);
            server.sendParticles(ParticleTypes.NOTE, getX() + Math.cos(angle) * 7D,
                    getY() + .7D, getZ() + Math.sin(angle) * 7D, 2, .1, .2, .1, 0.0D);
        }
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.5D, getZ(),
                45, 5D, .6D, 5D, .15D);
        server.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.RECORDS, 1.5F, .75F);
        for (Player player : server.players()) {
            if (player.distanceToSqr(this) < 32D * 32D)
                player.displayClientMessage(Component.translatable("message.avemujica.chorus"), true);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.CONSUME;
        // Dedicated pose debugger wins before the normal management GUI.
        if (player.getItemInHand(hand).is(ModRegistry.POSE_DEBUGGER)) {
            if (!(serverPlayer.hasPermissions(2) || serverPlayer.getAbilities().instabuild)) {
                serverPlayer.displayClientMessage(Component.literal("动作调试器仅限创造模式或管理员。"), true);
            } else if (!canBeManagedBy(serverPlayer)) {
                serverPlayer.displayClientMessage(Component.literal("只能调试自己管理的成员。"), true);
            } else {
                serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                        new PoseDebugMenu(id, inventory, getId()),
                        Component.literal("Ave Mujica · 动作调试器")));
            }
            return InteractionResult.CONSUME;
        }
        // All edible items, including previously accepted vanilla foods and
        // non-favorite custom snacks, enter the same feeding path. A favorite
        // gets stronger healing/special dialogue, never exclusive permission.
        if (hand == InteractionHand.MAIN_HAND && !serverPlayer.getMainHandItem().isEmpty()
                && serverPlayer.getMainHandItem().get(DataComponents.FOOD) != null) {
            float previousHealth = getHealth();
            if (feedFrom(serverPlayer) && getHealth() > previousHealth)
                VoiceDirector.feature(this, VoiceDirector.Feature.HEALED);
            return InteractionResult.CONSUME;
        }
        serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new MemberInteractionMenu(id, inventory, getId()),
                Component.literal(role().display)));
        return InteractionResult.CONSUME;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            triggerUikaRageOnSakikoDeath(source);
            PlayerBandRoster.onDeath(this);
        }
        super.die(source);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide) PlayerBandRoster.onRemoval(this, reason);
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("AveMember", role().id);
        if (manager != null) tag.putUUID("AveManager", manager);
        tag.putInt("AveTask", entityData.get(TASK_MODE));
        tag.putInt("AveTeamMode", teamMode().ordinal());
        if (teamLeader != null) tag.putUUID("AveTeamLeader", teamLeader);
        tag.putBoolean("AveTeamOptOut", teamOptOut);
        if (teamStageCenter != null) {
            tag.putDouble("AveTeamStageX", teamStageCenter.x);
            tag.putDouble("AveTeamStageY", teamStageCenter.y);
            tag.putDouble("AveTeamStageZ", teamStageCenter.z);
        }
        if (dutyCenter != null) {
            tag.putDouble("AveDutyX", dutyCenter.x);
            tag.putDouble("AveDutyY", dutyCenter.y);
            tag.putDouble("AveDutyZ", dutyCenter.z);
        }
        tag.putInt("AveChorusCd", chorusCooldown);
        tag.putInt("AveTrainingRank", trainingRank());
        tag.putInt("AveInstrumentColor", instrumentColor());
        tag.putString("AveInstrument", instrumentType().id);
        tag.putBoolean("AveInstrumentEnabled", usesPerformanceInstrument());
        tag.putBoolean("AveSecondStyleUnlocked", alternateUnlocked());
        // Save the actual manual choice, NOT the temporary forced rage style.
        tag.putBoolean("AveSecondStyleActive", entityData.get(ALT_ACTIVE));
        tag.putInt("AveUikaRageTicks", uikaRageTicksRemaining);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("AveMember")) setRole(MemberRole.byId(tag.getString("AveMember")));
        if (tag.hasUUID("AveManager")) manager = tag.getUUID("AveManager");
        // Legacy standby/follow is migrated to standby. Never resume automatic follow.
        entityData.set(TASK_MODE, tag.contains("AveTask") ? Mth.clamp(tag.getInt("AveTask"), 0, 3) : 0);
        entityData.set(TEAM_MODE, tag.contains("AveTeamMode")
                ? TeamMode.fromId(tag.getInt("AveTeamMode")).ordinal() : TeamMode.NONE.ordinal());
        teamLeader = tag.hasUUID("AveTeamLeader") ? tag.getUUID("AveTeamLeader") : null;
        teamOptOut = tag.getBoolean("AveTeamOptOut");
        if (tag.contains("AveTeamStageX") && tag.contains("AveTeamStageY") && tag.contains("AveTeamStageZ")) {
            teamStageCenter = new Vec3(tag.getDouble("AveTeamStageX"), tag.getDouble("AveTeamStageY"), tag.getDouble("AveTeamStageZ"));
            if (teamMode() == TeamMode.REHEARSAL || teamMode() == TeamMode.PERFORMANCE)
                assignStagePosition(teamStageCenter);
        }
        if (tag.contains("AveDutyX") && tag.contains("AveDutyY") && tag.contains("AveDutyZ"))
            dutyCenter = new Vec3(tag.getDouble("AveDutyX"), tag.getDouble("AveDutyY"), tag.getDouble("AveDutyZ"));
        chorusCooldown = Math.max(20, tag.getInt("AveChorusCd"));
        entityData.set(TRAINING_RANK, Mth.clamp(tag.getInt("AveTrainingRank"), 0, 15));
        setInstrumentColor(tag.getInt("AveInstrumentColor"));
        MemberInstrument savedInstrument = MemberInstrument.find(tag.getString("AveInstrument"));
        applyPerformanceInstrument(savedInstrument == null ? MemberInstrument.defaultFor(role()) : savedInstrument,
                !tag.contains("AveInstrumentEnabled") || tag.getBoolean("AveInstrumentEnabled"));
        boolean eligible = hasSecondCombatStyle();
        entityData.set(ALT_UNLOCKED, eligible && tag.getBoolean("AveSecondStyleUnlocked"));
        entityData.set(ALT_ACTIVE, eligible && tag.getBoolean("AveSecondStyleUnlocked")
                && tag.getBoolean("AveSecondStyleActive"));
        uikaRageTicksRemaining = role() == MemberRole.DOLORIS
                ? Mth.clamp(tag.getInt("AveUikaRageTicks"), 0, UIKA_RAGE_DURATION_TICKS) : 0;
        entityData.set(UIKA_RAGE_ACTIVE, uikaRageTicksRemaining > 0);
        updateTrainedHealth();
        // Daily actions (including manual pose previews) never survive a reload.
        debugPoseUntil = 0L;
        seatTarget = null;
        flowerToWatch = null;
        socialFocus = null;
        sakikoVisitTarget = null;
        expectedUikaVisitor = null;
        setPersonality(0);
        nextPersonalityTick = level().getGameTime() + 100L + random.nextInt(160);
    }
}
