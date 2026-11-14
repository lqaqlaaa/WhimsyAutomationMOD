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
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
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

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        // Vanilla inventory-panel bevel, without borrowing a chest GUI that
        // would paint dozens of slots which do not exist in this machine.
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
    }

    private void drawSlot(GuiGraphics g, int x, int y) {
        g.blitSprite(SLOT, x, y, 16, 16);
    }

    private void drawPlayerSlots(GuiGraphics g) {
        // Slot coordinates are GUI-local in AbstractContainerMenu. The painted
        // backgrounds must use the same leftPos/topPos transform as real slots.
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawSlot(g, leftPos + 8 + col * 18, topPos + 138 + row * 18);
        for (int col = 0; col < 9; col++)
            drawSlot(g, leftPos + 8 + col * 18, topPos + 196);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> menu.cyclePreviewPage(-1))
                .tooltip(Tooltip.create(Component.literal("上一页材料预览")))
                .bounds(leftPos + 276, topPos + 56, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> menu.cyclePreviewPage(1))
                .tooltip(Tooltip.create(Component.literal("下一页材料预览")))
                .bounds(leftPos + 296, topPos + 56, 14, 14).build());

        addRenderableWidget(Button.builder(Component.literal("‹"), b -> send(4))
                .tooltip(Tooltip.create(Component.literal("上一页拆解产物")))
                .bounds(leftPos + 114, topPos + 56, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> send(5))
                .tooltip(Tooltip.create(Component.literal("下一页拆解产物")))
                .bounds(leftPos + 134, topPos + 56, 14, 14).build());

        autoButton = Button.builder(autoLabel(), b -> send(2))
                .tooltip(Tooltip.create(autoLabel()))
                .bounds(leftPos + 184, topPos + 120, 160, 14).build();
        addRenderableWidget(autoButton);
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> { menu.cyclePreview(-1); send(0); })
                .tooltip(Tooltip.create(Component.literal("上一个匹配配方")))
                .bounds(leftPos + 187, topPos + 147, 12, 12).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> { menu.cyclePreview(1); send(1); })
                .tooltip(Tooltip.create(Component.literal("下一个匹配配方")))
                .bounds(leftPos + 207, topPos + 147, 12, 12).build());
        lockButton = Button.builder(Component.literal(menu.isLocked() ? "解锁" : "锁定"), b -> send(6))
                .bounds(leftPos + 224, topPos + 144, 44, 18).build();
        addRenderableWidget(lockButton);
        addRenderableWidget(Button.builder(Component.literal("拆解"), b -> send(3))
                .tooltip(Tooltip.create(Component.translatable("screen.whimsy_automation.process")))
                .bounds(leftPos + 272, topPos + 144, 72, 18).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        panel(graphics, leftPos, topPos + 54, 176, 166);
        panel(graphics, leftPos + 176, topPos + 54, 176, 166);

        // Exactly 27 real machine output slots.
        for (int i = 0; i < 27; i++)
            drawSlot(graphics, leftPos + 8 + (i % 9) * 18, topPos + 71 + (i / 9) * 18);

        // One real input slot, followed by exactly 18 non-interactive preview slots.
        drawSlot(graphics, leftPos + 184, topPos + 71);
        List<ItemStack> preview = menu.getPreviewStacks();
        for (int i = 0; i < 18; i++) {
            int x = leftPos + 202 + (i % 6) * 18;
            int y = topPos + 71 + (i / 6) * 18;
            drawSlot(graphics, x, y);
            if (i < preview.size()) {
                ItemStack stack = preview.get(i);
                graphics.renderItem(stack, x, y);
                graphics.renderItemDecorations(font, stack, x, y);
            }
        }
        drawPlayerSlots(graphics);
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
        int recipeCount = menu.getCandidateCount();
        int recipeIndex = menu.getSelectedRecipePosition();
        graphics.drawString(font, Component.literal("配方 " + (recipeCount == 0 ? 0 : recipeIndex) + "/" + recipeCount),
                184, 181, 0x404040, false);
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
