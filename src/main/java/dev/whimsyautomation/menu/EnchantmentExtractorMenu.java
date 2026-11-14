package dev.whimsyautomation.menu;

import dev.whimsyautomation.block.entity.EnchantmentExtractorBlockEntity;
import dev.whimsyautomation.registry.ModBlocks;
import dev.whimsyautomation.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;
import java.util.Arrays;

public final class EnchantmentExtractorMenu extends AbstractContainerMenu {
    private static final int PAGE_SIZE = 27;
    private static final int MIN_OUTPUT_PAGES = 10;
    private static final int OUTPUT_START = 1;
    private static final int OUTPUT_MENU_START = 1;
    private static final int MACHINE_SLOT_COUNT = OUTPUT_MENU_START + PAGE_SIZE;
    private final EnchantmentExtractorBlockEntity machine;
    private final Level level;
    private final ItemStack[] clientPageStacks = new ItemStack[PAGE_SIZE];
    private final ContainerLevelAccess access;
    private final IItemHandler pageHandler;
    private int syncedPage;
    private int syncedOutputSlotCount = OUTPUT_START + PAGE_SIZE * MIN_OUTPUT_PAGES;
    private boolean syncedAutoMode;
    private int syncedOutputMode = 1;

    public EnchantmentExtractorMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(id, playerInventory, data.readBlockPos());
    }

    public EnchantmentExtractorMenu(int id, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.ENCHANTMENT_EXTRACTOR.get(), id);
        this.level = playerInventory.player.level();
        this.machine = level.getBlockEntity(pos) instanceof EnchantmentExtractorBlockEntity be ? be : null;
        Arrays.fill(clientPageStacks, ItemStack.EMPTY);
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        this.pageHandler = new IItemHandlerModifiable() {
            private int actualSlot(int pageSlot) {
                return OUTPUT_START + getPage() * PAGE_SIZE + pageSlot;
            }

            @Override public int getSlots() { return PAGE_SIZE; }

            @Override public void setStackInSlot(int slot, ItemStack stack) {
                if (machine == null || slot < 0 || slot >= PAGE_SIZE) return;
                if (level.isClientSide) {
                    clientPageStacks[slot] = stack.copy();
                    return;
                }
                int actual = actualSlot(slot);
                machine.ensureOutputInventorySize(actual + 1);
                machine.getInventory().setStackInSlot(actual, stack);
            }

            @Override public ItemStack getStackInSlot(int slot) {
                if (machine == null || slot < 0 || slot >= PAGE_SIZE) return ItemStack.EMPTY;
                if (level.isClientSide) return clientPageStacks[slot];
                int actual = actualSlot(slot);
                return actual < machine.getInventory().getSlots()
                        ? machine.getInventory().getStackInSlot(actual) : ItemStack.EMPTY;
            }

            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (machine == null || slot < 0 || slot >= PAGE_SIZE) return stack;
                if (level.isClientSide) return stack;
                int actual = actualSlot(slot);
                machine.ensureOutputInventorySize(actual + 1);
                return machine.getInventory().insertItem(actual, stack, simulate);
            }

            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (machine == null || slot < 0 || slot >= PAGE_SIZE) return ItemStack.EMPTY;
                if (level.isClientSide) {
                    ItemStack cached = clientPageStacks[slot];
                    if (cached.isEmpty()) return ItemStack.EMPTY;
                    ItemStack extracted = cached.split(amount);
                    if (cached.isEmpty()) clientPageStacks[slot] = ItemStack.EMPTY;
                    return extracted;
                }
                int actual = actualSlot(slot);
                return actual < machine.getInventory().getSlots()
                        ? machine.getInventory().extractItem(actual, amount, simulate) : ItemStack.EMPTY;
            }

            @Override public int getSlotLimit(int slot) {
                if (machine == null || slot < 0 || slot >= PAGE_SIZE) return 64;
                int actual = actualSlot(slot);
                return actual < machine.getInventory().getSlots()
                        ? machine.getInventory().getSlotLimit(actual) : 64;
            }

            @Override public boolean isItemValid(int slot, ItemStack stack) {
                if (machine == null || slot < 0 || slot >= PAGE_SIZE) return false;
                int actual = actualSlot(slot);
                machine.ensureOutputInventorySize(actual + 1);
                return machine.getInventory().isItemValid(actual, stack);
            }
        };

        addSlot(new net.neoforged.neoforge.items.SlotItemHandler(
                machine != null ? machine.getInventory() : new net.neoforged.neoforge.items.ItemStackHandler(1),
                0, 8, 107));
        for (int i = 0; i < PAGE_SIZE; i++) addPagedOutputSlot(i);
        addPlayerInventory(playerInventory, 8, 139);

        addDataSlot(new DataSlot() {
            @Override public int get() { return syncedPage; }
            @Override public void set(int value) {
                // The page DataSlot is sent before the output-size DataSlot.
                // Clamping here can discard page 2 using the previous page count,
                // making the old page appear empty until another page update.
                syncedPage = Math.max(0, value);
            }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return machine == null ? OUTPUT_START + 1 : Math.min(32767, machine.getInventory().getSlots()); }
            @Override public void set(int value) {
                syncedOutputSlotCount = Math.max(OUTPUT_START + PAGE_SIZE * MIN_OUTPUT_PAGES, value);
                if (machine != null) machine.ensureOutputInventorySize(syncedOutputSlotCount);
            }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return machine != null && machine.isAutoMode() ? 1 : 0; }
            @Override public void set(int value) { syncedAutoMode = value != 0; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return machine != null ? machine.getOutputMode() : 1; }
            @Override public void set(int value) { syncedOutputMode = Math.max(0, Math.min(2, value)); }
        });
    }

    private void addPagedOutputSlot(int outputIndex) {
        int x = 8 + (outputIndex % 9) * 18;
        int y = 17 + (outputIndex / 9) * 18;
        addSlot(new SlotItemHandler(pageHandler, outputIndex, x, y));
    }

    private void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, x + col * 18, y + 58));
    }

    private int getActualOutputSlot(int menuSlotIndex) {
        return OUTPUT_START + getPage() * PAGE_SIZE + (menuSlotIndex - OUTPUT_MENU_START);
    }

    public int getPage() { return Math.min(syncedPage, getPageCount() - 1); }
    public boolean isAutoMode() { return syncedAutoMode; }
    public boolean isSplitLevels() { return syncedOutputMode == 2; }
    public int getOutputMode() { return syncedOutputMode; }
    public int getPageCount() {
        int outputSlots = machine == null || (machine.getLevel() != null && machine.getLevel().isClientSide)
                ? syncedOutputSlotCount - OUTPUT_START
                : machine.getGeneratedOutputCount();
        return Math.max(1, (outputSlots + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override public boolean clickMenuButton(Player player, int buttonId) {
        if (machine == null || player.level().isClientSide) return false;
        if (buttonId == 0) machine.setAutoMode(!machine.isAutoMode());
        else if (buttonId == 1) machine.processOnce();
        else if (buttonId == 2) machine.setOutputMode((machine.getOutputMode() + 1) % 3);
        else if (buttonId == 3) syncedPage = Math.max(0, syncedPage - 1);
        else if (buttonId == 4) syncedPage = Math.min(getPageCount() - 1, syncedPage + 1);
        else return false;
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();

        if (index >= OUTPUT_MENU_START && index < MACHINE_SLOT_COUNT) {
            int actualOutputSlot = getActualOutputSlot(index);
            ItemStack moving = source.copy();
            if (!moveItemStackTo(moving, MACHINE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
            int movedCount = copy.getCount() - moving.getCount();
            if (movedCount <= 0) return ItemStack.EMPTY;
            machine.consumeGeneratedBook(actualOutputSlot, copy.copyWithCount(movedCount));
            return copy.copyWithCount(movedCount);
        }

        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(source, MACHINE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = source.is(Items.ENCHANTED_BOOK)
                    ? moveItemStackTo(source, OUTPUT_MENU_START, MACHINE_SLOT_COUNT, false)
                    : moveItemStackTo(source, 0, 1, false);
            if (!moved) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) {
        return machine != null && stillValid(access, player, ModBlocks.ENCHANTMENT_EXTRACTOR.get());
    }

    public EnchantmentExtractorBlockEntity machine() { return machine; }
}
