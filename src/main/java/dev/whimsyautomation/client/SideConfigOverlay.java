package dev.whimsyautomation.client;

import dev.whimsyautomation.block.entity.SideConfigurableBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.HashMap;
import java.util.Map;

/** Client-side overlay state keyed by dimension and block position. */
public final class SideConfigOverlay {
    private record MachineKey(ResourceKey<Level> dimension, net.minecraft.core.BlockPos pos, BlockEntityType<?> type) {}
    private record OverlayState(boolean enabled, int[] sideModes, boolean[] visibleFaces) {}

    private static final Map<MachineKey, OverlayState> STATES = new HashMap<>();

    private SideConfigOverlay() {}

    private static MachineKey key(BlockEntity machine) {
        if (machine == null || machine.getLevel() == null) return null;
        return new MachineKey(machine.getLevel().dimension(), machine.getBlockPos().immutable(), machine.getType());
    }

    /** Stores the preview by world position, not BlockEntity object identity.
     * A client chunk can recreate its BlockEntity while the screen remains open. */
    public static void show(BlockEntity machine, int[] sideModes, boolean enabled) {
        MachineKey key = key(machine);
        if (key == null) return;
        if (!enabled) {
            STATES.remove(key);
            return;
        }
        int[] modes = new int[Direction.values().length];
        if (sideModes != null) {
            System.arraycopy(sideModes, 0, modes, 0, Math.min(sideModes.length, modes.length));
        }
        STATES.put(key, new OverlayState(true, modes, calculateVisibleFaces(modes)));
    }

    /** Predicts the same 0 -> 1 -> 2 -> 0 cycle sent to the server. */
    public static void cycleFace(BlockEntity machine, int side) {
        MachineKey key = key(machine);
        if (key == null || side < 0 || side >= Direction.values().length) return;
        OverlayState state = STATES.get(key);
        if (state == null || !state.enabled()) return;
        int[] modes = state.sideModes().clone();
        modes[side] = (modes[side] + 1) % 3;
        STATES.put(key, new OverlayState(true, modes, calculateVisibleFaces(modes)));
    }

    private static boolean[] calculateVisibleFaces(int[] sideModes) {
        int count = Direction.values().length;
        boolean anyConfigured = false;
        for (int mode : sideModes) {
            if (mode != 0) {
                anyConfigured = true;
                break;
            }
        }
        boolean[] visible = new boolean[count];
        for (int side = 0; side < count; side++) {
            visible[side] = !anyConfigured || sideModes[side] != 0;
        }
        return visible;
    }

    public static void clear(BlockEntity machine) {
        MachineKey key = key(machine);
        if (key != null) STATES.remove(key);
    }

    public static boolean isActive(BlockEntity machine) {
        MachineKey key = key(machine);
        OverlayState state = key == null ? null : STATES.get(key);
        return state != null && state.enabled();
    }

    public static boolean shouldShowFace(BlockEntity machine, int side) {
        if (!(machine instanceof SideConfigurableBlockEntity)
                || side < 0 || side >= Direction.values().length) return false;
        MachineKey key = key(machine);
        OverlayState state = key == null ? null : STATES.get(key);
        return state != null && state.enabled() && state.visibleFaces()[side];
    }

    public static int color(int side) {
        return switch (side) {
            case 0 -> 0xFFFF4545;
            case 1 -> 0xFF8CFF32;
            case 2 -> 0xFF527BFF;
            case 3 -> 0xFFFFFF35;
            case 4 -> 0xFFCF65FF;
            case 5 -> 0xFF35DFFF;
            default -> 0xFFFFFFFF;
        };
    }
}
