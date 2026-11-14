package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.DeconstructionTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class DeconstructionTableScreen extends AbstractContainerScreen<DeconstructionTableMenu> {
    private static final ResourceLocation SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/slot");
    private Button lockButton;
    private Button autoButton;

    public DeconstructionTableScreen(DeconstructionTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 260;
        imageHeight = 256;
        inventoryLabelY = 166;
        titleLabelY = 8;
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> { menu.cyclePreview(-1); send(0); })
                .bounds(leftPos + 8, topPos + 152, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> { menu.cyclePreview(1); send(1); })
                .bounds(leftPos + 30, topPos + 152, 20, 18).build());
        lockButton = Button.builder(Component.translatable("screen.whimsy_automation.lock"), b -> send(6))
                .bounds(leftPos + 54, topPos + 152, 62, 18).build();
        addRenderableWidget(lockButton);
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.process"), b -> send(3))
                .bounds(leftPos + 122, topPos + 152, 74, 18).build());
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> send(4))
                .bounds(leftPos + 200, topPos + 8, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> send(5))
                .bounds(leftPos + 222, topPos + 8, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> menu.cyclePreviewPage(-1))
                .bounds(leftPos + 200, topPos + 152, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> menu.cyclePreviewPage(1))
                .bounds(leftPos + 222, topPos + 152, 20, 18).build());
        autoButton = Button.builder(autoLabel(), b -> send(2)).bounds(leftPos + 8, topPos + 126, 42, 18).build();
        addRenderableWidget(autoButton);
    }

    private Component autoLabel() {
        return Component.translatable(menu.isAutoMode()
                ? "screen.whimsy_automation.auto"
                : "screen.whimsy_automation.auto");
    }

    private void slot(GuiGraphics g, int x, int y) {
        g.blitSprite(SLOT_SPRITE, x, y, 16, 16);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0101116);
        g.fill(x + 5, y + 24, x + 255, y + 100, 0xFF24221F);
        g.fill(x + 5, y + 102, x + 255, y + 150, 0xFF171B20);
        g.fill(x + 5, y + 152, x + 255, y + 190, 0xFF171B20);
        g.fill(x + 5, y + 166, x + 255, y + 256, 0xFF171B20);
        g.fill(x + 5, y + 24, x + 255, y + 25, 0xFFB77C45);
        g.fill(x + 5, y + 100, x + 255, y + 101, 0xFF65503C);

        slot(g, x + 22, y + 52);
        for (int i = 0; i < 27; i++) {
            slot(g, x + 50 + (i % 9) * 18, y + 42 + (i / 9) * 18);
        }
        for (int i = 0; i < 27; i++) slot(g, x + 26 + (i % 9) * 18, y + 178 + (i / 9) * 18);
        for (int i = 0; i < 9; i++) slot(g, x + 26 + i * 18, y + 236);

        List<ItemStack> preview = menu.getPreviewStacks();
        int columns = Math.max(1, Math.min(9, menu.getPreviewColumns()));
        int rows = Math.max(1, (preview.size() + columns - 1) / columns);
        int previewX = x + 50 + (9 - columns) * 9;
        int previewY = y + 110 + (2 - Math.min(2, rows)) * 9;
        for (int i = 0; i < preview.size(); i++) {
            int itemX = previewX + (i % columns) * 18;
            int itemY = previewY + (i / columns) * 18;
            slot(g, itemX, itemY);
            g.renderItem(preview.get(i), itemX, itemY);
            g.renderItemDecorations(font, preview.get(i), itemX, itemY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("screen.whimsy_automation.deconstruction"), 8, 8, 0xF0D0A2, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.input"), 12, 36, 0xD9B98D, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.outputs"), 50, 30, 0xA9D5B8, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.recipe_materials"), 50, 102, 0xA9D5B8, false);
        String status = font.plainSubstrByWidth(menu.getRecipeStatus(), 220);
        g.drawString(font, status, 12, 88, menu.isLocked() ? 0xFFFFC66E : 0xFFE0C49B, false);
        g.drawString(font, Component.translatable("container.inventory"), 26, inventoryLabelY, 0xFFB6B8BC, false);
        if (lockButton != null) {
            lockButton.setMessage(Component.translatable(menu.isLocked()
                    ? "screen.whimsy_automation.unlock" : "screen.whimsy_automation.lock"));
        }
        if (autoButton != null) autoButton.setMessage(autoLabel());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}