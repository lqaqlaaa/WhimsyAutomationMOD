package dev.whimsyautomation.block.entity;

import dev.whimsyautomation.registry.ModBlockEntities;
import dev.whimsyautomation.util.OversizedItemStackCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A 27-slot vanilla chest container whose authoritative slot stacks may use
 * the complete positive int range. The same backing list is exposed to the
 * vanilla Container implementation and the NeoForge automation capability.
 */
public final class BigCapacityChestBlockEntity extends ChestBlockEntity {
    public static final int SLOT_COUNT = 27;
    public static final int MAX_COUNT = Integer.MAX_VALUE;
    private static final String BIG_INVENTORY_TAG = "WhimsyBigCapacityInventory";

    private final BigChestItemHandler inventory = new BigChestItemHandler();

    public BigCapacityChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BIG_CAPACITY_CHEST.get(), pos, state);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return inventory.backingStacks();
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
            inventory.setStackInSlot(slot, stack);
        }
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        inventory.setStackInSlot(slot, stack);
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    public IItemHandler getAutomationItemHandler(@Nullable net.minecraft.core.Direction side) {
        return inventory;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(BIG_INVENTORY_TAG, OversizedItemStackCodec.serialize(inventory, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(BIG_INVENTORY_TAG, CompoundTag.TAG_COMPOUND)) {
            OversizedItemStackCodec.deserialize(inventory, registries, tag.getCompound(BIG_INVENTORY_TAG));
        }
    }

    private final class BigChestItemHandler extends ItemStackHandler {
        private BigChestItemHandler() {
            super(SLOT_COUNT);
        }

        private NonNullList<ItemStack> backingStacks() {
            return stacks;
        }

        @Override
        public int getSlotLimit(int slot) {
            return MAX_COUNT;
        }

        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return MAX_COUNT;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < SLOT_COUNT && !stack.isEmpty();
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }
}
