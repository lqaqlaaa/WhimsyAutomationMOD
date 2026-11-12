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
import net.minecraft.world.item.component.CustomData;
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

    // Separate manual input for enchanted books; the original inventory layout stays intact.
    private final ItemStackHandler bookInput = new ItemStackHandler(1) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
            return slot == 0 && stack.is(Items.ENCHANTED_BOOK) && stored != null && !stored.isEmpty();
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };

    public ItemStackHandler getBookInput() { return bookInput; }

    public boolean isAutoMode() { return autoMode; }
    public void setAutoMode(boolean enabled) { autoMode = enabled; setChanged(); }
    public boolean isSplitLevels() { return splitLevels; }
    public void setSplitLevels(boolean enabled) { splitLevels = enabled; setChanged(); }
    public void processOnce() { if (level != null && !level.isClientSide) processOneItem(false); }
    public void dropAllContents() {
        if (level == null || level.isClientSide) return;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        ItemStack pendingBook = bookInput.getStackInSlot(0);
        if (!pendingBook.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, pendingBook);
            bookInput.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    private final ItemStackHandler inventory = new ItemStackHandler(OUTPUT_START + OUTPUT_SLOTS) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && !stack.isEmpty() && !stack.is(Items.ENCHANTED_BOOK); }
        @Override protected void onContentsChanged(int slot) { if (slot == 0) autoExtractionPaused = false; setChanged(); }

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
        machine.processOneItem(true);
    }

    private void processOneItem(boolean automatic) {
        if (level == null) return;

        ItemStack item = inventory.getStackInSlot(0);
        ItemStack book = bookInput.getStackInSlot(0);

        // A book in the second input slot means "apply", never "extract".
        if (!item.isEmpty() && !book.isEmpty()) {
            applyBook(item, book);
            return;
        }

        // Prevent automatic mode from immediately stripping an item again after
        // the player has just applied a book to it. Changing the item input clears this.
        if (automatic && autoExtractionPaused) return;
        extractEnchantments(item);
    }

    public boolean canApplyBook(ItemStack item, ItemStack book) {
        if (item.isEmpty() || book.isEmpty()) return false;
        ItemEnchantments stored = book.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return false;

        ItemEnchantments existing = item.get(DataComponents.ENCHANTMENTS);
        List<Holder<Enchantment>> additions = new ArrayList<>();
        CustomData customData = book.get(DataComponents.CUSTOM_DATA);
        boolean splitFragment = customData != null && customData.copyTag().getBoolean("WhimsySplitLevelFragment");
        boolean changed = false;

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            if (!enchantment.value().isSupportedItem(item)) return false;

            if (existing != null) {
                for (Object2IntMap.Entry<Holder<Enchantment>> oldEntry : existing.entrySet()) {
                    // The same enchantment is allowed: it may be upgraded by the book.
                    if (!enchantment.equals(oldEntry.getKey())
                            && !Enchantment.areCompatible(enchantment, oldEntry.getKey())) return false;
                }
            }
            for (Holder<Enchantment> added : additions) {
                if (!Enchantment.areCompatible(enchantment, added)) return false;
            }
            additions.add(enchantment);

            int oldLevel = existing == null ? 0 : existing.getLevel(enchantment);
            int bookLevel = entry.getIntValue();
            int nextLevel = splitFragment && oldLevel > 0
                    ? oldLevel + 1
                    : oldLevel == bookLevel ? oldLevel + 1 : Math.max(oldLevel, bookLevel);
            nextLevel = Math.min(nextLevel, enchantment.value().getMaxLevel());
            if (nextLevel > oldLevel) changed = true;
        }
        return changed;
    }

    private void applyBook(ItemStack item, ItemStack book) {
        if (!canApplyBook(item, book)) return;

        ItemEnchantments stored = book.get(DataComponents.STORED_ENCHANTMENTS);
        ItemEnchantments existing = item.get(DataComponents.ENCHANTMENTS);
        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(
                existing == null ? ItemEnchantments.EMPTY : existing);
        CustomData customData = book.get(DataComponents.CUSTOM_DATA);
        boolean splitFragment = customData != null && customData.copyTag().getBoolean("WhimsySplitLevelFragment");

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int oldLevel = existing == null ? 0 : existing.getLevel(enchantment);
            int bookLevel = entry.getIntValue();
            int nextLevel = splitFragment && oldLevel > 0
                    ? oldLevel + 1
                    : oldLevel == bookLevel ? oldLevel + 1 : Math.max(oldLevel, bookLevel);
            nextLevel = Math.min(nextLevel, enchantment.value().getMaxLevel());
            if (nextLevel > oldLevel) merged.set(enchantment, nextLevel);
        }

        item.set(DataComponents.ENCHANTMENTS, merged.toImmutable());
        inventory.setStackInSlot(0, item);
        book.shrink(1);
        bookInput.setStackInSlot(0, book.isEmpty() ? ItemStack.EMPTY : book);
        autoExtractionPaused = true;
        setChanged();
    }
    private void extractEnchantments(ItemStack input) {
        if (input.isEmpty()) return;
        ItemEnchantments enchantments = input.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) return;

        int bookCount = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            bookCount += splitLevels ? Math.max(0, entry.getIntValue()) : 1;
        }
        if (bookCount + 1 > OUTPUT_SLOTS) return;

        ItemStack[] results = new ItemStack[bookCount + 1];
        ItemStack cleaned = input.copyWithCount(1);
        cleaned.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        results[0] = cleaned;

        int index = 1;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            int amount = splitLevels ? Math.max(0, entry.getIntValue()) : 1;
            int storedLevel = splitLevels ? 1 : entry.getIntValue();
            for (int n = 0; n < amount; n++) {
                ItemStack outputBook = new ItemStack(Items.ENCHANTED_BOOK);
                ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                stored.set(entry.getKey(), storedLevel);
                outputBook.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
                if (splitLevels) {
                    CompoundTag fragmentTag = new CompoundTag();
                    fragmentTag.putBoolean("WhimsySplitLevelFragment", true);
                    outputBook.set(DataComponents.CUSTOM_DATA, CustomData.of(fragmentTag));
                }
                results[index++] = outputBook;
            }
        }

        int[] slots = findEmptyOutputSlots(results.length);
        if (slots == null) return;
        for (int i = 0; i < results.length; i++) inventory.setStackInSlot(slots[i], results[i]);

        input.shrink(1);
        inventory.setStackInSlot(0, input.isEmpty() ? ItemStack.EMPTY : input);
        setChanged();
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
        tag.put("BookInput", bookInput.serializeNBT(registries));
        tag.putInt("WorkTicks", workTicks);
        tag.putBoolean("AutoMode", autoMode);
        tag.putBoolean("SplitLevels", splitLevels);
        tag.putBoolean("AutoExtractionPaused", autoExtractionPaused);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        if (tag.contains("BookInput")) bookInput.deserializeNBT(registries, tag.getCompound("BookInput"));
        autoExtractionPaused = tag.getBoolean("AutoExtractionPaused");
        workTicks = tag.getInt("WorkTicks");
        autoMode = tag.getBoolean("AutoMode");
        splitLevels = tag.getBoolean("SplitLevels");
    }
}
