package dev.whimsyautomation.block;

import dev.whimsyautomation.block.entity.EnchantmentExtractorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class EnchantmentExtractorBlock extends BaseEntityBlock {
    public EnchantmentExtractorBlock() {
        super(BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnchantmentExtractorBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
