package dev.whimsyautomation.menu;

import dev.whimsyautomation.block.entity.EnchantmentExtractorBlockEntity;
import dev.whimsyautomation.registry.ModBlocks;
import dev.whimsyautomation.registry.ModMenus;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class EnchantmentExtractorMenu extends AbstractContainerMenu {
    private final EnchantmentExtractorBlockEntity machine;
    private final ContainerLevelAccess access;

    public EnchantmentExtractorMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(id, playerInventory, data.readBlockPos());
    }

    public EnchantmentExtractorMenu(int id, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.ENCHANTMENT_EXTRACTOR.get(), id);
        this.machine = playerInventory.player.level().getBlockEntity(pos) instanceof EnchantmentExtractorBlockEntity be ? be : null;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);

        if (machine != null) {
            addSlot(new SlotItemHandler(machine.getInventory(), 0, 18, 52));
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new SlotItemHandler(machine.getInventory(), 1 + row * 9 + col, 48 + col * 18, 42 + row * 18));
                }
            }
        } else {
            addSlot(new Slot(new net.minecraft.world.SimpleContainer(1), 0, 18, 52));
            for (int row = 0; row < 6; row++) for (int col = 0; col < 9; col++)
                addSlot(new Slot(new net.minecraft.world.SimpleContainer(54), row * 9 + col, 48 + col * 18, 42 + row * 18));
        }

        addPlayerInventory(playerInventory, 26, 174);
    }

    private void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, x + col * 18, y + 58));
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (machine == null || player.level().isClientSide) return false;
        if (buttonId == 0) machine.setAutoMode(!machine.isAutoMode());
        else if (buttonId == 1) machine.processOnce();
        else if (buttonId == 2) machine.setSplitLevels(!machine.isSplitLevels());
        else return false;
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        int machineSlots = 55;
        if (index < machineSlots) {
            if (!moveItemStackTo(source, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return machine != null && stillValid(access, player, ModBlocks.ENCHANTMENT_EXTRACTOR.get());
    }

    public EnchantmentExtractorBlockEntity machine() {
        return machine;
    }
}
