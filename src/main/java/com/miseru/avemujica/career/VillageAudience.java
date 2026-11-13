package com.miseru.avemujica.career;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A lightweight, genuine villager audience for paid village shows. Only loaded,
 * real villagers are invited; this does not spawn, teleport or reprogram villagers.
 * GoalSelector gives ordinary life/work/panic AI a chance to take priority.
 */
public final class VillageAudience {
    private VillageAudience() { }
    private static final int MAX_SPECTATORS = 16;
    private static final Map<UUID, Show> SHOWS = new HashMap<>();

    private record Seat(BlockPos feet) { }
    private static final class Show {
        final ServerLevel level;
        final BlockPos stage;
        final Map<UUID, Seat> invited = new HashMap<>();
        final Set<UUID> arrived = new HashSet<>();
        Show(ServerLevel level, BlockPos stage) { this.level = level; this.stage = stage.immutable(); }
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Villager villager) {
                villager.goalSelector.addGoal(4, new WatchConcertGoal(villager));
            }
        });
    }

    private static boolean available(Villager villager) {
        if (!villager.isAlive() || villager.isSleeping() || villager.isTrading()
                || villager.isPassenger() || villager.hurtTime > 0) return false;
        if (!(villager.level() instanceof ServerLevel level)) return false;
        // Don't lure villagers into danger: a hostile mob near them stops the act.
        return level.getEntitiesOfClass(Monster.class, villager.getBoundingBox().inflate(7D),
                mob -> mob.isAlive() && mob.isAggressive()).isEmpty();
    }

    public static void start(ServerLevel level, UUID owner, BlockPos stage) {
        stop(owner);
        Show show = new Show(level, stage);
        List<Villager> candidates = level.getEntitiesOfClass(Villager.class,
                new AABB(stage).inflate(29D, 8D, 29D), VillageAudience::available);
        candidates.removeIf(v -> concertFor(v) != null); // No villager attends two simultaneous shows.
        candidates.sort(Comparator.comparingDouble(v -> v.distanceToSqr(
                stage.getX() + 0.5D, stage.getY() + 1D, stage.getZ() + 8D)));
        // Front-of-stage seating: no standing on the platform (-4..+4) or stairs (+5).
        int[] seatX = {-5, -3, -1, 1, 3, 5};
        int[] seatZ = {8, 10, 12};
        List<BlockPos> seats = new ArrayList<>();
        for (int z : seatZ) for (int x : seatX) {
            int atX = stage.getX() + x, atZ = stage.getZ() + z;
            if (!level.hasChunkAt(new BlockPos(atX, stage.getY(), atZ))) continue;
            int feetY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, atX, atZ);
            BlockPos feet = new BlockPos(atX, feetY, atZ);
            if (Math.abs(feetY - stage.getY()) > 4 || !level.getBlockState(feet).isAir()
                    || !level.getBlockState(feet.above()).isAir()) continue;
            seats.add(feet);
        }
        int max = Math.min(MAX_SPECTATORS, Math.min(candidates.size(), seats.size()));
        for (int i = 0; i < max; i++) show.invited.put(candidates.get(i).getUUID(), new Seat(seats.get(i)));
        SHOWS.put(owner, show);
    }

    public static void stop(UUID owner) {
        SHOWS.remove(owner);
    }

    public static int arrived(ServerLevel level, UUID owner) {
        Show show = SHOWS.get(owner);
        return show == null || show.level != level ? 0 : show.arrived.size();
    }

    private static Show concertFor(Villager villager) {
        for (Show show : SHOWS.values()) {
            if (show.level == villager.level() && show.invited.containsKey(villager.getUUID())) return show;
        }
        return null;
    }

    public static void tick(ServerLevel level, UUID owner, BlockPos stage, long now) {
        Show show = SHOWS.get(owner);
        // World reload / previously saved ongoing tours create a fresh invitation.
        if (show == null || show.level != level || !show.stage.equals(stage)) {
            start(level, owner, stage);
            show = SHOWS.get(owner);
        }
        if (show == null) return;
        for (UUID id : show.invited.keySet()) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Villager villager) || !available(villager)) continue;
            Seat seat = show.invited.get(id);
            if (villager.distanceToSqr(seat.feet.getX() + .5D, seat.feet.getY(), seat.feet.getZ() + .5D) > 3.1D) continue;
            show.arrived.add(id);
            villager.getLookControl().setLookAt(stage.getX() + .5D, stage.getY() + 2.1D,
                    stage.getZ() + .5D, 35F, 30F);
            // Beat-based coordinated but staggered crowd reactions.
            int phase = (int)((now + (id.getLeastSignificantBits() & 31)) % 40L);
            if (phase == 0) {
                villager.getJumpControl().jump();
                level.sendParticles(ParticleTypes.NOTE, villager.getX(), villager.getEyeY() + .25D,
                        villager.getZ(), 2, .20D, .18D, .20D, .03D);
            } else if (phase == 20) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getEyeY() + .20D,
                        villager.getZ(), 3, .22D, .25D, .22D, 0D);
            }
            if ((now + (id.getLeastSignificantBits() & 127)) % 100 == 0) {
                level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL,
                        .42F, 1.05F + (villager.getId() % 5) * .08F);
            }
        }
    }

    private static final class WatchConcertGoal extends Goal {
        private final Villager villager;
        private Show show;
        private Seat seat;
        private int repath;
        WatchConcertGoal(Villager villager) {
            this.villager = villager;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        @Override public boolean canUse() {
            if (!available(villager)) return false;
            Show current = concertFor(villager);
            if (current == null) return false;
            show = current;
            seat = current.invited.get(villager.getUUID());
            return seat != null;
        }
        @Override public boolean canContinueToUse() {
            return available(villager) && show != null && SHOWS.containsValue(show)
                    && show.invited.containsKey(villager.getUUID());
        }
        @Override public void start() {
            repath = 0;
        }
        @Override public void tick() {
            if (seat == null || show == null) return;
            double x = seat.feet.getX() + .5D, y = seat.feet.getY(), z = seat.feet.getZ() + .5D;
            if (villager.distanceToSqr(x, y, z) > 2.5D) {
                if (--repath <= 0) {
                    repath = 25;
                    villager.getNavigation().moveTo(x, y, z, 0.75D);
                }
            } else {
                villager.getNavigation().stop();
                villager.getLookControl().setLookAt(show.stage.getX() + .5D,
                        show.stage.getY() + 2D, show.stage.getZ() + .5D, 35F, 30F);
            }
        }
        @Override public void stop() {
            villager.getNavigation().stop();
            show = null;
            seat = null;
        }
    }
}
