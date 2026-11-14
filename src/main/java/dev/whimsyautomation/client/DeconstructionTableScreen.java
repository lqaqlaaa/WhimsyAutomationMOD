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
    private static final int PICKER_VISIBLE_ROWS = 7;
    private boolean pickerDragging;
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
        // renderBg uses screen coordinates; real menu slots are positioned separately.
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
        // renderBg is not translated to GUI-local coordinates in 1.21.1.
        // Background art must use screen coordinates; menu slots remain local.
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
                if (menu.getIngredientOptions(group).length > 1) graphics.drawString(font, "▾", x + 10, y + 1, 0xFF245B9A, true);
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
        // renderLabels is already translated to the GUI origin.
        int x = 176, y = 54;
        // Cover the entire right machine pane so no preview labels, input
        // slots, or action buttons leak out below the dialog.
        panel(graphics, x, y, 176, 166);
        graphics.fill(x + 2, y + 2, x + 174, y + 114, 0xFFE0E0E0);
        graphics.drawString(font, Component.literal("六向设置"), x + 8, y + 8, 0xFF202020, false);
        graphics.drawString(font, Component.literal("×"), x + 162, y + 7, 0xFF202020, false);
        String[] names = {"下", "上", "北", "南", "西", "东"};
        String[] modes = {"关", "入", "出"};
        int[] colors = {0xFFE74C3C, 0xFF2ECC71, 0xFF3498DB, 0xFFF1C40F, 0xFF9B59B6, 0xFF1ABC9C};
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            int cellX = x + 8 + col * 55, cellY = y + 34 + row * 25;
            graphics.fill(cellX, cellY + 1, cellX + 8, cellY + 9, colors[i]);
            graphics.drawString(font, Component.literal(names[i] + "：" + modes[Math.min(2, menu.getSideMode(i))]),
                    cellX + 12, cellY, 0xFF303030, false);
        }
        graphics.drawString(font, Component.literal("点击方向切换输入/输出/关闭"), x + 8, y + 91, 0xFF606060, false);
    }

    private boolean clickSideConfig(double mouseX, double mouseY) {
        if (!showSideConfig) return false;
        int x = leftPos + 176, y = topPos + 54;
        if (mouseX >= x + 150 && mouseX < x + 176 && mouseY >= y && mouseY < y + 24) {
            showSideConfig = false;
            return true;
        }
        if (mouseX >= x + 5 && mouseX < x + 171 && mouseY >= y + 28 && mouseY < y + 82) {
            int col = Math.min(2, Math.max(0, (int) ((mouseX - (x + 8)) / 55)));
            int row = mouseY < y + 59 ? 0 : 1;
            send(10 + row * 3 + col);
            return true;
        }
        // Consume every click while the modal is open; never forward it to the
        // hidden container slots or widgets.
        showSideConfig = false;
        return true;
    }

    // Modal rendering runs under an explicit GUI-origin pose. Input hit tests
    // stay in absolute screen coordinates, so these are intentionally separate.
    private int pickerX() { return 176; }
    private int pickerY() { return 54; }
    private int pickerHitX() { return leftPos + 176; }
    private int pickerHitY() { return topPos + 54; }
    private int pickerHeight() { return 166; }

    private void renderIngredientPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selectedIngredientGroup < 0) return;
        ItemStack[] options = menu.getIngredientOptions(selectedIngredientGroup);
        if (options.length <= 1) { selectedIngredientGroup = -1; return; }
        int x = pickerX(), y = pickerY(), visible = Math.min(PICKER_VISIBLE_ROWS, options.length);
        panel(graphics, x, y, 176, pickerHeight());
        graphics.fill(x + 3, y + 3, x + 173, y + 21, 0xFF8B8B8B);
        graphics.drawString(font, Component.literal("选择材料"), x + 8, y + 8, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.literal("×"), x + 162, y + 7, 0xFFFFFFFF, false);
        int start = Math.min(ingredientPickerScroll, Math.max(0, options.length - visible));
        int selected = menu.getSelectedIngredientVariant(selectedIngredientGroup);
        for (int row = 0; row < visible; row++) {
            int index = start + row, rowY = y + 25 + row * 18;
            if (index == selected) graphics.fill(x + 5, rowY, x + 166, rowY + 18, 0xFF8AA9C8);
            graphics.renderItem(options[index], x + 8, rowY + 1);
            graphics.drawString(font, options[index].getHoverName(), x + 30, rowY + 6, 0xFF202020, false);
            if (index == selected) graphics.drawString(font, "✓", x + 153, rowY + 6, 0xFF174A17, false);
        }
        if (options.length > PICKER_VISIBLE_ROWS) {
            int trackTop = y + 25, trackHeight = PICKER_VISIBLE_ROWS * 18;
            graphics.fill(x + 169, trackTop, x + 172, trackTop + trackHeight, 0xFF777777);
            int thumbHeight = Math.max(12, trackHeight * PICKER_VISIBLE_ROWS / options.length);
            int maxScroll = options.length - PICKER_VISIBLE_ROWS;
            int thumbY = trackTop + (trackHeight - thumbHeight) * ingredientPickerScroll / maxScroll;
            graphics.fill(x + 169, thumbY, x + 172, thumbY + thumbHeight, 0xFF303030);
        }
        graphics.drawString(font, Component.literal((start + 1) + " / " + options.length), x + 8, y + 151, 0xFF404040, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (showSideConfig) return true;
        if (selectedIngredientGroup >= 0) {
            int x = pickerHitX(), y = pickerHitY();
            ItemStack[] options = menu.getIngredientOptions(selectedIngredientGroup);
            if (mouseX >= x && mouseX < x + 176 && mouseY >= y + 25 && mouseY < y + 151) {
                int max = Math.max(0, options.length - PICKER_VISIBLE_ROWS);
                ingredientPickerScroll = Math.max(0, Math.min(max,
                        ingredientPickerScroll + (deltaY < 0 ? 1 : -1)));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectedIngredientGroup >= 0) {
            int x = pickerHitX(), y = pickerHitY();
            ItemStack[] options = menu.getIngredientOptions(selectedIngredientGroup);
            int visible = Math.min(PICKER_VISIBLE_ROWS, options.length);
            if (mouseX >= x + 150 && mouseX < x + 176 && mouseY >= y && mouseY < y + 24) {
                selectedIngredientGroup = -1;
                return true;
            }
            if (options.length > PICKER_VISIBLE_ROWS && mouseX >= x + 166 && mouseX < x + 176 && mouseY >= y + 25 && mouseY < y + 151) { pickerDragging = true; return true; }
            if (mouseX >= x + 5 && mouseX < x + 166 && mouseY >= y + 25 && mouseY < y + 25 + visible * 18) {
                int row = (int) ((mouseY - (y + 25)) / 18);
                int index = ingredientPickerScroll + row;
                if (index >= 0 && index < options.length) {
                    send(1000 + selectedIngredientGroup * 32768 + index);
                    selectedIngredientGroup = -1;
                }
                return true;
            }
            selectedIngredientGroup = -1;
            return true;
        }
        if (clickSideConfig(mouseX, mouseY)) return true;
        int gridX = leftPos + 202, gridY = topPos + 71;
        if (mouseX >= gridX && mouseX < gridX + 108 && mouseY >= gridY && mouseY < gridY + 54) {
            int col = (int) ((mouseX - gridX) / 18), row = (int) ((mouseY - gridY) / 18);
            int group = menu.getPreviewPage() * 18 + row * 6 + col;
            if (menu.getIngredientOptions(group).length > 1) {
                selectedIngredientGroup = group;
                ingredientPickerScroll = 0;
                showSideConfig = false;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (selectedIngredientGroup >= 0) {
            int y = pickerHitY();
            ItemStack[] options = menu.getIngredientOptions(selectedIngredientGroup);
            if (pickerDragging && options.length > PICKER_VISIBLE_ROWS) {
                int trackTop = y + 25, trackHeight = PICKER_VISIBLE_ROWS * 18;
                int thumbHeight = Math.max(12, trackHeight * PICKER_VISIBLE_ROWS / options.length);
                int maxScroll = options.length - PICKER_VISIBLE_ROWS;
                double ratio = Math.max(0, Math.min(1, (mouseY - trackTop - thumbHeight / 2.0) / (trackHeight - thumbHeight)));
                ingredientPickerScroll = (int) Math.round(ratio * maxScroll);
            }
            return true;
        }
        if (showSideConfig) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pickerDragging = false;
        if (selectedIngredientGroup >= 0 || showSideConfig) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectedIngredientGroup >= 0 || showSideConfig) {
            if (keyCode == 256) {
                selectedIngredientGroup = -1;
                showSideConfig = false;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (showSideConfig || selectedIngredientGroup >= 0) {
            // Keep the left output storage and player inventory visible. The
            // opaque dialog replaces only the right-hand machine controls.
            // AbstractContainerScreen renders the background, machine GUI,
            // real slot contents, and widgets exactly once. A second explicit
            // renderBackground call was darkening the world a second time.
            super.render(graphics, -10000, -10000, partialTick);
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos, topPos, 0);
            if (showSideConfig) renderSideConfig(graphics);
            else renderIngredientPicker(graphics, mouseX, mouseY);
            graphics.pose().popPose();
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
