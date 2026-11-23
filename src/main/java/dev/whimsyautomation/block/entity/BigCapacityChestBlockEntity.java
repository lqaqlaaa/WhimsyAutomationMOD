package dev.whimsyautomation.block.entity;

import dev.whimsyautomation.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.level.block.state.BlockState;

import java.math.BigInteger;

/**
 * Vanilla chest presentation and lifecycle backed by an exact-count inventory.
 * ItemStack instances exposed to vanilla are compatibility views only; the
 * BigCapacityInventory is the authoritative source for every stored quantity.
 */
public final class BigCapacityChestBlockEntity extends ChestBlockEntity {
    public static final BigInteger MAX_COUNT = BigCapacityInventory.MAX_SLOT_COUNT;

    private NonNullList<ItemStack> representatives = NonNullList.withSize(27, ItemStack.EMPTY);
    private BigCapacityInventory inventory = new BigCapacityInventory(27);

    public BigCapacityChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BIG_CAPACITY_CHEST.get(), pos, state);
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return representatives;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        representatives = NonNullList.withSize(27, ItemStack.EMPTY);
        if (inventory == null) inventory = new BigCapacityInventory(27);
        else inventory.clear();
        for (int i = 0; i < Math.min(27, items.size()); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                representatives.set(i, stack.copyWithCount(1));
                inventory.set(i, stack, BigInteger.valueOf(stack.getCount()));
            }
        }
    }

    @Override public int getContainerSize() { return 27; }

    // Vanilla APIs can only express an int-sized transfer. The authoritative
    // capacity is independent of this compatibility limit.
    @Override public int getMaxStackSize() { return Integer.MAX_VALUE; }

    /**
     * Container#getItem is a live compatibility view because vanilla hoppers
     * mutate the returned stack before invoking setChanged(). reconcileSlot()
     * converts that bounded mutation into an exact BigInteger delta.
     */
    @Override
    public ItemStack getItem(int slot) {
        if (!valid(slot)) return ItemStack.EMPTY;
        reconcileSlot(slot);
        return representatives.get(slot);
    }

    public ItemStack getDisplayItem(int slot) {
        return valid(slot) ? inventory.displayStack(slot) : ItemStack.EMPTY;
    }

    public ItemStack getAutomationStack(int slot) {
        return valid(slot) ? inventory.transferStack(slot) : ItemStack.EMPTY;
    }

    public BigInteger getExactCount(int slot) {
        if (!valid(slot)) return BigInteger.ZERO;
        reconcileSlot(slot);
        return inventory.count(slot);
    }

    private void reconcileSlot(int slot) {
        if (!valid(slot)) return;
        ItemStack stack = representatives.get(slot);
        if (stack.isEmpty()) {
            if (inventory.count(slot).signum() > 0) {
                // A vanilla path may replace the live view with EMPTY. Treat
                // this as an explicit removal only when setItem/removeItem was
                // not used (those methods update the inventory first).
                inventory.set(slot, ItemStack.EMPTY, BigInteger.ZERO);
            }
            return;
        }
        int delta = stack.getCount() - 1;
        if (delta == 0) return;
        BigInteger next = inventory.count(slot).add(BigInteger.valueOf(delta))
                .max(BigInteger.ZERO).min(MAX_COUNT);
        ItemStack template = inventory.template(slot);
        if (next.signum() == 0) {
            inventory.set(slot, ItemStack.EMPTY, BigInteger.ZERO);
            representatives.set(slot, ItemStack.EMPTY);
        } else {
            inventory.set(slot, template.isEmpty() ? stack : template, next);
            stack.setCount(1);
        }
    }

    private void reconcileAll() {
        for (int slot = 0; slot < 27; slot++) reconcileSlot(slot);
    }

    public IItemHandler getAutomationItemHandler(net.minecraft.core.Direction side) {
        return new BigCapacityChestItemHandler(this);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (!valid(slot) || amount <= 0) return ItemStack.EMPTY;
        reconcileSlot(slot);
        ItemStack result = inventory.extract(slot, amount, false);
        representatives.set(slot, inventory.isEmpty(slot) ? ItemStack.EMPTY : inventory.template(slot));
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, Integer.MAX_VALUE);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!valid(slot)) return;
        if (stack == null || stack.isEmpty()) {
            inventory.set(slot, ItemStack.EMPTY, BigInteger.ZERO);
            representatives.set(slot, ItemStack.EMPTY);
        } else {
            inventory.set(slot, stack, BigInteger.valueOf(stack.getCount()));
            representatives.set(slot, stack.copyWithCount(1));
        }
        setChanged();
    }

    /** Inserts a bounded ItemStack batch into the authoritative exact-count slot. */
    public int insertExact(int slot, ItemStack stack, int requested) {
        if (!valid(slot) || stack == null || stack.isEmpty() || requested <= 0) return 0;
        reconcileSlot(slot);
        int accepted = inventory.insert(slot, stack, requested, false);
        if (accepted > 0) {
            if (representatives.get(slot).isEmpty()) representatives.set(slot, stack.copyWithCount(1));
            setChanged();
        }
        return accepted;
    }

    /**
     * Applies a vanilla GUI's visible stack projection. Only the visible portion
     * is changed; hidden excess is preserved when the item identity is unchanged.
     */
    public void setGuiItem(int slot, ItemStack stack) {
        if (!valid(slot)) return;
        reconcileSlot(slot);
        if (stack == null || stack.isEmpty()) {
            inventory.set(slot, ItemStack.EMPTY, BigInteger.ZERO);
            representatives.set(slot, ItemStack.EMPTY);
            setChanged();
            return;
        }
        ItemStack old = inventory.template(slot);
        BigInteger current = inventory.count(slot);
        if (!old.isEmpty() && ItemStack.isSameItemSameComponents(old, stack)) {
            int before = current.min(BigInteger.valueOf(64)).intValue();
            int after = Math.min(64, stack.getCount());
            BigInteger next = current.add(BigInteger.valueOf(after - before)).max(BigInteger.ZERO).min(MAX_COUNT);
            inventory.set(slot, next.signum() == 0 ? ItemStack.EMPTY : old, next);
        } else {
            inventory.set(slot, stack, BigInteger.valueOf(stack.getCount()));
        }
        representatives.set(slot, inventory.isEmpty(slot) ? ItemStack.EMPTY : inventory.template(slot));
        setChanged();
    }

    /**
     * Emits legal ItemStack batches. Note that a quantity near 10^308 cannot be
     * represented as a practical number of world entities; callers should avoid
     * invoking this for enormous inventories and use a packed chest item instead.
     */
    public void dropAllContents(Level level, BlockPos pos) {
        if (level == null || level.isClientSide) return;
        reconcileAll();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        BigInteger intMax = BigInteger.valueOf(Integer.MAX_VALUE);
        for (int slot = 0; slot < 27; slot++) {
            BigInteger remaining = inventory.count(slot);
            ItemStack template = inventory.template(slot);
            if (remaining.signum() <= 0 || template.isEmpty()) continue;
            // World item entities are inherently int-counted. This legacy
            // destruction path is retained temporarily while packed-item
            // preservation is integrated into the block's loot lifecycle.
            while (remaining.signum() > 0) {
                int batch = remaining.min(intMax).intValue();
                ItemEntity entity = new ItemEntity(level, x, y, z, template.copyWithCount(batch));
                entity.setDefaultPickUpDelay();
                level.addFreshEntity(entity);
                remaining = remaining.subtract(BigInteger.valueOf(batch));
            }
            inventory.set(slot, ItemStack.EMPTY, BigInteger.ZERO);
            representatives.set(slot, ItemStack.EMPTY);
        }
        setChanged();
    }

    @Override public boolean isEmpty() {
        reconcileAll();
        for (int i = 0; i < 27; i++) if (!inventory.isEmpty(i)) return false;
        return true;
    }

    @Override public void clearContent() {
        inventory.clear();
        for (int i = 0; i < 27; i++) representatives.set(i, ItemStack.EMPTY);
        setChanged();
    }

    @Override protected Component getDefaultName() {
        return Component.translatable("container.whimsy_automation.big_capacity_chest");
    }

    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return null; }

    private boolean valid(int slot) { return slot >= 0 && slot < 27; }

    @Override
    public void setChanged() {
        reconcileAll();
        super.setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        reconcileAll();
        super.saveAdditional(tag, registries);
        CompoundTag exact = new CompoundTag();
        for (int i = 0; i < 27; i++) {
            BigInteger count = inventory.count(i);
            if (count.signum() > 0) exact.putString(Integer.toString(i), count.toString());
        }
        tag.put("BigCapacityAmounts", exact);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.clear();
        CompoundTag exact = tag.getCompound("BigCapacityAmounts");
        for (int i = 0; i < 27; i++) {
            String key = Integer.toString(i);
            ItemStack template = representatives.get(i);
            if (exact.contains(key)) {
                try {
                    BigInteger value = new BigInteger(exact.getString(key));
                    if (value.signum() > 0 && !template.isEmpty()) {
                        inventory.set(i, template, value.min(MAX_COUNT));
                    }
                } catch (NumberFormatException ignored) {
                    inventory.set(i, ItemStack.EMPTY, BigInteger.ZERO);
                }
            } else if (!template.isEmpty()) {
                inventory.set(i, template, BigInteger.valueOf(template.getCount()));
            }
            representatives.set(i, inventory.isEmpty(i) ? ItemStack.EMPTY : inventory.template(i));
        }
    }
}
