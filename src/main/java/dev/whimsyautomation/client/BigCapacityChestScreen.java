package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.BigCapacityChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public final class BigCapacityChestScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<BigCapacityChestMenu> {
    public BigCapacityChestScreen(BigCapacityChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 114 + 18 * menu.getRows();
        inventoryLabelY = imageHeight - 94;
        titleLabelY = 6;
    }
    @Override public BigCapacityChestMenu getMenu() { return menu; }

    @Override protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, String countString) {
        super.renderSlotContents(graphics, stack, slot, "");
    }
    @Override protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null && hoveredSlot.index < menu.getRows() * 9 && hoveredSlot.hasItem()) {
            ItemStack stack = hoveredSlot.getItem();
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(stack));
            BigInteger count = menu.exactCount(hoveredSlot.index);
            String shown = count.equals(BigInteger.TEN.pow(308)) ? "1E308" : String.format("%,d", count);
            lines.add(1, Component.literal("数量：" + shown));
            graphics.renderTooltip(font, lines, stack.getTooltipImage(), stack, mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }
}
