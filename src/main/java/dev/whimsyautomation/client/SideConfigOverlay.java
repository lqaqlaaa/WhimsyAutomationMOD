package dev.whimsyautomation.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client-side six-face previews, independently tracked for every loaded level
 * and block position. A single global target caused opening a second machine
 * to silently replace the first machine's preview.
 */
public final class SideConfigOverlay {
    private static final Map<Level, Map<BlockPos, OverlayState>> STATES = new WeakHashMap<>();

    private SideConfigOverlay() {}

    public static void show(Level level, BlockPos pos, int[] sideModes, boolean showAll) {
        if (level == null || pos == null) return;
        Map<BlockPos, OverlayState> levelStates = STATES.computeIfAbsent(level, ignored -> new HashMap<>());
        levelStates.put(pos.immutable(), new OverlayState(
                sideModes == null ? new int[6] : sideModes.clone(), showAll));
    }

    public static void clear(Level level, BlockPos pos) {
        if (level == null || pos == null) return;
        Map<BlockPos, OverlayState> levelStates = STATES.get(level);
        if (levelStates == null) return;
        levelStates.remove(pos);
        if (levelStates.isEmpty()) STATES.remove(level);
    }

    public static boolean isActiveAt(Level level, BlockPos pos) {
        return state(level, pos) != null;
    }

    public static boolean isShowingAllFacesAt(Level level, BlockPos pos) {
        OverlayState state = state(level, pos);
        return state != null && state.showAllFaces;
    }

    public static boolean shouldShowFace(Level level, BlockPos pos, int side) {
        OverlayState state = state(level, pos);
        return state != null && (state.showAllFaces || mode(state, side) != 0);
    }

    private static OverlayState state(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        Map<BlockPos, OverlayState> levelStates = STATES.get(level);
        return levelStates == null ? null : levelStates.get(pos);
    }

    private static int mode(OverlayState state, int side) {
        return side < 0 || side >= state.modes.length ? 0 : state.modes[side];
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

    private record OverlayState(int[] modes, boolean showAllFaces) {}
}
