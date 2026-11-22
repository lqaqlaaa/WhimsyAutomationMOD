package dev.whimsyautomation.client;

import net.minecraft.core.BlockPos;

/** Client-only state for the temporary six-face automation preview. */
public final class SideConfigOverlay {
    private static BlockPos target;
    private static int[] modes = new int[6];

    private SideConfigOverlay() {}

    public static void show(BlockPos pos, int[] sideModes) {
        target = pos.immutable();
        modes = sideModes == null ? new int[6] : sideModes.clone();
    }

    public static void clear() {
        target = null;
        modes = new int[6];
    }

    public static boolean isActiveAt(BlockPos pos) {
        return target != null && target.equals(pos);
    }

    public static int mode(int side) {
        return side < 0 || side >= modes.length ? 0 : modes[side];
    }

    public static int color(int side) {
        return switch (side) {
            case 0 -> 0xFF993333;
            case 1 -> 0xFF7FCC19;
            case 2 -> 0xFF334CB2;
            case 3 -> 0xFFE5E533;
            case 4 -> 0xFF7F3FB2;
            case 5 -> 0xFF4C7F99;
            default -> 0xFFFFFFFF;
        };
    }
}
