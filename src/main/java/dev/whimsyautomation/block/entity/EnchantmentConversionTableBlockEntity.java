package dev.whimsyautomation.block.entity;

import dev.whimsyautomation.menu.EnchantmentConversionTableMenu;
import dev.whimsyautomation.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class EnchantmentConversionTableBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int SLOT_COUNT = 2;
    public static final int MAX_COPY_COUNT = 1_000_000;
    private static final int MAX_SERIALIZED_STACK_COUNT = 99;

    private ItemStack pendingTemplate = ItemStack.EMPTY;
    private int pendingCount;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public int getSlotLimit(int slot) {
            return slot == INPUT_SLOT ? 1 : MAX_SERIALIZED_STACK_COUNT;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == INPUT_SLOT || slot == OUTPUT_SLOT;
        }

        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            // Never place an oversized ItemStack in NBT. The remaining output
            // quantity is stored separately as an integer below.
            return slot == OUTPUT_SLOT ? MAX_SERIALIZED_STACK_COUNT : 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack extracted = super.extractItem(slot, amount, simulate);
            if (!simulate && slot == OUTPUT_SLOT && getStackInSlot(OUTPUT_SLOT).isEmpty()) {
                refillOutputBatch();
            }
            return extracted;
        }
    };

    public EnchantmentConversionTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENCHANTMENT_CONVERSION_TABLE.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    /** Total amount represented by the visible batch plus queued batches. */
    public int getTotalOutputCount() {
        return inventory.getStackInSlot(OUTPUT_SLOT).getCount() + pendingCount;
    }

    /** Called only by the validated server-side confirmation payload. */
    public boolean confirmCopy(int count) {
        if (level == null || level.isClientSide || count < 1 || count > MAX_COPY_COUNT) return false;
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty() || !inventory.getStackInSlot(OUTPUT_SLOT).isEmpty() || pendingCount > 0) return false;

        pendingTemplate = input.copyWithCount(1);
        pendingCount = count;
        inventory.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY);
        refillOutputBatch();
        setChanged();
        return true;
    }

    /** Refill the visible output after menu operations that bypass ItemStackHandler.extractItem. */
    public void refillOutputIfNeeded() {
        if (level != null && !level.isClientSide) {
            refillOutputBatch();
        }
    }

    private void refillOutputBatch() {
        if (pendingCount <= 0 || pendingTemplate.isEmpty()
                || !inventory.getStackInSlot(OUTPUT_SLOT).isEmpty()) return;

        int batchSize = Math.min(pendingCount, MAX_SERIALIZED_STACK_COUNT);
        inventory.setStackInSlot(OUTPUT_SLOT, pendingTemplate.copyWithCount(batchSize));
        pendingCount -= batchSize;
        if (pendingCount == 0) pendingTemplate = ItemStack.EMPTY;
        setChanged();
    }

    public void dropAllContents() {
        if (level == null || level.isClientSide) return;

        // Drop the visible batch and any queued batches separately so every
        // serialized ItemStack count stays inside Minecraft's [1, 99] range.
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        dropInLegalStacks(output);
        inventory.setStackInSlot(OUTPUT_SLOT, ItemStack.EMPTY);

        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        dropInLegalStacks(input);
        inventory.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY);

        if (pendingCount > 0 && !pendingTemplate.isEmpty()) {
            int remaining = pendingCount;
            while (remaining > 0) {
                int batchSize = Math.min(remaining, MAX_SERIALIZED_STACK_COUNT);
                dropInLegalStacks(pendingTemplate.copyWithCount(batchSize));
                remaining -= batchSize;
            }
        }
        pendingCount = 0;
        pendingTemplate = ItemStack.EMPTY;
        setChanged();
    }

    private void dropInLegalStacks(ItemStack stack) {
        if (stack.isEmpty()) return;
        int remaining = stack.getCount();
        while (remaining > 0) {
            int batchSize = Math.min(remaining, MAX_SERIALIZED_STACK_COUNT);
            ItemStack batch = stack.copyWithCount(batchSize);
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, batch);
            remaining -= batchSize;
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.whimsy_automation.enchantment_conversion_table");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new EnchantmentConversionTableMenu(id, playerInventory, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("PendingCount", pendingCount);
        if (!pendingTemplate.isEmpty()) {
            tag.put("PendingTemplate", pendingTemplate.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        pendingCount = Math.max(0, Math.min(MAX_COPY_COUNT, tag.getInt("PendingCount")));
        pendingTemplate = tag.contains("PendingTemplate")
                ? ItemStack.parse(registries, tag.getCompound("PendingTemplate")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        if (pendingCount > 0 && pendingTemplate.isEmpty()) pendingCount = 0;
    }
}
