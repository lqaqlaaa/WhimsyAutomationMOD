package dev.whimsyautomation.camouflage;

import dev.whimsyautomation.block.CamouflageMachineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Implemented by every machine that can display a camouflage block.
 * The stored state is visual-only; it never replaces the machine's real BlockState.
 */
public interface CamouflageableBlockEntity {
    BlockState getCamouflageState();
    void setCamouflageState(BlockState state);

    /**
     * Keep the state-level occlusion flag synchronized with the disguised
     * block. The server block-state update then rebuilds client chunk meshes.
     */
    default void syncCamouflageOcclusionProperty() {
        if (!(this instanceof BlockEntity blockEntity)) return;
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide) return;

        BlockState machineState = level.getBlockState(blockEntity.getBlockPos());
        if (!machineState.hasProperty(CamouflageMachineBlock.CAMOUFLAGE_SOLID)) return;

        BlockState camouflage = getCamouflageState();
        boolean solid = camouflage != null && !camouflage.isAir() && camouflage.canOcclude();
        if (machineState.getValue(CamouflageMachineBlock.CAMOUFLAGE_SOLID) != solid) {
            level.setBlock(blockEntity.getBlockPos(),
                    machineState.setValue(CamouflageMachineBlock.CAMOUFLAGE_SOLID, solid), 2);
        }

        // The light engine caches opacity and sky-light propagation separately
        // from chunk model data. Invalidate the disguise position explicitly.
        level.getChunkSource().getLightEngine().checkBlock(blockEntity.getBlockPos());
    }

    default void dropCamouflage() {
        if (!(this instanceof BlockEntity blockEntity) || blockEntity.getLevel() == null || blockEntity.getLevel().isClientSide) return;
        BlockState state = getCamouflageState();
        if (state == null || state.isAir()) return;
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (!stack.isEmpty()) Containers.dropItemStack(blockEntity.getLevel(), blockEntity.getBlockPos().getX() + 0.5,
                blockEntity.getBlockPos().getY() + 0.5, blockEntity.getBlockPos().getZ() + 0.5, stack);
        setCamouflageState(Blocks.AIR.defaultBlockState());
    }

    default boolean hasCamouflage() {
        BlockState state = getCamouflageState();
        return state != null && !state.isAir();
    }
}
