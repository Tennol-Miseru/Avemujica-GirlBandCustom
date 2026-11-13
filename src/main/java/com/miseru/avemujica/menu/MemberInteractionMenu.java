package com.miseru.avemujica.menu;

import com.miseru.avemujica.career.BandManagement;
import com.miseru.avemujica.career.BandCareerData;
import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.entity.TeamMode;
import com.miseru.avemujica.entity.TeamOrders;
import com.miseru.avemujica.registry.ModRegistry;
import com.miseru.avemujica.voice.VoiceDirector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

/** Vanilla-synchronized, server-authoritative NPC menu with no custom network packets. */
public final class MemberInteractionMenu extends AbstractContainerMenu {
    public static final int ENTITY = 0, ROLE = 1, HEALTH = 2, MAX_HEALTH = 3, TASK = 4, ACTIVE = 5, OWNED = 6, PERSONALITY = 7, TEAM_MODE = 8, TRAINING = 9;
    public static final int ALT_SUPPORTED = 10, ALT_UNLOCKED = 11, ALT_ACTIVE = 12;
    public static final int GIFT_ENABLED = 13;
    public static final int DATA_COUNT = 14;
    private final Player player;
    private final int targetId;
    private final ContainerData data;
    private int refreshTicks;

    public MemberInteractionMenu(int id, Inventory inventory) {
        this(id, inventory, -1);
    }

    public MemberInteractionMenu(int id, Inventory inventory, int targetId) {
        super(ModRegistry.MEMBER_INTERACTION_MENU, id);
        this.player = inventory.player;
        this.targetId = targetId;
        this.data = new SimpleContainerData(DATA_COUNT);
        addDataSlots(data);
        refresh();
    }

    private BandMemberEntity target() {
        int id = targetId < 0 ? data.get(ENTITY) : targetId;
        Entity entity = player.level().getEntity(id);
        return entity instanceof BandMemberEntity member ? member : null;
    }

    private void refresh() {
        if (!(player instanceof ServerPlayer server)) return;
        BandMemberEntity npc = target();
        if (npc == null) return;
        data.set(ENTITY, npc.getId());
        data.set(ROLE, npc.role().ordinal());
        data.set(HEALTH, Math.round(npc.getHealth()));
        data.set(MAX_HEALTH, Math.round(npc.getMaxHealth()));
        data.set(TASK, npc.task());
        data.set(ACTIVE, npc.isPerforming() ? 1 : 0);
        data.set(OWNED, npc.canBeManagedBy(server) ? 1 : 0);
        data.set(PERSONALITY, npc.personalityAction());
        data.set(TEAM_MODE, npc.teamMode().ordinal());
        data.set(TRAINING, npc.trainingRank());
        data.set(ALT_SUPPORTED, npc.hasSecondCombatStyle() ? 1 : 0);
        data.set(ALT_UNLOCKED, npc.alternateUnlocked() ? 1 : 0);
        data.set(ALT_ACTIVE, npc.alternateCombat() ? 1 : 0);
        data.set(GIFT_ENABLED, BandCareerData.get(server.serverLevel()).career(server.getUUID())
                .giftsEnabled(npc.role().id) ? 1 : 0);
    }

    @Override public void broadcastChanges() {
        if ((++refreshTicks % 5) == 0) refresh();
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) {
        BandMemberEntity npc = target();
        return npc != null && npc.isAlive() && player.distanceToSqr(npc) < 64.0D;
    }

    @Override public boolean clickMenuButton(Player clicker, int button) {
        if (!(clicker instanceof ServerPlayer server) || clicker != player || !stillValid(clicker)) return false;
        BandMemberEntity npc = target();
        if (npc == null || !npc.canBeManagedBy(server)) {
            server.displayClientMessage(Component.literal("只有该成员的管理者可以安排她。"), false);
            return false;
        }
        if (button == 23) {
            if (npc.role() == MemberRole.OBLIVIONIS) return false;
            BandCareerData careers = BandCareerData.get(server.serverLevel());
            BandCareerData.Career career = careers.career(server.getUUID());
            boolean enabled = !career.giftsEnabled(npc.role().id);
            career.dailyGiftEnabled.put(npc.role().id, enabled);
            careers.setDirty();
            server.displayClientMessage(Component.literal(npc.role().display + "的日常赠礼已" + (enabled ? "开启。" : "关闭。")), false);
            refresh(); broadcastChanges();
            return true;
        }
        if (button == 22) {
            // Server-authoritative recovery via the owner's living Sakiko only.
            if (npc.role() != MemberRole.OBLIVIONIS) return false;
            boolean hasOctopus = false;
            for (int slot = 0; slot < server.getInventory().getContainerSize(); slot++) {
                if (server.getInventory().getItem(slot).is(ModRegistry.BLUE_OCTOPUS)) {
                    hasOctopus = true; break;
                }
            }
            if (server.getOffhandItem().is(ModRegistry.BLUE_OCTOPUS)
                    || server.getMainHandItem().is(ModRegistry.BLUE_OCTOPUS)) hasOctopus = true;
            if (hasOctopus) {
                server.displayClientMessage(Component.literal("祥子：蓝色小章鱼就在你身上，不需要再领一只。"), false);
                return true;
            }
            BandCareerData careers = BandCareerData.get(server.serverLevel());
            BandCareerData.Career career = careers.career(server.getUUID());
            long now = server.serverLevel().getGameTime();
            if (now >= career.lastOctopusReplacementTick
                    && now - career.lastOctopusReplacementTick < 6000L) {
                int seconds = (int)((6000L - (now - career.lastOctopusReplacementTick) + 19L) / 20L);
                server.displayClientMessage(Component.literal("祥子：刚刚已经给过你了，请再等 " + seconds + " 秒。"), false);
                return true;
            }
            ItemStack replacement = new ItemStack(ModRegistry.BLUE_OCTOPUS);
            if (!server.getInventory().add(replacement) && !replacement.isEmpty())
                server.drop(replacement, false);
            career.lastOctopusReplacementTick = now;
            careers.setDirty();
            server.displayClientMessage(Component.literal("丰川祥子交给了你一个新的蓝色小章鱼。别再弄丢了。"), false);
            return true;
        }
        if (button == 20) {
            boolean unlocked = npc.unlockAlternateCombat(server);
            if (unlocked) VoiceDirector.feature(npc, VoiceDirector.Feature.COMBAT_UNLOCK);
            server.displayClientMessage(Component.literal(unlocked
                    ? "已解锁并启用第二战斗模式！可随时切换。"
                    : "解锁条件：训练 Lv.5，背包有 8 颗绿宝石（每人单独解锁）。"), false);
            refresh(); broadcastChanges();
            return unlocked;
        }
        if (button == 21) {
            boolean switched = npc.toggleAlternateCombat(server);
            if (switched) VoiceDirector.feature(npc, npc.alternateCombat()
                    ? VoiceDirector.Feature.COMBAT_ON : VoiceDirector.Feature.COMBAT_OFF);
            if (switched) server.displayClientMessage(Component.literal(npc.alternateCombat()
                    ? "已切换至第二战斗模式。" : "已切换回音符战斗模式。"), false);
            refresh(); broadcastChanges();
            return switched;
        }
        if (button >= 10 && button <= 15) {
            if (npc.role() != MemberRole.OBLIVIONIS) return false;
            TeamMode order = switch (button) {
                case 10 -> TeamMode.FOLLOW;
                case 11 -> TeamMode.REHEARSAL;
                case 12 -> TeamMode.PERFORMANCE;
                case 13 -> TeamMode.REST;
                case 14 -> TeamMode.STANDBY;
                default -> TeamMode.FREE;
            };
            int affected = TeamOrders.issue(server, npc, order);
            if (affected == 0) return false;
            refresh();
            broadcastChanges();
            return true;
        }
        if (button >= 2 && button <= 5 && npc.role() == MemberRole.OBLIVIONIS
                && npc.teamMode() != TeamMode.NONE) {
            server.displayClientMessage(Component.literal("祥子正在指挥全队，请先在团队菜单中更改模式。"), false);
            return false;
        }
        switch (button) {
            case 0 -> {
                float previousHealth = npc.getHealth();
                boolean fed = npc.feedFrom(server);
                if (fed && npc.getHealth() > previousHealth)
                    VoiceDirector.feature(npc, VoiceDirector.Feature.HEALED);
                if (!fed)
                    server.displayClientMessage(Component.literal(
                            "请主手持可食用物品；演出或交战时不能喂食。"), false);
            }
            case 1 -> {
                boolean started = npc.beginSolo();
                if (started) VoiceDirector.feature(npc, VoiceDirector.Feature.SOLO);
                server.displayClientMessage(Component.literal(started ? "开始12秒试奏。" : "正在演出或独奏尚未冷却。"), false);
            }
            case 2 -> { npc.assignTask(0); VoiceDirector.feature(npc, VoiceDirector.Feature.INDIVIDUAL_STANDBY); }
            case 3 -> { npc.assignTask(1); VoiceDirector.feature(npc, VoiceDirector.Feature.PATROL); }
            case 4 -> { npc.assignTask(2); VoiceDirector.feature(npc, VoiceDirector.Feature.GUARD); }
            case 5 -> { npc.assignTask(3); VoiceDirector.feature(npc, VoiceDirector.Feature.INDIVIDUAL_REST); }
            case 6 -> { BandManagement.openMenu(server); return true; }
            default -> { return false; }
        }
        refresh();
        broadcastChanges();
        return true;
    }

    public boolean giftsEnabled() { return data.get(GIFT_ENABLED) != 0; }
    public int entityId() { return data.get(ENTITY); }
    public int role() { return data.get(ROLE); }
    public int trainingRank() { return data.get(TRAINING); }
    public boolean supportsAlternate() { return data.get(ALT_SUPPORTED) != 0; }
    public boolean alternateUnlocked() { return data.get(ALT_UNLOCKED) != 0; }
    public boolean alternateActive() { return data.get(ALT_ACTIVE) != 0; }
    public int health() { return data.get(HEALTH); }
    public int maxHealth() { return data.get(MAX_HEALTH); }
    public int task() { return data.get(TASK); }
    public boolean performing() { return data.get(ACTIVE) != 0; }
    public boolean owned() { return data.get(OWNED) != 0; }
    public int personality() { return data.get(PERSONALITY); }
    public TeamMode teamMode() { return TeamMode.fromId(data.get(TEAM_MODE)); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
