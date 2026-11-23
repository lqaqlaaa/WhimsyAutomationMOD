package dev.whimsyautomation.menu;

import dev.whimsyautomation.block.entity.BigCapacityChestBlockEntity;
import dev.whimsyautomation.registry.ModMenus;
import dev.whimsyautomation.network.BigCapacityChestSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public final class BigCapacityChestMenu extends ChestMenu {
    private final BigCapacityChestBlockEntity first;
    private final BigCapacityChestBlockEntity second;
    private final int rows;
    private final Level clientLevel;
    private final BlockPos firstPos;
    private final BlockPos secondPos;
    private final Player viewer;
    private BigInteger[] syncedCounts = new BigInteger[54];
    private List<String> lastSentCounts = List.of();

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
        this.viewer = inventory.player;
        java.util.Arrays.fill(this.syncedCounts, BigInteger.ZERO);

        // Replace only the server-side chest slots. Their visible ItemStack is a
        // 64-item projection, while all mutations go directly to the exact count.
        if (first != null) {
            for (int i = 0; i < rows * 9; i++) {
                BigCapacityChestBlockEntity owner = i < 27 ? first : second;
                int localSlot = i < 27 ? i : i - 27;
                if (owner != null) {
                    slots.set(i, new BigCapacitySlot(owner, localSlot, 8 + (i % 9) * 18,
                            18 + (i / 9) * 18));
                }
            }
        }
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
    public ItemStack quickMoveStack(Player player, int index) {
        int chestSlots = rows * 9;
        if (index < 0 || index >= slots.size() || first == null) return ItemStack.EMPTY;
        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) return ItemStack.EMPTY;

        if (index < chestSlots) {
            BigCapacityChestBlockEntity owner = index < 27 ? first : second;
            int local = index < 27 ? index : index - 27;
            if (owner == null) return ItemStack.EMPTY;
            BigInteger remaining = owner.getExactCount(local);
            if (remaining.signum() <= 0) return ItemStack.EMPTY;
            ItemStack template = owner.getItem(local);
            int movedTotal = 0;
            while (remaining.signum() > 0) {
                int batchSize = remaining.min(BigInteger.valueOf(64)).intValue();
                ItemStack batch = template.copyWithCount(batchSize);
                if (!moveItemStackTo(batch, chestSlots, slots.size(), true)) break;
                int moved = batchSize - batch.getCount();
                if (moved <= 0) break;
                ItemStack extracted = owner.removeItem(local, moved);
                movedTotal += extracted.getCount();
                remaining = remaining.subtract(BigInteger.valueOf(extracted.getCount()));
                if (extracted.getCount() != moved) break;
            }
            if (movedTotal <= 0) return ItemStack.EMPTY;
            ItemStack result = template.copyWithCount(Math.min(64, movedTotal));
            sourceSlot.onTake(player, result);
            sourceSlot.setChanged();
            return result;
        }

        ItemStack original = sourceSlot.getItem().copy();
        ItemStack moving = original.copy();
        int insertedTotal = 0;
        for (int target = 0; target < chestSlots && !moving.isEmpty(); target++) {
            BigCapacityChestBlockEntity owner = target < 27 ? first : second;
            int local = target < 27 ? target : target - 27;
            if (owner == null) continue;
            int inserted = owner.insertExact(local, moving, moving.getCount());
            if (inserted > 0) {
                moving.shrink(inserted);
                insertedTotal += inserted;
            }
        }
        if (insertedTotal <= 0) return ItemStack.EMPTY;
        sourceSlot.set(moving.isEmpty() ? ItemStack.EMPTY : moving);
        sourceSlot.onTake(player, original.copyWithCount(insertedTotal));
        sourceSlot.setChanged();
        return original;
    }

    private static final class BigCapacitySlot extends Slot {
        private final BigCapacityChestBlockEntity owner;
        private final int localSlot;

        private BigCapacitySlot(BigCapacityChestBlockEntity owner, int localSlot, int x, int y) {
            super(owner, localSlot, x, y);
            this.owner = owner;
            this.localSlot = localSlot;
        }

        @Override public ItemStack getItem() { return owner.getDisplayItem(localSlot); }
        @Override public boolean hasItem() { return !owner.getExactCount(localSlot).equals(BigInteger.ZERO); }
        @Override public void set(ItemStack stack) { owner.setGuiItem(localSlot, stack); }
        @Override public ItemStack remove(int amount) { return owner.removeItem(localSlot, amount); }
        @Override public void setChanged() { owner.setChanged(); }
        @Override public boolean mayPlace(ItemStack stack) { return !stack.isEmpty(); }
        @Override public int getMaxStackSize() { return Integer.MAX_VALUE; }
        @Override public int getMaxStackSize(ItemStack stack) { return Integer.MAX_VALUE; }

        @Override
        public ItemStack safeInsert(ItemStack stack, int increment) {
            if (stack.isEmpty() || increment <= 0 || !mayPlace(stack)) return stack;
            int offered = Math.min(increment, stack.getCount());
            int inserted = owner.insertExact(localSlot, stack, offered);
            return inserted <= 0 ? stack : stack.copyWithCount(stack.getCount() - inserted);
        }

        @Override
        public ItemStack safeTake(int amount, int maxAmount, Player player) {
            int visible = getItem().getCount();
            int taken = Math.min(Math.min(amount, maxAmount), visible);
            return taken <= 0 ? ItemStack.EMPTY : owner.removeItem(localSlot, taken);
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (first == null || !(viewer instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || serverPlayer.containerMenu != this) return;
        List<String> current = new ArrayList<>(rows * 9);
        for (int i = 0; i < rows * 9; i++) {
            BigCapacityChestBlockEntity owner = i < 27 ? first : second;
            int local = i < 27 ? i : i - 27;
            current.add(owner == null ? "0" : owner.getExactCount(local).toString());
        }
        if (!current.equals(lastSentCounts)) {
            lastSentCounts = List.copyOf(current);
            BigCapacityChestSyncPayload.send(serverPlayer, containerId, current);
        }
    }

    public void applySyncedCounts(List<String> values) {
        java.util.Arrays.fill(syncedCounts, BigInteger.ZERO);
        for (int i = 0; i < Math.min(values.size(), rows * 9); i++) {
            try {
                BigInteger parsed = new BigInteger(values.get(i));
                syncedCounts[i] = parsed.signum() > 0 ? parsed.min(BigCapacityChestBlockEntity.MAX_COUNT) : BigInteger.ZERO;
            } catch (NumberFormatException ignored) {
                syncedCounts[i] = BigInteger.ZERO;
            }
        }
    }

    public int getRows() { return rows; }

    public BigInteger exactCount(int menuSlot) {
        if (menuSlot < 0 || menuSlot >= rows * 9) return BigInteger.ZERO;
        if (clientLevel != null) return syncedCounts[menuSlot];
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
