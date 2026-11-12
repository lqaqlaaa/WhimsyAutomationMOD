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
    }

    private final ItemStackHandler inventory = new ItemStackHandler(OUTPUT_START + OUTPUT_SLOTS) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (slot == 0) return !getStackEnchantments(stack).isEmpty();
            return stack.is(Items.ENCHANTED_BOOK) && !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
        }
        @Override protected void onContentsChanged(int slot) {
            if (!internalInventoryUpdate && slot > 0) {
                ItemStack offeredBook = getStackInSlot(slot);
                if (!offeredBook.isEmpty() && offeredBook.is(Items.ENCHANTED_BOOK) && applyBookToInput(offeredBook)) {
                    internalInventoryUpdate = true;
                    setStackInSlot(slot, ItemStack.EMPTY);
                    internalInventoryUpdate = false;
                }
            }
            if (slot == 0) autoExtractionPaused = false;
            setChanged();
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
        machine.processOneItem(true);
    }

    private void processOneItem(boolean automatic) {
        if (level == null) return;

        ItemStack item = inventory.getStackInSlot(0);
        // Prevent automatic mode from immediately stripping an item again after
        // the player has just applied a book to it. Changing the item input clears this.
        if (automatic && autoExtractionPaused) return;
        extractEnchantments(item);
    }

    public boolean canApplyBook(ItemStack item, ItemStack book) {
        if (item.isEmpty() || !book.is(Items.ENCHANTED_BOOK)) return false;
        ItemEnchantments stored = book.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return false;

        ItemEnchantments existing = item.get(DataComponents.ENCHANTMENTS);
        List<Holder<Enchantment>> additions = new ArrayList<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int bookLevel = entry.getIntValue();
            if (bookLevel <= 0 || !enchantment.value().isSupportedItem(item)) return false;

            // Reject the whole book if any enchantment conflicts. The same
            // enchantment is intentionally mergeable with itself.
            if (existing != null) {
                for (Object2IntMap.Entry<Holder<Enchantment>> oldEntry : existing.entrySet()) {
                    if (!enchantment.equals(oldEntry.getKey())
                            && !Enchantment.areCompatible(enchantment, oldEntry.getKey())) return false;
                }
            }
            for (Holder<Enchantment> added : additions) {
                if (!Enchantment.areCompatible(enchantment, added)) return false;
            }

            int oldLevel = existing == null ? 0 : existing.getLevel(enchantment);
            if ((long) oldLevel + bookLevel > Integer.MAX_VALUE) return false;
            additions.add(enchantment);
        }
        return !additions.isEmpty();
    }

    private boolean applyBookToInput(ItemStack book) {
        ItemStack item = inventory.getStackInSlot(0);
        if (!canApplyBook(item, book)) return false;

        ItemEnchantments stored = book.get(DataComponents.STORED_ENCHANTMENTS);
        ItemEnchantments existing = item.get(DataComponents.ENCHANTMENTS);
        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(
                existing == null ? ItemEnchantments.EMPTY : existing);

        // Match EnchantmentCustomTable's default merge rule: a new enchantment
        // keeps the book's level; a duplicate adds the two levels together.
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
            int oldLevel = existing == null ? 0 : existing.getLevel(entry.getKey());
            merged.set(entry.getKey(), oldLevel + entry.getIntValue());
        }

        item.set(DataComponents.ENCHANTMENTS, merged.toImmutable());
        inventory.setStackInSlot(0, item);
        autoExtractionPaused = true;
        setChanged();
        return true;
    }

    private static ItemEnchantments getStackEnchantments(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK)
                ? stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
    }
    private void extractEnchantments(ItemStack input) {
        if (input.isEmpty()) return;
        ItemEnchantments enchantments = getStackEnchantments(input);
        if (enchantments.isEmpty()) return;

        int bookCount = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            bookCount += splitLevels ? Math.max(0, entry.getIntValue()) : 1;
        }
        if (bookCount + 1 > OUTPUT_SLOTS) return;

        ItemStack[] results = new ItemStack[bookCount + 1];
        ItemStack cleaned = input.copyWithCount(1);
        if (input.is(Items.ENCHANTED_BOOK)) cleaned.set(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        else cleaned.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
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
                results[index++] = outputBook;
            }
        }

        int[] slots = findEmptyOutputSlots(results.length);
        if (slots == null) return;
        internalInventoryUpdate = true;
        for (int i = 0; i < results.length; i++) inventory.setStackInSlot(slots[i], results[i]);
        internalInventoryUpdate = false;

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
        workTicks = tag.getInt("WorkTicks");
        autoMode = tag.getBoolean("AutoMode");
        splitLevels = tag.getBoolean("SplitLevels");
    }
}
