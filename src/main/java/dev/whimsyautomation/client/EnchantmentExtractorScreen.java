package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.EnchantmentExtractorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class EnchantmentExtractorScreen extends AbstractContainerScreen<EnchantmentExtractorMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private Button autoButton;
    private Button processButton;
    private Button outputModeButton;

    public EnchantmentExtractorScreen(EnchantmentExtractorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = 127;
        titleLabelY = 6;
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private Component autoLabel() {
        return Component.literal(menu.isAutoMode() ? "自动✓" : "自动×");
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
                .bounds(leftPos + 136, topPos + 5, 16, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> send(4))
                .bounds(leftPos + 154, topPos + 5, 16, 18).build());

        autoButton = Button.builder(autoLabel(), b -> send(0))
                .bounds(leftPos + 26, topPos + 106, 32, 18).build();
        addRenderableWidget(autoButton);
        processButton = Button.builder(Component.translatable("screen.whimsy_automation.process"), b -> send(1))
                .bounds(leftPos + 60, topPos + 106, 40, 18).build();
        addRenderableWidget(processButton);
        outputModeButton = Button.builder(outputModeLabel(), b -> send(2))
                .bounds(leftPos + 102, topPos + 106, 66, 18).build();
        addRenderableWidget(outputModeButton);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 176, 222);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.extractor"),
                8, titleLabelY, 0x404040, false);
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.page",
                menu.getPage() + 1, menu.getPageCount()), 82, 7, 0x404040, false);
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.book_input"),
                8, 96, 0x404040, false);
        graphics.drawString(font, Component.translatable("container.inventory"),
                8, inventoryLabelY, 0x404040, false);

        if (autoButton != null) autoButton.setMessage(autoLabel());
        if (outputModeButton != null) outputModeButton.setMessage(outputModeLabel());
        if (processButton != null) processButton.setMessage(Component.translatable("screen.whimsy_automation.process"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
