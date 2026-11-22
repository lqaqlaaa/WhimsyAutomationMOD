package dev.whimsyautomation.block;

import com.mojang.serialization.MapCodec;
import dev.whimsyautomation.block.entity.BigCapacityChestBlockEntity;
import dev.whimsyautomation.menu.BigCapacityChestMenu;
import dev.whimsyautomation.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;

public final class BigCapacityChestBlock extends ChestBlock {
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (ChestBlock.isChestBlockedAt(level, pos)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        MenuProvider provider = getMenuProvider(state, level, pos);
        if (provider != null) {
            if (!level.isClientSide) {
                player.openMenu(provider, buffer -> buffer.writeBlockPos(pos));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (ChestBlock.isChestBlockedAt(level, pos)) return null;
        if (!(level.getBlockEntity(pos) instanceof BigCapacityChestBlockEntity first)) return null;

        Component title = Component.translatable("container.whimsy_automation.big_capacity_chest");
        if (state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE) {
            return new SimpleMenuProvider((id, inv, player) ->
                    new BigCapacityChestMenu(id, inv, first, null, pos, 3), title);
        }

        Direction direction = ChestBlock.getConnectedDirection(state);
        if (!(level.getBlockEntity(pos.relative(direction)) instanceof BigCapacityChestBlockEntity second)) return null;
        return new SimpleMenuProvider((id, inv, player) ->
                new BigCapacityChestMenu(id, inv, first, second, pos, 6), title);
    }
}
