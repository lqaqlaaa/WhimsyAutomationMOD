package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.BigCapacityChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BigCapacityChestScreen extends AbstractContainerScreen<BigCapacityChestMenu> {
    private static final ResourceLocation CONTAINER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public BigCapacityChestScreen(BigCapacityChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageHeight = 114 + menu.getRowCount() * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int chestHeight = this.menu.getRowCount() * 18 + 17;
        graphics.blit(CONTAINER_TEXTURE, x, y, 0, 0, this.imageWidth, chestHeight);
        graphics.blit(CONTAINER_TEXTURE, x, y + chestHeight, 0, 126, this.imageWidth, 96);
    }

    @Override
    protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, String countString) {
        super.renderSlotContents(graphics, stack, slot, "");
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int chestSlots = this.menu.getRowCount() * 9;
        for (Slot slot : this.menu.slots) {
            if (slot.index >= chestSlots || !slot.hasItem()) continue;
            int x = this.leftPos + slot.x;
            int y = this.topPos + slot.y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                ItemStack stack = slot.getItem();
                List<Component> tooltip = new ArrayList<>(getTooltipFromContainerItem(stack));
                tooltip.add(Component.translatable("tooltip.whimsy_automation.big_capacity_chest.count",
                        NumberFormat.getIntegerInstance(Locale.ROOT).format(stack.getCount())));
                graphics.renderTooltip(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }
}
