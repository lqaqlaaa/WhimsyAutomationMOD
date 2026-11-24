package dev.whimsyautomation.client;

/**
 * Responsive layout helpers copied from the TorchCampfireDamage GUI system.
 * All measurements use Minecraft's logical GUI dimensions.
 */
final class RemoteTransferGuiLayout {
    static final int BASE_W = 320;
    static final int BASE_H = 240;

    private RemoteTransferGuiLayout() {}

    static float density(int width, int height) {
        float wRatio = (float) BASE_W / Math.max(1, width);
        float hRatio = (float) BASE_H / Math.max(1, height);
        return Math.max(0.5f, Math.min(2.0f, Math.max(wRatio, hRatio)));
    }

    static int fit(int base, int width, int height, int min) {
        return Math.max(min, Math.round(base / density(width, height)));
    }

    static int cols(int availableWidth, int minColumnWidth) {
        return availableWidth >= minColumnWidth * 2 ? 2 : 1;
    }

    static int maxRows(int availableHeight, int rowHeight, int minRowHeight) {
        int rows = availableHeight / Math.max(1, rowHeight);
        return rows < 1 ? Math.max(1, availableHeight / Math.max(1, minRowHeight)) : rows;
    }

    static int actualRowHeight(int availableHeight, int rowCount, int desired, int min) {
        if (rowCount <= 0) return desired;
        return Math.max(min, Math.min(desired, availableHeight / rowCount));
    }
}
