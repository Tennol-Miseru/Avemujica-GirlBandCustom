package com.miseru.avemujica.menu;

import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Server-side validated animation selection through vanilla container button packets. */
public final class PoseDebugMenu extends AbstractContainerMenu {
    private static final int ROLE = 0, ACTION = 1;
    private final Player player;
    private final int serverTargetId;
    private final ContainerData data = new SimpleContainerData(2);

    public PoseDebugMenu(int id, Inventory inventory) {
        this(id, inventory, -1);
    }
    public PoseDebugMenu(int id, Inventory inventory, int targetId) {
        super(ModRegistry.POSE_DEBUG_MENU, id);
        this.player = inventory.player;
        this.serverTargetId = targetId;
        addDataSlots(data);
        refresh();
    }
    private BandMemberEntity target() {
        if (serverTargetId < 0) return null; // server-only; client just receives role/action
        Entity entity = player.level().getEntity(serverTargetId);
        return entity instanceof BandMemberEntity member ? member : null;
    }
    private void refresh() {
        if (!(player instanceof ServerPlayer)) return;
        BandMemberEntity npc = target();
        if (npc == null) return;
        data.set(ROLE, npc.role().ordinal());
        data.set(ACTION, npc.personalityAction());
    }
    @Override public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }
    public MemberRole selectedRole() {
        int index = data.get(ROLE);
        MemberRole[] values = MemberRole.values();
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
    public int activeAction() { return data.get(ACTION); }
    @Override public boolean stillValid(Player user) {
        if (user != player) return false;
        // Client menus do not have a server target id; only server authorizes actions.
        if (!(user instanceof ServerPlayer server)) return true;
        BandMemberEntity npc = target();
        return npc != null && npc.isAlive() && user.distanceToSqr(npc) <= 64.0D
                && (user.getMainHandItem().is(ModRegistry.POSE_DEBUGGER)
                    || user.getOffhandItem().is(ModRegistry.POSE_DEBUGGER))
                && (server.hasPermissions(2) || server.getAbilities().instabuild);
    }
    @Override public boolean clickMenuButton(Player clicker, int button) {
        if (!(clicker instanceof ServerPlayer server) || !stillValid(clicker) || button < 0 || button > 5) return false;
        BandMemberEntity npc = target();
        if (npc == null || !npc.canBeManagedBy(server)) return false;
        boolean success;
        if (button == 5) {
            success = npc.debugResetPose(server);
        } else {
            int action = PoseDebugActions.action(npc.role(), button);
            if (action < 0) return false;
            success = npc.debugTriggerPose(server, action);
        }
        if (!success) server.displayClientMessage(Component.literal(
                "无法演示：成员正在演奏/巡演、战斗、处于水中，或没有操作权限。"), true);
        refresh();
        broadcastChanges();
        return success;
    }
    @Override public ItemStack quickMoveStack(Player user, int index) { return ItemStack.EMPTY; }
}
