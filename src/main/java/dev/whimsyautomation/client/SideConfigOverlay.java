package dev.whimsyautomation.client;

import dev.whimsyautomation.block.entity.SideConfigurableBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.WeakHashMap;

/** Client-side overlay state keyed by the actual block-entity instance. */
public final class SideConfigOverlay {
    private record OverlayState(boolean enabled, int[] sideModes) {}

    private static final Map<BlockEntity, OverlayState> STATES = new WeakHashMap<>();

    private SideConfigOverlay() {}

    /**
     * Updates the preview from the menu's synchronized side modes.
     * The menu DataSlots are authoritative on the client; the client block entity
     * is not assumed to receive a live update for every automation-side change.
     */
    public static void show(BlockEntity machine, int[] sideModes, boolean enabled) {
        if (machine == null) return;
        if (!enabled) {
            STATES.remove(machine);
            return;
        }
        int[] copy = new int[Direction.values().length];
        if (sideModes != null) {
            System.arraycopy(sideModes, 0, copy, 0, Math.min(sideModes.length, copy.length));
        }
        STATES.put(machine, new OverlayState(true, copy));
    }

    public static void clear(BlockEntity machine) {
        if (machine != null) STATES.remove(machine);
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

        int[] modes = state.sideModes();
        boolean anyConfigured = false;
        for (int mode : modes) {
            if (mode != 0) {
                anyConfigured = true;
                break;
            }
        }
        return !anyConfigured || modes[side] != 0;
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
