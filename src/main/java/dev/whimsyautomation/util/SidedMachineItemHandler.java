package dev.whimsyautomation.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import java.util.function.BooleanSupplier;

/**
 * Hopper-facing view of a machine inventory.
 * Top/sides expose the input slot; the bottom exposes output slots only.
 */
public final class SidedMachineItemHandler implements IItemHandler {
    private final ItemStackHandler inventory;
    private final int outputStart;
    private final boolean input;
    private final boolean output;
    private final BooleanSupplier allowInputExtraction;

    public SidedMachineItemHandler(ItemStackHandler inventory, int outputStart, boolean input, boolean output) {
        this(inventory, outputStart, input, output, () -> false);
    }

    /**
     * Optional final hopper slot for extracting the machine input after its
     * generated outputs have been fully consumed.
     */
    public SidedMachineItemHandler(ItemStackHandler inventory, int outputStart, boolean input,
                                   boolean output, BooleanSupplier allowInputExtraction) {
        this.inventory = inventory;
        this.outputStart = outputStart;
        this.input = input;
        this.output = output;
        this.allowInputExtraction = allowInputExtraction;
    }

    @Override
    public int getSlots() {
        return (input ? 1 : 0) + (output ? inventory.getSlots() - outputStart : 0)
                + (output && allowInputExtraction.getAsBoolean() ? 1 : 0);
    }

    private int map(int slot) {
        if (slot < 0 || slot >= getSlots()) return -1;
        if (input && slot == 0) return 0;
        int outputSlots = output ? inventory.getSlots() - outputStart : 0;
        int outputIndex = slot - (input ? 1 : 0);
        if (outputIndex >= outputSlots) {
            return output && allowInputExtraction.getAsBoolean() && outputIndex == outputSlots ? 0 : -1;
        }
        return output ? outputStart + outputIndex : -1;
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
        if (!output || mapped < 0 || amount <= 0) return ItemStack.EMPTY;
        if (mapped == 0 && !allowInputExtraction.getAsBoolean()) return ItemStack.EMPTY;
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
