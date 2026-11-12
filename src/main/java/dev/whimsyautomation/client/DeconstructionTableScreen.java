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
        imageWidth = 240;
        imageHeight = 310;
        inventoryLabelY = 204;
        titleLabelY = 8;
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> send(0))
                .bounds(leftPos + 12, topPos + 108, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> send(1))
                .bounds(leftPos + 35, topPos + 108, 20, 18).build());
        lockButton = Button.builder(Component.translatable("screen.whimsy_automation.lock"), b -> send(6))
                .bounds(leftPos + 64, topPos + 108, 68, 18).build();
        addRenderableWidget(lockButton);
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.process"), b -> send(3))
                .bounds(leftPos + 140, topPos + 108, 84, 18).build());
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> send(4))
                .bounds(leftPos + 166, topPos + 130, 24, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> send(5))
                .bounds(leftPos + 198, topPos + 130, 24, 18).build());
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
        g.fill(x + 5, y + 24, x + 235, y + 104, 0xFF24221F);
        g.fill(x + 5, y + 106, x + 235, y + 144, 0xFF171B20);
        g.fill(x + 5, y + 146, x + 235, y + 204, 0xFF171B20);
        g.fill(x + 5, y + 210, x + 235, y + 304, 0xFF171B20);
        g.fill(x + 5, y + 24, x + 235, y + 25, 0xFFB77C45);
        g.fill(x + 5, y + 204, x + 235, y + 205, 0xFF65503C);

        slot(g, x + 22, y + 60);
        for (int i = 0; i < 9; i++) slot(g, x + 112 + (i % 3) * 18, y + 50 + (i / 3) * 18);
        for (int i = 0; i < 27; i++) slot(g, x + 26 + (i % 9) * 18, y + 150 + (i / 9) * 18);
        for (int i = 0; i < 27; i++) slot(g, x + 26 + (i % 9) * 18, y + 218 + (i / 9) * 18);
        for (int i = 0; i < 9; i++) slot(g, x + 26 + i * 18, y + 276);

        List<ItemStack> preview = menu.getPreviewStacks();
        for (int i = 0; i < preview.size(); i++) {
            ItemStack stack = preview.get(i);
            if (!stack.isEmpty()) g.renderItem(stack, x + 112 + (i % 3) * 18, y + 50 + (i / 3) * 18);
        }
        drawSwitch(g, x + 12, y + 132, menu.isAutoMode());
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("screen.whimsy_automation.deconstruction"), 8, 8, 0xF0D0A2, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.input"), 12, 43, 0xD9B98D, false);
        String status = font.plainSubstrByWidth(menu.getRecipeStatus(), 150);
        g.drawString(font, status, 48, 36, menu.isLocked() ? 0xFFFFC66E : 0xFFE0C49B, false);
        String recipeId = font.plainSubstrByWidth(menu.getPreviewRecipeId(), 150);
        g.drawString(font, recipeId.isEmpty() ? Component.translatable("screen.whimsy_automation.preview_empty") : recipeId,
                48, 52, 0xFFB9C6D3, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.recipe_materials"), 112, 38, 0xFFA9D5B8, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.outputs"), 26, 138, 0xFFA9D5B8, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.auto_short"), 48, 134, 0xFFC9D3E1, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.page", menu.getPage() + 1, menu.getPageCount()), 116, 134, 0xFFD4C7AF, false);
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
            if (localX >= 12 && localX < 42 && localY >= 132 && localY < 145) {
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