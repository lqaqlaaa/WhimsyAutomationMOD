package dev.whimsyautomation.client;

import dev.whimsyautomation.block.entity.SideConfigurableBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.WeakHashMap;

/** Client-side overlay state keyed by the actual block-entity instance. */
public final class SideConfigOverlay {
    private record OverlayState(boolean enabled, boolean[] visibleFaces) {}

    private static final Map<BlockEntity, OverlayState> STATES = new WeakHashMap<>();

    private SideConfigOverlay() {}

    /**
     * Takes one snapshot when the player enables the overlay. The renderer never
     * reads side configuration; it only consumes the precomputed face flags.
     */
    public static void show(BlockEntity machine, int[] sideModes, boolean enabled) {
        if (machine == null) return;
        if (!enabled) {
            STATES.remove(machine);
            return;
        }
        int[] modes = new int[Direction.values().length];
        if (sideModes != null) {
            System.arraycopy(sideModes, 0, modes, 0, Math.min(sideModes.length, modes.length));
        }
        MODES.put(machine, modes);
        STATES.put(machine, new OverlayState(true, calculateVisibleFaces(modes)));
    }

    /**
     * Predicts the same 0 -> 1 -> 2 -> 0 cycle sent to the server. This updates
     * only the cached overlay for this machine; no menu/BE state is polled.
     */
    public static void cycleFace(BlockEntity machine, int side) {
        if (machine == null || side < 0 || side >= Direction.values().length) return;
        OverlayState state = STATES.get(machine);
        if (state == null || !state.enabled()) return;

        boolean[] visible = state.visibleFaces().clone();
        // A face that was hidden because all sides were disabled needs its
        // underlying mode to be tracked, so store modes in the state as well.
        int[] modes = MODES.get(machine);
        if (modes == null) return;
        modes = modes.clone();
        modes[side] = (modes[side] + 1) % 3;
        MODES.put(machine, modes);
        STATES.put(machine, new OverlayState(true, calculateVisibleFaces(modes)));
    }

    private static final Map<BlockEntity, int[]> MODES = new WeakHashMap<>();

    private static boolean[] calculateVisibleFaces(int[] sideModes) {
        int count = Direction.values().length;
        int[] modes = new int[count];
        if (sideModes != null) {
            System.arraycopy(sideModes, 0, modes, 0, Math.min(sideModes.length, count));
        }
        boolean anyConfigured = false;
        for (int mode : modes) {
            if (mode != 0) {
                anyConfigured = true;
                break;
            }
        }
        boolean[] visible = new boolean[count];
        for (int side = 0; side < count; side++) {
            visible[side] = !anyConfigured || modes[side] != 0;
        }
        return visible;
    }

    public static void clear(BlockEntity machine) {
        if (machine != null) {
            STATES.remove(machine);
            MODES.remove(machine);
        }
    }

    public static boolean isActive(BlockEntity machine) {
        OverlayState state = STATES.get(machine);
        return state != null && state.enabled();
    }

    /**
     * Show only configured input/output faces when at least one is configured.
     * When all six are disabled, the enabled preview shows all six faces.
     */
    public static boolean shouldShowFace(BlockEntity machine, int side) {
        if (!(machine instanceof SideConfigurableBlockEntity) || side < 0 || side >= Direction.values().length) {
            return false;
        }
        OverlayState state = STATES.get(machine);
        if (state == null || !state.enabled()) return false;

        return state.visibleFaces()[side];
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
