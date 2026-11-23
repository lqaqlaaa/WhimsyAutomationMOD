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
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
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
        if (level == null || !getBlockState().hasProperty(ChestBlock.TYPE)
                || getBlockState().getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return inventory;
        }
        BlockPos partnerPos = worldPosition.relative(ChestBlock.getConnectedDirection(getBlockState()));
        if (level.getBlockEntity(partnerPos) instanceof BigCapacityChestBlockEntity partner) {
            return new CombinedInvWrapper(inventory, partner.inventory);
        }
        return inventory;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        // ChestBlockEntity's vanilla serializer uses the vanilla stack codec.
        // Temporarily save one-count display copies there, then persist the real
        // integer counts through the dedicated codec below.
        NonNullList<ItemStack> stacks = inventory.backingStacks();
        int[] realCounts = new int[stacks.size()];
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            realCounts[slot] = stack.getCount();
            if (!stack.isEmpty()) stack.setCount(1);
        }
        try {
            super.saveAdditional(tag, registries);
        } finally {
            for (int slot = 0; slot < stacks.size(); slot++) {
                ItemStack stack = stacks.get(slot);
                if (!stack.isEmpty()) stack.setCount(realCounts[slot]);
            }
        }
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
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= SLOT_COUNT || amount <= 0) return ItemStack.EMPTY;
            ItemStack present = getStackInSlot(slot);
            if (present.isEmpty()) return ItemStack.EMPTY;
            // IItemHandler extraction is a transport batch, not a way to place
            // an oversized stack into another mod's ordinary ItemStack slot.
            return super.extractItem(slot, Math.min(amount, present.getMaxStackSize()), simulate);
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
