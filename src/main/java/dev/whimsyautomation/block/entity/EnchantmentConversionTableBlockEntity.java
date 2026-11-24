package dev.whimsyautomation.block.entity;

import dev.whimsyautomation.menu.EnchantmentConversionTableMenu;
import dev.whimsyautomation.registry.ModBlockEntities;
import dev.whimsyautomation.util.OversizedItemStackCodec;
import dev.whimsyautomation.util.SidedMachineItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public final class EnchantmentConversionTableBlockEntity extends BlockEntity implements MenuProvider, SideConfigurableBlockEntity, dev.whimsyautomation.remote.RemoteTransferInventoryProvider {
    private boolean autoMode;
    private int copyCount = 64;
    private final int[] sideModes = new int[6];
    // Client-only preference; not persisted or synchronized as machine gameplay data.
    private boolean faceColorDisplayEnabled = true;

    private final IItemHandler[] sidedHandlers = new IItemHandler[6];
    private boolean internalInventoryUpdate;
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int SLOT_COUNT = 2;
    public static final int MAX_COPY_COUNT = Integer.MAX_VALUE;
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
            if (autoMode && !internalInventoryUpdate && level != null && !level.isClientSide
                    && !getStackInSlot(INPUT_SLOT).isEmpty() && getStackInSlot(OUTPUT_SLOT).isEmpty()) {
                confirmCopy(copyCount);
            }
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= getSlots() || amount <= 0) return ItemStack.EMPTY;
            // Automation may request the full oversized machine stack. Player-facing
            // extraction is limited by the menu, not by this shared machine handler.
            return super.extractItem(slot, amount, simulate);
        }
    };

    public EnchantmentConversionTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENCHANTMENT_CONVERSION_TABLE.get(), pos, state);
    }

    public boolean isAutoMode() { return autoMode; }
    public int getCopyCount() { return copyCount; }
    public void setCopyCount(int count) {
        if (count < 1 || count > MAX_COPY_COUNT || copyCount == count) return;
        copyCount = count;
        setChanged();
    }
    public void setAutoMode(boolean enabled) {
        if (autoMode == enabled) return;
        autoMode = enabled;
        setChanged();
        if (enabled && level != null && !level.isClientSide) confirmCopy(copyCount);
    }
    @Override public int getSideMode(Direction side) { return sideModes[side.ordinal()]; }
    @Override public boolean isFaceColorDisplayEnabled() { return faceColorDisplayEnabled; }
    @Override public void setFaceColorDisplayEnabled(boolean enabled) { faceColorDisplayEnabled = enabled; }
    @Override public void setSideModeForDisplay(Direction side, int mode) {
        sideModes[side.ordinal()] = Math.max(0, Math.min(2, mode));
    }
    public void cycleSideMode(Direction side) {
        int index = side.ordinal();
        sideModes[index] = (sideModes[index] + 1) % 3;
        sidedHandlers[index] = null;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    private final IItemHandler automationInputHandler = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? inventory.getStackInSlot(INPUT_SLOT) : ItemStack.EMPTY;
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot == 0 ? inventory.insertItem(INPUT_SLOT, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return slot == 0 ? 1 : 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && !stack.isEmpty(); }
    };

    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        if (side == null) return new SidedMachineItemHandler(inventory, automationInputHandler,
                OUTPUT_SLOT, true, true, () -> false, () -> true);
        int index = side.ordinal();
        if (sidedHandlers[index] == null) {
            int mode = sideModes[index];
            sidedHandlers[index] = new SidedMachineItemHandler(inventory, automationInputHandler,
                    OUTPUT_SLOT, mode == 1, mode == 2, () -> false, () -> true);
        }
        return sidedHandlers[index];
    }

    @Override public net.neoforged.neoforge.items.IItemHandler getRemoteTransferInventory() { return inventory; }

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
        copyCount = count;

        internalInventoryUpdate = true;
        try {
            inventory.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY);
            ItemStack output = input.copyWithCount(count);
            dev.whimsyautomation.util.PlayerStackRules.normalizeStackLimit(output);
            inventory.setStackInSlot(OUTPUT_SLOT, output);
        } finally { internalInventoryUpdate = false; }
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
        tag.putBoolean("AutoMode", autoMode);
        tag.putInt("CopyCount", copyCount);
        tag.putIntArray("SideModes", sideModes);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        autoMode = tag.getBoolean("AutoMode");
        copyCount = Math.max(1, Math.min(MAX_COPY_COUNT, tag.contains("CopyCount") ? tag.getInt("CopyCount") : 64));
        int[] savedSideModes = tag.getIntArray("SideModes");
        for (int i = 0; i < Math.min(savedSideModes.length, sideModes.length); i++) sideModes[i] = Math.max(0, Math.min(2, savedSideModes[i]));
        java.util.Arrays.fill(sidedHandlers, null);
        if (tag.contains("OversizedInventory")) {
            internalInventoryUpdate = true;
            try { OversizedItemStackCodec.deserialize(inventory, registries, tag.getCompound("OversizedInventory")); }
            finally { internalInventoryUpdate = false; }
        } else if (tag.contains("Inventory")) {
            // Backward compatibility: migrate the former 99-item batch plus pending queue.
            internalInventoryUpdate = true;
            try {
                inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
                int queued = Math.max(0, Math.min(MAX_COPY_COUNT, tag.getInt("PendingCount")));
                ItemStack visible = inventory.getStackInSlot(OUTPUT_SLOT);
                ItemStack queuedTemplate = tag.contains("PendingTemplate")
                        ? ItemStack.parse(registries, tag.getCompound("PendingTemplate")).orElse(ItemStack.EMPTY)
                        : ItemStack.EMPTY;
                ItemStack template = !visible.isEmpty() ? visible : queuedTemplate;
                if (!template.isEmpty() && queued > 0
                        && (visible.isEmpty() || ItemStack.isSameItemSameComponents(visible, queuedTemplate))) {
                    inventory.setStackInSlot(OUTPUT_SLOT, template.copyWithCount((int) Math.min(MAX_COPY_COUNT, (long) visible.getCount() + queued)));
                }
            } finally { internalInventoryUpdate = false; }
        }
    }
}
