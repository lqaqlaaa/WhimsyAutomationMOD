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
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_27.png");
    private static final ResourceLocation SLOT =
            ResourceLocation.withDefaultNamespace("container/slot");
    private Button autoButton;
    private Button lockButton;

    public DeconstructionTableScreen(DeconstructionTableMenu menu, Inventory inventory, Component title) {
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
        return Component.translatable("screen.whimsy_automation.auto_state",
                Component.translatable(menu.isAutoMode()
                        ? "screen.whimsy_automation.on" : "screen.whimsy_automation.off"));
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> menu.cyclePreviewPage(-1))
                .tooltip(Tooltip.create(Component.literal("上一页材料预览")))
                .bounds(leftPos + 184, topPos + 126, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> menu.cyclePreviewPage(1))
                .tooltip(Tooltip.create(Component.literal("下一页材料预览")))
                .bounds(leftPos + 204, topPos + 126, 18, 18).build());

        addRenderableWidget(Button.builder(Component.literal("‹"), b -> send(4))
                .tooltip(Tooltip.create(Component.literal("上一页拆解产物")))
                .bounds(leftPos + 118, topPos + 2, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> send(5))
                .tooltip(Tooltip.create(Component.literal("下一页拆解产物")))
                .bounds(leftPos + 138, topPos + 2, 18, 18).build());

        autoButton = Button.builder(autoLabel(), b -> send(2))
                .tooltip(Tooltip.create(autoLabel()))
                .bounds(leftPos + 204, topPos + 17, 88, 18).build();
        addRenderableWidget(autoButton);
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> { menu.cyclePreview(-1); send(0); })
                .tooltip(Tooltip.create(Component.literal("上一个匹配配方")))
                .bounds(leftPos + 184, topPos + 42, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> { menu.cyclePreview(1); send(1); })
                .tooltip(Tooltip.create(Component.literal("下一个匹配配方")))
                .bounds(leftPos + 204, topPos + 42, 18, 18).build());
        lockButton = Button.builder(Component.literal(menu.isLocked() ? "解锁" : "锁定"), b -> send(6))
                .bounds(leftPos + 224, topPos + 42, 32, 18).build();
        addRenderableWidget(lockButton);
        addRenderableWidget(Button.builder(Component.literal("拆解"), b -> send(3))
                .tooltip(Tooltip.create(Component.translatable("screen.whimsy_automation.process")))
                .bounds(leftPos + 260, topPos + 42, 32, 18).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, 176, 166, 176, 166);
        graphics.blitSprite(SLOT, leftPos + 184, topPos + 17, 16, 16);
        List<ItemStack> preview = menu.getPreviewStacks();
        for (int i = 0; i < 18; i++) {
            int x = leftPos + 184 + (i % 6) * 18;
            int y = topPos + 65 + (i / 6) * 18;
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
                158, 7, 0x404040, false);
        graphics.drawString(font, Component.translatable("container.inventory"),
                8, inventoryLabelY, 0x404040, false);
        graphics.drawString(font, Component.literal("输入物品"), 184, 36, 0x404040, false);
        graphics.drawString(font, Component.literal("配方材料"), 184, 55, 0x404040, false);
        graphics.drawString(font, Component.literal((menu.getPreviewPage() + 1) + "/" + menu.getPreviewPageCount()),
                228, 131, 0x404040, false);
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
