package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.EnchantmentExtractorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class EnchantmentExtractorScreen extends AbstractContainerScreen<EnchantmentExtractorMenu> {
    private static final ResourceLocation SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/slot");
    private Button autoButton;
    private Button processButton;
    private Button outputModeButton;
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
        autoButton = Button.builder(Component.translatable("screen.whimsy_automation.auto"), b -> send(0))
                .bounds(leftPos + 8, topPos + 104, 48, 18).build();
        addRenderableWidget(autoButton);
        processButton = Button.builder(Component.translatable("screen.whimsy_automation.process"), b -> send(1))
                .bounds(leftPos + 82, topPos + 104, 60, 18).build();
        addRenderableWidget(processButton);
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.previous"), b -> send(3)).bounds(leftPos + 52, topPos + 124, 42, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.next"), b -> send(4)).bounds(leftPos + 146, topPos + 124, 42, 18).build());
        outputModeButton = Button.builder(outputModeLabel(), b -> send(2))
                .bounds(leftPos + 145, topPos + 104, 88, 18).build();
        addRenderableWidget(outputModeButton);
    }

    private Component outputModeLabel() {
        return Component.translatable(switch (menu.getOutputMode()) {
            case 0 -> "screen.whimsy_automation.mode_combined_short";
            case 1 -> "screen.whimsy_automation.mode_enchantments_short";
            default -> "screen.whimsy_automation.mode_levels_short";
        });
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF00B0F19);
        g.fill(x + 5, y + 24, x + 235, y + 100, 0xFF151D2B);
        g.fill(x + 5, y + 102, x + 235, y + 143, 0xFF111824);
        g.fill(x + 5, y + 146, x + 235, y + 244, 0xFF111824);
        g.fill(x + 5, y + 24, x + 235, y + 25, 0xFF8B5AD8);
        g.fill(x + 5, y + 143, x + 235, y + 144, 0xFF34445B);
        slot(g, x + 18, y + 52);
        for (int i = 0; i < 27; i++) slot(g, x + 48 + (i % 9) * 18, y + 42 + (i / 9) * 18);
        for (int i = 0; i < 27; i++) slot(g, x + 26 + (i % 9) * 18, y + 158 + (i / 9) * 18);
        for (int i = 0; i < 9; i++) slot(g, x + 26 + i * 18, y + 216);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("screen.whimsy_automation.extractor"), 8, 8, 0xE8D8FF, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.input"), 12, 30, 0x8EDDE0, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.outputs"), 48, 30, 0xD5C5F2, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.book_input"), 4, 68, 0xD5C5F2, false);
        g.drawString(font, Component.translatable("screen.whimsy_automation.page", menu.getPage() + 1, menu.getPageCount()), 100, 128, 0xD5C5F2, false);
        if (outputModeButton != null) outputModeButton.setMessage(outputModeLabel());
        if (autoButton != null) autoButton.setMessage(Component.translatable("screen.whimsy_automation.auto"));
        g.drawString(font, Component.translatable("container.inventory"), 26, inventoryLabelY, 0xAAB7C8, false);
        if (processButton != null) processButton.setMessage(Component.translatable("screen.whimsy_automation.process"));
    }

    private void slot(GuiGraphics g, int x, int y) {
        g.blitSprite(SLOT_SPRITE, x, y, 16, 16);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
