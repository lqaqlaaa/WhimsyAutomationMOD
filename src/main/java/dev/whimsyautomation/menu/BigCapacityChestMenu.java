package dev.whimsyautomation.menu;

import dev.whimsyautomation.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class BigCapacityChestMenu extends ChestMenu {
    public BigCapacityChestMenu(int id, Inventory playerInventory, Container container, int rows) {
        super(ModMenus.BIG_CAPACITY_CHEST.get(), id, playerInventory, container, rows);
        // A stored slot may contain more than a legal player stack. Every manual
        // extraction is limited to the item's normal max stack size; the balance
        // remains in the chest for the next pickup.
        for (int slotIndex = 0; slotIndex < container.getContainerSize(); slotIndex++) {
            Slot original = this.slots.get(slotIndex);
            this.slots.set(slotIndex, new Slot(container, slotIndex, original.x, original.y) {
                @Override
                public ItemStack remove(int amount) {
                    ItemStack present = getItem();
                    if (present.isEmpty()) return ItemStack.EMPTY;
                    return super.remove(Math.min(amount, present.getMaxStackSize()));
                }
            });
        }
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
