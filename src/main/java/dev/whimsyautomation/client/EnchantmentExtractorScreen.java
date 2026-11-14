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
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_27.png");
    private static final ResourceLocation SLOT =
            ResourceLocation.withDefaultNamespace("container/slot");
    private Button autoButton;
    private Button processButton;
    private Button outputModeButton;

    public EnchantmentExtractorScreen(EnchantmentExtractorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 166;
        inventoryLabelY = 72;
        titleLabelY = 6;
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

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> send(3))
                .tooltip(Tooltip.create(Component.translatable("screen.whimsy_automation.previous")))
                .bounds(leftPos + 118, topPos + 2, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> send(4))
                .tooltip(Tooltip.create(Component.translatable("screen.whimsy_automation.next")))
                .bounds(leftPos + 138, topPos + 2, 18, 18).build());

        autoButton = Button.builder(autoLabel(), b -> send(0))
                .bounds(leftPos + 204, topPos + 17, 88, 18).build();
        addRenderableWidget(autoButton);
        processButton = Button.builder(Component.translatable("screen.whimsy_automation.process"), b -> send(1))
                .bounds(leftPos + 184, topPos + 42, 52, 18).build();
        addRenderableWidget(processButton);
        outputModeButton = Button.builder(outputModeLabel(), b -> send(2))
                .bounds(leftPos + 240, topPos + 42, 52, 18).build();
        addRenderableWidget(outputModeButton);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, 176, 166, 176, 166);
        graphics.blitSprite(SLOT, leftPos + 184, topPos + 17, 16, 16);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.extractor"),
                8, titleLabelY, 0x404040, false);
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.page",
                menu.getPage() + 1, menu.getPageCount()), 158, 7, 0x404040, false);
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.book_input"),
                184, 36, 0x404040, false);
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
