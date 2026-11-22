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
public final class EnchantmentExtractorBlockEntity extends BlockEntity implements MenuProvider, SideConfigurableBlockEntity {
        
    
    public static final int OUTPUT_START = 1;
    private static final int PAGE_SIZE = 27;
    private static final int OUTPUT_STACK_LIMIT = Integer.MAX_VALUE;
    private static final int BOOK_STACK_COMPONENT_LIMIT = 99;
    private int workTicks;
    private boolean autoMode = false;
    private boolean splitLevels = false;
    private boolean combinedOutput = false;
    private boolean autoExtractionPaused;
    // 0=disabled, 1=input, 2=output; Direction ordinal order.
    private final int[] sideModes = new int[] {0, 0, 0, 0, 0, 0};
    private final IItemHandler[] sidedHandlers = new IItemHandler[6];

    private final DynamicInputBuffer inputBuffer = new DynamicInputBuffer(18);
    private final IItemHandler automationInputHandler = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        // This is an insertion-only virtual inlet. The actual contents are shown
        // in the paged buffer UI, not in this single automation-facing slot.
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot == 0 ? inputBuffer.insertQueued(stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return slot == 0 ? 64 : 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && !stack.isEmpty(); }
    };

    private boolean internalInventoryUpdate;
    private boolean extractingGeneratedOutput;
    private boolean persistentOutputMode;

    public boolean isAutoMode() { return autoMode; }
    public boolean isPersistentOutputMode() { return persistentOutputMode; }
    public void setAutoMode(boolean enabled) {
        if (autoMode == enabled) return;
        autoMode = enabled;
        if (enabled) {
            internalInventoryUpdate = true;
            for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++) inventory.setStackInSlot(slot, ItemStack.EMPTY);
            internalInventoryUpdate = false;
            persistentOutputMode = true;
        } else if (!hasStoredOutputs()) {
            persistentOutputMode = false;
            refreshGeneratedSlots();
        }
        setChanged();
    }
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
    /** Manual mode refreshes its preview; automatic mode performs a real extraction cycle. */
    public void processOnce() {
        if (level == null || level.isClientSide) return;
        if (autoMode) processAutomaticInput(); else refreshGeneratedSlots();
    }
    private boolean hasStoredOutputs() {
        for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++)
            if (!inventory.getStackInSlot(slot).isEmpty()) return true;
        return false;
    }
    private void addToOutputPlan(List<ItemStack> plan, ItemStack incoming) {
        if (incoming.isEmpty()) return;
        ItemStack template = incoming.copyWithCount(1);
        int limit = Math.max(1, Math.min(99, template.getMaxStackSize()));
        int remaining = incoming.getCount();
        for (ItemStack present : plan) {
            if (remaining <= 0) break;
            if (present.isEmpty() || !ItemStack.isSameItemSameComponents(present, template)) continue;
            int room = Math.max(0, Math.min(99, present.getMaxStackSize()) - present.getCount());
            int moved = Math.min(room, remaining);
            if (moved > 0) { present.grow(moved); remaining -= moved; }
        }
        for (int i = 0; i < plan.size() && remaining > 0; i++) {
            if (!plan.get(i).isEmpty()) continue;
            int moved = Math.min(limit, remaining);
            plan.set(i, template.copyWithCount(moved));
            remaining -= moved;
        }
        while (remaining > 0) {
            int moved = Math.min(limit, remaining);
            plan.add(template.copyWithCount(moved));
            remaining -= moved;
        }
    }
    /** Atomically converts one input into physical enchanted books and its stripped source item. */
    private void processAutomaticInput() {
        ItemStack input = inventory.getStackInSlot(0);
        if (input.isEmpty()) return;
        ItemEnchantments enchantments = getStackEnchantments(input);
        List<ItemStack> plan = new ArrayList<>();
        for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++) plan.add(inventory.getStackInSlot(slot).copy());
        if (!enchantments.isEmpty()) {
            if (combinedOutput) addToOutputPlan(plan, createCombinedBook(enchantments));
            else for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                int lvl = entry.getIntValue();
                if (lvl <= 0) continue;
                if (splitLevels) {
                    while (lvl > 0) {
                        int count = Math.min(BOOK_STACK_COMPONENT_LIMIT, lvl);
                        ItemStack book = createBook(entry.getKey(), 1);
                        book.setCount(count);
                        addToOutputPlan(plan, book);
                        lvl -= count;
                    }
                } else addToOutputPlan(plan, createBook(entry.getKey(), lvl));
            }
        }
        ItemStack stripped = input.copy();
        if (stripped.is(Items.ENCHANTED_BOOK)) stripped.remove(DataComponents.STORED_ENCHANTMENTS);
        else stripped.remove(DataComponents.ENCHANTMENTS);
        addToOutputPlan(plan, stripped);
        internalInventoryUpdate = true;
        try {
            inventory.resize(OUTPUT_START + Math.max(PAGE_SIZE * 10, plan.size()));
            for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++)
                inventory.setStackInSlot(slot, slot - OUTPUT_START < plan.size() ? plan.get(slot - OUTPUT_START) : ItemStack.EMPTY);
            inventory.setStackInSlot(0, ItemStack.EMPTY);
            persistentOutputMode = true;
        } finally { internalInventoryUpdate = false; }
        setChanged();
    }
    public void dropAllContents() {
        if (level == null || level.isClientSide) return;
        ItemStack input = inventory.getStackInSlot(0);
        if (!input.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, input);
        if (persistentOutputMode) for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++) {
            ItemStack stored = inventory.getStackInSlot(slot);
            if (!stored.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stored);
        }
        for (int slot = 0; slot < inputBuffer.getSlots(); slot++) {
            ItemStack queued = inputBuffer.getStackInSlot(slot);
            if (!queued.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, queued);
        }
        internalInventoryUpdate = true;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        internalInventoryUpdate = false;
        for (int slot = 0; slot < inputBuffer.getSlots(); slot++) inputBuffer.setStackInSlot(slot, ItemStack.EMPTY);
    }

    private final DynamicItemStackHandler inventory = new DynamicItemStackHandler(OUTPUT_START + PAGE_SIZE * 10) {
        @Override public int getSlotLimit(int slot) { return slot == 0 ? 1 : OversizedItemStackCodec.MAX_COUNT; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty()) return false;
            // The extractor must accept every non-empty input. Items without
            // enchantments are valid pass-through items and are exposed to the
            // bottom automation handler once there is nothing to extract.
            if (slot == 0) return true;
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
            if (slot == 0 && !autoMode && !persistentOutputMode) refreshGeneratedSlots();
            // Manual mode uses virtual projections; persistent automatic outputs
            // are real stacks and must never be rebuilt from the input,
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
            int requestedCount = Math.min(amount, Math.min(present.getCount(), Math.max(1, present.getMaxStackSize())));
            ItemStack requested = present.copyWithCount(requestedCount);
            if (!persistentOutputMode && !canConsumeGeneratedBook(slot, requested)) return ItemStack.EMPTY;
            if (simulate) return super.extractItem(slot, requestedCount, true);
            extractingGeneratedOutput = true;
            ItemStack extracted;
            try {
                extracted = super.extractItem(slot, requestedCount, false);
            } finally {
                extractingGeneratedOutput = false;
            }
            if (!extracted.isEmpty() && !persistentOutputMode) removeGeneratedBook(slot, extracted, true);
            if (persistentOutputMode) compactOutputInventory();
            if (persistentOutputMode && !autoMode && !hasStoredOutputs()) {
                persistentOutputMode = false;
                refreshGeneratedSlots();
            }
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
            return slot == 0 ? 1 : OversizedItemStackCodec.MAX_COUNT;
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

    private final IItemHandler unsidedHandler = new SidedMachineItemHandler(inventory, automationInputHandler,
            OUTPUT_START, true, true,
            () -> !autoMode && !persistentOutputMode && getStackEnchantments(inventory.getStackInSlot(0)).isEmpty(), () -> autoMode || persistentOutputMode);

    public int getSideMode(Direction side) { return sideModes[side.ordinal()]; }
    public void cycleSideMode(Direction side) {
        int index = side.ordinal();
        sideModes[index] = (sideModes[index] + 1) % 3;
        sidedHandlers[index] = null;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public EnchantmentExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENCHANTMENT_EXTRACTOR.get(), pos, state);
    }

    public ItemStackHandler getInventory() { return inventory; }
    public ItemStackHandler getInputBuffer() { return inputBuffer; }

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

    /** Release trailing unused pages while retaining the original ten-page base allocation. */
    private void compactOutputInventory() {
        int lastUsed = OUTPUT_START;
        for (int slot = inventory.getSlots() - 1; slot >= OUTPUT_START; slot--) {
            if (!inventory.getStackInSlot(slot).isEmpty()) { lastUsed = slot + 1; break; }
        }
        int used = Math.max(PAGE_SIZE * 10, lastUsed - OUTPUT_START);
        int target = OUTPUT_START + ((used + PAGE_SIZE - 1) / PAGE_SIZE) * PAGE_SIZE;
        if (target < inventory.getSlots()) {
            inventory.resize(target);
            setChanged();
        }
    }

    public void compactInputBuffer() { inputBuffer.compact(); }

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
        int index = side.ordinal();
        if (sidedHandlers[index] == null) {
            int mode = sideModes[index];
            sidedHandlers[index] = new SidedMachineItemHandler(inventory, automationInputHandler, OUTPUT_START,
                    mode == 1, mode == 2,
                    () -> mode == 2 && !autoMode && !persistentOutputMode && getStackEnchantments(inventory.getStackInSlot(0)).isEmpty(), () -> autoMode || persistentOutputMode);
        }
        return sidedHandlers[index];
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnchantmentExtractorBlockEntity machine) {
        if (level.isClientSide) return;
        // The automation input queue feeds the single authoritative processing slot.
        // Keep the queue FIFO and never merge queued items into the active item.
        if (machine.inventory.getStackInSlot(0).isEmpty()) {
            ItemStack next = machine.takeNextBufferedItem();
            if (!next.isEmpty()) machine.inventory.setStackInSlot(0, next);
        }
        if (machine.autoMode) machine.processAutomaticInput();
    }

    private ItemStack takeNextBufferedItem() {
        int firstOccupied = -1;
        for (int slot = 0; slot < inputBuffer.getSlots(); slot++) {
            if (!inputBuffer.getStackInSlot(slot).isEmpty()) {
                firstOccupied = slot;
                break;
            }
        }
        if (firstOccupied < 0) return ItemStack.EMPTY;

        ItemStack first = inputBuffer.getStackInSlot(firstOccupied);
        ItemStack next = first.copyWithCount(1);
        first.shrink(1);
        inputBuffer.setStackInSlot(firstOccupied, first);
        if (first.isEmpty()) {
            for (int slot = firstOccupied + 1; slot < inputBuffer.getSlots(); slot++) {
                inputBuffer.setStackInSlot(slot - 1, inputBuffer.getStackInSlot(slot).copy());
            }
            inputBuffer.setStackInSlot(inputBuffer.getSlots() - 1, ItemStack.EMPTY);
        }
        inputBuffer.compact();
        return next;
    }

    private final class DynamicInputBuffer extends ItemStackHandler {
        private DynamicInputBuffer(int size) { super(size); }

        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override protected int getStackLimit(int slot, ItemStack stack) {
            return Math.min(64, stack.getMaxStackSize());
        }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < getSlots() && !stack.isEmpty();
        }
        private ItemStack insertQueued(ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            ItemStack remainder = stack;
            // Prefer merging with existing compatible stacks before allocating slots.
            for (int slot = 0; slot < getSlots() && !remainder.isEmpty(); slot++) {
                ItemStack present = getStackInSlot(slot);
                if (!present.isEmpty() && ItemStack.isSameItemSameComponents(present, remainder))
                    remainder = super.insertItem(slot, remainder, simulate);
            }
            // Then use existing empty slots.
            for (int slot = 0; slot < getSlots() && !remainder.isEmpty(); slot++) {
                if (getStackInSlot(slot).isEmpty())
                    remainder = super.insertItem(slot, remainder, simulate);
            }
            if (remainder.isEmpty()) return ItemStack.EMPTY;
            // The queue grows in page-sized chunks, so simulation must advertise
            // the same effectively unbounded capacity as the real insertion.
            if (simulate) return ItemStack.EMPTY;
            while (!remainder.isEmpty()) {
                int start = getSlots();
                resize(start + 18);
                for (int slot = start; slot < getSlots() && !remainder.isEmpty(); slot++)
                    remainder = super.insertItem(slot, remainder, false);
            }
            return ItemStack.EMPTY;
        }

        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || slot < 0 || slot >= getSlots()) return stack;
            ItemStack remainder = super.insertItem(slot, stack, simulate);
            if (!remainder.isEmpty() && slot == getSlots() - 1) {
                int oldSize = getSlots();
                if (simulate) {
                    // Advertise the capacity of a prospective new slot so pipes
                    // that simulate before inserting will still trigger expansion.
                    int accepted = Math.min(remainder.getCount(),
                            Math.min(64, remainder.getMaxStackSize()));
                    int left = remainder.getCount() - accepted;
                    return left <= 0 ? ItemStack.EMPTY : remainder.copyWithCount(left);
                }
                resize(oldSize + 18);
                return super.insertItem(oldSize, remainder, false);
            }
            return remainder;
        }
        private void resize(int size) {
            if (size == stacks.size()) return;
            NonNullList<ItemStack> resized = NonNullList.withSize(size, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(stacks.size(), size); i++) resized.set(i, stacks.get(i));
            stacks = resized;
        }

        private void compact() {
            int lastUsed = 0;
            for (int slot = stacks.size() - 1; slot >= 0; slot--) {
                if (!stacks.get(slot).isEmpty()) { lastUsed = slot + 1; break; }
            }
            int target = Math.max(18, ((lastUsed + 17) / 18) * 18);
            if (target < stacks.size()) resize(target);
        }
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
        try {
            if (item.is(Items.ENCHANTED_BOOK)) item.set(DataComponents.STORED_ENCHANTMENTS, merged.toImmutable());
            else item.set(DataComponents.ENCHANTMENTS, merged.toImmutable());
            inventory.setStackInSlot(0, item);
        } finally {
            internalInventoryUpdate = false;
        }
        // Restoring a physical book changes the authoritative input enchantments.
        // Rebuild the virtual output projection immediately so restored books can
        // be extracted again and no stale preview survives the inventory update.
        refreshGeneratedSlots();
        setChanged();
        return true;
    }

    private static ItemEnchantments getStackEnchantments(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK)
                ? stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
    }
    private void refreshGeneratedSlots() {
        if (level == null || level.isClientSide || internalInventoryUpdate || autoMode || persistentOutputMode) return;
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
        try {
            if (input.is(Items.ENCHANTED_BOOK)) {
                // An enchanted book is only a valid source while it still has
                // stored enchantments. Once the final enchantment is extracted,
                // consume the now-ordinary book instead of leaving it in the input.
                if (remaining.toImmutable().isEmpty()) {
                    inventory.setStackInSlot(0, ItemStack.EMPTY);
                } else {
                    input.set(DataComponents.STORED_ENCHANTMENTS, remaining.toImmutable());
                    inventory.setStackInSlot(0, input);
                }
            } else {
                // Ordinary equipment remains in the input after its enchantments
                // have been stripped; only enchanted books are consumed at zero.
                input.set(DataComponents.ENCHANTMENTS, remaining.toImmutable());
                inventory.setStackInSlot(0, input);
            }
            if (!outputAlreadyExtracted) {
                ItemStack remainingOutput = inventory.getStackInSlot(outputSlot).copy();
                remainingOutput.shrink(book.getCount());
                inventory.setStackInSlot(outputSlot, remainingOutput);
            }
        } finally {
            internalInventoryUpdate = false;
        }
        // Keep other output slots stable. Their stacks remain valid projections
        // of the remaining input enchantments and are validated on extraction.
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
        tag.put("OversizedInventory", OversizedItemStackCodec.serialize(inventory, registries));
        tag.put("InputBuffer", inputBuffer.serializeNBT(registries));
        tag.putInt("WorkTicks", workTicks);
        tag.putBoolean("AutoMode", autoMode);
        tag.putBoolean("SplitLevels", splitLevels);
        tag.putBoolean("CombinedOutput", combinedOutput);
        tag.putBoolean("AutoExtractionPaused", autoExtractionPaused);
        tag.putBoolean("PersistentOutputMode", persistentOutputMode);
        tag.putIntArray("SideModes", sideModes);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("OversizedInventory")) OversizedItemStackCodec.deserialize(inventory, registries, tag.getCompound("OversizedInventory"));
        else if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        if (tag.contains("InputBuffer")) inputBuffer.deserializeNBT(registries, tag.getCompound("InputBuffer"));
        autoExtractionPaused = tag.getBoolean("AutoExtractionPaused");
        boolean hasOutputModeMarker = tag.contains("PersistentOutputMode");
        persistentOutputMode = tag.getBoolean("PersistentOutputMode");
        workTicks = tag.getInt("WorkTicks");
        autoMode = tag.getBoolean("AutoMode");
        // Older saves stored generated books in output slots as virtual previews.
        // Never reinterpret those preview stacks as physical queued output.
        if (!hasOutputModeMarker && autoMode) {
            internalInventoryUpdate = true;
            for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++)
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            internalInventoryUpdate = false;
            persistentOutputMode = true;
        }
        splitLevels = tag.getBoolean("SplitLevels");
        combinedOutput = tag.getBoolean("CombinedOutput");
        int[] savedSideModes = tag.getIntArray("SideModes");
        for (int i = 0; i < Math.min(savedSideModes.length, sideModes.length); i++) sideModes[i] = Math.max(0, Math.min(2, savedSideModes[i]));
        java.util.Arrays.fill(sidedHandlers, null);
        // Rebuild only after mode flags have been restored from disk.
        if (level != null && !level.isClientSide && !persistentOutputMode && !autoMode) refreshGeneratedSlots();
    }
    
    
    
    
}
