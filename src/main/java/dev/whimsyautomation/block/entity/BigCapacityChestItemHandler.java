package dev.whimsyautomation.block.entity;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.math.BigInteger;

/**
 * NeoForge automation view over one physical chest or a linked double chest.
 * A double chest exposes one ordered 54-slot handler from either half.
 */
public final class BigCapacityChestItemHandler implements IItemHandler {
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private final BigCapacityChestBlockEntity left;
    private final BigCapacityChestBlockEntity right;

    public BigCapacityChestItemHandler(BigCapacityChestBlockEntity left, BigCapacityChestBlockEntity right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public int getSlots() {
        return right == null ? 27 : 54;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        BigCapacityChestBlockEntity owner = owner(slot);
        return owner == null ? ItemStack.EMPTY : owner.getAutomationStack(localSlot(slot));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        BigCapacityChestBlockEntity owner = owner(slot);
        if (owner == null) return stack;

        int local = localSlot(slot);
        ItemStack existing = owner.getItem(local);
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return stack;

        BigInteger room = BigCapacityChestBlockEntity.MAX_COUNT.subtract(owner.getExactCount(local)).max(BigInteger.ZERO);
        int accepted = BigInteger.valueOf(stack.getCount()).min(room).intValue();
        if (accepted <= 0) return stack;
        if (!simulate) {
            int inserted = owner.insertExact(local, stack, accepted);
            accepted = inserted;
        }
        return accepted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - accepted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        BigCapacityChestBlockEntity owner = owner(slot);
        if (owner == null) return ItemStack.EMPTY;

        int local = localSlot(slot);
        ItemStack existing = owner.getAutomationStack(local);
        if (existing.isEmpty()) return ItemStack.EMPTY;
        int extracted = BigInteger.valueOf(amount).min(owner.getExactCount(local)).min(INT_MAX).intValue();
        if (extracted <= 0) return ItemStack.EMPTY;
        return simulate ? existing.copyWithCount(extracted) : owner.removeItem(local, extracted);
    }

    @Override
    public int getSlotLimit(int slot) {
        return owner(slot) == null ? 0 : Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return owner(slot) != null && !stack.isEmpty();
    }

    private BigCapacityChestBlockEntity owner(int slot) {
        if (slot < 0 || slot >= getSlots()) return null;
        return slot < 27 ? left : right;
    }

    private int localSlot(int slot) {
        return slot < 27 ? slot : slot - 27;
    }
}
