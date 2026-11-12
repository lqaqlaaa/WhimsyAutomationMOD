package dev.whimsyautomation.menu;

import dev.whimsyautomation.block.entity.EnchantmentExtractorBlockEntity;
import dev.whimsyautomation.registry.ModBlocks;
import dev.whimsyautomation.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class EnchantmentExtractorMenu extends AbstractContainerMenu {
    private static final int PAGE_SIZE = 27;
    private static final int MACHINE_SLOT_COUNT = 218;
    private static final int OUTPUT_PAGES = 8;
    private final EnchantmentExtractorBlockEntity machine;
    private final ContainerLevelAccess access;
    private int syncedPage;
    private boolean syncedAutoMode;
    private boolean syncedSplitLevels;

    public EnchantmentExtractorMenu(int id, Inventory playerInventory, FriendlyByteBuf data) { this(id, playerInventory, data.readBlockPos()); }
    public EnchantmentExtractorMenu(int id, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.ENCHANTMENT_EXTRACTOR.get(), id);
        this.machine = playerInventory.player.level().getBlockEntity(pos) instanceof EnchantmentExtractorBlockEntity be ? be : null;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        if (machine != null) {
            addSlot(new net.neoforged.neoforge.items.SlotItemHandler(machine.getInventory(), 0, 18, 52));
            addSlot(new net.neoforged.neoforge.items.SlotItemHandler(machine.getBookInput(), 0, 18, 76));
            for (int i = 0; i < 216; i++) addPagedOutputSlot(machine.getInventory(), i);
        } else {
            addSlot(new Slot(new net.minecraft.world.SimpleContainer(1), 0, 18, 52));
            for (int i = 0; i < PAGE_SIZE; i++) addSlot(new Slot(new net.minecraft.world.SimpleContainer(1), 0, 48 + (i % 9) * 18, 42 + (i / 9) * 18));
        }
        addPlayerInventory(playerInventory, 26, 158);
        addDataSlot(new DataSlot() {
            @Override public int get() { return syncedPage; }
            @Override public void set(int value) { syncedPage = Math.max(0, Math.min(OUTPUT_PAGES - 1, value)); }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return machine != null && machine.isAutoMode() ? 1 : 0; }
            @Override public void set(int value) { syncedAutoMode = value != 0; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return machine != null && machine.isSplitLevels() ? 1 : 0; }
            @Override public void set(int value) { syncedSplitLevels = value != 0; }
        });
    }

    private void addPagedOutputSlot(ItemStackHandler handler, int outputIndex) {
        int actualSlot = 1 + outputIndex;
        int visibleIndex = outputIndex % PAGE_SIZE;
        int x = 48 + (visibleIndex % 9) * 18;
        int y = 42 + (visibleIndex / 9) * 18;
        addSlot(new net.neoforged.neoforge.items.SlotItemHandler(handler, actualSlot, x, y) {
            @Override public boolean isActive() {
                return outputIndex / PAGE_SIZE == syncedPage;
            }
        });
    }

    private void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, x + col * 18, y + 58));
    }

    public int getPage() { return syncedPage; }
    public boolean isAutoMode() { return syncedAutoMode; }
    public boolean isSplitLevels() { return syncedSplitLevels; }
    public int getPageCount() { return OUTPUT_PAGES; }

    @Override public boolean clickMenuButton(Player player, int buttonId) {
        if (machine == null || player.level().isClientSide) return false;
        if (buttonId == 0) machine.setAutoMode(!machine.isAutoMode());
        else if (buttonId == 1) machine.processOnce();
        else if (buttonId == 2) machine.setSplitLevels(!machine.isSplitLevels());
        else if (buttonId == 3) syncedPage = Math.max(0, syncedPage - 1);
        else if (buttonId == 4) syncedPage = Math.min(OUTPUT_PAGES - 1, syncedPage + 1);
        else return false;
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(source, MACHINE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = source.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)
                    ? moveItemStackTo(source, 1, 2, false)
                    : moveItemStackTo(source, 0, 1, false);
            if (!moved) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return machine != null && stillValid(access, player, ModBlocks.ENCHANTMENT_EXTRACTOR.get()); }
    public EnchantmentExtractorBlockEntity machine() { return machine; }
}
