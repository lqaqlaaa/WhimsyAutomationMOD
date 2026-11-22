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

    /** Server menu: receives the real vanilla chest container (BE or CompoundContainer). */
    public BigCapacityChestMenu(int id, Inventory inventory, Container vanillaContainer,
                                BigCapacityChestBlockEntity first, BigCapacityChestBlockEntity second,
                                BlockPos firstPos, BlockPos secondPos, int rows) {
        this(id, inventory, vanillaContainer, first, second, rows, null, firstPos, secondPos);
    }

    private BigCapacityChestMenu(int id, Inventory inventory, Container container,
                                 BigCapacityChestBlockEntity first, BigCapacityChestBlockEntity second,
                                 int rows, Level clientLevel, BlockPos firstPos, BlockPos secondPos) {
        super(rows == 6 ? ModMenus.BIG_CAPACITY_CHEST_6.get() : ModMenus.BIG_CAPACITY_CHEST_3.get(),
                id, inventory, container, rows);
        this.first = first;
        this.second = second;
        this.rows = rows;
        this.clientLevel = clientLevel;
        this.firstPos = firstPos;
        this.secondPos = secondPos;
    }

    /** Client menu uses a normal mirror container; server slot packets populate it. */
    public BigCapacityChestMenu(int id, Inventory inventory, net.minecraft.network.FriendlyByteBuf data) {
        this(id, inventory, clientSetup(inventory, data.readBlockPos()));
    }

    private BigCapacityChestMenu(int id, Inventory inventory, Setup setup) {
        this(id, inventory, setup.container, null, null, setup.rows,
                inventory.player.level(), setup.firstPos, setup.secondPos);
    }

    private static Setup clientSetup(Inventory inventory, BlockPos clickedPos) {
        Level level = inventory.player.level();
        BlockState state = level.getBlockState(clickedPos);
        int rows = 3;
        BlockPos firstPos = clickedPos;
        BlockPos secondPos = null;
        if (state.hasProperty(BlockStateProperties.CHEST_TYPE)
                && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE) {
            Direction connected = ChestBlock.getConnectedDirection(state);
            secondPos = clickedPos.relative(connected);
            rows = 6;
            if (state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.RIGHT) {
                firstPos = secondPos;
                secondPos = clickedPos;
            }
        }
        return new Setup(new SimpleContainer(rows * 9), firstPos, secondPos, rows);
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

    public int getRows() { return rows; }

    public BigInteger exactCount(int menuSlot) {
        if (menuSlot < 0 || menuSlot >= rows * 9) return BigInteger.ZERO;
        boolean useFirst = menuSlot < 27 || rows == 3;
        BigCapacityChestBlockEntity owner;
        if (useFirst) {
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
