package dev.whimsyautomation.client;

/**
 * 界面自适应布局工具。
 * 直接移植自 TorchCampfireDamage 的 GuiLayout，保留原始计算逻辑。
 */
final class RemoteTransferGuiLayout {
    public static final int BASE_W = 320;
    public static final int BASE_H = 240;

    private RemoteTransferGuiLayout() {}

    public static float density(int width, int height) {
        float wRatio = (float) BASE_W / Math.max(1, width);
        float hRatio = (float) BASE_H / Math.max(1, height);
        float d = Math.max(wRatio, hRatio);
        return Math.max(0.5f, Math.min(2.0f, d));
    }

    public static int fit(int base, int width, int height, int min) {
        float d = density(width, height);
        int v = Math.round(base / d);
        return Math.max(min, v);
    }

    public static int cols(int availW, int minColW) {
        return availW >= minColW * 2 ? 2 : 1;
    }

    public static int maxRows(int availH, int rowH, int minRowH) {
        int rows = availH / Math.max(1, rowH);
        if (rows < 1) {
            rows = Math.max(1, availH / Math.max(1, minRowH));
        }
        return rows;
    }

    public static int actualRowH(int availH, int rowCount, int rowH, int minRowH) {
        if (rowCount <= 0) return rowH;
        int v = availH / rowCount;
        return Math.max(minRowH, Math.min(rowH, v));
    }
}
