package com.miseru.avemujica.menu;

import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.registry.ModRegistry;
import com.miseru.avemujica.team.PlayerBandRoster;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative, creative/op-only band progression test panel. */
public final class BandDebugMenu extends AbstractContainerMenu {
    private static final int ROLES = MemberRole.values().length;
    private final Player player;
    private final ContainerData status = new SimpleContainerData(ROLES * 3);
    private int refreshTicks;

    public BandDebugMenu(int id, Inventory inventory) {
        super(ModRegistry.BAND_DEBUG_MENU, id);
        player = inventory.player;
        addDataSlots(status);
        refresh();
    }

    public static boolean authorized(ServerPlayer user) {
        return user.hasPermissions(2) || user.getAbilities().instabuild;
    }

    private boolean holdingTool() {
        return player.getMainHandItem().is(ModRegistry.DEBUG_TOOL)
                || player.getOffhandItem().is(ModRegistry.DEBUG_TOOL);
    }

    @Override public boolean stillValid(Player user) {
        return user == player && holdingTool() &&
                (!(user instanceof ServerPlayer server) || authorized(server));
    }

    private void refresh() {
        if (!(player instanceof ServerPlayer server)) return;
        for (MemberRole role : MemberRole.values()) {
            BandMemberEntity npc = PlayerBandRoster.findCurrentLoaded(server.serverLevel(), server.getUUID(), role);
            int idx = role.ordinal() * 3;
            status.set(idx, npc == null ? -1 : npc.trainingRank());
            status.set(idx + 1, npc != null && npc.alternateUnlocked() ? 1 : 0);
            status.set(idx + 2, npc != null && npc.alternateCombat() ? 1 : 0);
        }
    }

    @Override public void broadcastChanges() {
        if (++refreshTicks % 5 == 0) refresh();
        super.broadcastChanges();
    }

    @Override public boolean clickMenuButton(Player clicker, int button) {
        if (!(clicker instanceof ServerPlayer server) || !stillValid(clicker)) return false;
        // Per-member buttons: Lv5, Lv15, unlock, toggle, reset.
        // Group buttons: all Lv5, all Lv15 with styles, all reset.
        if (button < 0 || button > ROLES * 5 + 2) return false;
        int action = button >= ROLES * 5 ? button - ROLES * 5 : button % 5;
        int first = button >= ROLES * 5 ? 0 : button / 5;
        int last = button >= ROLES * 5 ? ROLES : first + 1;
        boolean any = false;
        for (int i = first; i < last; i++) {
            MemberRole role = MemberRole.values()[i];
            BandMemberEntity member = PlayerBandRoster.findCurrentLoaded(server.serverLevel(), server.getUUID(), role);
            if (member == null) {
                double angle = (i - 2) * Math.PI / 5D;
                Vec3 destination = server.position().add(Math.cos(angle) * 3D, .2D, Math.sin(angle) * 3D);
                member = PlayerBandRoster.summonOrRecall(server.serverLevel(), destination, role, server.getUUID());
            }
            if (member == null) {
                server.displayClientMessage(Component.literal(role.display + "：旧档案尚未迁移，请先加载原成员所在区块。"), false);
                continue;
            }
            int rank = member.trainingRank();
            boolean unlocked = member.alternateUnlocked();
            boolean enabled = member.alternateCombat();
            if (button >= ROLES * 5) {
                if (action == 0) rank = 5;
                else if (action == 1) { rank = 15; unlocked = member.hasSecondCombatStyle(); enabled = unlocked; }
                else { rank = 0; unlocked = false; enabled = false; }
            } else {
                switch (action) {
                    case 0 -> rank = 5;
                    case 1 -> rank = 15;
                    case 2 -> { rank = Math.max(rank, 5); unlocked = member.hasSecondCombatStyle(); enabled = unlocked; }
                    case 3 -> { if (unlocked) enabled = !enabled; }
                    case 4 -> { rank = 0; unlocked = false; enabled = false; }
                    default -> { return false; }
                }
            }
            member.debugSetProgress(rank, unlocked, enabled);
            any = true;
        }
        refresh();
        broadcastChanges();
        if (any) server.displayClientMessage(Component.literal("[Ave Mujica] 调试进度已保存到你的角色档案。"), true);
        return any;
    }

    public int rank(int role) { return status.get(role * 3); }
    public boolean unlocked(int role) { return status.get(role * 3 + 1) != 0; }
    public boolean active(int role) { return status.get(role * 3 + 2) != 0; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
