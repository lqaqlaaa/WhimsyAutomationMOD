package dev.whimsyautomation.camouflage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Occlusion shape for camouflage machines.
 *
 * The machine's visible rim is a thin perimeter shell. The body occlusion is
 * supplied by the disguised block itself, so partial blocks (stairs, slabs,
 * fences, panes, etc.) do not get treated as full cubes by chunk face culling.
 */
public final class CamouflageOcclusion {
    private static final VoxelShape FRAME = Shapes.or(
            Block.box(0, 0, 0, 1, 16, 16),
            Block.box(15, 0, 0, 16, 16, 16),
            Block.box(1, 0, 0, 15, 16, 1),
            Block.box(1, 0, 15, 15, 16, 16),
            Block.box(1, 0, 1, 15, 1, 15),
            Block.box(1, 15, 1, 15, 16, 15)
    );

    private CamouflageOcclusion() {}

    public static VoxelShape getShape(BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CamouflageableBlockEntity machine) {
            BlockState camouflage = machine.getCamouflageState();
            if (camouflage != null && !camouflage.isAir()) {
                return Shapes.or(FRAME, camouflage.getOcclusionShape(level, pos));
            }
        }
        return FRAME;
    }
}
