package com.miseru.avemujica.block;

import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Persistent stock. Stores all registered merch display items, including legacy stock from old saves, not arbitrary NBT. */
public final class MerchStallBlockEntity extends BlockEntity {
    private static final int STOCK_LIMIT = 512;
    private final int[] stock = new int[9];
    private UUID owner;

    public MerchStallBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MERCH_STALL_ENTITY, pos, state);
    }
    public UUID owner() { return owner; }
    public int count(int type) { return type >= 0 && type < 9 ? stock[type] : 0; }
    public int insert(int type, int amount, UUID player) {
        if (type < 0 || type >= 9 || amount <= 0 || (owner != null && !owner.equals(player))) return 0;
        int accepted = Math.min(amount, STOCK_LIMIT - stock[type]);
        if (accepted <= 0) return 0;
        if (owner == null) owner = player;
        stock[type] += accepted;
        setChanged();
        return accepted;
    }
    public int remove(int type, int amount, UUID player) {
        if (type < 0 || type >= 9 || amount <= 0 || owner == null || !owner.equals(player)) return 0;
        int taken = Math.min(amount, stock[type]);
        stock[type] -= taken;
        if (taken > 0) setChanged();
        return taken;
    }
    public void clear() { for (int i = 0; i < 9; i++) stock[i] = 0; setChanged(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putIntArray("MerchStock", stock);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        int[] saved = tag.getIntArray("MerchStock");
        for (int i = 0; i < 9; i++) stock[i] = i < saved.length ? Math.max(0, Math.min(STOCK_LIMIT, saved[i])) : 0;
    }
}
