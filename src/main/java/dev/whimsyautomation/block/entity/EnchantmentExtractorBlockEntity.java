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
import net.minecraft.core.NonNullList;
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
    private static final int PAGE_SIZE = 27;
    private static final int OUTPUT_STACK_LIMIT = Integer.MAX_VALUE;
    private int workTicks;
    private boolean autoMode = false;
    private boolean splitLevels = false;
    private boolean combinedOutput = false;
    private boolean autoExtractionPaused;

    private boolean internalInventoryUpdate;
    private boolean extractingGeneratedOutput;

    public boolean isAutoMode() { return autoMode; }
    public void setAutoMode(boolean enabled) { autoMode = enabled; setChanged(); }
    public boolean isSplitLevels() { return splitLevels; }
    public boolean isCombinedOutput() { return combinedOutput; }
    public int getOutputMode() { return combinedOutput ? 0 : (splitLevels ? 2 : 1); }
    public void setOutputMode(int mode) {
        int normalized = Math.floorMod(mode, 3);
        combinedOutput = normalized == 0;
        splitLevels = normalized == 2;
        refreshGeneratedSlots();
        setChanged();
    }
    public void setSplitLevels(boolean enabled) {
        if (splitLevels == enabled && !combinedOutput) return;
        splitLevels = enabled;
        combinedOutput = false;
        // The split option is a display/extraction rule, not an automation task.
        // Rebuild outputs immediately even when automatic mode is disabled.
        refreshGeneratedSlots();
        setChanged();
    }
    /** Rebuilds the virtual enchanted-book outputs; the input item is never consumed. */
    public void processOnce() { if (level != null && !level.isClientSide) refreshGeneratedSlots(); }
    public void dropAllContents() {
        if (level == null || level.isClientSide) return;
        // Output books are virtual projections of the enchantments still stored
        // on the input item. Drop only the input; dropping both would duplicate
        // every enchantment that had not yet been extracted.
        ItemStack input = inventory.getStackInSlot(0);
        if (!input.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, input);
        }
        internalInventoryUpdate = true;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        internalInventoryUpdate = false;
    }

    private final DynamicItemStackHandler inventory = new DynamicItemStackHandler(OUTPUT_START + PAGE_SIZE * 10) {
        @Override public int getSlotLimit(int slot) { return slot == 0 ? 1 : OUTPUT_STACK_LIMIT; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (slot == 0) return !getStackEnchantments(stack).isEmpty();
            // Output slots are a virtual projection of the input item's enchantments.
            // Restoration is handled explicitly by the menu's page handler; allowing
            // direct writes here lets stale/client slot updates overwrite the projection.
            return false;
        }
        @Override protected void onContentsChanged(int slot) {
            if (internalInventoryUpdate || extractingGeneratedOutput) { setChanged(); return; }
            // Menu slot synchronization also writes stacks into the client-side
            // ItemStackHandler. The client must never apply an incoming enchanted
            // book to the input or clear the synchronized generated output slot.
            if (level == null || level.isClientSide) { setChanged(); return; }
            if (slot == 0) {
                refreshGeneratedSlots();
            }
            // Slots 1+ are generated, read-only projections. A book can only be
            // restored through EnchantmentExtractorMenu.pageHandler.insertItem(),
            // which consumes exactly the amount actually accepted. Never infer a
            // player insertion from a raw slot mutation: that path used to apply
            // one book and erase an entire stacked input, or rebuild stale output.
            setChanged();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot <= 0 || amount <= 0) return super.extractItem(slot, amount, simulate);
            ItemStack present = getStackInSlot(slot);
            if (present.isEmpty()) return ItemStack.EMPTY;
            int requestedCount = Math.min(amount, present.getCount());
            ItemStack requested = present.copyWithCount(requestedCount);
            // Validate against the authoritative input before the output slot is
            // touched. A stale client slot can therefore never mint a book.
            if (!canConsumeGeneratedBook(slot, requested)) return ItemStack.EMPTY;
            if (simulate) return super.extractItem(slot, requestedCount, true);
            extractingGeneratedOutput = true;
            ItemStack extracted;
            try {
                extracted = super.extractItem(slot, requestedCount, false);
            } finally {
                extractingGeneratedOutput = false;
            }
            if (!extracted.isEmpty()) removeGeneratedBook(slot, extracted, true);
            return extracted;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
            // Preserve the saved dynamic output size. Legacy saves keep their
            // original contents and are resized again when outputs are rebuilt.
            super.deserializeNBT(registries, tag);
        }
    };

    private static class DynamicItemStackHandler extends ItemStackHandler {
        private DynamicItemStackHandler(int size) { super(size); }

        @Override protected int getStackLimit(int slot, ItemStack stack) {
            return slot == 0 ? 1 : OUTPUT_STACK_LIMIT;
        }

        private void resize(int size) {
            int targetSize = Math.max(OUTPUT_START + PAGE_SIZE * 10, size);
            if (targetSize == stacks.size()) return;
            NonNullList<ItemStack> resized = NonNullList.withSize(targetSize, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(stacks.size(), targetSize); i++) {
                resized.set(i, stacks.get(i));
            }
            stacks = resized;
        }
    }

    private final IItemHandler topHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, false);
    private final IItemHandler bottomHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, false, true,
            () -> getStackEnchantments(inventory.getStackInSlot(0)).isEmpty());
    private final IItemHandler sideHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, false);
    private final IItemHandler unsidedHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, true);

    public EnchantmentExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENCHANTMENT_EXTRACTOR.get(), pos, state);
    }

    public ItemStackHandler getInventory() { return inventory; }

    /** Grows the dynamic output inventory without moving or replacing existing stacks. */
    public void ensureOutputInventorySize(int size) {
        if (size > inventory.getSlots()) {
            inventory.resize(size);
            setChanged();
        }
    }

    /** Allocated output positions, including intentionally emptied stable slots. */
    public int getGeneratedOutputCount() {
        return Math.max(0, inventory.getSlots() - OUTPUT_START);
    }

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
        // Generated output is derived when the input or output mode changes.
        // Rebuilding it on a timer would refill deliberately emptied output slots.
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
            // Extracted enchantments must be restorable to the same item, even if
            // the item/enchantment pair is normally rejected by vanilla anvil rules.
            if (bookLevel <= 0) return false;
            int oldLevel = existing.getLevel(enchantment);
            int mergedLevel = oldLevel == 0 ? bookLevel
                    : oldLevel == bookLevel ? oldLevel + 1 : oldLevel + bookLevel;
            if (mergedLevel <= 0) return false;
            additions.add(enchantment);
        }
        return !additions.isEmpty();
    }

    /** Restores an extracted enchanted book to the machine's current input item. */
    public boolean restoreBook(ItemStack book) {
        if (level == null || level.isClientSide || book.isEmpty()) return false;
        // One handler insertion represents exactly one physical enchanted book.
        // The caller owns stack shrinking and may repeat for the remaining books.
        return applyBookToInput(book);
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
            if (combinedOutput) {
                // A combined result is one physical enchanted book.
                generated.add(createCombinedBook(enchantments));
            } else {
                for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                    int enchantmentLevel = entry.getIntValue();
                    if (enchantmentLevel <= 0) continue;
                    if (splitLevels) {
                        // Level-I books are grouped into legal stacks of at most 99.
                        // A level-150 enchantment therefore becomes stacks 99 + 51,
                        // not 150 separate occupied slots.
                        int remainingLevels = enchantmentLevel;
                        while (remainingLevels > 0) {
                            int stackCount = Math.min(BOOK_STACK_COMPONENT_LIMIT, remainingLevels);
                            ItemStack levelOneBooks = createBook(entry.getKey(), 1);
                            levelOneBooks.setCount(stackCount);
                            generated.add(levelOneBooks);
                            remainingLevels -= stackCount;
                        }
                    } else {
                        generated.add(createBook(entry.getKey(), enchantmentLevel));
                    }
                }
            }
        }

        internalInventoryUpdate = true;
        int requiredSize = Math.max(OUTPUT_START + 27 * 10, OUTPUT_START + generated.size());
        inventory.resize(requiredSize);
        // Clear the entire previous projection first. Without this, changing
        // from split-level mode to combined/separate mode leaves ghost books
        // behind in old output slots and can duplicate extracted enchantments.
        for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        for (int index = 0; index < generated.size(); index++) {
            inventory.setStackInSlot(OUTPUT_START + index, generated.get(index));
        }
        internalInventoryUpdate = false;
        setChanged();
    }

    private ItemStack createCombinedBook(ItemEnchantments enchantments) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            stored.set(entry.getKey(), entry.getIntValue());
        }
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return book;
    }

    private ItemStack createBook(Holder<Enchantment> enchantment, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        if (level == 1) book.set(DataComponents.MAX_STACK_SIZE, BOOK_STACK_COMPONENT_LIMIT);
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(enchantment, level);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return book;
    }

    /** Validate a generated output against the current authoritative input item. */
    public boolean canConsumeGeneratedBook(int outputSlot, ItemStack book) {
        if (outputSlot < OUTPUT_START || outputSlot >= inventory.getSlots()
                || book.isEmpty() || !book.is(Items.ENCHANTED_BOOK)) return false;
        ItemStack currentOutput = inventory.getStackInSlot(outputSlot);
        if (currentOutput.isEmpty() || !ItemStack.isSameItemSameComponents(currentOutput, book)
                || book.getCount() > currentOutput.getCount()) return false;
        ItemEnchantments available = getStackEnchantments(inventory.getStackInSlot(0));
        ItemEnchantments requested = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (requested.isEmpty()) return false;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : requested.entrySet()) {
            long requestedLevel = (long) entry.getIntValue() * book.getCount();
            if (available.getLevel(entry.getKey()) < requestedLevel) return false;
        }
        return true;
    }

    /** Called by the menu only after the requested amount was transferred. */
    public boolean consumeGeneratedBook(int outputSlot, ItemStack book) {
        if (!canConsumeGeneratedBook(outputSlot, book)) return false;
        return removeGeneratedBook(outputSlot, book, false);
    }

    private boolean removeGeneratedBook(int outputSlot, ItemStack book, boolean outputAlreadyExtracted) {
        if (level == null || level.isClientSide || book.isEmpty()) return false;
        ItemStack input = inventory.getStackInSlot(0);
        if (input.isEmpty()) return false;
        ItemEnchantments current = getStackEnchantments(input);
        ItemEnchantments removed = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (removed.isEmpty()) return false;

        ItemEnchantments.Mutable remaining = new ItemEnchantments.Mutable(current);
        int extractedCount = book.getCount();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : removed.entrySet()) {
            long totalRemoved = (long) entry.getIntValue() * extractedCount;
            int currentLevel = current.getLevel(entry.getKey());
            if (totalRemoved > currentLevel) return false;
            remaining.set(entry.getKey(), currentLevel - (int) totalRemoved);
        }

        internalInventoryUpdate = true;
        if (input.is(Items.ENCHANTED_BOOK)) input.set(DataComponents.STORED_ENCHANTMENTS, remaining.toImmutable());
        else input.set(DataComponents.ENCHANTMENTS, remaining.toImmutable());
        inventory.setStackInSlot(0, input);
        if (!outputAlreadyExtracted) {
            ItemStack remainingOutput = inventory.getStackInSlot(outputSlot).copy();
            remainingOutput.shrink(book.getCount());
            inventory.setStackInSlot(outputSlot, remainingOutput);
        }
        internalInventoryUpdate = false;
        setChanged();
        return true;
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
        tag.putBoolean("CombinedOutput", combinedOutput);
        tag.putBoolean("AutoExtractionPaused", autoExtractionPaused);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        autoExtractionPaused = tag.getBoolean("AutoExtractionPaused");
        workTicks = tag.getInt("WorkTicks");
        autoMode = tag.getBoolean("AutoMode");
        splitLevels = tag.getBoolean("SplitLevels");
        combinedOutput = tag.getBoolean("CombinedOutput");
        // Rebuild only after mode flags have been restored from disk.
        if (level != null && !level.isClientSide) refreshGeneratedSlots();
    }
}
