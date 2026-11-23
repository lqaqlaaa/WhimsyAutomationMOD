package dev.whimsyautomation.block;

import com.mojang.serialization.MapCodec;
import dev.whimsyautomation.block.entity.BigCapacityChestBlockEntity;
import dev.whimsyautomation.registry.ModBlockEntities;
import net.minecraft.world.level.block.BlockBehaviour;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A vanilla chest block with the vanilla double-chest state machine and menu behavior.
 * The inventory implementation lives in BigCapacityChestBlockEntity.
 */
public final class BigCapacityChestBlock extends ChestBlock {
    public static final MapCodec<BigCapacityChestBlock> CODEC = MapCodec.unit(BigCapacityChestBlock::new);

    public BigCapacityChestBlock() {
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST), ModBlockEntities.BIG_CAPACITY_CHEST::get);
    }

    @Override
    public MapCodec<? extends ChestBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BigCapacityChestBlockEntity(pos, state);
    }
}
