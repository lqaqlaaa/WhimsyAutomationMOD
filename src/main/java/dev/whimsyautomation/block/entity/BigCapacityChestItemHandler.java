package dev.whimsyautomation.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.items.IItemHandler;

import java.math.BigInteger;

/**
 * NeoForge automation view over a physical chest. The view resolves its partner
 * dynamically so a capability cached while the chest was single still sees the
 * correct 54-slot inventory after it is joined to another chest.
 */
public final class BigCapacityChestItemHandler implements IItemHandler {
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private final BigCapacityChestBlockEntity anchor;

    public BigCapacityChestItemHandler(BigCapacityChestBlockEntity anchor) {
        this.anchor = anchor;
    }

    @Override
    public int getSlots() {
        return parts().right == null ? 27 : 54;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        BigCapacityChestBlockEntity owner = owner(slot);
        return owner == null ? ItemStack.EMPTY : owner.getAutomationStack(localSlot(slot));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        BigCapacityChestBlockEntity owner = owner(slot);
        if (owner == null) return stack;

        int local = localSlot(slot);
        ItemStack existing = owner.getItem(local);
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return stack;

        BigInteger room = BigCapacityChestBlockEntity.MAX_COUNT.subtract(owner.getExactCount(local)).max(BigInteger.ZERO);
        int accepted = BigInteger.valueOf(stack.getCount()).min(room).intValue();
        if (accepted <= 0) return stack;
        if (!simulate) accepted = owner.insertExact(local, stack, accepted);
        return accepted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - accepted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        BigCapacityChestBlockEntity owner = owner(slot);
        if (owner == null) return ItemStack.EMPTY;

        int local = localSlot(slot);
        ItemStack existing = owner.getAutomationStack(local);
        if (existing.isEmpty()) return ItemStack.EMPTY;
        int extracted = BigInteger.valueOf(amount).min(owner.getExactCount(local)).min(INT_MAX).intValue();
        if (extracted <= 0) return ItemStack.EMPTY;
        return simulate ? existing.copyWithCount(extracted) : owner.removeItem(local, extracted);
    }

    @Override
    public int getSlotLimit(int slot) {
        return owner(slot) == null ? 0 : Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return owner(slot) != null && !stack.isEmpty();
    }

    private BigCapacityChestBlockEntity owner(int slot) {
        if (slot < 0) return null;
        Parts parts = parts();
        if (slot < 27) return parts.left;
        return slot < 54 ? parts.right : null;
    }

    private int localSlot(int slot) {
        return slot < 27 ? slot : slot - 27;
    }

    private Parts parts() {
        BlockState state = anchor.getBlockState();
        if (anchor.getLevel() == null || !state.hasProperty(BlockStateProperties.CHEST_TYPE)
                || state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE) {
            return new Parts(anchor, null);
        }

        BlockPos partnerPos = anchor.getBlockPos().relative(ChestBlock.getConnectedDirection(state));
        if (!(anchor.getLevel().getBlockEntity(partnerPos) instanceof BigCapacityChestBlockEntity partner)) {
            return new Parts(anchor, null);
        }
        return state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.RIGHT
                ? new Parts(partner, anchor)
                : new Parts(anchor, partner);
    }

    private record Parts(BigCapacityChestBlockEntity left, BigCapacityChestBlockEntity right) {}
}
