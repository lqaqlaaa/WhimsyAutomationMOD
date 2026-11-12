package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.EnchantmentExtractorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class EnchantmentExtractorScreen extends AbstractContainerScreen<EnchantmentExtractorMenu> {
    private Button autoButton;
    private Button splitButton;
    public EnchantmentExtractorScreen(EnchantmentExtractorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 240;
        imageHeight = 250;
        inventoryLabelY = 144;
        titleLabelY = 8;
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override protected void init() {
        super.init();
        autoButton = addRenderableWidget(Button.builder(autoLabel(), b -> send(0)).bounds(leftPos + 12, topPos + 104, 68, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.process"), b -> send(1)).bounds(leftPos + 84, topPos + 104, 62, 18).build());
        splitButton = addRenderableWidget(Button.builder(splitLabel(), b -> send(2)).bounds(leftPos + 150, topPos + 104, 78, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.previous"), b -> send(3)).bounds(leftPos + 52, topPos + 124, 42, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.next"), b -> send(4)).bounds(leftPos + 146, topPos + 124, 42, 18).build());
    }

    private Component autoLabel() {
        return Component.translatable("screen.whimsy_automation.auto_state", Component.translatable(menu.isAutoMode() ? "screen.whimsy_automation.on" : "screen.whimsy_automation.off"));
    }

    private Component splitLabel() {
        return Component.translatable("screen.whimsy_automation.split_state", Component.translatable(menu.isSplitLevels() ? "screen.whimsy_automation.on" : "screen.whimsy_automation.off"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (autoButton != null) autoButton.setMessage(autoLabel());
        if (splitButton != null) splitButton.setMessage(splitLabel());
    }

    private void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF08090B);
        g.fill(x, y, x + 16, y + 16, 0xFF77716A);
        g.fill(x + 1, y + 1, x + 15, y + 15, 0xFF292B2E);
        g.fill(x + 2, y + 2, x + 14, y + 14, 0xFF202225);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF00B0F19);
        g.fill(x + 5, y + 24, x + 235, y + 100, 0xFF151D2B);
        g.fill(x + 5, y + 102, x + 235, y + 143, 0xFF111824);
        g.fill(x + 5, y + 146, x + 235, y + 244, 0xFF111824);
        g.fill(x + 5, y + 24, x + 235, y + 25, 0xFF8B5AD8);
        g.fill(x + 5, y + 143, x + 235, y + 144, 0xFF34445B);
        slot(g, x + 18, y + 52);
        slot(g, x + 18, y + 76);
        for (int i = 0; i < 27; i++) slot(g, x + 48 + (i % 9) * 18, y + 42 + (i / 9) * 18);
        for (int i = 0; i < 27; i++) slot(g, x + 26 + (i % 9) * 18, y + 158 + (i / 9) * 18);
        for (int i = 0; i < 9; i++) slot(g, x + 26 + i * 18, y + 216);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("screen.whimsy_automation.extractor"), 8, 8, 0xE8D8FF, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.input"), 12, 30, 0x8EDDE0, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.book_input"), 4, 68, 0xD5C5F2, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.outputs"), 48, 30, 0xD5C5F2, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.page", menu.getPage() + 1, menu.getPageCount()), 100, 128, 0xD5C5F2, false);
        g.drawString(font, Component.translatable("container.inventory"), 26, inventoryLabelY, 0xAAB7C8, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
