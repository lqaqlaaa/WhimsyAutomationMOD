package dev.whimsyautomation.menu;

import dev.whimsyautomation.block.entity.BigCapacityChestBlockEntity;
import dev.whimsyautomation.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.math.BigInteger;

public final class BigCapacityChestMenu extends ChestMenu {
    private final BigCapacityChestBlockEntity first;
    private final BigCapacityChestBlockEntity second;
    private final int rows;
    public BigCapacityChestMenu(int id, Inventory inventory, BigCapacityChestBlockEntity first,
                                BigCapacityChestBlockEntity second, BlockPos pos, int rows) {
        this(id, inventory, combined(first, second, rows), first, second, rows);
    }
    public BigCapacityChestMenu(int id, Inventory inventory, Container container,
                                BigCapacityChestBlockEntity first, BigCapacityChestBlockEntity second,
                                BlockPos pos, int rows) {
        super(rows == 6 ? ModMenus.BIG_CAPACITY_CHEST_6.get() : ModMenus.BIG_CAPACITY_CHEST_3.get(),
                id, inventory, container, rows);
        this.first = first;
        this.second = second;
        this.rows = rows;
    }
    public BigCapacityChestMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, clientContainer(inventory, data.readBlockPos()));
    }
    private BigCapacityChestMenu(int id, Inventory inventory, Setup setup) {
        this(id, inventory, setup.container, setup.first, setup.second, setup.rows == 6 ? null : null, setup.rows);
    }
    private static Container combined(BigCapacityChestBlockEntity a, BigCapacityChestBlockEntity b, int rows) {
        if (rows == 6 && b != null) return new CompoundContainer(a, b);
        return a == null ? new SimpleContainer(rows * 9) : a;
    }
    private static Setup clientContainer(Inventory inventory, BlockPos pos) {
        var level = inventory.player.level();
        if (level.getBlockEntity(pos) instanceof BigCapacityChestBlockEntity a) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(BlockStateProperties.CHEST_TYPE)
                    && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE) {
                Direction direction = ChestBlock.getConnectedDirection(state);
                if (level.getBlockEntity(pos.relative(direction)) instanceof BigCapacityChestBlockEntity b)
                    return new Setup(new CompoundContainer(a, b), a, b, 6);
            }
            return new Setup(a, a, null, 3);
        }
        return new Setup(new SimpleContainer(27), null, null, 3);
    }
    private record Setup(Container container, BigCapacityChestBlockEntity first,
                         BigCapacityChestBlockEntity second, int rows) {}
    @Override public net.minecraft.world.item.ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        net.minecraft.world.inventory.Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = sourceSlot.getItem().copy();
        ItemStack moving = original.copy();
        int chestSlots = rows * 9;
        boolean moved;
        if (index < chestSlots) {
            moved = moveItemStackTo(moving, chestSlots, slots.size(), true);
        } else {
            moved = moveItemStackTo(moving, 0, chestSlots, false);
        }
        if (!moved) return ItemStack.EMPTY;
        int amountMoved = original.getCount() - moving.getCount();
        if (amountMoved <= 0) return ItemStack.EMPTY;
        // Remove only the amount actually transferred. Vanilla ChestMenu's
        // default quick-move clears a slot when its 64-item preview is emptied,
        // which would otherwise erase the remainder of a BigInteger-sized stack.
        sourceSlot.remove(amountMoved);
        sourceSlot.onTake(player, original.copyWithCount(amountMoved));
        sourceSlot.setChanged();
        return original;
    }

    public BigInteger exactCount(int menuSlot) {
        if (menuSlot < 0 || menuSlot >= rows * 9) return BigInteger.ZERO;
        BigCapacityChestBlockEntity owner = menuSlot < 27 || second == null ? first : second;
        int local = menuSlot < 27 || second == null ? menuSlot : menuSlot - 27;
        return owner == null ? BigInteger.ZERO : owner.getExactCount(local);
    }
}
