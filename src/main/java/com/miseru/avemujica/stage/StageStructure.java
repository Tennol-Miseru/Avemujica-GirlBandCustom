package com.miseru.avemujica.stage;

import com.miseru.avemujica.block.StagePart;
import com.miseru.avemujica.block.StagePartBlock;
import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Portable stage deployed from one item: eleven blocks wide, nine deep, plus
 * speakers and front steps. Full blocks give musicians a REAL navigable floor.
 * No solid blocks or fluids are overwritten: deployment is preflighted before
 * the item is spent. Small slopes are bridged by automatically generated legs.
 */
public final class StageStructure {
    private StageStructure() { }

    /** SOUTH is the historical geometry: stage front is toward positive Z. */
    public static int[] rotate(int x, int z, Direction front) {
        return switch (front) {
            case WEST -> new int[] {-z, x};
            case NORTH -> new int[] {-x, -z};
            case EAST -> new int[] {z, -x};
            default -> new int[] {x, z};
        };
    }

    private static void put(Map<BlockPos, StagePart> plan, BlockPos root, Direction front,
                            int x, int y, int z, StagePart type) {
        if (x == 0 && y == 0 && z == 0) return; // center is the controller block
        int[] point = rotate(x, z, front);
        // Beam geometry must follow the chosen rotation, not just move its block.
        if (front.getAxis() == Direction.Axis.X) {
            if (type == StagePart.BEAM_X) type = StagePart.BEAM_Z;
            else if (type == StagePart.BEAM_Z) type = StagePart.BEAM_X;
        }
        plan.put(root.offset(point[0], y, point[1]), type);
    }

    public static Map<BlockPos, StagePart> plan(BlockPos root) { return plan(root, Direction.SOUTH); }

    public static Map<BlockPos, StagePart> plan(BlockPos root, Direction front) {
        Map<BlockPos, StagePart> p = new LinkedHashMap<>();
        // Walkable elevated 11 × 9 stage floor. The center is the controller.
        for (int x = -5; x <= 5; x++) for (int z = -4; z <= 4; z++) {
            StagePart type = (x == -5 || x == 5 || z == -4 || z == 4)
                    ? StagePart.EDGE : StagePart.DECK;
            put(p, root, front, x, 0, z, type);
        }
        // Half-height front stairs, so villagers/players can walk onto the deck.
        for (int x = -2; x <= 2; x++) put(p, root, front, x, 0, 5, StagePart.STEP);
        // Speaker cabinets on each side, outside the performing area.
        for (int y = 0; y <= 2; y++) {
            put(p, root, front, -6, y, 2, StagePart.SPEAKER);
            put(p, root, front,  6, y, 2, StagePart.SPEAKER);
        }
        // Four narrow freestanding truss columns, above the actual floor.
        for (int x : new int[]{-5, 5}) for (int z : new int[]{-4, 4}) {
            for (int y = 1; y <= 4; y++) put(p, root, front, x, y, z, StagePart.POST);
        }
        // Truss cross-members 5 blocks above the ground and beyond head height.
        for (int x = -5; x <= 5; x++) {
            put(p, root, front, x, 5, -4, StagePart.BEAM_X);
            put(p, root, front, x, 5,  4, StagePart.BEAM_X);
        }
        for (int z = -3; z <= 3; z++) {
            put(p, root, front, -5, 5, z, StagePart.BEAM_Z);
            put(p, root, front,  5, 5, z, StagePart.BEAM_Z);
        }
        // Five separate themed spotlights hanging from the rear crossbeam.
        int[] xLights = {-4, -2, 0, 2, 4};
        // Left-to-right: Mutsumi, Sakiko, Uika, Umiri, Nyamu.
        StagePart[] lamps = {StagePart.LAMP_GREEN, StagePart.LAMP_SKY_BLUE,
                StagePart.LAMP_GOLD, StagePart.LAMP_GRAY_BLUE, StagePart.LAMP_PURPLE};
        for (int i = 0; i < xLights.length; i++) {
            put(p, root, front, xLights[i], 4, -4, lamps[i]);
        }
        // Small wedge monitors along the front edge, outside the members' marks.
        for (int x : new int[]{-3, 0, 3}) put(p, root, front, x, 1, 4, StagePart.MONITOR);
        return p;
    }

    /** Localized terrain search rather than requiring pixel-perfect placement. */
    public static final int MAX_RAISE = 6;
    public static final int MAX_SUPPORT_DEPTH = 10;
    public static final int MAX_HORIZONTAL_SHIFT = 2;
    private static final int MIN_DECK_SUPPORTS = 4;

    // Prefer the center, then the closest translation; do not rotate the
    // stage or unexpectedly move it far away from the player's chosen spot.
    private static final int[][] ROOT_SHIFTS = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}
    };

    private static final int[][] FOOTINGS = {
            {-5, -4}, {0, -4}, {5, -4},
            {-5,  0}, {0,  0}, {5,  0},
            {-5,  4}, {0,  4}, {5,  4},
            {-6,  2}, {6,  2},
            {-2,  5}, {0, 5}, {2, 5}
    };

    /** Preflighted footprint: optional overhead decorations may be omitted. */
    public record Placement(BlockPos root, Direction front, Map<BlockPos, StagePart> supports,
                            Map<BlockPos, StagePart> parts) { }

    private static boolean freeForStage(Level level, BlockPos pos, Player player) {
        return pos.getY() >= level.getMinBuildHeight()
                && pos.getY() < level.getMaxBuildHeight()
                && level.hasChunkAt(pos)
                && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos).canBeReplaced()
                && (player == null || level.mayInteract(player, pos));
    }

    /**
     * A decorative speaker or truss must not veto the usable floor. Keep the
     * original full stage on open terrain; omit the whole truss roof when one
     * piece is blocked (no unsupported floating lights/partial roof). Speakers
     * are separately optional; monitors and stairs are evaluated individually.
     */
    private static Map<BlockPos, StagePart> usableParts(Level level, BlockPos root, Player player, Direction front) {
        Map<BlockPos, StagePart> full = plan(root, front);
        Map<BlockPos, StagePart> used = new LinkedHashMap<>();
        // The center controller and 11x9 walkable deck are always mandatory.
        for (Map.Entry<BlockPos, StagePart> entry : full.entrySet()) {
            StagePart type = entry.getValue();
            if ((type == StagePart.DECK || type == StagePart.EDGE)
                    && !freeForStage(level, entry.getKey(), player)) return null;
            if (type == StagePart.DECK || type == StagePart.EDGE) used.put(entry.getKey(), type);
        }
        boolean roofClear = true;
        for (Map.Entry<BlockPos, StagePart> entry : full.entrySet()) {
            StagePart type = entry.getValue();
            boolean roof = type == StagePart.POST || type == StagePart.BEAM_X || type == StagePart.BEAM_Z
                    || type == StagePart.LAMP_GREEN || type == StagePart.LAMP_SKY_BLUE
                    || type == StagePart.LAMP_GOLD || type == StagePart.LAMP_GRAY_BLUE
                    || type == StagePart.LAMP_PURPLE;
            if (roof && !freeForStage(level, entry.getKey(), player)) roofClear = false;
        }
        if (roofClear) {
            for (Map.Entry<BlockPos, StagePart> entry : full.entrySet()) {
                StagePart type = entry.getValue();
                if (type == StagePart.POST || type == StagePart.BEAM_X || type == StagePart.BEAM_Z
                        || type == StagePart.LAMP_GREEN || type == StagePart.LAMP_SKY_BLUE
                        || type == StagePart.LAMP_GOLD || type == StagePart.LAMP_GRAY_BLUE
                        || type == StagePart.LAMP_PURPLE) used.put(entry.getKey(), type);
            }
        }
        // Two independently skippable stacks, not six mandatory obstacles.
        for (int x : new int[] {-6, 6}) {
            int[] offset = rotate(x, 2, front);
            boolean clear = true;
            for (int y = 0; y <= 2; y++)
                if (!freeForStage(level, root.offset(offset[0], y, offset[1]), player)) clear = false;
            if (clear) for (int y = 0; y <= 2; y++)
                used.put(root.offset(offset[0], y, offset[1]), StagePart.SPEAKER);
        }
        for (Map.Entry<BlockPos, StagePart> entry : full.entrySet()) {
            StagePart type = entry.getValue();
            if ((type == StagePart.MONITOR || type == StagePart.STEP)
                    && freeForStage(level, entry.getKey(), player))
                used.put(entry.getKey(), type);
        }
        return used;
    }

    /**
     * A small number of distributed secure deck footings is enough for a
     * suspended floor. One deep pit, water patch or obstruction under an
     * OPTIONAL footing no longer rejects the entire construction. Never dig
     * through buildings, liquid, or protected blocks to make a support.
     */
    private static Map<BlockPos, StagePart> supports(Level level, BlockPos root, Player player,
                                                     Map<BlockPos, StagePart> parts, Direction front) {
        Map<BlockPos, StagePart> result = new LinkedHashMap<>();
        boolean[] sides = new boolean[4]; // left, right, rear, front
        int groundedDeckSupports = 0;
        for (int[] oldFooting : FOOTINGS) {
            int[] footing = rotate(oldFooting[0], oldFooting[1], front);
            // Never build a freestanding leg for a skipped optional speaker/step.
            if ((Math.abs(footing[0]) > 5 || Math.abs(footing[1]) > 4)
                    && !parts.containsKey(root.offset(footing[0], 0, footing[1]))) continue;
            BlockPos cursor = root.offset(footing[0], -1, footing[1]);
            Map<BlockPos, StagePart> column = new LinkedHashMap<>();
            boolean grounded = false;
            for (int depth = 0; depth <= MAX_SUPPORT_DEPTH; depth++) {
                if (cursor.getY() < level.getMinBuildHeight() || !level.hasChunkAt(cursor)) break;
                BlockState existing = level.getBlockState(cursor);
                if (!level.getFluidState(cursor).isEmpty()) break;
                if (existing.isFaceSturdy(level, cursor, Direction.UP)) {
                    grounded = true;
                    break;
                }
                if (depth == MAX_SUPPORT_DEPTH || !freeForStage(level, cursor, player)) break;
                column.put(cursor.immutable(), StagePart.SUPPORT);
                cursor = cursor.below();
            }
            if (!grounded) continue;
            result.putAll(column);
            // Count only supports under the actual platform, not speakers/steps.
            if (Math.abs(oldFooting[0]) <= 5 && Math.abs(oldFooting[1]) <= 4) {
                groundedDeckSupports++;
                if (oldFooting[0] < 0) sides[0] = true;
                if (oldFooting[0] > 0) sides[1] = true;
                if (oldFooting[1] < 0) sides[2] = true;
                if (oldFooting[1] > 0) sides[3] = true;
            }
        }
        if (groundedDeckSupports < MIN_DECK_SUPPORTS) return null;
        for (boolean hasSupport : sides) if (!hasSupport) return null;
        return result;
    }

    /** Select the nearest safe origin with a clean 11x9 floor. */
    public static Placement findPlacement(Level level, BlockPos clickedRoot, Player player) {
        return findPlacement(level, clickedRoot, player, Direction.SOUTH);
    }

    public static Placement findPlacement(Level level, BlockPos clickedRoot, Player player, Direction front) {
        for (int raise = 0; raise <= MAX_RAISE; raise++) {
            for (int[] shift : ROOT_SHIFTS) {
                BlockPos root = clickedRoot.offset(shift[0], raise, shift[1]);
                if (root.getY() < level.getMinBuildHeight()
                        || root.getY() + 6 >= level.getMaxBuildHeight()) continue;
                if (!freeForStage(level, root, player)) continue;
                Map<BlockPos, StagePart> parts = usableParts(level, root, player, front);
                if (parts == null) continue;
                Map<BlockPos, StagePart> pillars = supports(level, root, player, parts, front);
                if (pillars != null) return new Placement(root, front, Map.copyOf(pillars), Map.copyOf(parts));
            }
        }
        return null;
    }

    /** Locate the controller from any existing piece of one of the four deck corners.
     * Recognizes deck edge, truss column, upper beam, and generated supports on the
     * same X/Z column; no other stage blocks can trigger packing. The controller's
     * persisted facing disambiguates rotated stages and works in old saves.
     */
    public static BlockPos controllerAtCorner(Level level, BlockPos clicked) {
        if (!level.hasChunkAt(clicked)) return null;
        BlockState clickedState = level.getBlockState(clicked);
        if (!clickedState.is(ModRegistry.STAGE_PART)) return null;
        StagePart material = clickedState.getValue(StagePartBlock.PART);
        for (Direction front : new Direction[]{Direction.SOUTH, Direction.WEST,
                Direction.NORTH, Direction.EAST}) {
            for (int x : new int[]{-5, 5}) for (int z : new int[]{-4, 4}) {
                int[] offset = rotate(x, z, front);
                for (int dy = -MAX_SUPPORT_DEPTH; dy <= 5; dy++) {
                    if (dy < 0 && material != StagePart.SUPPORT) continue;
                    if (dy == 0 && material != StagePart.EDGE) continue;
                    if (dy > 0 && dy < 5 && material != StagePart.POST) continue;
                    if (dy == 5 && material != (front.getAxis() == Direction.Axis.X
                            ? StagePart.BEAM_Z : StagePart.BEAM_X)) continue;
                    BlockPos center = clicked.offset(-offset[0], -dy, -offset[1]);
                    if (!level.hasChunkAt(center)) continue;
                    BlockState controller = level.getBlockState(center);
                    if (controller.is(ModRegistry.TOUR_STAGE)
                            && controller.getValue(com.miseru.avemujica.block.TourStageBlock.FACING) == front)
                        return center;
                }
            }
        }
        return null;
    }

    /** Compatibility probe: true if a safe elevation exists at this spot. */
    public static boolean canAssemble(Level level, BlockPos root, Player player) {
        return findPlacement(level, root, player) != null;
    }

    /** All generated pieces are built only after successful controller placement. */
    public static void assemble(Level level, Placement placement) {
        for (Map.Entry<BlockPos, StagePart> support : placement.supports().entrySet()) {
            BlockState state = ModRegistry.STAGE_PART.defaultBlockState()
                    .setValue(StagePartBlock.PART, support.getValue());
            level.setBlock(support.getKey(), state, 3);
        }
        for (Map.Entry<BlockPos, StagePart> part : placement.parts().entrySet()) {
            BlockState state = ModRegistry.STAGE_PART.defaultBlockState()
                    .setValue(StagePartBlock.PART, part.getValue());
            level.setBlock(part.getKey(), state, 3);
        }
    }

    /** Compatibility method for callers with an explicit origin and legs. */
    public static void assemble(Level level, BlockPos root, Map<BlockPos, StagePart> supports) {
        Map<BlockPos, StagePart> parts = usableParts(level, root, null, Direction.SOUTH);
        if (parts != null) assemble(level, new Placement(root, Direction.SOUTH, supports, parts));
    }

    /** Compatibility with the old origin-only call. */
    public static void assemble(Level level, BlockPos root) {
        Map<BlockPos, StagePart> parts = usableParts(level, root, null, Direction.SOUTH);
        if (parts == null) return;
        Map<BlockPos, StagePart> pillars = supports(level, root, null, parts, Direction.SOUTH);
        if (pillars != null) assemble(level, new Placement(root, Direction.SOUTH, pillars, parts));
    }

    /** Stage pieces drop nothing; breaking the controller clears stage and legs. */
    public static void disassemble(Level level, BlockPos root) { disassemble(level, root, Direction.SOUTH); }

    public static void disassemble(Level level, BlockPos root, Direction front) {
        for (Map.Entry<BlockPos, StagePart> part : plan(root, front).entrySet()) {
            BlockState existing = level.getBlockState(part.getKey());
            if (existing.is(ModRegistry.STAGE_PART)
                    && existing.getValue(StagePartBlock.PART) == part.getValue()) {
                level.removeBlock(part.getKey(), false);
            }
        }
        // Do not re-scan terrain to reconstruct a new leg plan: the ground may
        // have changed since deployment. Instead remove only our SUPPORT blocks
        // at the known footing columns, stopping at the maximum support depth.
        for (int[] oldFooting : FOOTINGS) {
            int[] footing = rotate(oldFooting[0], oldFooting[1], front);
            for (int depth = 1; depth <= MAX_SUPPORT_DEPTH; depth++) {
                BlockPos pos = root.offset(footing[0], -depth, footing[1]);
                if (pos.getY() < level.getMinBuildHeight() || !level.hasChunkAt(pos)) break;
                BlockState existing = level.getBlockState(pos);
                if (existing.is(ModRegistry.STAGE_PART)
                        && existing.getValue(StagePartBlock.PART) == StagePart.SUPPORT) {
                    level.removeBlock(pos, false);
                }
            }
        }
    }
}
