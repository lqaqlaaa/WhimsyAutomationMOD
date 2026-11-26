package dev.whimsyautomation.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import java.util.function.BooleanSupplier;

/** Compact, live view of a machine inventory for external automation. */
public final class SidedMachineItemHandler implements IItemHandler {
    private final ItemStackHandler inventory;
    private final IItemHandler inputInventory;
    private final int outputStart;
    private final boolean input;
    private final boolean output;
    private final BooleanSupplier allowInputExtraction;
    private final BooleanSupplier enabled;

    public SidedMachineItemHandler(ItemStackHandler inventory, int outputStart, boolean input, boolean output) {
        this(inventory, inventory, outputStart, input, output, () -> false, () -> true);
    }

    public SidedMachineItemHandler(ItemStackHandler inventory, int outputStart, boolean input, boolean output,
                                   BooleanSupplier allowInputExtraction) {
        this(inventory, inventory, outputStart, input, output, allowInputExtraction, () -> true);
    }

    public SidedMachineItemHandler(ItemStackHandler inventory, int outputStart, boolean input, boolean output,
                                   BooleanSupplier allowInputExtraction, BooleanSupplier enabled) {
        this(inventory, inventory, outputStart, input, output, allowInputExtraction, enabled);
    }

    public SidedMachineItemHandler(ItemStackHandler inventory, IItemHandler inputInventory,
                                   int outputStart, boolean input, boolean output,
                                   BooleanSupplier allowInputExtraction, BooleanSupplier enabled) {
        this.inventory = inventory;
        this.inputInventory = inputInventory;
        this.outputStart = outputStart;
        this.input = input;
        this.output = output;
        this.allowInputExtraction = allowInputExtraction;
        this.enabled = enabled;
    }

    private int occupiedOutputSlots() {
        if (!output) return 0;
        int count = 0;
        for (int i = outputStart; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) count++;
        }
        return count;
    }

    @Override
    public int getSlots() {
        if (!enabled.getAsBoolean()) return 0;
        return (input ? inputInventory.getSlots() : 0) + occupiedOutputSlots()
                + (output && allowInputExtraction.getAsBoolean() ? 1 : 0);
    }

    private int map(int slot) {
        if (!enabled.getAsBoolean() || slot < 0 || slot >= getSlots()) return -1;
        int inputSlots = input ? inputInventory.getSlots() : 0;
        if (input && slot < inputSlots) return -slot - 2;
        int outputIndex = slot - inputSlots;
        if (output) {
            for (int i = outputStart; i < inventory.getSlots(); i++) {
                if (inventory.getStackInSlot(i).isEmpty()) continue;
                if (outputIndex-- == 0) return i;
            }
        }
        if (output && allowInputExtraction.getAsBoolean() && outputIndex == 0) return 0;
        return -1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        int mapped = map(slot);
        if (mapped == -1) return ItemStack.EMPTY;
        return mapped <= -2 ? inputInventory.getStackInSlot(-mapped - 2) : inventory.getStackInSlot(mapped);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        int mapped = map(slot);
        if (!input || mapped > -2) return stack;
        return inputInventory.insertItem(-mapped - 2, stack, simulate);
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
        if (mapped == -1) return 0;
        return mapped <= -2 ? inputInventory.getSlotLimit(-mapped - 2) : inventory.getSlotLimit(mapped);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        int mapped = map(slot);
        return input && mapped <= -2 && inputInventory.isItemValid(-mapped - 2, stack);
    }
}