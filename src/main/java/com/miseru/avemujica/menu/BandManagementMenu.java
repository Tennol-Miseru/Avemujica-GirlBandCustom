package com.miseru.avemujica.menu;

import com.miseru.avemujica.career.BandManagement;
import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative, inventory-free management screen menu.
 *
 * Uses vanilla container data synchronization, so no extra custom packets or client-side
 * economy mutations are necessary. The two halves of large integers are transmitted
 * separately because container DataSlots are serialized as signed 16-bit values.
 */
public final class BandManagementMenu extends AbstractContainerMenu {
    public static final int EMERALDS_LO = 0, EMERALDS_HI = 1;
    public static final int FANS_LO = 2, FANS_HI = 3;
    public static final int FAME = 4, SHOWS = 5, SKILL = 6, BOND = 7, MORALE = 8;
    public static final int SOUND = 9, LIGHTS = 10, MEMBERS = 11, NOTE_BLOCKS = 12;
    public static final int PRACTICE_WAIT = 13, PROMOTE_WAIT = 14, SHOW_REMAINING = 15;
    public static final int CONTEXT_READY = 16, IN_OVERWORLD = 17, RECRUITED = 18, PERFORMING = 19;
    public static final int BAND_LEVEL = 20, BAND_XP_LO = 21, NEXT_XP = 22;
    public static final int VISITED = 23, TOUR_SHOWS = 24, PENDING_LOOT = 25;
    public static final int AVG_TRAINING = 26, VILLAGE_HINT = 27, BAND_XP_HI = 28;
    public static final int VILLAGE_POPULATION = 29, VILLAGE_BEDS = 30, VILLAGE_BELL = 31;
    public static final int DATA_COUNT = 32;
    private static final String[] ACTIONS = {"recruit", "rehearse", "show", "tour", "promote", "sound", "lights", "claim", "rehearse_enhanced"};

    private final ContainerData data;
    private final Player owner;
    private int refreshTick;

    /** Client-side factory required by MenuType. */
    public BandManagementMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainerData(DATA_COUNT));
    }

    /** Server-side menu, bound to the player rather than to a shared block inventory. */
    public BandManagementMenu(int containerId, Inventory inventory, ContainerData data) {
        super(ModRegistry.BAND_MANAGEMENT_MENU, containerId);
        if (data.getCount() != DATA_COUNT) throw new IllegalArgumentException("Band GUI data size mismatch");
        this.owner = inventory.player;
        this.data = data;
        this.addDataSlots(data);
        refresh();
    }

    private void refresh() {
        if (owner instanceof ServerPlayer server) {
            int[] snapshot = BandManagement.menuSnapshot(server);
            for (int i = 0; i < DATA_COUNT; i++) data.set(i, snapshot[i]);
        }
    }

    @Override
    public void broadcastChanges() {
        // Venue scans inspect nearby blocks. Refresh once per second, not 4x/s.
        if ((++refreshTick % 20) == 0) refresh();
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        // Client button clicks send a vanilla menu-button packet; only the server may apply commands.
        if (!(player instanceof ServerPlayer server) || player != owner || id < 0 || id >= ACTIONS.length) return false;
        // command() validates the dimension, ownership, costs, cooldowns, and team readiness again.
        BandManagement.command(server, ACTIONS[id], false);
        refresh();
        this.broadcastChanges();
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return player.isAlive(); }

    private int value(int index) { return data.get(index); }
    private int splitValue(int low, int high) { return (data.get(low) & 65535) | ((data.get(high) & 65535) << 16); }
    public int bandLevel() { return value(BAND_LEVEL); }
    public int bandXp() { return splitValue(BAND_XP_LO, BAND_XP_HI); }
    public int nextXp() { return value(NEXT_XP); }
    public int visited() { return value(VISITED); }
    public int tourShows() { return value(TOUR_SHOWS); }
    public int pendingLoot() { return value(PENDING_LOOT); }
    public int averageTraining() { return value(AVG_TRAINING); }
    public boolean villageHint() { return value(VILLAGE_HINT) != 0; }
    public int villagePopulation() { return value(VILLAGE_POPULATION); }
    public int villageBeds() { return value(VILLAGE_BEDS); }
    public boolean villageBell() { return value(VILLAGE_BELL) != 0; }
    public int emeralds() { return splitValue(EMERALDS_LO, EMERALDS_HI); }
    public int fans() { return splitValue(FANS_LO, FANS_HI); }
    public int fame() { return value(FAME); }
    public int shows() { return value(SHOWS); }
    public int skill() { return value(SKILL); }
    public int bond() { return value(BOND); }
    public int morale() { return value(MORALE); }
    public int soundLevel() { return value(SOUND); }
    public int lightLevel() { return value(LIGHTS); }
    public int members() { return value(MEMBERS); }
    public int noteBlocks() { return value(NOTE_BLOCKS); }
    public int practiceWait() { return value(PRACTICE_WAIT); }
    public int promoteWait() { return value(PROMOTE_WAIT); }
    public int showRemaining() { return value(SHOW_REMAINING); }
    /** Former desk slot retained for protocol layout compatibility. */
    public boolean managementAvailable() { return value(CONTEXT_READY) != 0; }
    public boolean inOverworld() { return value(IN_OVERWORLD) != 0; }
    public boolean recruited() { return value(RECRUITED) != 0; }
    public boolean performing() { return value(PERFORMING) != 0; }
    public int recruitCost() { return recruited() ? Math.max(0, 5 - members()) * BandManagement.REPLACEMENT_COST : BandManagement.FIRST_RECRUIT_COST; }
    public int soundCost() { return BandManagement.upgradeCost(soundLevel()); }
    public int lightsCost() { return BandManagement.upgradeCost(lightLevel()); }
    public boolean stageReady() { return members() >= 5; }

    public boolean canRun(int id) {
        if (id == 7) return pendingLoot() > 0;
        if (!inOverworld()) return false;
        return switch (id) {
            case 0 -> members() < 5 && emeralds() >= recruitCost();
            case 1 -> stageReady() && !performing(); // free, no cooldown
            case 8 -> stageReady() && !performing() && practiceWait() == 0
                    && (owner.getAbilities().instabuild || emeralds() >= BandManagement.PRACTICE_COST);
            case 2 -> stageReady() && !performing() && bond() >= 20 && morale() >= 15 && emeralds() >= BandManagement.SHOW_COST;
            // Keep the action clickable even when prerequisites are missing:
            // the server will explain exactly why, instead of a silent grey button.
            // This is not an authorization bypass: command("tour") rechecks all rules.
            case 3 -> !performing();
            case 4 -> !performing() && promoteWait() == 0 && emeralds() >= BandManagement.PROMOTION_COST;
            case 5 -> soundLevel() < 5 && emeralds() >= soundCost();
            case 6 -> lightLevel() < 5 && emeralds() >= lightsCost();
            default -> false;
        };
    }
}
