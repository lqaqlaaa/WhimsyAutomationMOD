package dev.whimsyautomation.camouflage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/**
 * Runs the target block's neighbour-shape logic against a read-only view where
 * the machine position reports the camouflage state. This is what allows
 * fences, walls, panes and similar stateful models to calculate connections.
 */
public final class CamouflageStateUpdater {
    private static final ThreadLocal<Boolean> UPDATING = ThreadLocal.withInitial(() -> false);

    private CamouflageStateUpdater() {}

    public static void updateNeighbor(LevelAccessor level, BlockPos pos, Direction direction,
                                      BlockState neighborState, BlockPos neighborPos) {
        if (UPDATING.get() || !(level.getBlockEntity(pos) instanceof CamouflageableBlockEntity machine)) return;
        BlockState camouflage = machine.getCamouflageState();
        if (camouflage == null || camouflage.isAir()) return;

        UPDATING.set(true);
        try {
            LevelAccessor view = createView(level, pos, camouflage);
            BlockState updatedCamouflage = camouflage.updateShape(direction, neighborState, view, pos, neighborPos);
            if (updatedCamouflage != camouflage) {
                machine.setCamouflageState(updatedCamouflage);
                camouflage = updatedCamouflage;
                view = createView(level, pos, camouflage);
            }

            BlockState updatedNeighbor = neighborState.updateShape(direction.getOpposite(), camouflage,
                    view, neighborPos, pos);
            if (updatedNeighbor != neighborState) {
                Block.updateOrDestroy(neighborState, updatedNeighbor, level, neighborPos, 3);
            }
        } finally {
            UPDATING.set(false);
        }
    }

    public static void updateAllNeighbors(LevelAccessor level, BlockPos pos) {
        if (UPDATING.get() || !(level.getBlockEntity(pos) instanceof CamouflageableBlockEntity machine)) return;
        BlockState camouflage = machine.getCamouflageState();
        if (camouflage == null || camouflage.isAir()) return;
        UPDATING.set(true);
        try {
            camouflage.updateNeighbourShapes(createView(level, pos, camouflage), pos, 3);
        } finally {
            UPDATING.set(false);
        }
    }

    private static LevelAccessor createView(LevelAccessor delegate, BlockPos targetPos, BlockState targetState) {
        return (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(),
                new Class<?>[]{LevelAccessor.class}, (proxy, method, args) -> {
                    if (args != null && args.length == 1 && args[0] instanceof BlockPos queriedPos) {
                        if (method.getName().equals("getBlockState") && targetPos.equals(queriedPos)) return targetState;
                        if (method.getName().equals("getBlockEntity") && targetPos.equals(queriedPos)) return null;
                        if (method.getName().equals("getFluidState") && targetPos.equals(queriedPos)) return targetState.getFluidState();
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }
}
