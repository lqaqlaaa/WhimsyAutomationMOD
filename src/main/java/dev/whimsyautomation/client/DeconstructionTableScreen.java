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
        imageWidth = 240;
        imageHeight = 250;
        inventoryLabelY = 148;
        titleLabelY = 8;
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> send(0)).bounds(leftPos + 12, topPos + 104, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> send(1)).bounds(leftPos + 35, topPos + 104, 20, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.auto"), b -> send(2)).bounds(leftPos + 12, topPos + 124, 48, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.process"), b -> send(3)).bounds(leftPos + 64, topPos + 124, 66, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.previous"), b -> send(4)).bounds(leftPos + 64, topPos + 124, 48, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.next"), b -> send(5)).bounds(leftPos + 128, topPos + 124, 48, 18).build());
    }

    private void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF08090B);
        g.fill(x, y, x + 16, y + 16, 0xFF77716A);
        g.fill(x + 1, y + 1, x + 15, y + 15, 0xFF292B2E);
        g.fill(x + 2, y + 2, x + 14, y + 14, 0xFF202225);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0101116);
        g.fill(x + 5, y + 24, x + 235, y + 100, 0xFF24221F);
        g.fill(x + 5, y + 96, x + 235, y + 143, 0xFF171B20);
        g.fill(x + 5, y + 146, x + 235, y + 244, 0xFF171B20);
        g.fill(x + 5, y + 24, x + 235, y + 25, 0xFFB77C45);
        g.fill(x + 5, y + 143, x + 235, y + 144, 0xFF65503C);
        slot(g, x + 22, y + 52);
        for (int i = 0; i < 27; i++) slot(g, x + 66 + (i % 9) * 18, y + 42 + (i / 9) * 18);
        for (int i = 0; i < 27; i++) slot(g, x + 26 + (i % 9) * 18, y + 158 + (i / 9) * 18);
        for (int i = 0; i < 9; i++) slot(g, x + 26 + i * 18, y + 216);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("screen.whimsy_automation.deconstruction"), 8, 8, 0xF0D0A2, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.input"), 12, 30, 0xD9B98D, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.outputs"), 66, 30, 0xA9D5B8, false);
        String status = font.plainSubstrByWidth(menu.getRecipeStatus(), 164);
        g.drawString(font, status, 62, 94, 0xE0C49B, false);
        g.drawString(font, "Page " + (menu.getPage() + 1) + "/" + menu.getPageCount(), 170, 30, 0xD4C7AF, false);
        g.drawString(font, Component.translatable("container.inventory"), 26, inventoryLabelY, 0xB6B8BC, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
