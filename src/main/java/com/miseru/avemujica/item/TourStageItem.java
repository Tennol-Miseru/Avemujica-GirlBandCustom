package com.miseru.avemujica.item;

import com.miseru.avemujica.stage.StageStructure;
import com.miseru.avemujica.career.BandCareerData;
import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.Locale;

/** Packed, rotatable tour stage. Sneak-right-click air to select the audience-facing direction. */
public final class TourStageItem extends BlockItem {
    private static final String DIRECTION_TAG = "MujicaStageFront";
    public TourStageItem(Block block, Item.Properties settings) { super(block, settings); }

    public static Direction facing(ItemStack stack, Direction fallback) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return fallback;
        String name = custom.copyTag().getString(DIRECTION_TAG);
        try {
            Direction direction = Direction.valueOf(name.toUpperCase(Locale.ROOT));
            return direction == Direction.NORTH || direction == Direction.SOUTH
                    || direction == Direction.EAST || direction == Direction.WEST ? direction : fallback;
        } catch (IllegalArgumentException ignored) { return fallback; }
    }

    public static void setFacing(ItemStack stack, Direction front) {
        CustomData old = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = old == null ? new CompoundTag() : old.copyTag();
        tag.putString(DIRECTION_TAG, front.name());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return super.use(level, player, hand);
        Direction current = facing(stack, player.getDirection().getOpposite());
        Direction next = current.getClockWise();
        if (!level.isClientSide) {
            setFacing(stack, next);
            player.displayClientMessage(Component.literal("舞台正面朝向：" + directionLabel(next) + "（潜行+右键空气继续旋转）"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static String directionLabel(Direction direction) {
        return switch (direction) {
            case NORTH -> "北"; case SOUTH -> "南"; case EAST -> "东"; case WEST -> "西";
            default -> "南";
        };
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Direction front = facing(context.getItemInHand(),
                context.getPlayer() == null ? Direction.SOUTH : context.getPlayer().getDirection().getOpposite());
        boolean levelTwo = getBlock() == ModRegistry.TOUR_STAGE_LEVEL2;
        StageStructure.Placement placement = levelTwo
                ? com.miseru.avemujica.stage.StageStructureLevel2.findPlacement(
                        context.getLevel(), context.getClickedPos(), context.getPlayer(), front)
                : StageStructure.findPlacement(
                        context.getLevel(), context.getClickedPos(), context.getPlayer(), front);
        if (placement == null) {
            Player player = context.getPlayer();
            if (player != null && !context.getLevel().isClientSide) {
                player.displayClientMessage(Component.literal(
                        levelTwo ? "无法搭建二级舞台：需要33×17空地及约24格净空，且四侧有牢固支撑；潜行右键空气可旋转。"
                                : "无法搭建舞台：此方向需要11×9安全地板区域与分布支撑，已尝试自动平移、抬升。潜行右键空气可换方向。"), true);
            }
            return InteractionResult.FAIL;
        }
        BlockPlaceContext raised = BlockPlaceContext.at(context, placement.root(), Direction.UP);
        InteractionResult placed = super.place(raised);
        if (placed.consumesAction() && !context.getLevel().isClientSide) {
            if (levelTwo) com.miseru.avemujica.stage.StageStructureLevel2.assemble(context.getLevel(), placement);
            else StageStructure.assemble(context.getLevel(), placement);
            Player player = context.getPlayer();
            if (player != null) {
                player.displayClientMessage(Component.literal((levelTwo ? "二级舞台" : "舞台") + "已搭建（面向" + directionLabel(front)
                        + "）。空手潜行右键舞台四角任意高度的结构方块可收起。"), true);
                if (player instanceof ServerPlayer serverPlayer) {
                    BandCareerData data = BandCareerData.get(serverPlayer.serverLevel());
                    BandCareerData.Career career = data.career(serverPlayer.getUUID());
                    if (!career.starterOctopusGranted) {
                        ItemStack gift = new ItemStack(ModRegistry.BLUE_OCTOPUS);
                        boolean added = serverPlayer.getInventory().add(gift);
                        if (!added) serverPlayer.drop(gift, false);
                        career.starterOctopusGranted = true;
                        data.setDirty();
                        serverPlayer.displayClientMessage(Component.literal("丰川祥子赠送了你一个蓝色小章鱼：“从现在起，我们就是命运共同体。”"), false);
                    }
                }
            }
        }
        return placed;
    }
}
