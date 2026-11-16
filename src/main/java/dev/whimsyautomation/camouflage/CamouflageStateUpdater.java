package dev.whimsyautomation.camouflage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs the target block's neighbour-shape logic against a view where the
 * machine position reports the camouflage state.
 *
 * The view is deliberately read-through but write-protected at the disguised
 * position: neighbour logic must never replace or remove the real machine.
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
            LevelAccessor view = createView(level, pos, camouflage, machine);
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

    private static LevelAccessor createView(LevelAccessor delegate, BlockPos targetPos, BlockState targetState, CamouflageableBlockEntity machine) {
        BlockPos immutableTargetPos = targetPos.immutable();
        AtomicReference<BlockState> virtualState = new AtomicReference<>(targetState);
        return (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(),
                new Class<?>[]{LevelAccessor.class}, (proxy, method, args) -> {
                    if (args != null && args.length > 0 && args[0] instanceof BlockPos queriedPos
                            && immutableTargetPos.equals(queriedPos)) {
                        if (method.getName().equals("getBlockState")) return virtualState.get();
                        if (method.getName().equals("getBlockEntity")) return null;
                        if (method.getName().equals("getFluidState")) return virtualState.get().getFluidState();
                    }

                    /*
                     * A write to the virtual position changes the disguise,
                     * not the real machine. Removal and foreign block-entity
                     * writes are rejected until they have dedicated storage.
                     */
                    if (isProtectedMutation(method, args, immutableTargetPos)) {
                        if (("setBlock".equals(method.getName()) || "setBlockAndUpdate".equals(method.getName()))
                                && args.length > 1 && args[1] instanceof BlockState newState) {
                            virtualState.set(newState);
                            machine.setCamouflageState(newState);
                            return true;
                        }
                        return defaultValue(method.getReturnType());
                    }

                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private static boolean isProtectedMutation(Method method, Object[] args, BlockPos targetPos) {
        if (args == null || args.length == 0 || !(args[0] instanceof BlockPos mutationPos)
                || !targetPos.equals(mutationPos)) {
            return false;
        }

        return switch (method.getName()) {
            case "setBlock", "setBlockAndUpdate", "removeBlock", "destroyBlock",
                    "setBlockEntity", "removeBlockEntity", "blockEntityChanged" -> true;
            default -> false;
        };
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == void.class) return null;
        if (returnType == boolean.class) return false;
        if (returnType == char.class) return '\\0';
        if (returnType == byte.class) return (byte) 0;
        if (returnType == short.class) return (short) 0;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0F;
        if (returnType == double.class) return 0D;
        return null;
    }
}
