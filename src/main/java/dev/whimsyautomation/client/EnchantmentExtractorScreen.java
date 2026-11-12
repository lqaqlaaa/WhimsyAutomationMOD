package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.EnchantmentExtractorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class EnchantmentExtractorScreen extends AbstractContainerScreen<EnchantmentExtractorMenu> {
    public EnchantmentExtractorScreen(EnchantmentExtractorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 228;
        imageHeight = 280;
        inventoryLabelY = 162;
        titleLabelY = 8;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("AUTO"), b -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 12, topPos + 86, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("PROCESS"), b -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1);
        }).bounds(leftPos + 64, topPos + 86, 58, 18).build());
        addRenderableWidget(Button.builder(Component.literal("SPLIT I"), b -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 2);
        }).bounds(leftPos + 126, topPos + 86, 72, 18).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xF00B0F19);
        graphics.fill(x + 6, y + 25, x + imageWidth - 6, y + 157, 0xFF151D2B);
        graphics.fill(x + 6, y + 164, x + imageWidth - 6, y + imageHeight - 6, 0xFF111824);
        graphics.fill(x + 10, y + 30, x + 40, y + 82, 0xFF202B3C);
        graphics.fill(x + 44, y + 30, x + imageWidth - 10, y + 154, 0xFF101722);
        graphics.fill(x + 6, y + 24, x + imageWidth - 6, y + 25, 0xFF8B5AD8);
        graphics.fill(x + 6, y + 157, x + imageWidth - 6, y + 158, 0xFF34445B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.extractor"), 8, 8, 0xE8D8FF, false);
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.input"), 12, 30, 0x8EDDE0, false);
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.outputs"), 48, 30, 0xD5C5F2, false);
        graphics.drawString(font, Component.translatable("container.inventory"), 26, inventoryLabelY, 0xAAB7C8, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
