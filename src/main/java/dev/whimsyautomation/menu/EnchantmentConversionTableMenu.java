package dev.whimsyautomation.menu;

import dev.whimsyautomation.block.entity.EnchantmentConversionTableBlockEntity;
import dev.whimsyautomation.registry.ModBlocks;
import dev.whimsyautomation.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class EnchantmentConversionTableMenu extends AbstractContainerMenu {
    private final EnchantmentConversionTableBlockEntity machine;
    private final ContainerLevelAccess access;
    private final BlockPos blockPos;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            int count = machine == null ? 0 : machine.getTotalOutputCount();
            if (index == 0) return count & 0xFFFF;
            if (index == 1) return (count >>> 16) & 0xFFFF;
            if (index == 2) return machine != null && machine.isAutoMode() ? 1 : 0;
            if (index == 3) return machine == null ? 64 & 0xFFFF : machine.getCopyCount() & 0xFFFF;
            if (index == 4) return machine == null ? 0 : (machine.getCopyCount() >>> 16) & 0xFFFF;
            if (index >= 5 && index < 11) return machine == null ? 0 : machine.getSideMode(Direction.values()[index - 5]);
            return 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) syncedOutputCount = (syncedOutputCount & 0xFFFF0000) | (value & 0xFFFF);
            else if (index == 1) syncedOutputCount = (syncedOutputCount & 0xFFFF) | ((value & 0xFFFF) << 16);
            else if (index == 2) syncedAutoMode = value != 0;
            else if (index == 3) syncedCopyCount = (syncedCopyCount & 0xFFFF0000) | (value & 0xFFFF);
            else if (index == 4) syncedCopyCount = (syncedCopyCount & 0xFFFF) | ((value & 0xFFFF) << 16);
            else if (index >= 5 && index < 11) syncedSideModes[index - 5] = Math.max(0, Math.min(2, value));
        }

        @Override
        public int getCount() { return 11; }
    };

    private boolean syncedAutoMode;
    private int syncedCopyCount = 64;
    private int syncedOutputCount;
    private final int[] syncedSideModes = new int[6];

    public EnchantmentConversionTableMenu(int id, Inventory inv, FriendlyByteBuf data) {
        this(id, inv, data.readBlockPos());
    }

    public EnchantmentConversionTableMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.ENCHANTMENT_CONVERSION_TABLE.get(), id);
        blockPos = pos;
        machine = inv.player.level().getBlockEntity(pos) instanceof EnchantmentConversionTableBlockEntity be ? be : null;
        access = ContainerLevelAccess.create(inv.player.level(), pos);
        addDataSlots(data);
        ItemStackHandler handler = machine == null ? new ItemStackHandler(2) : machine.getInventory();

        addSlot(new SlotItemHandler(handler, EnchantmentConversionTableBlockEntity.INPUT_SLOT, 28, 50));
        addSlot(new SlotItemHandler(handler, EnchantmentConversionTableBlockEntity.OUTPUT_SLOT, 132, 50) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 104 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, 8 + col * 18, 162));
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public EnchantmentConversionTableBlockEntity machine() {
        return machine;
    }

    public boolean isAutoMode() { return machine != null && machine.getLevel() != null && !machine.getLevel().isClientSide ? machine.isAutoMode() : syncedAutoMode; }
    public int getCopyCount() { return machine != null && machine.getLevel() != null && !machine.getLevel().isClientSide ? machine.getCopyCount() : syncedCopyCount; }
    public int getSideMode(int side) { return side < 0 || side >= 6 ? 0 : (machine != null && machine.getLevel() != null && !machine.getLevel().isClientSide ? machine.getSideMode(Direction.values()[side]) : syncedSideModes[side]); }

    public int getTotalOutputCount() {
        return machine != null && machine.getLevel() != null && !machine.getLevel().isClientSide
                ? machine.getTotalOutputCount() : syncedOutputCount;
    }

    @Override public boolean clickMenuButton(Player player, int buttonId) {
        if (machine == null || player.level().isClientSide) return false;
        if (buttonId == 0) machine.setAutoMode(!machine.isAutoMode());
        else if (buttonId >= 10 && buttonId <= 15) machine.cycleSideMode(Direction.values()[buttonId - 10]);
        else return false;
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();

        if (index == EnchantmentConversionTableBlockEntity.OUTPUT_SLOT) {
            // Never shift-click an oversized machine stack directly into player
            // inventory slots. Transfer at most one legal stack and debit the
            // exact accepted amount from the authoritative oversized inventory.
            int legalAmount = Math.min(source.getCount(), source.getMaxStackSize());
            // Use a count-adjusted copy without stripping or rewriting components.
            ItemStack moving = source.copyWithCount(legalAmount);
            if (!moveItemStackTo(moving, 2, slots.size(), true)) return ItemStack.EMPTY;
            int moved = legalAmount - moving.getCount();
            if (moved <= 0 || machine == null) return ItemStack.EMPTY;
            ItemStack extracted = machine.getInventory().extractItem(
                    EnchantmentConversionTableBlockEntity.OUTPUT_SLOT, moved, false);
            if (extracted.getCount() != moved) return ItemStack.EMPTY;
            return original.copyWithCount(moved);
        } else if (index >= 2) {
            if (!moveItemStackTo(source, EnchantmentConversionTableBlockEntity.INPUT_SLOT,
                    EnchantmentConversionTableBlockEntity.INPUT_SLOT + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return machine != null && stillValid(access, player, ModBlocks.ENCHANTMENT_CONVERSION_TABLE.get());
    }
}
