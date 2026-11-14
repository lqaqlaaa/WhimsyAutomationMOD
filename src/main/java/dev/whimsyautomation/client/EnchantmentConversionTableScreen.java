package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.EnchantmentConversionTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class EnchantmentConversionTableScreen extends AbstractContainerScreen<EnchantmentConversionTableMenu> {
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");

    public EnchantmentConversionTableScreen(EnchantmentConversionTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 72;
        titleLabelY = 6;
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.blitSprite(SLOT, x, y, 16, 16);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Do not paint the complete vanilla smithing screen over this machine:
        // it contains a decorative hammer/template glyph that covers our real
        // slot. Build only the panel and the four slots this menu actually owns.
        panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawSlot(graphics, leftPos + 8, topPos + 48);
        drawSlot(graphics, leftPos + 26, topPos + 48);
        drawSlot(graphics, leftPos + 44, topPos + 48);
        drawSlot(graphics, leftPos + 134, topPos + 48);

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawSlot(graphics, leftPos + 8 + col * 18, topPos + 84 + row * 18);
        for (int col = 0; col < 9; col++)
            drawSlot(graphics, leftPos + 8 + col * 18, topPos + 142);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.conversion_table"),
                8, titleLabelY, 0x404040, false);
        graphics.drawString(font, Component.translatable("container.inventory"),
                8, inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
