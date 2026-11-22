package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.DeconstructionTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

import java.util.List;

public final class DeconstructionTableScreen extends AbstractContainerScreen<DeconstructionTableMenu> {
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
    private Button autoButton;
    private Button lockButton;
    private EditBox outputPageField;
    private EditBox previewPageField;
    private int selectedIngredientGroup = -1;
    private int ingredientPickerScroll;
    private static final int PICKER_VISIBLE_ROWS = 7;
    private boolean pickerDragging;
    private boolean showSideConfig;
    private boolean showFaceColors;
    private UiLayoutEditor layoutEditor;
    private Button layoutDebugButton;
    private Button layoutExportButton;
    private Button layoutResetButton;

    public DeconstructionTableScreen(DeconstructionTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 352;
        imageHeight = 222;
        inventoryLabelY = 130;
        titleLabelY = 42;
    }

    private double uiScale() {
        double horizontal = (width - 8.0) / imageWidth;
        double vertical = (height - 8.0) / imageHeight;
        return Math.max(0.05, Math.min(1.0, Math.min(horizontal, vertical)));
    }

    private double virtualMouseX(double x) {
        double scale = uiScale();
        return width / 2.0 + (x - width / 2.0) / scale;
    }

    private double virtualMouseY(double y) {
        double scale = uiScale();
        return height / 2.0 + (y - height / 2.0) / scale;
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private EditBox pageField(int x, int y, int initialPage) {
        EditBox field = new EditBox(font, leftPos + x, topPos + y, 22, 18, Component.literal("页码"));
        field.setMaxLength(10);
        field.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        field.setValue(Integer.toString(initialPage));
        field.setHint(Component.literal("页"));
        return field;
    }

    private void jumpPage(EditBox field, int pageCount, int channel) {
        if (field == null) return;
        int requested;
        try { requested = Integer.parseInt(field.getValue()); }
        catch (NumberFormatException ignored) { requested = 1; }
        int page = Math.max(0, Math.min(Math.max(1, pageCount) - 1, requested - 1));
        field.setValue(Integer.toString(page + 1));
        send(-((page * 2 + channel) + 1));
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

    private void drawLayoutLabel(GuiGraphics graphics, String id, Component value,
                                  int x, int y, int color, boolean shadow) {
        UiLayoutProfile.Element e = layoutEditor.element(id, "label", x, y,
                Math.max(1, font.width(value)), 9);
        float sx = e.width / (float) Math.max(1, font.width(value)) * e.textScale;
        float sy = e.height / 9.0F * e.textScale;
        graphics.pose().pushPose();
        graphics.pose().translate(e.x, e.y, 0.0F);
        graphics.pose().scale(sx, sy, 1.0F);
        graphics.drawString(font, value, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    private void drawLayoutPanel(GuiGraphics graphics, String id,
                                 int x, int y, int width, int height) {
        UiLayoutProfile.Element e = layoutEditor.element(id, "panel", x, y, width, height);
        panel(graphics, leftPos + e.x, topPos + e.y, e.width, e.height);
    }

    private void drawLayoutSlot(GuiGraphics graphics, String id, int menuSlot,
                                int x, int y) {
        UiLayoutProfile.Element e = layoutEditor.element(id, "slot", x, y, 16, 16);
        graphics.blitSprite(SLOT, leftPos + e.x, topPos + e.y, e.width, e.height);
    }

    private void drawPlayerSlots(GuiGraphics g) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) {
                int index = 28 + row * 9 + col;
                drawLayoutSlot(g, "player_slot_" + index, index, 8 + col * 18, 139 + row * 18);
            }
        for (int col = 0; col < 9; col++) {
            int index = 55 + col;
            drawLayoutSlot(g, "player_slot_" + index, index, 8 + col * 18, 197);
        }
    }

    @Override
    protected void init() {
        super.init();
        showFaceColors = menu.machine() != null && SideConfigOverlay.isActive(menu.machine());
        topPos -= 27;
        layoutEditor = new UiLayoutEditor("deconstruction_table");
        layoutDebugButton = Button.builder(Component.literal("布局"), b -> layoutEditor.toggle())
                .bounds(leftPos + 312, topPos + 38, 32, 14).build();
        addRenderableWidget(layoutDebugButton);
        layoutExportButton = Button.builder(Component.literal("导出"), b -> {
            try { layoutEditor.export(); } catch (java.io.IOException ignored) { }
        }).bounds(leftPos + 250, topPos + 38, 28, 14).build();
        layoutResetButton = Button.builder(Component.literal("复位"), b -> layoutEditor.reset())
                .bounds(leftPos + 280, topPos + 38, 28, 14).build();
        addRenderableWidget(layoutExportButton);
        addRenderableWidget(layoutResetButton);
        outputPageField = pageField(90, 53, menu.getPage() + 1);
        addRenderableWidget(outputPageField);
        addRenderableWidget(Button.builder(Component.literal("跳"), b -> jumpPage(outputPageField, menu.getPageCount(), 0))
                .bounds(leftPos + 114, topPos + 55, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> {
            send(4); outputPageField.setValue(Integer.toString(Math.max(1, menu.getPage())));
        }).tooltip(Tooltip.create(Component.literal("上一页拆解产物")))
                .bounds(leftPos + 130, topPos + 55, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> {
            send(5); outputPageField.setValue(Integer.toString(Math.min(menu.getPageCount(), menu.getPage() + 2)));
        }).tooltip(Tooltip.create(Component.literal("下一页拆解产物")))
                .bounds(leftPos + 146, topPos + 55, 14, 14).build());

        previewPageField = pageField(262, 53, menu.getPreviewPage() + 1);
        addRenderableWidget(previewPageField);
        addRenderableWidget(Button.builder(Component.literal("跳"), b -> jumpPage(previewPageField, menu.getPreviewPageCount(), 1))
                .bounds(leftPos + 286, topPos + 55, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> {
            int page = Math.max(0, menu.getPreviewPage() - 1);
            send(-((page * 2 + 1) + 1));
            previewPageField.setValue(Integer.toString(page + 1));
        }).tooltip(Tooltip.create(Component.literal("上一页材料预览")))
                .bounds(leftPos + 302, topPos + 55, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> {
            int page = Math.min(menu.getPreviewPageCount() - 1, menu.getPreviewPage() + 1);
            send(-((page * 2 + 1) + 1));
            previewPageField.setValue(Integer.toString(page + 1));
        }).tooltip(Tooltip.create(Component.literal("下一页材料预览")))
                .bounds(leftPos + 318, topPos + 55, 14, 14).build());

        addRenderableWidget(Button.builder(Component.literal("‹"), b -> { menu.cyclePreview(-1); send(0); })
                .tooltip(Tooltip.create(Component.literal("上一个匹配配方")))
                .bounds(leftPos + 184, topPos + 177, 18, 15).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> { menu.cyclePreview(1); send(1); })
                .tooltip(Tooltip.create(Component.literal("下一个匹配配方")))
                .bounds(leftPos + 204, topPos + 177, 18, 15).build());
        lockButton = Button.builder(Component.literal(menu.isLocked() ? "解锁" : "锁定"), b -> send(6))
                .bounds(leftPos + 224, topPos + 177, 50, 15).build();
        addRenderableWidget(lockButton);
        addRenderableWidget(Button.builder(Component.literal("拆解"), b -> send(3))
                .tooltip(Tooltip.create(Component.translatable("screen.whimsy_automation.process")))
                .bounds(leftPos + 276, topPos + 177, 68, 15).build());

        autoButton = Button.builder(autoLabel(), b -> send(2))
                .tooltip(Tooltip.create(autoLabel()))
                .bounds(leftPos + 184, topPos + 198, 78, 15).build();
        addRenderableWidget(autoButton);
        addRenderableWidget(Button.builder(Component.literal("六向设置"), b -> {
            showSideConfig = !showSideConfig;
            selectedIngredientGroup = -1;
        }).tooltip(Tooltip.create(Component.literal("分别设置六个面的自动化输入/输出")))
                .bounds(leftPos + 264, topPos + 198, 80, 15).build());


        layoutEditor.bindWidgets(children(), leftPos, topPos);
        for (net.minecraft.client.gui.components.AbstractWidget control : layoutEditor.createControls(font, leftPos, topPos))
            addRenderableWidget(control);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // renderBg is not translated to GUI-local coordinates in 1.21.1.
        // Background art must use screen coordinates; menu slots remain local.
        // One continuous vanilla-style container surface: no artificial
        // divider between the machine inventory and the player inventory.
        drawLayoutPanel(graphics, "main_panel", 0, 36, imageWidth, imageHeight - 38);

        // Exactly 27 real machine output slots.
        for (int i = 0; i < 27; i++)
            drawLayoutSlot(graphics, "output_slot_" + i, 1 + i,
                    8 + (i % 9) * 18, 71 + (i / 9) * 18);

        // Input and ingredients share the upper-right work area. Ingredient
        // previews use the same nine-column slot grid as every inventory.
        drawLayoutSlot(graphics, "input_slot", 0, 184, 53);
        List<ItemStack> preview = menu.getPreviewStacks();
        for (int i = 0; i < 27; i++) {
            UiLayoutProfile.Element previewSlot = layoutEditor.element("preview_slot_" + i, "preview_slot",
                    184 + (i % 9) * 18, 71 + (i / 9) * 18, 16, 16);
            int x = leftPos + previewSlot.x;
            int y = topPos + previewSlot.y;
            graphics.blitSprite(SLOT, x, y, previewSlot.width, previewSlot.height);
            if (i < preview.size()) {
                ItemStack stack = preview.get(i);
                int itemX = x + (previewSlot.width - 16) / 2;
                int itemY = y + (previewSlot.height - 16) / 2;
                graphics.renderItem(stack, itemX, itemY);
                graphics.renderItemDecorations(font, stack.copyWithCount(1), itemX, itemY);
            }
        }
        drawPlayerSlots(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawLayoutLabel(graphics, "title", Component.translatable("screen.whimsy_automation.deconstruction"),
                8, titleLabelY, 0x404040, false);
        drawLayoutLabel(graphics, "output_page_label",
                Component.literal("保存区 " + (menu.getPage() + 1) + "/" + Math.max(1, menu.getPageCount()) + "页"),
                8, 54, 0x404040, false);
        if (outputPageField != null && !outputPageField.isFocused())
            outputPageField.setValue(Integer.toString(menu.getPage() + 1));
        drawLayoutLabel(graphics, "inventory_label", Component.translatable("container.inventory"),
                8, inventoryLabelY, 0x404040, false);
        drawLayoutLabel(graphics, "preview_page_label",
                Component.literal("材料 " + (menu.getPreviewPage() + 1) + "/" + Math.max(1, menu.getPreviewPageCount()) + "页"),
                184, 130, 0x404040, false);
        drawLayoutLabel(graphics, "input_label", Component.literal("输入"), 202, 57, 0x404040, false);
        drawLayoutLabel(graphics, "recipe_page_label",
                Component.literal("配方 " + menu.getSelectedRecipePosition() + "/" + menu.getCandidateCount()),
                184, 151, 0x404040, false);
        if (previewPageField != null && !previewPageField.isFocused())
            previewPageField.setValue(Integer.toString(menu.getPreviewPage() + 1));
        if (autoButton != null) autoButton.setMessage(autoLabel());
        if (lockButton != null) lockButton.setMessage(Component.literal(menu.isLocked() ? "解锁" : "锁定"));

    }

    private void syncSideOverlay() {
        if (menu.machine() == null) return;
        // Read the six modes from the menu's DataSlots. Those values are synced
        // from the server and are authoritative on the client while the screen is open.
        int[] sideModes = new int[6];
        for (int side = 0; side < sideModes.length; side++) {
            sideModes[side] = menu.getSideMode(side);
        }
        SideConfigOverlay.show(menu.machine(), sideModes, showFaceColors);
    }

    private void renderSideConfig(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        int[] modes = new int[6];
        for (int i = 0; i < modes.length; i++) modes[i] = menu.getSideMode(i);
        SideConfigPanel.render(graphics, font, leftPos, topPos, mouseX, mouseY, partialTick, modes, showFaceColors);
    }

    private static boolean inside(double mouseX, double mouseY,
                                  double x, double y, double width, double height) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }

    private boolean clickSideConfig(double mouseX, double mouseY, int mouseButton) {
        if (!showSideConfig) return false;
        int action = SideConfigPanel.hit(mouseX - leftPos, mouseY - topPos);
        if (action == -1) return true;
        if (action == 7) showSideConfig = false;
        else if (action >= 0 && action < 6) {
            send(10 + action);
            SideConfigOverlay.cycleFace(menu.machine(), action);
        } else if (action == 6) {
            showFaceColors = !showFaceColors;
            syncSideOverlay();
        }
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
        mouseX = virtualMouseX(mouseX);
        mouseY = virtualMouseY(mouseY);
        if (layoutEditor != null && layoutEditor.enabled()) return true;
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
        mouseX = virtualMouseX(mouseX);
        mouseY = virtualMouseY(mouseY);
        if (layoutDebugButton != null && layoutDebugButton.isMouseOver(mouseX, mouseY)) {
            layoutEditor.toggle();
            return true;
        }
        if (layoutEditor != null && layoutEditor.enabled()) {
            if (layoutEditor.isControlAt(mouseX, mouseY)
                    || (layoutExportButton != null && layoutExportButton.isMouseOver(mouseX, mouseY))
                    || (layoutResetButton != null && layoutResetButton.isMouseOver(mouseX, mouseY)))
                return super.mouseClicked(mouseX, mouseY, button);
            return layoutEditor.click(mouseX, mouseY, leftPos, topPos, button);
        }
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
        if (clickSideConfig(mouseX, mouseY, button)) return true;
        for (int i = 0; i < 27; i++) {
            UiLayoutProfile.Element cell = layoutEditor.element("preview_slot_" + i, "preview_slot",
                    184 + (i % 9) * 18, 71 + (i / 9) * 18, 16, 16);
            int cellX = leftPos + cell.x, cellY = topPos + cell.y;
            if (mouseX >= cellX && mouseX < cellX + cell.width
                    && mouseY >= cellY && mouseY < cellY + cell.height) {
                int displayIndex = menu.getPreviewPage() * 27 + i;
                int group = menu.getPreviewGroupIndex(displayIndex);
                if (group >= 0 && menu.previewHasAlternatives(displayIndex)) {
                    selectedIngredientGroup = group;
                    ingredientPickerScroll = 0;
                    showSideConfig = false;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        mouseX = virtualMouseX(mouseX);
        mouseY = virtualMouseY(mouseY);
        if (layoutEditor != null && layoutEditor.enabled()) return layoutEditor.drag(mouseX, mouseY, net.minecraft.client.gui.screens.Screen.hasShiftDown());
        double scale = uiScale();
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
        return super.mouseDragged(mouseX, mouseY, button, dragX / scale, dragY / scale);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX = virtualMouseX(mouseX);
        mouseY = virtualMouseY(mouseY);
        if (layoutEditor != null && layoutEditor.enabled()) {
            if (layoutEditor.hasFocusedInput() || layoutEditor.isControlAt(mouseX, mouseY))
                return super.mouseReleased(mouseX, mouseY, button);
            return layoutEditor.release();
        }
        pickerDragging = false;
        if (selectedIngredientGroup >= 0 || showSideConfig) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (layoutEditor != null && layoutEditor.enabled() && layoutEditor.hasFocusedInput())
            return super.charTyped(codePoint, modifiers);
        if (layoutEditor != null && layoutEditor.enabled()) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (layoutEditor != null && layoutEditor.enabled()) {
            if (layoutEditor.hasFocusedInput()) return super.keyPressed(keyCode, scanCode, modifiers);
            if (layoutEditor.keyPressed(keyCode, modifiers)) return true;
            if (keyCode == 69) {
                try { layoutEditor.export(); } catch (java.io.IOException ignored) { }
                return true;
            }
            if (keyCode == 82) { layoutEditor.reset(); return true; }
            if (keyCode == 256) { layoutEditor.toggle(); return true; }
            return true;
        }
        if (selectedIngredientGroup >= 0 || showSideConfig) {
            if (keyCode == 256) {
                selectedIngredientGroup = -1;
                showSideConfig = false;
            }
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            if (outputPageField != null && outputPageField.isFocused()) {
                jumpPage(outputPageField, menu.getPageCount(), 0);
                return true;
            }
            if (previewPageField != null && previewPageField.isFocused()) {
                jumpPage(previewPageField, menu.getPreviewPageCount(), 1);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Render the normal item tooltip for manually rendered preview stacks. */
    private void renderPreviewItemTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (layoutEditor == null || layoutEditor.enabled() || selectedIngredientGroup >= 0 || showSideConfig) return;
        List<ItemStack> preview = menu.getPreviewStacks();
        for (int i = 0; i < Math.min(27, preview.size()); i++) {
            UiLayoutProfile.Element cell = layoutEditor.element("preview_slot_" + i, "preview_slot",
                    184 + (i % 9) * 18, 71 + (i / 9) * 18, 16, 16);
            int x = leftPos + cell.x, y = topPos + cell.y;
            if (mouseX >= x && mouseX < x + cell.width
                    && mouseY >= y && mouseY < y + cell.height) {
                ItemStack stack = preview.get(i);
                if (!stack.isEmpty()) graphics.renderTooltip(font, StackCountDisplay.withCount(getTooltipFromContainerItem(stack), stack), stack.getTooltipImage(), mouseX, mouseY);
                return;
            }
        }
    }

    private Component alternativeTooltip(double mouseX, double mouseY) {
        if (layoutEditor == null || selectedIngredientGroup >= 0 || showSideConfig) return null;
        for (int i = 0; i < 27; i++) {
            UiLayoutProfile.Element cell = layoutEditor.element("preview_slot_" + i, "preview_slot",
                    184 + (i % 9) * 18, 71 + (i / 9) * 18, 16, 16);
            int displayIndex = menu.getPreviewPage() * 27 + i;
            int group = menu.getPreviewGroupIndex(displayIndex);
            if (group < 0 || menu.getIngredientOptions(group).length <= 1) continue;
            int x = leftPos + cell.x, y = topPos + cell.y;
            if (mouseX >= x && mouseX < x + cell.width && mouseY >= y && mouseY < y + cell.height) {
                return Component.literal("可替代材料：点击选择其他配方材料");
            }
        }
        return null;
    }

    private void renderAlternativeBadges(GuiGraphics graphics) {
        List<ItemStack> preview = menu.getPreviewStacks();
        for (int i = 0; i < Math.min(27, preview.size()); i++) {
            int displayIndex = menu.getPreviewPage() * 27 + i;
            if (!menu.previewHasAlternatives(displayIndex)) continue;
            UiLayoutProfile.Element slot = layoutEditor.element("preview_slot_" + i, "preview_slot",
                    184 + (i % 9) * 18, 71 + (i / 9) * 18, 16, 16);
            int x = leftPos + slot.x + slot.width - 8;
            int y = topPos + slot.y;
            graphics.fill(x, y, x + 8, y + 8, 0xFF344F70);
            graphics.drawString(font, "#", x + 2, y, 0xFFFFFFFF, false);
        }
    }

    @Override
    public void removed() {
        // Do not clear the world overlay here: closing the GUI must leave it visible.
        super.removed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        double scale = uiScale();
        double virtualX = virtualMouseX(mouseX), virtualY = virtualMouseY(mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0 * (1.0 - scale), height / 2.0 * (1.0 - scale), 0.0);
        graphics.pose().scale((float) scale, (float) scale, 1.0F);
        if (showSideConfig || selectedIngredientGroup >= 0) {
            // Keep the left output storage and player inventory visible. The
            // opaque dialog replaces only the right-hand machine controls.
            // AbstractContainerScreen renders the background, machine GUI,
            // real slot contents, and widgets exactly once. A second explicit
            // renderBackground call was darkening the world a second time.
            super.render(graphics, -10000, -10000, partialTick);
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 300.0F);
            renderAlternativeBadges(graphics);
            graphics.flush();
            graphics.pose().popPose();
            // Finish the base container pass before drawing the modal. Item
            // stacks use a positive GUI Z offset, so a later fill at Z=0 is
            // not guaranteed to cover them when depth testing is active.
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos, topPos, 500.0F);
            if (showSideConfig) renderSideConfig(graphics, virtualX, virtualY, partialTick);
            else renderIngredientPicker(graphics, (int) virtualX, (int) virtualY);
            // Submit the modal while its elevated Z transform is still active.
            graphics.flush();
            graphics.pose().popPose();
            graphics.pose().popPose();
            return;
        }
        if (layoutExportButton != null) layoutExportButton.visible = layoutEditor != null && layoutEditor.enabled();
        if (layoutResetButton != null) layoutResetButton.visible = layoutEditor != null && layoutEditor.enabled();
        super.render(graphics, (int) virtualX, (int) virtualY, partialTick);
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        renderAlternativeBadges(graphics);
        graphics.flush();
        graphics.pose().popPose();
        if (layoutEditor != null) layoutEditor.render(graphics, leftPos, topPos);
        renderTooltip(graphics, (int) virtualX, (int) virtualY);
        renderPreviewItemTooltip(graphics, (int) virtualX, (int) virtualY);
        if (alternativeTooltip(virtualX, virtualY) != null) {
            graphics.drawString(font, "可替代材料 · 点击选择", leftPos + 184, topPos + 211, 0xFF344F70, true);
        }
        graphics.pose().popPose();
    }

    @Override
    protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, String countString) {
        super.renderSlotContents(graphics, stack, slot, stack.getCount() > 1 ? "" : countString);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            ItemStack stack = hoveredSlot.getItem();
            graphics.renderTooltip(font, StackCountDisplay.withCount(getTooltipFromContainerItem(stack), stack),
                    stack.getTooltipImage(), mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }
}
