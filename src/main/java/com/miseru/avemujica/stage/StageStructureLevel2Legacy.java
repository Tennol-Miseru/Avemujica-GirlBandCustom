package com.miseru.avemujica.stage;

import com.miseru.avemujica.block.StagePart;
import com.miseru.avemujica.block.StagePartBlock;
import com.miseru.avemujica.block.TourStageBlock;
import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Crimson Rose II: an actual 25x14 walkable stage with a 17-block illuminated
 * rose and two raised instrument galleries. Uses the existing StagePart block
 * so all placed decoration is generated/removed atomically with its controller.
 * The original 11x9 tour stage is intentionally unchanged.
 */
final class StageStructureLevel2Legacy {
    private StageStructureLevel2Legacy() { }

    private static final int HALF_WIDTH = 12;
    private static final int BACK = -7;
    private static final int FRONT = 6;
    private static final int ROOF_Y = 19;
    private static final int MAX_SUPPORT_DEPTH = 10;

    private static final int[][] FOOTINGS = {
            {-12,-7}, {0,-7}, {12,-7}, {-12,0}, {0,0}, {12,0},
            {-12,6}, {0,6}, {12,6}, {-9,-3}, {9,-3}
    };

    // From top to bottom: a fifteen-row 17x15 pixel rose with a lit gold heart.
    // '.' represents the deep crimson backing, not empty world space.
    private static final String[] ROSE = {
            "......rrrrr......",
            "....rrRRRRRrr....",
            "...rrRrrrrrRrr...",
            "..rrR..rrr..Rrr..",
            ".rr...rrRrr...rr.",
            ".rr..rrGGGrr..rr.",
            "rr..rrGGGGGrr..rr",
            "rr..rrGGWGGrr..rr",
            "rr..rrGWWWGrr..rr",
            "rr..rrGGWGGrr..rr",
            ".rr..rrGGGrr..rr.",
            ".rr...rrRrr...rr.",
            "..rrR..rrr..Rrr..",
            "...rrRrrrrrRrr...",
            "....rrRRRRRrr...."
    };

    private static void put(Map<BlockPos,StagePart> plan, BlockPos root, Direction front,
                            int x, int y, int z, StagePart part) {
        if (x == 0 && y == 0 && z == 0) return; // real controller block
        int[] relative = StageStructure.rotate(x, z, front);
        plan.put(root.offset(relative[0], y, relative[1]), part);
    }

    public static Map<BlockPos, StagePart> plan(BlockPos root, Direction front) {
        Map<BlockPos, StagePart> p = new LinkedHashMap<>();
        // Real walkable floor: two side galleries are elevated by two blocks.
        for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
            for (int z = BACK; z <= FRONT; z++) {
                boolean rim = x == -HALF_WIDTH || x == HALF_WIDTH || z == BACK || z == FRONT;
                StagePart tile = rim ? StagePart.LV2_BORDER : StagePart.LV2_DECK;
                if (Math.abs(x) <= 5 && z >= 1 && z <= 5) tile = StagePart.LV2_CARPET;
                if (z == FRONT && (x % 3 == 0)) tile = StagePart.LV2_GOLD;
                put(p, root, front, x, 0, z, tile);
            }
        }
        // Wide accessible front entrance, half-slabs joining the grass and deck.
        for (int x = -3; x <= 3; x++) put(p, root, front, x, 0, FRONT + 1, StagePart.STEP);

        // Crimson brick backdrop. The emblem is an extra front-facing layer:
        // it has real depth and does not have any invisible missing pixels.
        for (int x = -12; x <= 12; x++) {
            for (int y = 1; y <= 18; y++) {
                put(p, root, front, x, y, BACK, (x == -12 || x == 12 || y == 18)
                        ? StagePart.LV2_BLACK : StagePart.LV2_RED);
            }
        }
        // Stacked illuminated glass ribbons left and right of the rose.
        StagePart[] windows = {StagePart.LV2_WINDOW_BLUE, StagePart.LV2_WINDOW_PINK,
                StagePart.LV2_WINDOW_BLUE, StagePart.LV2_WINDOW_VIOLET,
                StagePart.LV2_WINDOW_GOLD};
        for (int side : new int[]{-1,1}) {
            for (int strip = 0; strip < windows.length; strip++) {
                for (int x = 10; x <= 11; x++) {
                    for (int yy = 0; yy < 2; yy++)
                        put(p, root, front, side*x, 3 + strip*3 + yy, BACK+1, windows[strip]);
                }
            }
        }
        // The block mosaic is genuinely lit in-game, without depending on a shader.
        for (int row = 0; row < ROSE.length; row++) {
            String line = ROSE[row];
            for (int ix = 0; ix < line.length(); ix++) {
                StagePart material = switch (line.charAt(ix)) {
                    case 'r' -> StagePart.LV2_ROSE;
                    case 'R' -> StagePart.LV2_ROSE_DARK;
                    case 'G' -> StagePart.LV2_ROSE_GOLD;
                    case 'W' -> StagePart.LV2_ROSE_WHITE;
                    default -> null;
                };
                if (material != null) put(p, root, front, ix - 8, 17 - row, BACK+1, material);
            }
        }
        // Two raised galleries for Sakiko's keyboard and Nyamu's drums.
        // Leave a safe strip of deck before the wall; musicians stand at z=-2.
        for (int side : new int[]{-1,1}) {
            for (int x = 8; x <= 11; x++) {
                for (int z = -4; z <= 0; z++) {
                    put(p, root, front, side*x, 1, z, StagePart.LV2_BLACK);
                    put(p, root, front, side*x, 2, z, StagePart.LV2_BORDER);
                }
            }
            // Two step-up risers accessed from the front side of each gallery.
            for (int x = 9; x <= 10; x++) {
                put(p, root, front, side*x, 1, 2, StagePart.LV2_BORDER);
                put(p, root, front, side*x, 1, 1, StagePart.LV2_BLACK);
                put(p, root, front, side*x, 2, 1, StagePart.LV2_BORDER);
            }
            // Thin rail posts at back/corners, not in the performance marks.
            for (int x = 8; x <= 11; x++) {
                if (x % 3 == 2) {
                    put(p, root, front, side*x, 3, -4, StagePart.LV2_RAIL);
                    put(p, root, front, side*x, 3, 0, StagePart.LV2_RAIL);
                }
            }
            // Visual instrument models borrowed from the mod's ORIGINAL assets.
            put(p, root, front, side*9, 3, -4, side == -1
                    ? StagePart.LV2_KEYBOARD : StagePart.LV2_DRUMS);
        }
        // Black/red trusses and lighting rig, structural columns on outer corners.
        for (int side : new int[]{-1,1}) {
            for (int z : new int[]{BACK, FRONT}) {
                for (int y = 1; y <= ROOF_Y; y++)
                    put(p, root, front, side*12, y, z,
                            y % 4 == 0 ? StagePart.LV2_GOLD : StagePart.LV2_TRUSS);
            }
            for (int z = BACK; z <= FRONT; z++) {
                if ((z - BACK) % 3 == 0)
                    put(p, root, front, side*12, ROOF_Y, z, StagePart.LV2_GOLD);
                else put(p, root, front, side*12, ROOF_Y, z, StagePart.LV2_TRUSS);
            }
        }
        // Roofing is a perimeter plus cross-beams, not an opaque full ceiling:
        // the LED rose and raised galleries remain visible in daylight.
        for (int x = -12; x <= 12; x++) {
            for (int z : new int[]{BACK, -3, 2, FRONT}) {
                put(p, root, front, x, ROOF_Y, z,
                        x % 4 == 0 ? StagePart.LV2_GOLD : StagePart.LV2_TRUSS);
            }
        }
        for (int x : new int[]{-10,-5,0,5,10}) {
            put(p, root, front, x, ROOF_Y - 1, FRONT, StagePart.LV2_SPOTLIGHT);
            put(p, root, front, x, ROOF_Y - 1, -3, StagePart.LV2_SPOTLIGHT);
        }
        // Distinct stage-edge uplights, no giant spotlight visual obstruction.
        for (int x : new int[]{-11,-6,6,11})
            put(p, root, front, x, 1, 5, StagePart.LV2_SPOTLIGHT);
        return p;
    }

    private static boolean clear(Level level, BlockPos at, Player player) {
        return at.getY() >= level.getMinBuildHeight()
                && at.getY() < level.getMaxBuildHeight()
                && level.hasChunkAt(at)
                && level.getFluidState(at).isEmpty()
                && level.getBlockState(at).canBeReplaced()
                && (player == null || level.mayInteract(player, at));
    }

    private static Map<BlockPos, StagePart> supportPlan(Level level, BlockPos root, Player player,
                                                        Direction front) {
        Map<BlockPos,StagePart> result = new LinkedHashMap<>();
        int grounded = 0;
        boolean[] sides = new boolean[4];
        for (int[] base : FOOTINGS) {
            int[] offset = StageStructure.rotate(base[0], base[1], front);
            BlockPos cursor = root.offset(offset[0], -1, offset[1]);
            Map<BlockPos,StagePart> pillar = new LinkedHashMap<>();
            boolean anchored = false;
            for (int depth = 0; depth <= MAX_SUPPORT_DEPTH; depth++) {
                if (cursor.getY() < level.getMinBuildHeight() || !level.hasChunkAt(cursor)) break;
                BlockState existing = level.getBlockState(cursor);
                if (!level.getFluidState(cursor).isEmpty()) break;
                if (existing.isFaceSturdy(level, cursor, Direction.UP)) { anchored = true; break; }
                if (depth == MAX_SUPPORT_DEPTH || !clear(level, cursor, player)) break;
                pillar.put(cursor.immutable(), StagePart.SUPPORT);
                cursor = cursor.below();
            }
            if (!anchored) continue;
            grounded++;
            result.putAll(pillar);
            if (base[0] < 0) sides[0] = true;
            if (base[0] > 0) sides[1] = true;
            if (base[1] < 0) sides[2] = true;
            if (base[1] > 0) sides[3] = true;
        }
        if (grounded < 5) return null;
        for (boolean anchored : sides) if (!anchored) return null;
        return result;
    }

    /** No blocks are ever overwritten. Placement is a fully preflighted operation. */
    public static StageStructure.Placement findPlacement(Level level, BlockPos clicked,
                                                         Player player, Direction front) {
        for (int raise = 0; raise <= StageStructure.MAX_RAISE; raise++) {
            // Test the requested center first, then nearby translations.
            for (int[] shift : new int[][]{{0,0},{1,0},{-1,0},{0,1},{0,-1},
                    {1,1},{1,-1},{-1,1},{-1,-1}}) {
                BlockPos root = clicked.offset(shift[0], raise, shift[1]);
                if (root.getY() + ROOF_Y >= level.getMaxBuildHeight() || !clear(level, root, player)) continue;
                Map<BlockPos,StagePart> parts = plan(root, front);
                boolean safe = true;
                for (BlockPos at : parts.keySet()) {
                    if (!clear(level, at, player)) { safe = false; break; }
                }
                if (!safe) continue;
                Map<BlockPos,StagePart> pillars = supportPlan(level, root, player, front);
                if (pillars != null) return new StageStructure.Placement(root, front, pillars, parts);
            }
        }
        return null;
    }

    public static void assemble(Level level, StageStructure.Placement placement) {
        StageStructure.assemble(level, placement);
    }

    /** Packing a stage removes only the exact generated blocks and support legs. */
    public static void disassemble(Level level, BlockPos root, Direction front) {
        for (Map.Entry<BlockPos,StagePart> part : plan(root, front).entrySet()) {
            if (!level.hasChunkAt(part.getKey())) continue;
            BlockState existing = level.getBlockState(part.getKey());
            if (existing.is(ModRegistry.STAGE_PART)
                    && existing.getValue(StagePartBlock.PART) == part.getValue())
                level.removeBlock(part.getKey(), false);
        }
        for (int[] old : FOOTINGS) {
            int[] at = StageStructure.rotate(old[0],old[1],front);
            for (int depth = 1; depth <= MAX_SUPPORT_DEPTH; depth++) {
                BlockPos pos = root.offset(at[0], -depth, at[1]);
                if (!level.hasChunkAt(pos) || pos.getY() < level.getMinBuildHeight()) break;
                BlockState existing = level.getBlockState(pos);
                if (existing.is(ModRegistry.STAGE_PART)
                        && existing.getValue(StagePartBlock.PART) == StagePart.SUPPORT)
                    level.removeBlock(pos, false);
            }
        }
    }

    /** Four corner columns and their supports have the existing sneak-click pickup UX. */
    public static BlockPos controllerAtCorner(Level level, BlockPos clicked) {
        if (!level.hasChunkAt(clicked)) return null;
        BlockState state = level.getBlockState(clicked);
        if (!state.is(ModRegistry.STAGE_PART)) return null;
        StagePart material = state.getValue(StagePartBlock.PART);
        for (Direction front : new Direction[]{Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST}) {
            for (int x : new int[]{-12,12}) for (int z : new int[]{BACK,FRONT}) {
                int[] at = StageStructure.rotate(x,z,front);
                for (int y = -MAX_SUPPORT_DEPTH; y <= ROOF_Y; y++) {
                    if (y < 0 && material != StagePart.SUPPORT) continue;
                    if (y == 0 && material != StagePart.LV2_BORDER && material != StagePart.LV2_GOLD) continue;
                    if (y > 0 && material != StagePart.LV2_TRUSS && material != StagePart.LV2_GOLD) continue;
                    BlockPos root = clicked.offset(-at[0], -y, -at[1]);
                    if (level.hasChunkAt(root)) {
                        BlockState controller = level.getBlockState(root);
                        if (controller.is(ModRegistry.TOUR_STAGE_LEVEL2)
                                && controller.getValue(TourStageBlock.FACING) == front)
                            return root;
                    }
                }
            }
        }
        return null;
    }
}
