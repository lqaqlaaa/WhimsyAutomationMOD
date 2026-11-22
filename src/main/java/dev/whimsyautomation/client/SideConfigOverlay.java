package dev.whimsyautomation.client;

import dev.whimsyautomation.block.entity.SideConfigurableBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.WeakHashMap;

/** Client-side overlay visibility keyed by the actual block-entity instance. */
public final class SideConfigOverlay {
    private static final Map<BlockEntity, Boolean> ACTIVE = new WeakHashMap<>();

    private SideConfigOverlay() {}

    /** Enables/disables the frame preview for this exact machine instance. */
    public static void show(BlockEntity machine, boolean enabled) {
        if (machine == null) return;
        if (enabled) ACTIVE.put(machine, Boolean.TRUE);
        else ACTIVE.remove(machine);
    }

    public static void clear(BlockEntity machine) {
        if (machine != null) ACTIVE.remove(machine);
    }

    public static boolean isActive(BlockEntity machine) {
        return machine != null && ACTIVE.containsKey(machine);
    }

    /**
     * If any side is configured for input or output, show only configured sides.
     * If all six modes are disabled, the enabled preview shows all six faces.
     */
    public static boolean shouldShowFace(BlockEntity machine, int side) {
        if (!(machine instanceof SideConfigurableBlockEntity configurable) || !isActive(machine)
                || side < 0 || side >= Direction.values().length) return false;
        boolean anyConfigured = false;
        for (Direction direction : Direction.values()) {
            if (configurable.getSideMode(direction) != 0) {
                anyConfigured = true;
                break;
            }
        }
        return !anyConfigured || configurable.getSideMode(Direction.values()[side]) != 0;
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
