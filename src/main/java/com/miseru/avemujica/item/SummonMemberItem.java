package com.miseru.avemujica.item;

import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.registry.ModRegistry;
import com.miseru.avemujica.team.PlayerBandRoster;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public class SummonMemberItem extends Item {
    private final MemberRole role;

    public SummonMemberItem(MemberRole role, Properties props) {
        super(props);
        this.role = role;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        boolean restored = context.getPlayer() instanceof ServerPlayer player
                && PlayerBandRoster.hasIdentity(server, player.getUUID(), role);
        BandMemberEntity npc;
        if (context.getPlayer() instanceof ServerPlayer owner)
            npc = PlayerBandRoster.summonOrRecall(server, Vec3.atBottomCenterOf(pos), role, owner.getUUID());
        else npc = spawn(server, Vec3.atBottomCenterOf(pos), role);
        if (npc == null) {
            if (context.getPlayer() instanceof ServerPlayer owner) {
                if (!PlayerBandRoster.summonEnabled(server, owner.getUUID(), role)) {
                    owner.displayClientMessage(Component.literal(role.display
                            + "已在角色自定义中设为暂不召集。"), false);
                } else {
                    int wait = PlayerBandRoster.revivalRemainingSeconds(server, owner.getUUID(), role);
                    if (wait > 0) owner.displayClientMessage(Component.literal(role.display
                            + "的复活冷却还剩 " + wait + " 秒。"), false);
                }
            }
            return InteractionResult.FAIL;
        }
        // Recalling an already-owned bandmate doesn't eat another summoning token.
        if (!restored && (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild))
            context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    public static BandMemberEntity spawn(ServerLevel level, Vec3 location, MemberRole role) {
        BandMemberEntity entity = ModRegistry.BAND_MEMBER.create(level);
        if (entity == null) return null;
        entity.moveTo(location.x, location.y, location.z, level.random.nextFloat() * 360.0F, 0F);
        entity.setRole(role);
        level.addFreshEntity(entity);
        return entity;
    }
}
