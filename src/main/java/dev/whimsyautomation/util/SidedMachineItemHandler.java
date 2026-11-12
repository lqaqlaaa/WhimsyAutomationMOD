package dev.whimsyautomation.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Hopper-facing view of a machine inventory.
 * Top/sides expose the input slot; the bottom exposes output slots only.
 */
public final class SidedMachineItemHandler implements IItemHandler {
    private final ItemStackHandler inventory;
    private final int outputStart;
    private final boolean input;
    private final boolean output;

    public SidedMachineItemHandler(ItemStackHandler inventory, int outputStart, boolean input, boolean output) {
        this.inventory = inventory;
        this.outputStart = outputStart;
        this.input = input;
        this.output = output;
    }

    @Override
    public int getSlots() {
        return (input ? 1 : 0) + (output ? inventory.getSlots() - outputStart : 0);
    }

    private int map(int slot) {
        if (slot < 0 || slot >= getSlots()) return -1;
        if (input && slot == 0) return 0;
        return outputStart + slot - (input ? 1 : 0);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        int mapped = map(slot);
        return mapped < 0 ? ItemStack.EMPTY : inventory.getStackInSlot(mapped);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        int mapped = map(slot);
        if (mapped != 0 || !input) return stack;
        return inventory.insertItem(mapped, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        int mapped = map(slot);
        if (mapped < outputStart || !output) return ItemStack.EMPTY;
        return inventory.extractItem(mapped, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        int mapped = map(slot);
        return mapped < 0 ? 0 : inventory.getSlotLimit(mapped);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        int mapped = map(slot);
        return mapped == 0 && input && inventory.isItemValid(mapped, stack);
    }
}
