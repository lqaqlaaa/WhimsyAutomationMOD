package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.DeconstructionTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class DeconstructionTableScreen extends AbstractContainerScreen<DeconstructionTableMenu> {
    private Button lockButton;

    public DeconstructionTableScreen(DeconstructionTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 420;
        imageHeight = 300;
        inventoryLabelY = 208;
        titleLabelY = 8;
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> { menu.cyclePreview(-1); send(0); })
                .bounds(leftPos + 12, topPos + 166, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> { menu.cyclePreview(1); send(1); })
                .bounds(leftPos + 35, topPos + 166, 20, 18).build());
        lockButton = Button.builder(Component.translatable("screen.whimsy_automation.lock"), b -> send(6))
                .bounds(leftPos + 64, topPos + 166, 68, 18).build();
        addRenderableWidget(lockButton);
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.process"), b -> send(3))
                .bounds(leftPos + 140, topPos + 166, 84, 18).build());
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> send(4))
                .bounds(leftPos + 78, topPos + 188, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> send(5))
                .bounds(leftPos + 100, topPos + 188, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> menu.cyclePreviewPage(-1))
                .bounds(leftPos + 328, topPos + 188, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> menu.cyclePreviewPage(1))
                .bounds(leftPos + 350, topPos + 188, 20, 18).build());
    }

    private void drawSwitch(GuiGraphics g, int x, int y, boolean enabled) {
        int track = enabled ? 0xFF3E9B68 : 0xFF555B66;
        g.fill(x, y, x + 30, y + 13, 0xFF090B10);
        g.fill(x + 1, y + 1, x + 29, y + 12, track);
        int knobX = enabled ? x + 17 : x + 3;
        g.fill(knobX, y + 2, knobX + 10, y + 11, 0xFFE9EDF2);
        g.fill(knobX + 1, y + 3, knobX + 9, y + 10, 0xFFFFFFFF);
    }

    private void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF08090B);
        g.fill(x, y, x + 16, y + 16, 0xFF77716A);
        g.fill(x + 1, y + 1, x + 15, y + 15, 0xFF292B2E);
        g.fill(x + 2, y + 2, x + 14, y + 14, 0xFF202225);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0101116);
        g.fill(x + 5, y + 24, x + 415, y + 160, 0xFF24221F);
        g.fill(x + 5, y + 162, x + 415, y + 208, 0xFF171B20);
        g.fill(x + 5, y + 212, x + 415, y + 299, 0xFF171B20);
        g.fill(x + 5, y + 24, x + 415, y + 25, 0xFFB77C45);
        g.fill(x + 5, y + 160, x + 415, y + 161, 0xFF65503C);

        slot(g, x + 22, y + 52);
        for (int i = 0; i < 54; i++) {
            slot(g, x + 66 + (i % 9) * 18, y + 42 + (i / 9) * 18);
        }
        for (int i = 0; i < 27; i++) slot(g, x + 26 + (i % 9) * 18, y + 220 + (i / 9) * 18);
        for (int i = 0; i < 9; i++) slot(g, x + 26 + i * 18, y + 278);

        List<ItemStack> preview = menu.getPreviewStacks();
        int columns = Math.max(3, menu.getPreviewColumns());
        int rows = Math.max(1, (preview.size() + columns - 1) / columns);
        int previewX = x + 246 + (9 - columns) * 9;
        int previewY = y + 42 + (6 - rows) * 9;
        for (int i = 0; i < preview.size(); i++) {
            int itemX = previewX + (i % columns) * 18;
            int itemY = previewY + (i / columns) * 18;
            slot(g, itemX, itemY);
            g.renderItem(preview.get(i), itemX, itemY);
        }
        drawSwitch(g, x + 12, y + 190, menu.isAutoMode());
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("screen.whimsy_automation.deconstruction"), 8, 8, 0xF0D0A2, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.input"), 12, 36, 0xD9B98D, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.outputs"), 66, 30, 0xA9D5B8, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.recipe_materials"), 246, 30, 0xA9D5B8, false);
        String status = font.plainSubstrByWidth(menu.getRecipeStatus(), 220);
        g.drawString(font, status, 12, 100, menu.isLocked() ? 0xFFFFC66E : 0xFFE0C49B, false);
        String recipeId = font.plainSubstrByWidth(menu.getPreviewRecipeId(), 220);
        if (!recipeId.isEmpty()) g.drawString(font, recipeId, 12, 112, 0xFFB9C6D3, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.auto_short"), 48, 192, 0xFFC9D3E1, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.page", menu.getPage() + 1, menu.getPageCount()), 126, 192, 0xFFD4C7AF, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.preview_page", menu.getPreviewPage() + 1, menu.getPreviewPageCount()), 245, 192, 0xFFD4C7AF, false);
        g.drawString(font, Component.translatable("container.inventory"), 26, inventoryLabelY, 0xFFB6B8BC, false);
        if (lockButton != null) {
            lockButton.setMessage(Component.translatable(menu.isLocked()
                    ? "screen.whimsy_automation.unlock" : "screen.whimsy_automation.lock"));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int localX = (int) mouseX - leftPos;
            int localY = (int) mouseY - topPos;
            if (localX >= 12 && localX < 42 && localY >= 190 && localY < 203) {
                send(2);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}