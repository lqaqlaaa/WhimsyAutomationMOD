package dev.whimsyautomation.block.entity;

import dev.whimsyautomation.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class EnchantmentExtractorBlockEntity extends BlockEntity {
    public EnchantmentExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENCHANTMENT_EXTRACTOR.get(), pos, state);
    }
}
