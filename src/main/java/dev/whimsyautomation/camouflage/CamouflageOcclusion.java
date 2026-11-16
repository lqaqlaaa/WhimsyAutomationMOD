package dev.whimsyautomation.camouflage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.extensions.IBlockGetterExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/**
 * Combines the visible machine rim with the target's voxel occlusion shape.
 * Target shape queries run through a view that reports the camouflage state
 * at the machine position instead of exposing the real machine state.
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

    public static BlockState getCamouflageState(BlockGetter level, BlockPos pos) {
        if (level instanceof IBlockGetterExtension extension) {
            ModelData data = extension.getModelData(pos);
            BlockState state = data.get(CamouflageModelData.CAMOUFLAGE_STATE);
            if (state != null) return state;
        }

        // Do not touch live block entities from client chunk-meshing workers.
        if (level instanceof Level actualLevel && actualLevel.isClientSide) return null;
        if (level.getBlockEntity(pos) instanceof CamouflageableBlockEntity machine) {
            return machine.getCamouflageState();
        }
        return null;
    }

    public static BlockGetter createView(BlockGetter delegate, BlockPos targetPos, BlockState targetState) {
        BlockPos immutablePos = targetPos.immutable();
        return (BlockGetter) Proxy.newProxyInstance(BlockGetter.class.getClassLoader(),
                new Class<?>[]{BlockGetter.class}, (proxy, method, args) -> {
                    if (args != null && args.length > 0 && args[0] instanceof BlockPos queriedPos
                            && immutablePos.equals(queriedPos)) {
                        if (method.getName().equals("getBlockState")) return targetState;
                        if (method.getName().equals("getBlockEntity")) return null;
                        if (method.getName().equals("getFluidState")) return targetState.getFluidState();
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    public static VoxelShape getShape(BlockGetter level, BlockPos pos) {
        BlockState camouflage = getCamouflageState(level, pos);
        if (camouflage != null && !camouflage.isAir()) {
            return Shapes.or(FRAME, camouflage.getOcclusionShape(createView(level, pos, camouflage), pos));
        }
        return FRAME;
    }
}
