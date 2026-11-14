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
    private int selectedIngredientGroup = -1;
    private int ingredientPickerScroll;
    private boolean showSideConfig;

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
                .bounds(leftPos + 184, topPos + 138, 98, 14).build();
        addRenderableWidget(autoButton);
        addRenderableWidget(Button.builder(Component.literal("六向设置"), b -> {
            showSideConfig = !showSideConfig;
            selectedIngredientGroup = -1;
        }).tooltip(Tooltip.create(Component.literal("分别设置六个面的自动化输入/输出")) )
                .bounds(leftPos + 286, topPos + 138, 58, 14).build());
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> { menu.cyclePreview(-1); send(0); })
                .tooltip(Tooltip.create(Component.literal("上一个匹配配方")))
                .bounds(leftPos + 184, topPos + 156, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> { menu.cyclePreview(1); send(1); })
                .tooltip(Tooltip.create(Component.literal("下一个匹配配方")))
                .bounds(leftPos + 204, topPos + 156, 18, 18).build());
        lockButton = Button.builder(Component.literal(menu.isLocked() ? "解锁" : "锁定"), b -> send(6))
                .bounds(leftPos + 224, topPos + 156, 44, 18).build();
        addRenderableWidget(lockButton);
        addRenderableWidget(Button.builder(Component.literal("拆解"), b -> send(3))
                .tooltip(Tooltip.create(Component.translatable("screen.whimsy_automation.process")))
                .bounds(leftPos + 272, topPos + 156, 72, 18).build());
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
                int group = menu.getPreviewPage() * 18 + i;
                if (menu.getIngredientOptions(group).length > 1) graphics.drawString(font, "+", x + 11, y - 2, 0xFF2A7FFF, true);
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
                184, 177, 0x404040, false);
        if (autoButton != null) autoButton.setMessage(autoLabel());
        if (lockButton != null) lockButton.setMessage(Component.literal(menu.isLocked() ? "解锁" : "锁定"));
    }

    private void renderSideConfig(GuiGraphics graphics) {
        if (!showSideConfig) return;
        int x = leftPos + 184, y = topPos + 74;
        panel(graphics, x, y, 160, 66);
        String[] names = {"下", "上", "北", "南", "西", "东"};
        String[] modes = {"关", "入", "出"};
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            String label = names[i] + ":" + modes[Math.min(2, menu.getSideMode(i))];
            graphics.drawString(font, Component.literal(label), x + 8 + col * 52, y + 8 + row * 25, 0x404040, false);
        }
        graphics.drawString(font, Component.literal("点击切换：关闭 / 输入 / 输出"), x + 5, y + 54, 0x606060, false);
    }

    private boolean clickSideConfig(double mouseX, double mouseY) {
        if (!showSideConfig) return false;
        int x = leftPos + 184, y = topPos + 74;
        if (mouseX >= x && mouseX < x + 160 && mouseY >= y + 2 && mouseY < y + 52) {
            int col = Math.min(2, (int) ((mouseX - (x + 4)) / 52));
            int row = mouseY < y + 32 ? 0 : 1;
            int side = row * 3 + col;
            send(10 + side);
            return true;
        }
        showSideConfig = false;
        return false;
    }

    private void renderIngredientPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selectedIngredientGroup < 0) return;
        ItemStack[] options = menu.getIngredientOptions(selectedIngredientGroup);
        if (options.length <= 1) { selectedIngredientGroup = -1; return; }
        int x = leftPos + 190, y = topPos + 74;
        int visible = Math.min(8, options.length);
        panel(graphics, x, y, 158, visible * 18 + 4);
        graphics.drawString(font, Component.literal("选择材料"), x + 5, y + 4, 0x404040, false);
        int start = Math.min(ingredientPickerScroll, Math.max(0, options.length - visible));
        for (int row = 0; row < visible; row++) {
            int index = start + row; ItemStack option = options[index]; int rowY = y + 16 + row * 18;
            graphics.renderItem(option, x + 4, rowY);
            graphics.drawString(font, option.getHoverName(), x + 24, rowY + 4, 0x404040, false);
        }
        if (options.length > visible) graphics.drawString(font, Component.literal((start + 1) + "-" + (start + visible) + "/" + options.length), x + 112, y + 4, 0x606060, false);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (selectedIngredientGroup >= 0) {
            ItemStack[] options = menu.getIngredientOptions(selectedIngredientGroup);
            int max = Math.max(0, options.length - 8);
            ingredientPickerScroll = Math.max(0, Math.min(max, ingredientPickerScroll + (delta < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clickSideConfig(mouseX, mouseY)) return true;
        if (selectedIngredientGroup >= 0) {
            ItemStack[] options = menu.getIngredientOptions(selectedIngredientGroup); int x = leftPos + 190, y = topPos + 74;
            int visible = Math.min(8, options.length);
            if (mouseX >= x && mouseX < x + 158 && mouseY >= y + 16 && mouseY < y + 16 + visible * 18) {
                int row = (int) ((mouseY - (y + 16)) / 18); int index = ingredientPickerScroll + row;
                if (index >= 0 && index < options.length) { send(1000 + selectedIngredientGroup * 32768 + index); selectedIngredientGroup = -1; return true; }
            }
            if (mouseX < x || mouseX >= x + 158 || mouseY < y || mouseY >= y + 16 + visible * 18) selectedIngredientGroup = -1;
            return true;
        }
        int gridX = leftPos + 202, gridY = topPos + 71;
        if (mouseX >= gridX && mouseX < gridX + 108 && mouseY >= gridY && mouseY < gridY + 54) {
            int col = (int) ((mouseX - gridX) / 18), row = (int) ((mouseY - gridY) / 18);
            int group = menu.getPreviewPage() * 18 + row * 6 + col;
            if (menu.getIngredientOptions(group).length > 1) { selectedIngredientGroup = group; ingredientPickerScroll = 0; return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderIngredientPicker(graphics, mouseX, mouseY);
        renderSideConfig(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
