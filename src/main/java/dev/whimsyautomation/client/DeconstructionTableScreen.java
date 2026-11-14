package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.DeconstructionTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class DeconstructionTableScreen extends AbstractContainerScreen<DeconstructionTableMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation SLOT =
            ResourceLocation.withDefaultNamespace("container/slot");
    private Button autoButton;
    private Button lockButton;

    public DeconstructionTableScreen(DeconstructionTableMenu menu, Inventory inventory, Component title) {
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
        return Component.translatable("screen.whimsy_automation.auto_state",
                Component.translatable(menu.isAutoMode()
                        ? "screen.whimsy_automation.on" : "screen.whimsy_automation.off"));
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> menu.cyclePreviewPage(-1))
                .tooltip(Tooltip.create(Component.literal("上一页材料预览")))
                .bounds(leftPos + 274, topPos + 56, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> menu.cyclePreviewPage(1))
                .tooltip(Tooltip.create(Component.literal("下一页材料预览")))
                .bounds(leftPos + 294, topPos + 56, 18, 18).build());

        addRenderableWidget(Button.builder(Component.literal("‹"), b -> send(4))
                .tooltip(Tooltip.create(Component.literal("上一页拆解产物")))
                .bounds(leftPos + 112, topPos + 56, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> send(5))
                .tooltip(Tooltip.create(Component.literal("下一页拆解产物")))
                .bounds(leftPos + 132, topPos + 56, 18, 18).build());

        autoButton = Button.builder(autoLabel(), b -> send(2))
                .tooltip(Tooltip.create(autoLabel()))
                .bounds(leftPos + 184, topPos + 4, 160, 18).build();
        addRenderableWidget(autoButton);
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> { menu.cyclePreview(-1); send(0); })
                .tooltip(Tooltip.create(Component.literal("上一个匹配配方")))
                .bounds(leftPos + 184, topPos + 28, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> { menu.cyclePreview(1); send(1); })
                .tooltip(Tooltip.create(Component.literal("下一个匹配配方")))
                .bounds(leftPos + 204, topPos + 28, 18, 18).build());
        lockButton = Button.builder(Component.literal(menu.isLocked() ? "解锁" : "锁定"), b -> send(6))
                .bounds(leftPos + 224, topPos + 28, 44, 18).build();
        addRenderableWidget(lockButton);
        addRenderableWidget(Button.builder(Component.literal("拆解"), b -> send(3))
                .tooltip(Tooltip.create(Component.translatable("screen.whimsy_automation.process")))
                .bounds(leftPos + 272, topPos + 28, 72, 18).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Vanilla 27-slot chest layout: draw the three-row chest header,
        // then the inventory section from the lower part of generic_54.png.
        // This avoids the non-existent generic_27 texture and keeps the
        // rendered slots aligned with the menu's real Slot coordinates.
        // Two adjacent vanilla 27-slot container panels: machine outputs on
        // the left, input/material preview on the right. Controls live above
        // the panels so they never cover inventory slots.
        graphics.blit(BACKGROUND, leftPos, topPos + 54, 0, 0, 176, 71, 256, 256);
        graphics.blit(BACKGROUND, leftPos, topPos + 125, 0, 126, 176, 95, 256, 256);
        graphics.blit(BACKGROUND, leftPos + 176, topPos + 54, 0, 0, 176, 71, 256, 256);
        // The right-hand area is machine-specific preview/storage only.
        // Do not draw a second vanilla player-inventory panel here.
                List<ItemStack> preview = menu.getPreviewStacks();
        for (int i = 0; i < 18; i++) {
            int x = leftPos + 202 + (i % 8) * 18;
            int y = topPos + 71 + (i / 8) * 18;
            graphics.blitSprite(SLOT, x, y, 16, 16);
            if (i < preview.size()) {
                ItemStack stack = preview.get(i);
                graphics.renderItem(stack, x, y);
                graphics.renderItemDecorations(font, stack, x, y);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.deconstruction"),
                8, titleLabelY, 0x404040, false);
        graphics.drawString(font, Component.literal((menu.getPage() + 1) + "/" + menu.getPageCount()),
                152, 61, 0x404040, false);
        graphics.drawString(font, Component.translatable("container.inventory"),
                8, inventoryLabelY, 0x404040, false);
        graphics.drawString(font, Component.literal("输入"), 184, 60, 0x404040, false);
        graphics.drawString(font, Component.literal("材料预览"), 220, 60, 0x404040, false);
        graphics.drawString(font, Component.literal((menu.getPreviewPage() + 1) + "/" + menu.getPreviewPageCount()),
                314, 60, 0x404040, false);
        if (autoButton != null) autoButton.setMessage(autoLabel());
        if (lockButton != null) lockButton.setMessage(Component.literal(menu.isLocked() ? "解锁" : "锁定"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
