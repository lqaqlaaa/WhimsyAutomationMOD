package dev.whimsyautomation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Flat dark-tech button. Replaces the vanilla stone widget skin used by the
 * remote transfer screens.
 */
public class TechButton extends Button {
    public TechButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        boolean hot = active && isHoveredOrFocused();
        int bg = !active ? 0xFF121820 : (hot ? 0xFF163444 : 0xFF101820);
        int border = !active ? 0xFF2A3542 : (hot ? 0xFF00E5FF : 0xFF2C4A5C);
        int bar = !active ? 0xFF3A4552 : (hot ? 0xFF00E5FF : 0xFF007C96);
        graphics.fill(x, y, x + width, y + height, bg);
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, bar);
        int color = active ? 0xFFE8F6FF : 0xFF6E7C8A;
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                x + width / 2 + 1, y + (height - 8) / 2, color);
    }

    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private Tooltip tooltip;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;

        public Builder(Component message, OnPress onPress) {
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
            if (tooltip != null) button.setTooltip(tooltip);
            return button;
        }
    }
}
