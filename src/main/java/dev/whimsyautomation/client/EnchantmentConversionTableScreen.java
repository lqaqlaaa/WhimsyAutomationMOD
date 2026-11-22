package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.EnchantmentConversionTableMenu;
import dev.whimsyautomation.network.ConfirmItemCopyPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class EnchantmentConversionTableScreen extends AbstractContainerScreen<EnchantmentConversionTableMenu> {
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
    private EditBox amountInput;

    public EnchantmentConversionTableScreen(EnchantmentConversionTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 186;
        inventoryLabelY = 82;
        titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        amountInput = new EditBox(font, leftPos + 57, topPos + 49, 58, 18,
                Component.translatable("screen.whimsy_automation.copy_count"));
        amountInput.setValue("64");
        amountInput.setMaxLength(7);
        amountInput.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        addRenderableWidget(amountInput);

        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.confirm_copy"), button -> {
            try {
                int count = Integer.parseInt(amountInput.getValue());
                if (count >= 1 && count <= 1_000_000) {
                    ConfirmItemCopyPayload.send(menu.containerId, menu.getBlockPos(), count);
                }
            } catch (NumberFormatException ignored) {
                // An empty or out-of-range value is not sent to the server.
            }
        }).bounds(leftPos + 57, topPos + 72, 62, 20).build());
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

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawSlot(graphics, leftPos + 28, topPos + 50);
        drawSlot(graphics, leftPos + 132, topPos + 50);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawSlot(graphics, leftPos + 8 + col * 18, topPos + 104 + row * 18);
        for (int col = 0; col < 9; col++)
            drawSlot(graphics, leftPos + 8 + col * 18, topPos + 162);
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.copy_count"),
                leftPos + 57, topPos + 39, 0x404040, false);
        graphics.drawString(font, Component.literal("→"), leftPos + 91, topPos + 52, 0x404040, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.conversion_table"),
                8, titleLabelY, 0x404040, false);
        graphics.drawString(font, Component.translatable("container.inventory"),
                8, inventoryLabelY, 0x404040, false);
    }

    private void renderTotalOutputCount(GuiGraphics graphics) {
        int total = menu.getTotalOutputCount();
        if (total <= 0) return;
        String countText = Integer.toString(total);
        int x = leftPos + 132 + 16 - font.width(countText);
        int y = topPos + 50 + 9;
        // Draw the logical total over vanilla's per-stack count (which is at most 99).
        graphics.fill(x - 2, y - 1, x + font.width(countText) + 1, y + 9, 0x99000000);
        graphics.drawString(font, countText, x, y, 0xFFFFFF, true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTotalOutputCount(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
