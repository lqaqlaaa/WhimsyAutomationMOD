package dev.whimsyautomation.menu;

import dev.whimsyautomation.block.entity.EnchantmentConversionTableBlockEntity;
import dev.whimsyautomation.registry.ModBlocks;
import dev.whimsyautomation.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class EnchantmentConversionTableMenu extends AbstractContainerMenu {
    private final EnchantmentConversionTableBlockEntity machine;
    private final ContainerLevelAccess access;
    private final BlockPos blockPos;

    public EnchantmentConversionTableMenu(int id, Inventory inv, FriendlyByteBuf data) {
        this(id, inv, data.readBlockPos());
    }

    public EnchantmentConversionTableMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.ENCHANTMENT_CONVERSION_TABLE.get(), id);
        blockPos = pos;
        machine = inv.player.level().getBlockEntity(pos) instanceof EnchantmentConversionTableBlockEntity be ? be : null;
        access = ContainerLevelAccess.create(inv.player.level(), pos);
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

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();

        if (index == EnchantmentConversionTableBlockEntity.OUTPUT_SLOT) {
            if (!moveItemStackTo(source, 2, slots.size(), true)) return ItemStack.EMPTY;
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
