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
import java.util.Arrays;

/** A vanilla chest container whose exact per-slot amounts are stored independently of ItemStack's int count. */
public final class BigCapacityChestBlockEntity extends ChestBlockEntity {
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
    @Override public int getMaxStackSize() { return Integer.MAX_VALUE; }
    /**
     * Container#getItem must return the live stack reference. Vanilla hopper insertion
     * merges by mutating that returned stack in-place and then calling setChanged();
     * returning a copy silently loses every merged item.
     */
    @Override public ItemStack getItem(int slot) {
        if (!valid(slot)) return ItemStack.EMPTY;
        reconcileSlot(slot);
        return representatives.get(slot);
    }

    public ItemStack getDisplayItem(int slot) {
        if (!valid(slot)) return ItemStack.EMPTY;
        reconcileSlot(slot);
        if (amounts[slot].signum() <= 0 || representatives.get(slot).isEmpty()) return ItemStack.EMPTY;
        return representatives.get(slot).copyWithCount(amounts[slot].min(BigInteger.valueOf(64)).intValue());
    }

    public ItemStack getAutomationStack(int slot) {
        if (!valid(slot)) return ItemStack.EMPTY;
        reconcileSlot(slot);
        if (amounts[slot].signum() <= 0 || representatives.get(slot).isEmpty()) return ItemStack.EMPTY;
        return representatives.get(slot).copyWithCount(amounts[slot].min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue());
    }

    public BigInteger getExactCount(int slot) {
        if (!valid(slot)) return BigInteger.ZERO;
        reconcileSlot(slot);
        return amounts[slot];
    }

    private void reconcileSlot(int slot) {
        if (!valid(slot)) return;
        ItemStack stack = representatives.get(slot);
        if (stack == ItemStack.EMPTY) {
            if (amounts[slot].signum() <= 0) amounts[slot] = BigInteger.ZERO;
            return;
        }
        int delta = stack.getCount() - 1;
        if (delta == 0) return;
        BigInteger next = amounts[slot].add(BigInteger.valueOf(delta)).max(BigInteger.ZERO).min(MAX_COUNT);
        amounts[slot] = next;
        if (next.signum() <= 0 || stack.isEmpty()) {
            representatives.set(slot, ItemStack.EMPTY);
            amounts[slot] = BigInteger.ZERO;
        } else {
            stack.setCount(1);
        }
    }

    private void reconcileAll() {
        for (int slot = 0; slot < 27; slot++) reconcileSlot(slot);
    }

    /** Returns a single- or double-chest automation view, ordered LEFT then RIGHT. */
    public IItemHandler getAutomationItemHandler(net.minecraft.core.Direction side) {
        if (level == null || !getBlockState().hasProperty(BlockStateProperties.CHEST_TYPE)
                || getBlockState().getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE) {
            return new BigCapacityChestItemHandler(this, null);
        }
        BlockState state = getBlockState();
        BlockPos partnerPos = worldPosition.relative(ChestBlock.getConnectedDirection(state));
        if (!(level.getBlockEntity(partnerPos) instanceof BigCapacityChestBlockEntity partner)) {
            return new BigCapacityChestItemHandler(this, null);
        }
        return state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.RIGHT
                ? new BigCapacityChestItemHandler(partner, this)
                : new BigCapacityChestItemHandler(this, partner);
    }
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
        reconcileSlot(slot);
        if (stack == null || stack.isEmpty()) {
            representatives.set(slot, ItemStack.EMPTY);
            amounts[slot] = BigInteger.ZERO;
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
        reconcileSlot(slot);
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
        reconcileSlot(slot);
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
        reconcileAll();
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
        reconcileAll();
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
        reconcileAll();
        super.setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        reconcileAll();
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
