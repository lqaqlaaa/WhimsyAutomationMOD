package dev.whimsyautomation.block;

import com.mojang.serialization.MapCodec;
import dev.whimsyautomation.block.entity.BigCapacityChestBlockEntity;
import dev.whimsyautomation.menu.BigCapacityChestMenu;
import dev.whimsyautomation.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;

public final class BigCapacityChestBlock extends ChestBlock implements WorldlyContainerHolder {
    public static final MapCodec<BigCapacityChestBlock> CODEC = MapCodec.unit(BigCapacityChestBlock::new);

    public BigCapacityChestBlock() {
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST), () -> ModBlockEntities.BIG_CAPACITY_CHEST.get());
    }

    @Override
    public MapCodec<? extends ChestBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BigCapacityChestBlockEntity(pos, state);
    }

    /**
     * Hopper automation must see the same left-to-right 54-slot inventory no
     * matter which half of a double chest the hopper touches.
     */
    @Override
    public WorldlyContainer getContainer(BlockState state, LevelAccessor level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BigCapacityChestBlockEntity clicked)) return null;
        if (state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE) return clicked;

        BlockPos partnerPos = pos.relative(ChestBlock.getConnectedDirection(state));
        if (!(level.getBlockEntity(partnerPos) instanceof BigCapacityChestBlockEntity partner)) return clicked;

        BigCapacityChestBlockEntity left = clicked;
        BigCapacityChestBlockEntity right = partner;
        if (state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.RIGHT) {
            left = partner;
            right = clicked;
        }
        return new DoubleChestAutomationContainer(left, right);
    }

    private static final class DoubleChestAutomationContainer implements WorldlyContainer {
        private static final int[] SLOTS = new int[54];
        static {
            for (int i = 0; i < SLOTS.length; i++) SLOTS[i] = i;
        }
        private final BigCapacityChestBlockEntity left;
        private final BigCapacityChestBlockEntity right;

        private DoubleChestAutomationContainer(BigCapacityChestBlockEntity left,
                                               BigCapacityChestBlockEntity right) {
            this.left = left;
            this.right = right;
        }

        private BigCapacityChestBlockEntity owner(int slot) {
            return slot < 27 ? left : right;
        }
        private int local(int slot) { return slot < 27 ? slot : slot - 27; }
        private boolean valid(int slot) { return slot >= 0 && slot < 54; }

        @Override public int getContainerSize() { return 54; }
        @Override public int[] getSlotsForFace(Direction side) { return SLOTS; }
        @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
            return valid(slot) && !stack.isEmpty();
        }
        @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return valid(slot) && !stack.isEmpty() && owner(slot).getExactCount(local(slot)).signum() > 0;
        }
        @Override public ItemStack getItem(int slot) {
            return valid(slot) ? owner(slot).getItem(local(slot)) : ItemStack.EMPTY;
        }
        @Override public ItemStack removeItem(int slot, int amount) {
            return valid(slot) ? owner(slot).removeItem(local(slot), amount) : ItemStack.EMPTY;
        }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            return valid(slot) ? owner(slot).removeItemNoUpdate(local(slot)) : ItemStack.EMPTY;
        }
        @Override public void setItem(int slot, ItemStack stack) {
            if (valid(slot)) owner(slot).setItem(local(slot), stack);
        }
        @Override public boolean isEmpty() { return left.isEmpty() && right.isEmpty(); }
        @Override public void clearContent() { left.clearContent(); right.clearContent(); }
        @Override public void setChanged() { left.setChanged(); right.setChanged(); }
        @Override public boolean stillValid(Player player) {
            return left.stillValid(player) && right.stillValid(player);
        }
        @Override public void startOpen(Player player) { left.startOpen(player); right.startOpen(player); }
        @Override public void stopOpen(Player player) { left.stopOpen(player); right.stopOpen(player); }
        @Override public int getMaxStackSize() { return Integer.MAX_VALUE; }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) {
            return valid(slot) && !stack.isEmpty();
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof BigCapacityChestBlockEntity chest) {
            // Empty our exact-count store first. ChestBlock's vanilla removal path
            // still performs its normal bookkeeping, but cannot drop the one-item
            // representative stacks a second time.
            chest.dropAllContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // The only interaction customization is transporting the clicked position to the client menu.
    // All chest opening/closing bookkeeping is handled by the vanilla Container implementation.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (ChestBlock.isChestBlockedAt(level, pos)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        MenuProvider provider = getMenuProvider(state, level, pos);
        if (provider == null) return InteractionResult.PASS;
        if (!level.isClientSide) {
            player.openMenu(provider, buffer -> buffer.writeBlockPos(pos));
            player.awardStat(net.minecraft.stats.Stats.OPEN_CHEST);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (ChestBlock.isChestBlockedAt(level, pos)) return null;
        if (!(level.getBlockEntity(pos) instanceof BigCapacityChestBlockEntity clicked)) return null;

        int rows = state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE ? 3 : 6;
        Container vanillaContainer = ChestBlock.getContainer(this, state, level, pos, false);
        if (vanillaContainer == null) return null;

        BigCapacityChestBlockEntity first = clicked;
        BigCapacityChestBlockEntity second = null;
        BlockPos firstPos = pos;
        BlockPos secondPos = null;
        if (rows == 6) {
            Direction connected = ChestBlock.getConnectedDirection(state);
            secondPos = pos.relative(connected);
            if (!(level.getBlockEntity(secondPos) instanceof BigCapacityChestBlockEntity partner)) return null;
            second = partner;

            // Vanilla double-chest inventory order is LEFT then RIGHT.
            if (state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.RIGHT) {
                first = partner;
                second = clicked;
                firstPos = secondPos;
                secondPos = pos;
            }
        }

        Component title = Component.translatable("container.whimsy_automation.big_capacity_chest");
        BigCapacityChestBlockEntity menuFirst = first;
        BigCapacityChestBlockEntity menuSecond = second;
        BlockPos menuFirstPos = firstPos;
        BlockPos menuSecondPos = secondPos;
        return new SimpleMenuProvider((id, inv, player) ->
                new BigCapacityChestMenu(id, inv, vanillaContainer, menuFirst, menuSecond,
                        menuFirstPos, menuSecondPos, rows), title);
    }
}
