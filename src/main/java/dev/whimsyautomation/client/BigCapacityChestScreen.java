package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.BigCapacityChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public final class BigCapacityChestScreen extends AbstractContainerScreen<BigCapacityChestMenu> {
    private static final ResourceLocation CHEST_27_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_27.png");
    private static final ResourceLocation CHEST_54_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public BigCapacityChestScreen(BigCapacityChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 114 + 18 * menu.getRows();
        inventoryLabelY = imageHeight - 94;
        titleLabelY = 6;
    }

    @Override
    public BigCapacityChestMenu getMenu() {
        return menu;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ResourceLocation texture = menu.getRows() == 6 ? CHEST_54_TEXTURE : CHEST_27_TEXTURE;
        graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, String countString) {
        if (slot.index < menu.getRows() * 9 && !stack.isEmpty()) {
            int visible = menu.exactCount(slot.index).min(BigInteger.valueOf(64)).intValue();
            ItemStack display = stack.copyWithCount(Math.max(1, visible));
            // Hide only the vanilla count badge in the chest slot. Do not alter
            // AbstractContainerScreen's item-hover/tooltip pipeline.
            super.renderSlotContents(graphics, display, slot, "");
            return;
        }
        super.renderSlotContents(graphics, stack, slot, countString);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        // Match the machine screens: render the hovered ItemStack tooltip directly.
        // Calling super.renderTooltip here only renders generic Screen/widget
        // tooltips; it does NOT render an inventory slot's item tooltip.
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            ItemStack stack = hoveredSlot.getItem();
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(stack));

            if (hoveredSlot.index < menu.getRows() * 9) {
                BigInteger count = menu.exactCount(hoveredSlot.index);
                String shown = count.equals(BigInteger.TEN.pow(308)) ? "1E308" : String.format("%,d", count);
                if (!lines.isEmpty()) {
                    // Preserve the complete vanilla/modded tooltip and insert
                    // the exact stored quantity immediately below the item name.
                    lines.add(1, Component.literal("数量：" + shown));
                }
            } else {
                // Player inventory slots retain their normal stack-count line.
                lines = new ArrayList<>(StackCountDisplay.withCount(lines, stack));
            }

            graphics.renderTooltip(font, lines, stack.getTooltipImage(), stack, mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
