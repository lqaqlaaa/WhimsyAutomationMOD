package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.EnchantmentConversionTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class EnchantmentConversionTableScreen extends AbstractContainerScreen<EnchantmentConversionTableMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/smithing.png");

    public EnchantmentConversionTableScreen(EnchantmentConversionTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 72;
        titleLabelY = 6;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 176, 166);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.conversion_table"),
                8, titleLabelY, 0x404040, false);
        // Keep the native smithing-table layout uncluttered. Slot roles are
        // represented by the vanilla template/base/addition/result positions;
        // extra captions here collided with the smithing icon and result area.
        graphics.drawString(font, Component.translatable("container.inventory"), 8, inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
