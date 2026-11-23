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
import net.minecraft.world.WorldlyContainer;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.math.BigInteger;
import java.util.Arrays;

/** A vanilla chest container whose exact per-slot amounts are stored independently of ItemStack's int count. */
public final class BigCapacityChestBlockEntity extends ChestBlockEntity implements WorldlyContainer {
    public static final BigInteger MAX_COUNT = BigInteger.TEN.pow(308);
    private NonNullList<ItemStack> representatives = NonNullList.withSize(27, ItemStack.EMPTY);
    private BigInteger[] amounts = emptyAmounts();

    private static BigInteger[] emptyAmounts() {
        BigInteger[] result = new BigInteger[27];
        Arrays.fill(result, BigInteger.ZERO);
        return result;
    }
    public BigCapacityChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BIG_CAPACITY_CHEST.get(), pos, state);
    }
    @Override protected NonNullList<ItemStack> getItems() { return representatives; }
    @Override protected void setItems(NonNullList<ItemStack> items) {
        representatives = NonNullList.withSize(27, ItemStack.EMPTY);
        amounts = emptyAmounts();
        for (int i = 0; i < Math.min(27, items.size()); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                representatives.set(i, stack.copyWithCount(1));
                amounts[i] = BigInteger.valueOf(stack.getCount());
            }
        }
    }
    @Override public int getContainerSize() { return 27; }
    @Override public int[] getSlotsForFace(Direction side) {
        int[] slots = new int[27];
        for (int i = 0; i < slots.length; i++) slots[i] = i;
        return slots;
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return valid(slot) && stack != null && !stack.isEmpty();
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return valid(slot) && stack != null && !stack.isEmpty() && amounts[slot].signum() > 0;
    }
    @Override public int getMaxStackSize() { return Integer.MAX_VALUE; }
    @Override public ItemStack getItem(int slot) {
        if (!valid(slot) || amounts[slot].signum() <= 0 || representatives.get(slot).isEmpty()) return ItemStack.EMPTY;
        return representatives.get(slot).copyWithCount(1);
    }
    public ItemStack getDisplayItem(int slot) {
        if (!valid(slot) || amounts[slot].signum() <= 0 || representatives.get(slot).isEmpty()) return ItemStack.EMPTY;
        return representatives.get(slot).copyWithCount(amounts[slot].min(BigInteger.valueOf(64)).intValue());
    }
    public BigInteger getExactCount(int slot) { return valid(slot) ? amounts[slot] : BigInteger.ZERO; }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (!valid(slot) || amount <= 0 || amounts[slot].signum() <= 0) return ItemStack.EMPTY;
        int removed = amounts[slot].min(BigInteger.valueOf(amount)).intValue();
        ItemStack result = representatives.get(slot).copyWithCount(removed);
        amounts[slot] = amounts[slot].subtract(BigInteger.valueOf(removed));
        if (amounts[slot].signum() == 0) representatives.set(slot, ItemStack.EMPTY);
        setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, Integer.MAX_VALUE); }
    @Override public void setItem(int slot, ItemStack stack) {
        if (!valid(slot)) return;
        if (stack == null || stack.isEmpty()) {
            representatives.set(slot, ItemStack.EMPTY);
            amounts[slot] = BigInteger.ZERO;
            setChanged();
            return;
        }
        ItemStack old = representatives.get(slot);
        if (!old.isEmpty() && ItemStack.isSameItemSameComponents(old, stack)) {
            // Container slots expose one representative item; vanilla slot operations
            // write back the new visible count. Only the increase is added.
            int delta = Math.max(0, stack.getCount() - 1);
            if (delta > 0) amounts[slot] = amounts[slot].add(BigInteger.valueOf(delta)).min(MAX_COUNT);
        } else {
            representatives.set(slot, stack.copyWithCount(1));
            amounts[slot] = BigInteger.valueOf(stack.getCount()).min(MAX_COUNT);
        }
        setChanged();
    }
    /**
     * Inserts a bounded ItemStack into this virtual slot. This is the GUI/quick-move
     * entry point; hopper insertion continues to use the vanilla Container contract.
     */
    public int insertExact(int slot, ItemStack stack, int requested) {
        if (!valid(slot) || stack == null || stack.isEmpty() || requested <= 0) return 0;
        ItemStack old = representatives.get(slot);
        if (!old.isEmpty() && !ItemStack.isSameItemSameComponents(old, stack)) return 0;
        int accepted = BigInteger.valueOf(requested)
                .min(MAX_COUNT.subtract(amounts[slot]).max(BigInteger.ZERO))
                .intValue();
        if (accepted <= 0) return 0;
        if (old.isEmpty()) representatives.set(slot, stack.copyWithCount(1));
        amounts[slot] = amounts[slot].add(BigInteger.valueOf(accepted));
        setChanged();
        return accepted;
    }

    /**
     * Applies the visible 64-item GUI projection as an absolute value. A decrease
     * removes only the difference from the visible portion; hidden excess remains.
     */
    public void setGuiItem(int slot, ItemStack stack) {
        if (!valid(slot)) return;
        if (stack == null || stack.isEmpty()) {
            representatives.set(slot, ItemStack.EMPTY);
            amounts[slot] = BigInteger.ZERO;
            setChanged();
            return;
        }
        ItemStack old = representatives.get(slot);
        if (!old.isEmpty() && ItemStack.isSameItemSameComponents(old, stack)) {
            int visibleBefore = amounts[slot].min(BigInteger.valueOf(64)).intValue();
            int visibleAfter = Math.min(64, stack.getCount());
            if (visibleAfter > visibleBefore) {
                amounts[slot] = amounts[slot].add(BigInteger.valueOf(visibleAfter - visibleBefore)).min(MAX_COUNT);
            } else if (visibleAfter < visibleBefore) {
                amounts[slot] = amounts[slot].subtract(BigInteger.valueOf(visibleBefore - visibleAfter)).max(BigInteger.ZERO);
                if (amounts[slot].signum() == 0) representatives.set(slot, ItemStack.EMPTY);
            }
        } else {
            representatives.set(slot, stack.copyWithCount(1));
            amounts[slot] = BigInteger.valueOf(stack.getCount()).min(MAX_COUNT);
        }
        setChanged();
    }

    /**
     * Drops this physical chest's exact contents. ItemStack has an int count, so
     * exceptionally large stored values are emitted as multiple int-sized entities.
     */
    public void dropAllContents(Level level, BlockPos pos) {
        if (level == null || level.isClientSide) return;
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        BigInteger intMax = BigInteger.valueOf(Integer.MAX_VALUE);
        for (int slot = 0; slot < 27; slot++) {
            BigInteger remaining = amounts[slot];
            ItemStack template = representatives.get(slot);
            if (remaining.signum() <= 0 || template.isEmpty()) continue;
            while (remaining.signum() > 0) {
                int batch = remaining.min(intMax).intValue();
                ItemEntity entity = new ItemEntity(level, x, y, z, template.copyWithCount(batch));
                entity.setDefaultPickUpDelay();
                level.addFreshEntity(entity);
                remaining = remaining.subtract(BigInteger.valueOf(batch));
            }
            representatives.set(slot, ItemStack.EMPTY);
            amounts[slot] = BigInteger.ZERO;
        }
        setChanged();
    }
    @Override public boolean isEmpty() {
        for (BigInteger amount : amounts) if (amount.signum() > 0) return false;
        return true;
    }
    @Override public void clearContent() {
        for (int i = 0; i < 27; i++) {
            representatives.set(i, ItemStack.EMPTY);
            amounts[i] = BigInteger.ZERO;
        }
        setChanged();
    }
    @Override protected Component getDefaultName() {
        return Component.translatable("container.whimsy_automation.big_capacity_chest");
    }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return null; }
    private boolean valid(int slot) { return slot >= 0 && slot < 27; }

    @Override public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag exact = new CompoundTag();
        for (int i = 0; i < 27; i++) if (amounts[i].signum() > 0)
            exact.putString(Integer.toString(i), amounts[i].toString());
        tag.put("BigCapacityAmounts", exact);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        amounts = emptyAmounts();
        CompoundTag exact = tag.getCompound("BigCapacityAmounts");
        for (int i = 0; i < 27; i++) {
            String key = Integer.toString(i);
            if (exact.contains(key)) {
                try {
                    BigInteger value = new BigInteger(exact.getString(key));
                    if (value.signum() > 0) amounts[i] = value.min(MAX_COUNT);
                } catch (NumberFormatException ignored) { amounts[i] = BigInteger.ZERO; }
            } else if (!representatives.get(i).isEmpty()) {
                amounts[i] = BigInteger.valueOf(representatives.get(i).getCount());
                representatives.set(i, representatives.get(i).copyWithCount(1));
            }
            if (amounts[i].signum() == 0) representatives.set(i, ItemStack.EMPTY);
        }
    }
}
