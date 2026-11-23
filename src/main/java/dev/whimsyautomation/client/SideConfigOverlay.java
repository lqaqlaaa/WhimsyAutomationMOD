package dev.whimsyautomation.client;

/** Direction palette shared by the six-face legend and block-entity renderer. */
public final class SideConfigOverlay {
    private SideConfigOverlay() {}

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
