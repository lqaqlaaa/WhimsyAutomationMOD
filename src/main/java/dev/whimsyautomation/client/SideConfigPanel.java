package dev.whimsyautomation.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Canonical six-face configuration panel. All machines use the Deconstruction
 * Table layout and interaction geometry through this component.
 */
public final class SideConfigPanel {
    private static final String[] NAMES = {"下", "上", "北", "南", "西", "东"};
    private static final String[] MODES = {"关闭", "输入", "输出"};

    private SideConfigPanel() {}

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
    }

    public static void render(GuiGraphics graphics, Font font, int leftPos, int topPos,
                              double mouseX, double mouseY, float partialTick,
                              int[] sideModes, boolean showFaceColors) {
        int x = 176, y = 54;
        panel(graphics, x, y, 176, 166);
        graphics.fill(x + 2, y + 2, x + 174, y + 164, 0xFFE0E0E0);
        graphics.drawString(font, Component.literal("六向设置"), x + 8, y + 8, 0xFF202020, false);
        graphics.drawString(font, Component.literal("点击方向按钮循环切换：关闭 / 输入 / 输出"),
                x + 8, y + 20, 0xFF505050, false);
        for (int i = 0; i < 6; i++) {
            int bx = 184 + (i % 3) * 55;
            int by = 88 + (i / 3) * 25;
            int mode = Math.max(0, Math.min(2, sideModes[i]));
            Button.builder(Component.literal(NAMES[i] + " · " + MODES[mode]), b -> {})
                    .bounds(bx, by, 50, 20).build()
                    .render(graphics, (int) mouseX - leftPos, (int) mouseY - topPos, partialTick);
        }
        graphics.drawString(font, Component.literal("关闭总开关时，仅显示已启用输入或输出的面"),
                x + 8, y + 91, 0xFF505050, false);
        Button.builder(Component.literal("显示六面颜色框：" + (showFaceColors ? "开启" : "关闭")), b -> {})
                .bounds(184, 177, 160, 18).build()
                .render(graphics, (int) mouseX - leftPos, (int) mouseY - topPos, partialTick);
        Button.builder(Component.literal("×"), b -> {})
                .bounds(326, 58, 20, 16).build()
                .render(graphics, (int) mouseX - leftPos, (int) mouseY - topPos, partialTick);
    }

    /** Returns 0..5 for a face, 6 for the color toggle, 7 for close,
     * -2 for another click inside the modal, and -1 outside the modal. */
    public static int hit(double localX, double localY) {
        if (localX >= 326 && localX < 346 && localY >= 58 && localY < 74) return 7;
        for (int i = 0; i < 6; i++) {
            int x = 184 + (i % 3) * 55;
            int y = 88 + (i / 3) * 25;
            if (localX >= x && localX < x + 50 && localY >= y && localY < y + 20) return i;
        }
        if (localX >= 184 && localX < 344 && localY >= 177 && localY < 195) return 6;
        if (localX >= 176 && localX < 352 && localY >= 54 && localY < 220) return -2;
        return -1;
    }
}
