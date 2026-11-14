package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.EnchantmentExtractorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class EnchantmentExtractorScreen extends AbstractContainerScreen<EnchantmentExtractorMenu> {
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
    private Button autoButton;
    private Button processButton;
    private Button outputModeButton;

    public EnchantmentExtractorScreen(EnchantmentExtractorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 352;
        imageHeight = 220;
        inventoryLabelY = 126;
        titleLabelY = 60;
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private Component autoLabel() {
        return Component.literal(menu.isAutoMode() ? "自动：开启" : "自动：关闭");
    }

    private Component outputModeLabel() {
        return Component.translatable(switch (menu.getOutputMode()) {
            case 0 -> "screen.whimsy_automation.mode_combined_short";
            case 1 -> "screen.whimsy_automation.mode_enchantments_short";
            default -> "screen.whimsy_automation.mode_levels_short";
        });
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

    private void drawPlayerSlots(GuiGraphics g) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawSlot(g, leftPos + 8 + col * 18, topPos + 138 + row * 18);
        for (int col = 0; col < 9; col++)
            drawSlot(g, leftPos + 8 + col * 18, topPos + 196);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> send(3))
                .tooltip(Tooltip.create(Component.translatable("screen.whimsy_automation.previous")))
                .bounds(leftPos + 114, topPos + 55, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> send(4))
                .tooltip(Tooltip.create(Component.translatable("screen.whimsy_automation.next")))
                .bounds(leftPos + 134, topPos + 55, 14, 14).build());

        autoButton = Button.builder(autoLabel(), b -> send(0))
                .bounds(leftPos + 184, topPos + 96, 160, 18).build();
        addRenderableWidget(autoButton);
        processButton = Button.builder(Component.translatable("screen.whimsy_automation.process"), b -> send(1))
                .bounds(leftPos + 184, topPos + 120, 76, 18).build();
        addRenderableWidget(processButton);
        outputModeButton = Button.builder(outputModeLabel(), b -> send(2))
                .bounds(leftPos + 264, topPos + 120, 80, 18).build();
        addRenderableWidget(outputModeButton);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        panel(graphics, leftPos, topPos + 54, 176, 166);
        panel(graphics, leftPos + 176, topPos + 54, 176, 166);

        // Exactly 27 stored-output slots, one machine input slot, and the
        // player's single native 36-slot inventory. No decorative fake slots.
        for (int i = 0; i < 27; i++)
            drawSlot(graphics, leftPos + 8 + (i % 9) * 18, topPos + 71 + (i / 9) * 18);
        drawSlot(graphics, leftPos + 184, topPos + 71);
        drawPlayerSlots(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.extractor"),
                8, titleLabelY, 0x404040, false);
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.page",
                menu.getPage() + 1, menu.getPageCount()), 150, 61, 0x404040, false);
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.book_input"),
                202, 75, 0x404040, false);
        graphics.drawString(font, Component.translatable("container.inventory"),
                8, inventoryLabelY, 0x404040, false);
        if (autoButton != null) autoButton.setMessage(autoLabel());
        if (outputModeButton != null) outputModeButton.setMessage(outputModeLabel());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
