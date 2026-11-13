package com.miseru.avemujica.menu;

import com.miseru.avemujica.career.BandManagement;
import com.miseru.avemujica.career.TourAccount;
import com.miseru.avemujica.career.RedMoonPreference;
import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.TeamMode;
import com.miseru.avemujica.entity.TeamOrders;
import com.miseru.avemujica.music.MidiBandPlayer;
import com.miseru.avemujica.registry.ModRegistry;
import com.miseru.avemujica.team.OctopusSummons;
import com.miseru.avemujica.team.PlayerBandRoster;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;

/** Portable Sakiko-themed command center. Actions are fully validated server-side. */
public final class SakikoConsoleMenu extends AbstractContainerMenu {
    public static final int MEMBERS = 0, MODE = 1, BUSY = 2, NAME_TAGS = 3;
    public static final int RED_MOON_AVAILABLE = 4, RED_MOON_ENABLED = 5;
    private static final int DATA_COUNT = 6;
    private static final int TOGGLE_NAME_TAGS = 11;
    private static final int OPEN_TOUR_ACCOUNT = 12;
    private static final int TOGGLE_RED_MOON = 13;
    private static final int OPEN_CONSIGNMENT = 14;
    private static final int OPEN_SHOP = 15;
    private final Player owner;
    private final ContainerData data = new SimpleContainerData(DATA_COUNT);
    private int ticks;

    public SakikoConsoleMenu(int id, Inventory inventory) {
        super(ModRegistry.SAKIKO_CONSOLE_MENU, id);
        owner = inventory.player;
        addDataSlots(data);
        refresh();
    }

    private void refresh() {
        if (owner instanceof ServerPlayer player) {
            var nearby = TeamOrders.nearbyOwned(player);
            data.set(MEMBERS, nearby.size());
            var mode = nearby.values().stream().filter(member -> member.teamMode() != TeamMode.NONE)
                    .min(java.util.Comparator.comparingDouble(member -> player.distanceToSqr(member)))
                    .map(BandMemberEntity::teamMode).orElse(TeamMode.NONE);
            data.set(MODE, mode.ordinal());
            data.set(BUSY, OctopusSummons.busy(player) ? 1 : 0);
            data.set(NAME_TAGS, PlayerBandRoster.nameTagsVisible(player.serverLevel(), player.getUUID()) ? 1 : 0);
            data.set(RED_MOON_AVAILABLE, RedMoonPreference.unlocked(player) ? 1 : 0);
            data.set(RED_MOON_ENABLED, RedMoonPreference.enabled(player) ? 1 : 0);
        }
    }

    @Override public void broadcastChanges() {
        if (++ticks % 8 == 0) refresh();
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) {
        return player == owner && player.isAlive()
                && (player.getMainHandItem().is(ModRegistry.BLUE_OCTOPUS)
                    || player.getOffhandItem().is(ModRegistry.BLUE_OCTOPUS));
    }

    @Override public boolean clickMenuButton(Player clicker, int id) {
        if (!(clicker instanceof ServerPlayer player) || !stillValid(clicker)
                || id < 0 || id > OPEN_SHOP) return false;
        if (id == 0) {
            OctopusSummons.begin(player);
        } else if (id >= 1 && id <= 6) {
            TeamMode mode = TeamMode.values()[id]; // 1=FOLLOW .. 6=FREE
            if (OctopusSummons.busy(player) || TeamOrders.issueFromOctopus(player, mode) == 0) return false;
        } else if (id == 7) {
            BandManagement.openMenu(player);
        } else if (id == 8) {
            MidiBandPlayer.browse(player.createCommandSourceStack(), -1);
        } else if (id == 9) {
            MidiBandPlayer.stop(player.createCommandSourceStack());
        } else if (id == OPEN_SHOP) {
            com.miseru.avemujica.career.OctopusShop.open(player);
        } else if (id == OPEN_CONSIGNMENT) {
            com.miseru.avemujica.career.MerchConsignment.open(player);
        } else if (id == OPEN_TOUR_ACCOUNT) {
            TourAccount.open(player);
        } else if (id == TOGGLE_RED_MOON) {
            if (!RedMoonPreference.unlocked(player)) {
                player.displayClientMessage(Component.literal("需要五位成员的训练等级全部达到 Lv.15，才能切换红月表现。"), false);
                return false;
            }
            boolean enabled = RedMoonPreference.toggle(player);
            player.displayClientMessage(Component.literal(enabled
                    ? "红月表现：开启。五人满级合奏时将显示红月。"
                    : "红月表现：关闭。光影下使用正常日月，演出增益不变。"), false);
        } else if (id == TOGGLE_NAME_TAGS) {
            boolean visible = PlayerBandRoster.toggleNameTags(player);
            player.displayClientMessage(Component.literal(visible
                    ? "Ave Mujica：已显示五人头顶名称。" : "Ave Mujica：已隐藏五人头顶名称。"), false);
        } else if (id == 10) {
            BandMemberEntity sakiko = OctopusSummons.nearestSakiko(player);
            if (sakiko == null) {
                player.displayClientMessage(Component.literal("请先召集祥子到身边，再打开她的个人面板。"), false);
                return false;
            }
            player.openMenu(new SimpleMenuProvider((menuId, inventory, ignored) ->
                    new MemberInteractionMenu(menuId, inventory, sakiko.getId()),
                    Component.literal(sakiko.role().display)));
        }
        refresh();
        broadcastChanges();
        return true;
    }

    public int members() { return data.get(MEMBERS); }
    public int teamModeId() { return data.get(MODE); }
    public boolean summoning() { return data.get(BUSY) != 0; }
    public boolean nameTagsVisible() { return data.get(NAME_TAGS) != 0; }
    public boolean redMoonAvailable() { return data.get(RED_MOON_AVAILABLE) != 0; }
    public boolean redMoonEnabled() { return data.get(RED_MOON_ENABLED) != 0; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
