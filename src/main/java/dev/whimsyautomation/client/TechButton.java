package dev.whimsyautomation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Flat dark-cyan button used by the remote transfer screens.
 * Avoids the vanilla stone button texture.
 */
public final class TechButton extends Button {
    private static final int FILL = 0xFF121A24;
    private static final int FILL_HOVER = 0xFF173544;
    private static final int FILL_DISABLED = 0xFF151A20;
    private static final int EDGE = 0xFF00A8C0;
    private static final int EDGE_HOVER = 0xFF00E5FF;
    private static final int EDGE_DISABLED = 0xFF2C3846;
    private static final int LABEL = 0xFFE8F6FF;
    private static final int LABEL_DISABLED = 0xFF6E7C8A;

    private TechButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hot = active && isHoveredOrFocused();
        int fill = !active ? FILL_DISABLED : (hot ? FILL_HOVER : FILL);
        int edge = !active ? EDGE_DISABLED : (hot ? EDGE_HOVER : EDGE);
        int color = !active ? LABEL_DISABLED : LABEL;

        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, edge);
        graphics.fill(x, y + h - 1, x + w, y + h, edge);
        graphics.fill(x, y, x + 1, y + h, edge);
        graphics.fill(x + w - 1, y, x + w, y + h, edge);
        graphics.fill(x, y, x + 2, y + h, edge);

        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2, color);
    }

    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private Tooltip tooltip;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder tooltip(Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public TechButton build() {
            TechButton button = new TechButton(x, y, width, height, message, onPress);
            if (tooltip != null) {
                button.setTooltip(tooltip);
            }
            return button;
        }
    }
}
