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
    private Button autoButton;
    private Button lockButton;

    public DeconstructionTableScreen(DeconstructionTableMenu menu, Inventory inventory, Component title) {
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
        return Component.translatable("screen.whimsy_automation.auto_state",
                Component.translatable(menu.isAutoMode()
                        ? "screen.whimsy_automation.on" : "screen.whimsy_automation.off"));
    }

    @Override
    protected void init() {
        super.init();

        // The top row follows the vanilla container title area. Material-preview
        // paging is kept separate from the actual output-inventory paging.
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> menu.cyclePreviewPage(-1))
                .tooltip(Tooltip.create(Component.literal("上一页材料预览")))
                .bounds(leftPos + 100, topPos + 5, 16, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> menu.cyclePreviewPage(1))
                .tooltip(Tooltip.create(Component.literal("下一页材料预览")))
                .bounds(leftPos + 118, topPos + 5, 16, 18).build());
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> send(4))
                .tooltip(Tooltip.create(Component.literal("上一页拆解产物")))
                .bounds(leftPos + 140, topPos + 5, 16, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> send(5))
                .tooltip(Tooltip.create(Component.literal("下一页拆解产物")))
                .bounds(leftPos + 158, topPos + 5, 16, 18).build());

        // The sixth machine row is reserved for controls; the input slot remains
        // a real menu slot at the far left and never shares a button hitbox.
        autoButton = Button.builder(autoLabel(), b -> send(2))
                .tooltip(Tooltip.create(autoLabel()))
                .bounds(leftPos + 26, topPos + 106, 64, 18).build();
        addRenderableWidget(autoButton);
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> { menu.cyclePreview(-1); send(0); })
                .tooltip(Tooltip.create(Component.literal("上一个匹配配方")))
                .bounds(leftPos + 92, topPos + 106, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> { menu.cyclePreview(1); send(1); })
                .tooltip(Tooltip.create(Component.literal("下一个匹配配方")))
                .bounds(leftPos + 112, topPos + 106, 18, 18).build());
        lockButton = Button.builder(Component.literal(menu.isLocked() ? "解" : "锁"), b -> send(6))
                .tooltip(Tooltip.create(Component.translatable(menu.isLocked()
                        ? "screen.whimsy_automation.unlock" : "screen.whimsy_automation.lock")))
                .bounds(leftPos + 132, topPos + 106, 18, 18).build();
        addRenderableWidget(lockButton);
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> send(3))
                .tooltip(Tooltip.create(Component.translatable("screen.whimsy_automation.process")))
                .bounds(leftPos + 152, topPos + 106, 18, 18).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 176, 222);

        // The vanilla generic container texture already supplies all 54 slot
        // wells, including the preview rows and the player's inventory.
        List<ItemStack> preview = menu.getPreviewStacks();
        int columns = 9;
        for (int i = 0; i < preview.size() && i < 18; i++) {
            int itemX = leftPos + 8 + (i % columns) * 18;
            int itemY = topPos + 71 + (i / columns) * 18;
            graphics.renderItem(preview.get(i), itemX, itemY);
            graphics.renderItemDecorations(font, preview.get(i), itemX, itemY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.deconstruction"),
                8, titleLabelY, 0x404040, false);
        graphics.drawString(font, Component.translatable("container.inventory"),
                8, inventoryLabelY, 0x404040, false);

        if (autoButton != null) autoButton.setMessage(autoLabel());
        if (lockButton != null) lockButton.setMessage(Component.literal(menu.isLocked() ? "解" : "锁"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
