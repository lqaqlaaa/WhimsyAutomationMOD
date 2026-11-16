package dev.whimsyautomation.camouflage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import dev.whimsyautomation.camouflage.CamouflageModelData;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.extensions.IBlockGetterExtension;
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
            Shapes.box(0, 0, 0, 1.0 / 16.0, 1, 1),
            Shapes.box(15.0 / 16.0, 0, 0, 1, 1, 1),
            Shapes.box(1.0 / 16.0, 0, 0, 15.0 / 16.0, 1, 1.0 / 16.0),
            Shapes.box(1.0 / 16.0, 0, 15.0 / 16.0, 15.0 / 16.0, 1, 1),
            Shapes.box(1.0 / 16.0, 0, 1.0 / 16.0, 15.0 / 16.0, 1.0 / 16.0, 15.0 / 16.0),
            Shapes.box(1.0 / 16.0, 15.0 / 16.0, 1.0 / 16.0, 15.0 / 16.0, 1, 15.0 / 16.0)
    );

    private CamouflageOcclusion() {}

    public static VoxelShape getShape(BlockGetter level, BlockPos pos) {
        BlockState camouflage = null;

        /*
         * Chunk meshing runs on worker threads. Reading a BlockEntity directly
         * from those threads is not reliable; NeoForge exposes the immutable
         * ModelData snapshot specifically for this kind of render-time query.
         */
        if (level instanceof IBlockGetterExtension extension) {
            ModelData data = extension.getModelData(pos);
            camouflage = data.get(CamouflageModelData.CAMOUFLAGE_STATE);
        }

        // Server-side shape queries are not performed by the chunk mesher and
        // may safely fall back to the live BlockEntity state.
        if (camouflage == null && level.getBlockEntity(pos) instanceof CamouflageableBlockEntity machine) {
            camouflage = machine.getCamouflageState();
        }

        if (camouflage != null && !camouflage.isAir()) {
            return Shapes.or(FRAME, camouflage.getOcclusionShape(level, pos));
        }
        return FRAME;
    }
}
