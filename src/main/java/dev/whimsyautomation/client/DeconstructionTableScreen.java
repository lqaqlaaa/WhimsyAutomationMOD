package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.DeconstructionTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class DeconstructionTableScreen extends AbstractContainerScreen<DeconstructionTableMenu> {
    public DeconstructionTableScreen(DeconstructionTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 228;
        imageHeight = 230;
        inventoryLabelY = 112;
        titleLabelY = 8;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 12, topPos + 86, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1);
        }).bounds(leftPos + 35, topPos + 86, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("AUTO"), button -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 2);
        }).bounds(leftPos + 12, topPos + 106, 43, 16).build());
        addRenderableWidget(Button.builder(Component.literal("PROCESS"), button -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 3);
        }).bounds(leftPos + 58, topPos + 106, 62, 16).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xF0101116);
        graphics.fill(x + 6, y + 25, x + imageWidth - 6, y + 105, 0xFF24221F);
        graphics.fill(x + 6, y + 114, x + imageWidth - 6, y + imageHeight - 6, 0xFF171B20);
        graphics.fill(x + 10, y + 31, x + 40, y + 82, 0xFF3A3027);
        graphics.fill(x + 62, y + 30, x + imageWidth - 10, y + 98, 0xFF1A1E23);
        graphics.fill(x + 6, y + 24, x + imageWidth - 6, y + 25, 0xFFB77C45);
        graphics.fill(x + 6, y + 105, x + imageWidth - 6, y + 106, 0xFF65503C);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.deconstruction"), 8, 8, 0xF0D0A2, false);
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.input"), 12, 30, 0xD9B98D, false);
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.outputs"), 66, 30, 0xA9D5B8, false);
        String status = font.plainSubstrByWidth(menu.getRecipeStatus(), 158);
        graphics.drawString(font, status, 62, 89, 0xE0C49B, false);
        graphics.drawString(font, Component.translatable("container.inventory"), 26, inventoryLabelY, 0xB6B8BC, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
