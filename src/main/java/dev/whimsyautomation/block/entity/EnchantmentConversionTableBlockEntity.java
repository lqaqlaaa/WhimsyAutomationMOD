package dev.whimsyautomation.block.entity;

import dev.whimsyautomation.menu.EnchantmentConversionTableMenu;
import dev.whimsyautomation.registry.ModBlockEntities;
import dev.whimsyautomation.util.OversizedItemStackCodec;
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
    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public int getSlotLimit(int slot) {
            return slot == INPUT_SLOT ? 1 : MAX_COPY_COUNT;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == INPUT_SLOT || slot == OUTPUT_SLOT;
        }

        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return slot == OUTPUT_SLOT ? MAX_COPY_COUNT : 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == OUTPUT_SLOT && amount > 0) {
                amount = Math.min(amount, getStackInSlot(OUTPUT_SLOT).getMaxStackSize());
            }
            return super.extractItem(slot, amount, simulate);
        }
    };

    public EnchantmentConversionTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENCHANTMENT_CONVERSION_TABLE.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    /** Actual logical count stored in the output slot, not a display-only queue. */
    public int getTotalOutputCount() {
        return inventory.getStackInSlot(OUTPUT_SLOT).getCount();
    }

    /** Called only by the validated server-side confirmation payload. */
    public boolean confirmCopy(int count) {
        if (level == null || level.isClientSide || count < 1 || count > MAX_COPY_COUNT) return false;
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty() || !inventory.getStackInSlot(OUTPUT_SLOT).isEmpty()) return false;

        inventory.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY);
        inventory.setStackInSlot(OUTPUT_SLOT, input.copyWithCount(count));
        setChanged();
        return true;
    }

    public void dropAllContents() {
        if (level == null || level.isClientSide) return;

        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        dropInLegalStacks(output);
        inventory.setStackInSlot(OUTPUT_SLOT, ItemStack.EMPTY);

        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        dropInLegalStacks(input);
        inventory.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY);
        setChanged();
    }

    private void dropInLegalStacks(ItemStack stack) {
        if (stack.isEmpty()) return;
        int remaining = stack.getCount();
        int legalStackSize = Math.max(1, stack.getMaxStackSize());
        while (remaining > 0) {
            int batchSize = Math.min(remaining, legalStackSize);
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
        tag.put("OversizedInventory", OversizedItemStackCodec.serialize(inventory, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("OversizedInventory")) {
            OversizedItemStackCodec.deserialize(inventory, registries, tag.getCompound("OversizedInventory"));
        } else if (tag.contains("Inventory")) {
            // Backward compatibility: migrate the former 99-item batch plus pending queue.
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
            int queued = Math.max(0, Math.min(MAX_COPY_COUNT, tag.getInt("PendingCount")));
            ItemStack visible = inventory.getStackInSlot(OUTPUT_SLOT);
            ItemStack queuedTemplate = tag.contains("PendingTemplate")
                    ? ItemStack.parse(registries, tag.getCompound("PendingTemplate")).orElse(ItemStack.EMPTY)
                    : ItemStack.EMPTY;
            ItemStack template = !visible.isEmpty() ? visible : queuedTemplate;
            if (!template.isEmpty() && queued > 0
                    && (visible.isEmpty() || ItemStack.isSameItemSameComponents(visible, queuedTemplate))) {
                inventory.setStackInSlot(OUTPUT_SLOT, template.copyWithCount(visible.getCount() + queued));
            }
        }
    }
}
