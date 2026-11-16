package dev.whimsyautomation.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jetbrains.annotations.Nullable;

/**
 * A read-only view in which the machine position appears as its camouflage
 * state. Neighbours and biome/light context remain those of the real level.
 * The machine BlockEntity is intentionally hidden: copying arbitrary foreign
 * BlockEntity data is not safe and is handled as a separate compatibility tier.
 */
public final class CamouflageLevelView implements BlockAndTintGetter {
    private final BlockAndTintGetter delegate;
    private final BlockPos targetPos;
    private final BlockState targetState;

    public CamouflageLevelView(BlockAndTintGetter delegate, BlockPos targetPos, BlockState targetState) {
        this.delegate = delegate;
        this.targetPos = targetPos.immutable();
        this.targetState = targetState;
    }

    @Override public BlockState getBlockState(BlockPos pos) {
        return targetPos.equals(pos) ? targetState : delegate.getBlockState(pos);
    }

    @Nullable
    @Override public BlockEntity getBlockEntity(BlockPos pos) {
        return targetPos.equals(pos) ? null : delegate.getBlockEntity(pos);
    }

    @Override public FluidState getFluidState(BlockPos pos) {
        return targetPos.equals(pos) ? targetState.getFluidState() : delegate.getFluidState(pos);
    }

    @Override public float getShade(Direction direction, boolean shade) {
        return delegate.getShade(direction, shade);
    }

    @Override public LevelLightEngine getLightEngine() {
        return delegate.getLightEngine();
    }

    @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return delegate.getBlockTint(pos, resolver);
    }

    @Override public int getHeight() { return delegate.getHeight(); }

    @Override public int getMinBuildHeight() { return delegate.getMinBuildHeight(); }
}
