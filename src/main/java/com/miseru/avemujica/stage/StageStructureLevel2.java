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
 * Crimson Rose II (refined): 33-wide raised concert venue.
 * * Deepslate-brick structural frame and roof; nether-brick RECESSED back wall.
 * * Five inset coloured stained-glass bands per side, flush with the brick wall.
 * * High resolution emblem drawn as a transparent decal on nether bricks by the controller renderer.
 * * Two walkable raised galleries, stage steps and musicians' floor markings.
 *
 * Uses mod-owned StagePart blocks so the stage is removable without modifying
 * any surrounding player blocks. One controller per complete construction.
 * Do not delete StageStructureLevel2Legacy: old placed v1.1.76 stages use it.
 */
public final class StageStructureLevel2 {
    private StageStructureLevel2() { }

    public static final int HALF_WIDTH = 16;
    public static final int BACK = -8;
    public static final int FRONT = 8;
    public static final int ROOF_Y = 22;
    private static final int MAX_SUPPORT_DEPTH = 10;

    private static final int[][] FOOTINGS = {
            {-16,-8}, {0,-8}, {16,-8}, {-16,0}, {0,0}, {16,0},
            {-16,8}, {0,8}, {16,8}, {-12,-3}, {12,-3}, {-6,3}, {6,3}
    };

    // v1.1.77 rose data is kept ONLY to clean up stages placed before v1.1.78.
    // Newly built stage centers use plain nether bricks with a transparent
    // high-resolution emblem rendered flush against this backing wall.
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

    private static void put(Map<BlockPos, StagePart> plan, BlockPos root, Direction front,
                            int x, int y, int z, StagePart type) {
        if (x == 0 && y == 0 && z == 0) return; // central controller
        int[] rel = StageStructure.rotate(x,z,front);
        // Rail geometry must rotate with the stage, including the WEST/EAST
        // builds. Otherwise the visible beams stay on the wrong axis.
        if (front.getAxis() == Direction.Axis.X) {
            if (type == StagePart.LV2_RAIL_X) type = StagePart.LV2_RAIL_Z;
            else if (type == StagePart.LV2_RAIL_Z) type = StagePart.LV2_RAIL_X;
        }
        type = rotateSpecialPart(type, front);
        plan.put(root.offset(rel[0], y, rel[1]), type);
    }

    /** Rotate fence arms, not merely the post's location. No unwanted cross-shaped ends. */
    private static StagePart rotateSpecialPart(StagePart type, Direction front) {
        if (type == StagePart.LV2_STAIR_SOUTH) {
            return switch (front) {
                case WEST -> StagePart.LV2_STAIR_WEST;
                case NORTH -> StagePart.LV2_STAIR_NORTH;
                case EAST -> StagePart.LV2_STAIR_EAST;
                default -> StagePart.LV2_STAIR_SOUTH;
            };
        }
        int arms = switch (type) {
            case LV2_RAIL_CORNER_NE -> 1 | 2;
            case LV2_RAIL_CORNER_NW -> 1 | 8;
            case LV2_RAIL_CORNER_SE -> 4 | 2;
            case LV2_RAIL_CORNER_SW -> 4 | 8;
            case LV2_RAIL_END_N -> 1;
            case LV2_RAIL_END_E -> 2;
            case LV2_RAIL_END_S -> 4;
            case LV2_RAIL_END_W -> 8;
            default -> 0;
        };
        if (arms == 0) return type;
        int rotated = 0;
        for (int bit : new int[]{1, 2, 4, 8}) {
            if ((arms & bit) == 0) continue;
            int x = bit == 2 ? 1 : bit == 8 ? -1 : 0;
            int z = bit == 4 ? 1 : bit == 1 ? -1 : 0;
            int[] vector = StageStructure.rotate(x, z, front);
            rotated |= vector[0] > 0 ? 2 : vector[0] < 0 ? 8
                    : vector[1] > 0 ? 4 : 1;
        }
        return switch (rotated) {
            case 3 -> StagePart.LV2_RAIL_CORNER_NE;
            case 9 -> StagePart.LV2_RAIL_CORNER_NW;
            case 6 -> StagePart.LV2_RAIL_CORNER_SE;
            case 12 -> StagePart.LV2_RAIL_CORNER_SW;
            case 1 -> StagePart.LV2_RAIL_END_N;
            case 2 -> StagePart.LV2_RAIL_END_E;
            case 4 -> StagePart.LV2_RAIL_END_S;
            case 8 -> StagePart.LV2_RAIL_END_W;
            default -> throw new IllegalStateException("Invalid fence connection " + rotated);
        };
    }

    /** Same-looking 15-light flooring avoids visible spotlights and extra colliders. */
    private static boolean illuminatedFloorTile(int x, int z) {
        return (Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0)
                || (Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2);
    }

    /** Exact layout, independent of terrain. Safe to call in offline fixture tests. */
    public static Map<BlockPos, StagePart> plan(BlockPos root, Direction front) {
        Map<BlockPos, StagePart> p = new LinkedHashMap<>();

        // A real 33 x 17 floor, not a floating render. The outer edge and
        // fascia are DEEPSLATE BRICKS, the crimson center remains unobstructed.
        for (int x=-HALF_WIDTH; x<=HALF_WIDTH; x++) {
            for (int z=BACK; z<=FRONT; z++) {
                boolean edge = Math.abs(x)==HALF_WIDTH || z==BACK || z==FRONT;
                StagePart t = edge ? StagePart.LV2_BORDER : StagePart.LV2_DECK;
                if (Math.abs(x)<=7 && z>=1 && z<=7) t=StagePart.LV2_CARPET;
                // Keep the apron and near-front accents in uninterrupted
                // deepslate bricks. No bright-yellow hazard-stripe blocks.
                if (illuminatedFloorTile(x,z)) {
                    if (t == StagePart.LV2_DECK) t = StagePart.LV2_DECK_LIT;
                    else if (t == StagePart.LV2_CARPET) t = StagePart.LV2_CARPET_LIT;
                }
                put(p,root,front,x,0,z,t);
            }
        }
        // Main entry up from the grass; secondary approaches to side risers.
        for (int x=-4;x<=4;x++) put(p,root,front,x,0,FRONT+1,StagePart.STEP);

        // Entire wall is constructed from dark NETHER BRICKS; deepslate bricks
        // border the wall. Windows replace wall blocks at the EXACT same z.
        for (int x=-HALF_WIDTH;x<=HALF_WIDTH;x++) {
            for (int y=1;y<=20;y++) {
                StagePart material=(Math.abs(x)==HALF_WIDTH || y==20)
                        ? StagePart.LV2_BLACK : StagePart.LV2_RED;
                put(p,root,front,x,y,BACK,material);
            }
        }

        // Deepslate mullions embedded in the nether-brick wall; each window is
        // 5x2 with visible frames and a one-brick gap separating colour bands.
        StagePart[] glass = {
                StagePart.LV2_WINDOW_BLUE, StagePart.LV2_WINDOW_PINK,
                StagePart.LV2_WINDOW_BLUE, StagePart.LV2_WINDOW_VIOLET,
                StagePart.LV2_WINDOW_GOLD
        };
        for (int side:new int[]{-1,1}) {
            for (int y=2;y<=19;y++)
                put(p,root,front,side*10,y,BACK,StagePart.LV2_BLACK);
            for (int band=0;band<glass.length;band++) {
                int bottom=4+band*3;
                for (int x=11;x<=15;x++) {
                    for (int h=0;h<2;h++)
                        put(p,root,front,side*x,bottom+h,BACK,glass[band]);
                }
            }
            // Thin dark framing between the coloured windows, still part of
            // the brick face rather than separate posts floating in front.
            for (int divider:new int[]{3,6,9,12,15,18})
                for (int x=11;x<=15;x++)
                    put(p,root,front,side*x,divider,BACK,StagePart.LV2_BLACK);
        }

        // Central wall intentionally stays nether bricks.  The level-2 stage
        // controller renders the full, transparent uploaded Ave Mujica emblem
        // above this wall without replacing any brick blocks.  The surrounding
        // stained-glass bands remain embedded in the same wall plane.

        // Symmetrical raised galleries: a SINGLE central stairway per gallery.
        // Two classic deepslate-brick stair blocks (three-wide) climb the
        // two-block elevation. No detached steps, decorative yellow cubes,
        // or railing posts across the entrance.
        for (int side : new int[]{-1,1}) {
            for (int x=9; x<=15; x++) for (int z=-5; z<=0; z++) {
                put(p,root,front,side*x,1,z,StagePart.LV2_BLACK);
                boolean rim = x==9 || x==15 || z==-5 || z==0;
                StagePart deck = rim ? StagePart.LV2_BORDER : StagePart.LV2_DECK;
                if (deck == StagePart.LV2_DECK && (x==11 || x==13) && (z==-3 || z==-1))
                    deck = StagePart.LV2_DECK_LIT;
                put(p,root,front,side*x,2,z,deck);
            }
            // Each stair block is a real two-half stair shape: lower tread at
            // the audience side, upper tread at the rear. All stairs in the
            // width use identical geometry, with no side protrusions.
            for (int x=11; x<=13; x++) {
                put(p,root,front,side*x,1,2,StagePart.LV2_STAIR_SOUTH);
                put(p,root,front,side*x,2,1,StagePart.LV2_STAIR_SOUTH);
            }
            // Nether-brick-fence-style rear/side rail, connected only toward
            // its actual neighbours. The FRONT is deliberately open.
            for (int x=9; x<=15; x++) {
                StagePart rail;
                if (x==9) rail = side>0 ? StagePart.LV2_RAIL_CORNER_SE
                                           : StagePart.LV2_RAIL_CORNER_SW;
                else if (x==15) rail = side>0 ? StagePart.LV2_RAIL_CORNER_SW
                                                : StagePart.LV2_RAIL_CORNER_SE;
                else rail = StagePart.LV2_RAIL_X;
                put(p,root,front,side*x,3,-5,rail);
            }
            for (int x : new int[]{9,15}) for (int z=-4; z<=-1; z++) {
                put(p,root,front,side*x,3,z,
                        z==-1 ? StagePart.LV2_RAIL_END_N : StagePart.LV2_RAIL_Z);
            }
            // Existing musicians render their own keyboard/drums when playing.
            // Neither prop nor spotlight head is pre-built on the gallery.
        }

        // Deepslate columns (not nether brick) surround the actual wall.
        for (int side:new int[]{-1,1}) {
            for (int y=1;y<=ROOF_Y;y++) {
                put(p,root,front,side*HALF_WIDTH,y,FRONT,StagePart.LV2_TRUSS);
            }
            // Side truss running from the front columns to the brick wall.
            for (int z=BACK+1;z<=FRONT;z++)
                put(p,root,front,side*HALF_WIDTH,ROOF_Y-1,z,StagePart.LV2_TRUSS);
        }

        // Wide stepped canopy. A black/deepslate *brick* fascia is always
        // present even in bright daylight; coloured strips are recessed.
        for (int x=-HALF_WIDTH;x<=HALF_WIDTH;x++) {
            put(p,root,front,x,ROOF_Y,FRONT,StagePart.LV2_BLACK);
            put(p,root,front,x,ROOF_Y-1,FRONT,StagePart.LV2_BLACK);
            put(p,root,front,x,ROOF_Y,FRONT-1,StagePart.LV2_BLACK);
            put(p,root,front,x,ROOF_Y,FRONT-2,StagePart.LV2_BLACK);
            put(p,root,front,x,ROOF_Y,BACK,StagePart.LV2_BLACK);
            if (Math.abs(x)<=15)
                put(p,root,front,x,ROOF_Y-1,FRONT-1,StagePart.LV2_BLACK);
            // Red stripe inside upper deepslate roof trim.
            if (Math.abs(x)>=4 && Math.abs(x)<=14)
                put(p,root,front,x,ROOF_Y+1,FRONT-1,StagePart.LV2_RED);
        }
        for (int x=-14;x<=14;x++)
            put(p,root,front,x,ROOF_Y,2,StagePart.LV2_TRUSS);
        // Five SMALL overhead fixtures in the canonical member colours.
        // The moving volumetric beams are rendered client-side by the level-2
        // controller only during performances, not by particle emitters.
        StagePart[] colours = {StagePart.LAMP_SKY_BLUE, StagePart.LAMP_GREEN,
                StagePart.LAMP_GOLD, StagePart.LAMP_GRAY_BLUE, StagePart.LAMP_PURPLE};
        int[] lampXs = {-12,-5,0,5,12};
        for (int i=0;i<lampXs.length;i++)
            put(p,root,front,lampXs[i],ROOF_Y-1,2,colours[i]);
        // No floor spotlight "heads" and no pre-placed keyboard / drum kit.
        return p;
    }

    /**
     * v1.1.79 placed spotlights and instruments are safe to remove in-place.
     * Only delete the exact old mod-owned block states at their known offsets;
     * player placed instruments or altered blocks are never touched.
     * This also runs before packing up old builds, preventing orphan parts.
     */
    public static void reconcileDecorations(Level level, BlockPos root, Direction front, boolean addLamps) {
        if (isLegacy(level, root, front)) return;
        // Existing v1.1.92 stages are recoloured in-place. Only replace the
        // obsolete yellow mod-owned stage parts at their exact authored
        // positions, never nearby player blocks or the stained-glass windows.
        if (addLamps) {
            for (int x=-HALF_WIDTH; x<=HALF_WIDTH; x++) {
                if (x%3==0)
                    replaceIfPart(level,root,front,x,0,FRONT,
                            StagePart.LV2_GOLD, StagePart.LV2_BORDER);
                if (Math.abs(x)<=15 && Math.abs(x)%6<=1)
                    replaceIfPart(level,root,front,x,ROOF_Y-1,FRONT-1,
                            StagePart.LV2_GOLD, StagePart.LV2_BLACK);
            }
            for (int side : new int[]{-1,1}) {
                for (int x : new int[]{9,13})
                    replaceIfPart(level,root,front,side*x,0,7,
                            StagePart.LV2_GOLD, StagePart.LV2_DECK);
                for (int y=5;y<=ROOF_Y;y+=5)
                    replaceIfPart(level,root,front,side*HALF_WIDTH,y,FRONT,
                            StagePart.LV2_GOLD, StagePart.LV2_TRUSS);
            }
        }
        for (int side : new int[]{-1,1}) {
            removeIfPart(level,root,front,side*12,3,-3,
                    side < 0 ? StagePart.LV2_KEYBOARD : StagePart.LV2_DRUMS);
        }
        for (int x : new int[]{-15,-8,8,15})
            removeIfPart(level,root,front,x,1,FRONT-1,StagePart.LV2_SPOTLIGHT);
        for (int x=-14;x<=14;x++) if (x%4==0)
            removeIfPart(level,root,front,x,ROOF_Y-1,2,StagePart.LV2_SPOTLIGHT);
        for (int x : new int[]{-14,-7,0,7,14})
            removeIfPart(level,root,front,x,ROOF_Y-2,FRONT,StagePart.LV2_SPOTLIGHT);
        if (!addLamps) return;
        StagePart[] colours = {StagePart.LAMP_SKY_BLUE, StagePart.LAMP_GREEN,
                StagePart.LAMP_GOLD, StagePart.LAMP_GRAY_BLUE, StagePart.LAMP_PURPLE};
        int[] lampXs = {-12,-5,0,5,12};
        for (int i=0;i<lampXs.length;i++) {
            int[] rel = StageStructure.rotate(lampXs[i],2,front);
            BlockPos at = root.offset(rel[0],ROOF_Y-1,rel[1]);
            if (level.hasChunkAt(at) && level.getBlockState(at).isAir())
                level.setBlock(at, ModRegistry.STAGE_PART.defaultBlockState()
                        .setValue(StagePartBlock.PART,colours[i]), 3);
        }
    }

    private static void replaceIfPart(Level level, BlockPos root, Direction front,
                                      int x, int y, int z,
                                      StagePart oldPart, StagePart replacement) {
        int[] rel = StageStructure.rotate(x,z,front);
        BlockPos at = root.offset(rel[0],y,rel[1]);
        if (!level.hasChunkAt(at)) return;
        BlockState oldState = level.getBlockState(at);
        if (oldState.is(ModRegistry.STAGE_PART)
                && oldState.getValue(StagePartBlock.PART) == oldPart) {
            level.setBlock(at,oldState.setValue(StagePartBlock.PART,replacement),3);
        }
    }

    private static void removeIfPart(Level level,BlockPos root,Direction front,
                                     int x,int y,int z,StagePart part) {
        int[] rel=StageStructure.rotate(x,z,front);
        BlockPos at=root.offset(rel[0],y,rel[1]);
        if (!level.hasChunkAt(at)) return;
        BlockState state=level.getBlockState(at);
        if (state.is(ModRegistry.STAGE_PART) && state.getValue(StagePartBlock.PART)==part)
            level.removeBlock(at,false);
    }

    private static boolean clear(Level level,BlockPos at,Player player) {
        return at.getY()>=level.getMinBuildHeight()
                && at.getY()<level.getMaxBuildHeight()
                && level.hasChunkAt(at) && level.getFluidState(at).isEmpty()
                && level.getBlockState(at).canBeReplaced()
                && (player==null || level.mayInteract(player,at));
    }

    private static Map<BlockPos,StagePart> supports(Level level,BlockPos root,Player player,Direction front) {
        Map<BlockPos,StagePart> result=new LinkedHashMap<>();
        int grounded=0;
        boolean[] sides=new boolean[4];
        for (int[] f:FOOTINGS) {
            int[] rel=StageStructure.rotate(f[0],f[1],front);
            BlockPos at=root.offset(rel[0],-1,rel[1]);
            Map<BlockPos,StagePart> pillar=new LinkedHashMap<>();
            boolean anchored=false;
            for (int depth=0;depth<=MAX_SUPPORT_DEPTH;depth++) {
                if (at.getY()<level.getMinBuildHeight() || !level.hasChunkAt(at)) break;
                if (!level.getFluidState(at).isEmpty()) break;
                if (level.getBlockState(at).isFaceSturdy(level,at,Direction.UP)) {
                    anchored=true;break;
                }
                if (depth==MAX_SUPPORT_DEPTH || !clear(level,at,player)) break;
                pillar.put(at.immutable(),StagePart.SUPPORT);
                at=at.below();
            }
            if (!anchored) continue;
            grounded++;
            result.putAll(pillar);
            if(f[0]<0)sides[0]=true;
            if(f[0]>0)sides[1]=true;
            if(f[1]<0)sides[2]=true;
            if(f[1]>0)sides[3]=true;
        }
        if (grounded<6) return null;
        for(boolean side:sides) if(!side) return null;
        return result;
    }

    public static StageStructure.Placement findPlacement(Level level,BlockPos clicked,Player player,Direction front) {
        for(int raise=0;raise<=StageStructure.MAX_RAISE;raise++) {
            for(int[] shift:new int[][]{{0,0},{1,0},{-1,0},{0,1},{0,-1},
                    {1,1},{1,-1},{-1,1},{-1,-1}}) {
                BlockPos root=clicked.offset(shift[0],raise,shift[1]);
                if(root.getY()+ROOF_Y+1>=level.getMaxBuildHeight() || !clear(level,root,player)) continue;
                Map<BlockPos,StagePart> parts=plan(root,front);
                boolean free=true;
                for(BlockPos at:parts.keySet()) {
                    if(!clear(level,at,player)){free=false;break;}
                }
                if(!free) continue;
                Map<BlockPos,StagePart> posts=supports(level,root,player,front);
                if(posts!=null) return new StageStructure.Placement(root,front,posts,parts);
            }
        }
        return null;
    }

    public static void assemble(Level level,StageStructure.Placement placement) {
        StageStructure.assemble(level,placement);
    }

    /** Legacy v1.1.76 deployed stages must still fold completely. */
    public static boolean isLegacy(Level level,BlockPos root,Direction front) {
        int[] rel=StageStructure.rotate(0,-7,front);
        BlockPos marker=root.offset(rel[0],18,rel[1]);
        BlockState state=level.getBlockState(marker);
        return state.is(ModRegistry.STAGE_PART)
                && state.getValue(StagePartBlock.PART)==StagePart.LV2_BLACK;
    }

    public static void disassemble(Level level,BlockPos root,Direction front) {
        if(isLegacy(level,root,front)) {
            StageStructureLevel2Legacy.disassemble(level,root,front);
            return;
        }
        reconcileDecorations(level, root, front, false);
        // Old v1.1.77 stages still have the embedded rose. Reconstruct its
        // ORIGINAL plan when folding, otherwise old rose pieces would remain.
        Map<BlockPos, StagePart> stageParts = plan(root, front);
        int[] roseMarkerOffset = StageStructure.rotate(0, BACK, front);
        BlockPos roseMarker = root.offset(roseMarkerOffset[0], 19, roseMarkerOffset[1]);
        if (level.hasChunkAt(roseMarker)) {
            BlockState marker = level.getBlockState(roseMarker);
            if (marker.is(ModRegistry.STAGE_PART)
                    && marker.getValue(StagePartBlock.PART) == StagePart.LV2_ROSE) {
                for (int row = 0; row < ROSE.length; row++) {
                    for (int col = 0; col < ROSE[row].length(); col++) {
                        StagePart tile = switch (ROSE[row].charAt(col)) {
                            case 'r' -> StagePart.LV2_ROSE;
                            case 'R' -> StagePart.LV2_ROSE_DARK;
                            case 'G' -> StagePart.LV2_ROSE_GOLD;
                            case 'W' -> StagePart.LV2_ROSE_WHITE;
                            default -> null;
                        };
                        if (tile != null) put(stageParts, root, front, col - 8, 19 - row, BACK, tile);
                    }
                }
            }
        }
        // Remove only exact old stage-owned pieces whose slots were redesigned
        // in v1.1.91. This also allows a previously-built v1.1.90 gallery to
        // fold cleanly, including its now-abandoned isolated outer steps.
        for (int side : new int[]{-1, 1}) {
            removeIfPart(level, root, front, side*15, 1, 2, StagePart.LV2_BORDER);
            removeIfPart(level, root, front, side*15, 2, 1, StagePart.LV2_BORDER);
            for (int x=11; x<=13; x++)
                removeIfPart(level, root, front, side*x, 2, 1, StagePart.LV2_BORDER);
            for (int x : new int[]{10,14})
                removeIfPart(level, root, front, side*x, 3, -5, StagePart.LV2_RAIL);
            // Pre-1.1.92 half-slab staircase and disconnected front fence.
            for (int x=11; x<=13; x++) {
                removeIfPart(level, root, front, side*x, 1, 3, StagePart.LV2_STAIR_HALF);
                removeIfPart(level, root, front, side*x, 1, 2, StagePart.LV2_BORDER);
                removeIfPart(level, root, front, side*x, 1, 1, StagePart.LV2_BLACK);
                removeIfPart(level, root, front, side*x, 2, 1, StagePart.LV2_STAIR_HALF);
            }
            for (int x : new int[]{9,15}) {
                removeIfPart(level, root, front, side*x, 3, -5, StagePart.LV2_RAIL);
                removeIfPart(level, root, front, side*x, 3, 0, StagePart.LV2_RAIL);
                removeIfPart(level, root, front, side*x, 3, -1, StagePart.LV2_RAIL_Z);
            }
            for (int x : new int[]{10,14}) {
                removeIfPart(level, root, front, side*x, 3, 0,
                        front.getAxis() == Direction.Axis.X ? StagePart.LV2_RAIL_Z : StagePart.LV2_RAIL_X);
                removeIfPart(level, root, front, side*x, 1, 0, StagePart.LV2_GOLD);
            }
        }
        for (var entry:stageParts.entrySet()) {
            if(!level.hasChunkAt(entry.getKey()))continue;
            BlockState actual=level.getBlockState(entry.getKey());
            if(actual.is(ModRegistry.STAGE_PART)
                    && (actual.getValue(StagePartBlock.PART)==entry.getValue()
                        || isPreviousStagePiece(entry.getValue(), actual.getValue(StagePartBlock.PART))))
                level.removeBlock(entry.getKey(),false);
        }
        for (int[] f:FOOTINGS) {
            int[] rel=StageStructure.rotate(f[0],f[1],front);
            for(int d=1;d<=MAX_SUPPORT_DEPTH;d++) {
                BlockPos at=root.offset(rel[0],-d,rel[1]);
                if(!level.hasChunkAt(at)||at.getY()<level.getMinBuildHeight())break;
                BlockState actual=level.getBlockState(at);
                if(actual.is(ModRegistry.STAGE_PART)
                        && actual.getValue(StagePartBlock.PART)==StagePart.SUPPORT)
                    level.removeBlock(at,false);
            }
        }
    }

    /** Accept legacy stage-owned states only at offsets already in our plan. */
    private static boolean isPreviousStagePiece(StagePart wanted, StagePart existing) {
        return switch (wanted) {
            // v1.1.92 front apron/side accents were bright-yellow STAGE_PART
            // blocks, and must still fold with the rest of the old stage.
            case LV2_BORDER, LV2_DECK, LV2_TRUSS -> existing == StagePart.LV2_GOLD;
            case LV2_DECK_LIT -> existing == StagePart.LV2_DECK;
            case LV2_CARPET_LIT -> existing == StagePart.LV2_CARPET;
            case LV2_STAIR_SOUTH, LV2_STAIR_NORTH, LV2_STAIR_WEST, LV2_STAIR_EAST ->
                    existing == StagePart.LV2_BORDER || existing == StagePart.LV2_STAIR_HALF;
            case LV2_RAIL_CORNER_NE, LV2_RAIL_CORNER_NW,
                    LV2_RAIL_CORNER_SE, LV2_RAIL_CORNER_SW -> existing == StagePart.LV2_RAIL;
            case LV2_RAIL_END_N, LV2_RAIL_END_E, LV2_RAIL_END_S, LV2_RAIL_END_W ->
                    existing == StagePart.LV2_RAIL_Z || existing == StagePart.LV2_RAIL_X;
            case LV2_BLACK -> existing == StagePart.LV2_GOLD;
            default -> false;
        };
    }

    /** All four roof pillars remain pickup points in any orientation. */
    public static BlockPos controllerAtCorner(Level level,BlockPos clicked) {
        if(!level.hasChunkAt(clicked))return null;
        BlockState state=level.getBlockState(clicked);
        if(!state.is(ModRegistry.STAGE_PART))return null;
        StagePart part=state.getValue(StagePartBlock.PART);
        for(Direction front:new Direction[]{Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST}) {
            for(int side:new int[]{-HALF_WIDTH,HALF_WIDTH})
                for(int z:new int[]{BACK,FRONT}) {
                    int[] rel=StageStructure.rotate(side,z,front);
                    for(int y=-MAX_SUPPORT_DEPTH;y<=ROOF_Y;y++) {
                        if(y<0 && part!=StagePart.SUPPORT)continue;
                        if(y==0 && part!=StagePart.LV2_BORDER && part!=StagePart.LV2_GOLD)continue;
                        if(y>0 && part!=StagePart.LV2_TRUSS && part!=StagePart.LV2_GOLD
                                && part!=StagePart.LV2_BLACK)continue;
                        BlockPos root=clicked.offset(-rel[0],-y,-rel[1]);
                        if(!level.hasChunkAt(root))continue;
                        BlockState controller=level.getBlockState(root);
                        if(controller.is(ModRegistry.TOUR_STAGE_LEVEL2)
                                && controller.getValue(TourStageBlock.FACING)==front
                                && !isLegacy(level,root,front))return root;
                    }
                }
        }
        return StageStructureLevel2Legacy.controllerAtCorner(level,clicked);
    }
}
