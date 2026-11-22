package dev.whimsyautomation.client;

import net.minecraft.core.BlockPos;

/** Client-only state for the temporary six-face automation preview. */
public final class SideConfigOverlay {
    private static BlockPos target;
    private static int[] modes = new int[6];
    private static boolean showAllFaces;

    private SideConfigOverlay() {}

    public static void show(BlockPos pos, int[] sideModes, boolean showAll) {
        target = pos.immutable();
        modes = sideModes == null ? new int[6] : sideModes.clone();
        showAllFaces = showAll;
    }

    public static void clear() {
        target = null;
        modes = new int[6];
        showAllFaces = false;
    }

    public static boolean isActiveAt(BlockPos pos) {
        return target != null && target.equals(pos);
    }

    public static boolean shouldShowFace(int side) {
        return showAllFaces || mode(side) != 0;
    }

    public static int mode(int side) {
        return side < 0 || side >= modes.length ? 0 : modes[side];
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
