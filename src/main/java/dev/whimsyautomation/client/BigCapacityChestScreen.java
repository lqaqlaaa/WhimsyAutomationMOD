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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the same explicit tooltip dispatch used by the machine screens.
        // The chest screen previously overrode renderTooltip() but never
        // explicitly invoked it from its own render path.
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, String countString) {
        if (slot.index < menu.getRows() * 9 && !stack.isEmpty()) {
            int visible = menu.exactCount(slot.index).min(BigInteger.valueOf(64)).intValue();
            ItemStack display = stack.copyWithCount(Math.max(1, visible));
            // Keep the vanilla item-count badge visible. The old empty override
            // explicitly suppressed the count, making every stored stack look like 1.
            super.renderSlotContents(graphics, display, slot,
                    visible > 1 ? Integer.toString(visible) : "");
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
                // Preserve the complete vanilla/modded tooltip and always show
                // the exact BigInteger amount, independent of the 64-item icon projection.
                lines.add(Math.min(1, lines.size()), Component.literal("数量：" + shown));
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
