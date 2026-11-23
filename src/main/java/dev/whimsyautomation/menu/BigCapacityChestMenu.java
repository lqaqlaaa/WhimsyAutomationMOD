package dev.whimsyautomation.menu;

import dev.whimsyautomation.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class BigCapacityChestMenu extends ChestMenu {
    public BigCapacityChestMenu(int id, Inventory playerInventory, Container container, int rows) {
        super(ModMenus.BIG_CAPACITY_CHEST.get(), id, playerInventory, container, rows);
    }

    public BigCapacityChestMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(id, playerInventory, resolveClientContainer(playerInventory, data.readBlockPos()));
    }

    private BigCapacityChestMenu(int id, Inventory playerInventory, Container container) {
        this(id, playerInventory, container, Math.max(3, Math.min(6, container.getContainerSize() / 9)));
    }

    private static Container resolveClientContainer(Inventory playerInventory, BlockPos pos) {
        Level level = playerInventory.player.level();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            Container combined = ChestBlock.getContainer(chestBlock, state, level, pos, true);
            if (combined != null) return combined;
        }
        return new SimpleContainer(27);
    }
}
