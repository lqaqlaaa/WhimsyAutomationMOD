package dev.whimsyautomation.menu;

import dev.whimsyautomation.block.entity.BigCapacityChestBlockEntity;
import dev.whimsyautomation.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.math.BigInteger;

public final class BigCapacityChestMenu extends ChestMenu {
    private final BigCapacityChestBlockEntity first;
    private final BigCapacityChestBlockEntity second;
    private final int rows;
    private final Level clientLevel;
    private final BlockPos firstPos;
    private final BlockPos secondPos;

    public BigCapacityChestMenu(int id, Inventory inventory, BigCapacityChestBlockEntity first,
                                BigCapacityChestBlockEntity second, BlockPos pos, int rows) {
        this(id, inventory, new GuiChestContainer(first, second, rows), first, second,
                pos, rows, null, pos,
                second == null ? null : pos.relative(ChestBlock.getConnectedDirection(first.getBlockState())));
    }

    private BigCapacityChestMenu(int id, Inventory inventory, Container container,
                                 BigCapacityChestBlockEntity first, BigCapacityChestBlockEntity second,
                                 BlockPos pos, int rows, Level clientLevel,
                                 BlockPos firstPos, BlockPos secondPos) {
        super(rows == 6 ? ModMenus.BIG_CAPACITY_CHEST_6.get() : ModMenus.BIG_CAPACITY_CHEST_3.get(),
                id, inventory, container, rows);
        this.first = first;
        this.second = second;
        this.rows = rows;
        this.clientLevel = clientLevel;
        this.firstPos = firstPos;
        this.secondPos = secondPos;
    }

    public BigCapacityChestMenu(int id, Inventory inventory, net.minecraft.network.FriendlyByteBuf data) {
        this(id, inventory, clientSetup(inventory, data.readBlockPos()));
    }

    private BigCapacityChestMenu(int id, Inventory inventory, Setup setup) {
        this(id, inventory, setup.container, null, null, setup.firstPos, setup.rows,
                inventory.player.level(), setup.firstPos, setup.secondPos);
    }

    private static Setup clientSetup(Inventory inventory, BlockPos pos) {
        Level level = inventory.player.level();
        BlockState state = level.getBlockState(pos);
        int rows = 3;
        BlockPos otherPos = null;
        if (state.hasProperty(BlockStateProperties.CHEST_TYPE)
                && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE) {
            Direction direction = ChestBlock.getConnectedDirection(state);
            otherPos = pos.relative(direction);
            rows = 6;
        }
        return new Setup(new GuiChestContainer(level, pos, otherPos, rows), pos, otherPos, rows);
    }

    private record Setup(Container container, BlockPos firstPos, BlockPos secondPos, int rows) {}

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        net.minecraft.world.inventory.Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = sourceSlot.getItem().copy();
        ItemStack moving = original.copy();
        int chestSlots = rows * 9;
        boolean moved = index < chestSlots
                ? moveItemStackTo(moving, chestSlots, slots.size(), true)
                : moveItemStackTo(moving, 0, chestSlots, false);
        if (!moved) return ItemStack.EMPTY;
        int amountMoved = original.getCount() - moving.getCount();
        if (amountMoved <= 0) return ItemStack.EMPTY;
        sourceSlot.remove(amountMoved);
        sourceSlot.onTake(player, original.copyWithCount(amountMoved));
        sourceSlot.setChanged();
        return original;
    }

    private static final class GuiChestContainer implements Container {
        private final BigCapacityChestBlockEntity left;
        private final BigCapacityChestBlockEntity right;
        private final Level level;
        private final BlockPos firstPos;
        private final BlockPos secondPos;
        private final int rows;

        GuiChestContainer(BigCapacityChestBlockEntity left, BigCapacityChestBlockEntity right, int rows) {
            this.left = left;
            this.right = right;
            this.level = null;
            this.firstPos = null;
            this.secondPos = null;
            this.rows = rows;
        }

        GuiChestContainer(Level level, BlockPos firstPos, BlockPos secondPos, int rows) {
            this.left = null;
            this.right = null;
            this.level = level;
            this.firstPos = firstPos;
            this.secondPos = secondPos;
            this.rows = rows;
        }

        private BigCapacityChestBlockEntity first() {
            if (left != null) return left;
            return level != null && level.getBlockEntity(firstPos) instanceof BigCapacityChestBlockEntity be ? be : null;
        }

        private BigCapacityChestBlockEntity second() {
            if (right != null) return right;
            return level != null && secondPos != null
                    && level.getBlockEntity(secondPos) instanceof BigCapacityChestBlockEntity be ? be : null;
        }

        private BigCapacityChestBlockEntity owner(int slot) {
            return slot < 27 || rows == 3 ? first() : second();
        }

        private int local(int slot) {
            return slot < 27 || rows == 3 ? slot : slot - 27;
        }

        @Override public int getContainerSize() { return rows * 9; }
        @Override public boolean isEmpty() {
            BigCapacityChestBlockEntity a = first(), b = second();
            return (a == null || a.isEmpty()) && (b == null || b.isEmpty());
        }
        @Override public ItemStack getItem(int slot) {
            if (slot < 0 || slot >= getContainerSize()) return ItemStack.EMPTY;
            BigCapacityChestBlockEntity be = owner(slot);
            return be == null ? ItemStack.EMPTY : be.getDisplayItem(local(slot));
        }
        @Override public ItemStack removeItem(int slot, int amount) {
            if (slot < 0 || slot >= getContainerSize()) return ItemStack.EMPTY;
            BigCapacityChestBlockEntity be = owner(slot);
            return be == null ? ItemStack.EMPTY : be.removeItem(local(slot), amount);
        }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            if (slot < 0 || slot >= getContainerSize()) return ItemStack.EMPTY;
            BigCapacityChestBlockEntity be = owner(slot);
            return be == null ? ItemStack.EMPTY : be.removeItemNoUpdate(local(slot));
        }
        @Override public void setItem(int slot, ItemStack stack) {
            if (slot < 0 || slot >= getContainerSize()) return;
            BigCapacityChestBlockEntity be = owner(slot);
            if (be != null) be.setGuiItem(local(slot), stack);
        }
        @Override public int getMaxStackSize() { return Integer.MAX_VALUE; }
        @Override public void setChanged() {
            BigCapacityChestBlockEntity a = first(), b = second();
            if (a != null) a.setChanged();
            if (b != null) b.setChanged();
        }
        @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            BigCapacityChestBlockEntity a = first(), b = second();
            return a != null && a.stillValid(player) && (rows == 3 || b != null && b.stillValid(player));
        }
        @Override public void startOpen(net.minecraft.world.entity.player.Player player) {
            BigCapacityChestBlockEntity a = first(), b = second();
            if (a != null) a.startOpen(player);
            if (b != null) b.startOpen(player);
        }
        @Override public void stopOpen(net.minecraft.world.entity.player.Player player) {
            BigCapacityChestBlockEntity a = first(), b = second();
            if (a != null) a.stopOpen(player);
            if (b != null) b.stopOpen(player);
        }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return true; }
        @Override public void clearContent() {
            BigCapacityChestBlockEntity a = first(), b = second();
            if (a != null) a.clearContent();
            if (b != null) b.clearContent();
        }
    }

    public int getRows() { return rows; }

    public BigInteger exactCount(int menuSlot) {
        if (menuSlot < 0 || menuSlot >= rows * 9) return BigInteger.ZERO;
        BigCapacityChestBlockEntity owner;
        if (menuSlot < 27 || rows == 3) {
            owner = first != null ? first
                    : clientLevel != null && clientLevel.getBlockEntity(firstPos) instanceof BigCapacityChestBlockEntity be ? be : null;
        } else {
            owner = second != null ? second
                    : clientLevel != null && secondPos != null
                    && clientLevel.getBlockEntity(secondPos) instanceof BigCapacityChestBlockEntity be ? be : null;
        }
        int local = menuSlot < 27 || rows == 3 ? menuSlot : menuSlot - 27;
        return owner == null ? BigInteger.ZERO : owner.getExactCount(local);
    }
}
