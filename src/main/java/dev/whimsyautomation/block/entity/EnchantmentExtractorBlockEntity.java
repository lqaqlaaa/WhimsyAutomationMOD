package dev.whimsyautomation.block.entity;

import dev.whimsyautomation.menu.EnchantmentExtractorMenu;
import dev.whimsyautomation.registry.ModBlockEntities;
import dev.whimsyautomation.util.SidedMachineItemHandler;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Input slot 0; output slots 1-216. Each enchantment level can become a level-I book. */
public final class EnchantmentExtractorBlockEntity extends BlockEntity implements MenuProvider {
    public static final int OUTPUT_START = 1;
    public static final int OUTPUT_SLOTS = 216;
    private int workTicks;
    private boolean autoMode = false;
    private boolean splitLevels = false;
    private boolean autoExtractionPaused;

    private boolean internalInventoryUpdate;

    public boolean isAutoMode() { return autoMode; }
    public void setAutoMode(boolean enabled) { autoMode = enabled; setChanged(); }
    public boolean isSplitLevels() { return splitLevels; }
    public void setSplitLevels(boolean enabled) { splitLevels = enabled; setChanged(); }
    /** Rebuilds the virtual enchanted-book outputs; the input item is never consumed. */
    public void processOnce() { if (level != null && !level.isClientSide) refreshGeneratedSlots(); }
    public void dropAllContents() {
        if (level == null || level.isClientSide) return;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    private final ItemStackHandler inventory = new ItemStackHandler(OUTPUT_START + OUTPUT_SLOTS) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (slot == 0) return !getStackEnchantments(stack).isEmpty();
            return stack.is(Items.ENCHANTED_BOOK)
                    && !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
                    && canApplyBook(getStackInSlot(0), stack);
        }
        @Override protected void onContentsChanged(int slot) {
            if (internalInventoryUpdate) { setChanged(); return; }
            if (slot == 0) {
                refreshGeneratedSlots();
            } else {
                ItemStack offeredBook = getStackInSlot(slot);
                if (!offeredBook.isEmpty() && offeredBook.is(Items.ENCHANTED_BOOK)
                        && applyBookToInput(offeredBook)) {
                    internalInventoryUpdate = true;
                    setStackInSlot(slot, ItemStack.EMPTY);
                    internalInventoryUpdate = false;
                }
                refreshGeneratedSlots();
            }
            setChanged();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack extracted = super.extractItem(slot, amount, simulate);
            if (!simulate && !internalInventoryUpdate && slot > 0 && !extracted.isEmpty()) {
                removeGeneratedBook(extracted);
            }
            return extracted;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
            // Older saves stored only 54 output slots. Keep their item data, but
            // force the current capacity before ItemStackHandler resizes itself.
            CompoundTag migrated = tag.copy();
            migrated.putInt("Size", OUTPUT_START + OUTPUT_SLOTS);
            super.deserializeNBT(registries, migrated);
        }
    };

    private final IItemHandler topHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, false);
    private final IItemHandler bottomHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, false, true);
    private final IItemHandler sideHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, false);
    private final IItemHandler unsidedHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, true);

    public EnchantmentExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENCHANTMENT_EXTRACTOR.get(), pos, state);
    }

    public ItemStackHandler getInventory() { return inventory; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.whimsy_automation.enchantment_extractor");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new EnchantmentExtractorMenu(id, playerInventory,
                new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(worldPosition));
    }

    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        if (side == null) return unsidedHandler;
        if (side == Direction.DOWN) return bottomHandler;
        if (side == Direction.UP) return topHandler;
        return sideHandler;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnchantmentExtractorBlockEntity machine) {
        if (level.isClientSide) return;
        if (!machine.autoMode) return;
        if (++machine.workTicks < 10) return;
        machine.workTicks = 0;
        machine.refreshGeneratedSlots();
    }

    public boolean canApplyBook(ItemStack item, ItemStack book) {
        if (item.isEmpty() || !book.is(Items.ENCHANTED_BOOK)) return false;
        ItemEnchantments stored = book.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return false;

        ItemEnchantments existing = getStackEnchantments(item);
        List<Holder<Enchantment>> additions = new ArrayList<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int bookLevel = entry.getIntValue();
            // Follow EnchantmentCustomTable: do not reject an enchanted book merely
            // because its enchantment conflicts with another enchantment on the item.
            if (bookLevel <= 0 || !enchantment.value().isSupportedItem(item)) return false;
            int oldLevel = existing.getLevel(enchantment);
            int mergedLevel = oldLevel == 0 ? bookLevel
                    : oldLevel == bookLevel ? oldLevel + 1 : oldLevel + bookLevel;
            if (mergedLevel <= 0) return false;
            additions.add(enchantment);
        }
        return !additions.isEmpty();
    }

    private boolean applyBookToInput(ItemStack book) {
        ItemStack item = inventory.getStackInSlot(0);
        if (!canApplyBook(item, book)) return false;

        ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments existing = getStackEnchantments(item);
        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(existing);

        // Match the reference table: new enchantments keep their level; duplicate
        // enchantments merge by adding levels, subject to the normal max-level rule.
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
            int oldLevel = existing.getLevel(entry.getKey());
            int addedLevel = entry.getIntValue();
            merged.set(entry.getKey(), oldLevel == 0 ? addedLevel
                    : oldLevel == addedLevel ? oldLevel + 1 : oldLevel + addedLevel);
        }

        internalInventoryUpdate = true;
        if (item.is(Items.ENCHANTED_BOOK)) item.set(DataComponents.STORED_ENCHANTMENTS, merged.toImmutable());
        else item.set(DataComponents.ENCHANTMENTS, merged.toImmutable());
        inventory.setStackInSlot(0, item);
        internalInventoryUpdate = false;
        setChanged();
        return true;
    }

    private static ItemEnchantments getStackEnchantments(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK)
                ? stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
    }
    private void refreshGeneratedSlots() {
        if (level == null || level.isClientSide || internalInventoryUpdate) return;
        ItemStack input = inventory.getStackInSlot(0);
        ItemEnchantments enchantments = getStackEnchantments(input);
        List<ItemStack> generated = new ArrayList<>();

        if (!input.isEmpty() && !enchantments.isEmpty()) {
            boolean splitSingleBook = splitLevels && input.is(Items.ENCHANTED_BOOK) && enchantments.size() == 1;
            if (splitSingleBook) {
                Object2IntMap.Entry<Holder<Enchantment>> entry = enchantments.entrySet().iterator().next();
                for (int level : splitSingleEnchantmentLevels(entry.getIntValue())) {
                    generated.add(createBook(entry.getKey(), level));
                }
            } else {
                for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                    generated.add(createBook(entry.getKey(), entry.getIntValue()));
                }
            }
        }

        internalInventoryUpdate = true;
        for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++) {
            ItemStack next = slot - OUTPUT_START < generated.size()
                    ? generated.get(slot - OUTPUT_START) : ItemStack.EMPTY;
            inventory.setStackInSlot(slot, next);
        }
        internalInventoryUpdate = false;
        setChanged();
    }

    private ItemStack createBook(Holder<Enchantment> enchantment, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(enchantment, level);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return book;
    }

    /** Called by the menu after a shift-click has actually transferred a generated book. */
    public boolean consumeGeneratedBook(ItemStack book) {
        if (book.isEmpty() || !book.is(Items.ENCHANTED_BOOK)) return false;
        ItemEnchantments available = getStackEnchantments(inventory.getStackInSlot(0));
        ItemEnchantments requested = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (requested.isEmpty()) return false;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : requested.entrySet()) {
            if (available.getLevel(entry.getKey()) < entry.getIntValue()) return false;
        }
        removeGeneratedBook(book);
        return true;
    }

    private void removeGeneratedBook(ItemStack book) {
        if (level == null || level.isClientSide || book.isEmpty()) return;
        ItemStack input = inventory.getStackInSlot(0);
        if (input.isEmpty()) return;
        ItemEnchantments current = getStackEnchantments(input);
        ItemEnchantments removed = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (removed.isEmpty()) return;

        ItemEnchantments.Mutable remaining = new ItemEnchantments.Mutable(current);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : removed.entrySet()) {
            int currentLevel = current.getLevel(entry.getKey());
            if (currentLevel < entry.getIntValue()) return;
            remaining.set(entry.getKey(), currentLevel - entry.getIntValue());
        }

        internalInventoryUpdate = true;
        if (input.is(Items.ENCHANTED_BOOK)) input.set(DataComponents.STORED_ENCHANTMENTS, remaining.toImmutable());
        else input.set(DataComponents.ENCHANTMENTS, remaining.toImmutable());
        inventory.setStackInSlot(0, input);
        internalInventoryUpdate = false;
        refreshGeneratedSlots();
    }

    private static List<Integer> splitSingleEnchantmentLevels(int sourceLevel) {
        if (sourceLevel <= 1) return List.of();
        List<Integer> levels = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.LinkedHashSet<>();
        int remaining = sourceLevel;
        while (remaining > 1) {
            int levelToAdd = remaining / 2;
            if (seen.add(levelToAdd)) levels.add(levelToAdd);
            remaining -= levelToAdd;
        }
        return List.copyOf(levels);
    }

    private int[] findEmptyOutputSlots(int count) {
        int[] slots = new int[count];
        int found = 0;
        for (int slot = OUTPUT_START; slot < inventory.getSlots() && found < count; slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) slots[found++] = slot;
        }
        return found == count ? slots : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("WorkTicks", workTicks);
        tag.putBoolean("AutoMode", autoMode);
        tag.putBoolean("SplitLevels", splitLevels);
        tag.putBoolean("AutoExtractionPaused", autoExtractionPaused);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        autoExtractionPaused = tag.getBoolean("AutoExtractionPaused");
        if (level != null && !level.isClientSide) refreshGeneratedSlots();
        workTicks = tag.getInt("WorkTicks");
        autoMode = tag.getBoolean("AutoMode");
        splitLevels = tag.getBoolean("SplitLevels");
    }
}
