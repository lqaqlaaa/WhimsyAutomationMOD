package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.EnchantmentExtractorMenu;
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

public final class EnchantmentExtractorScreen extends AbstractContainerScreen<EnchantmentExtractorMenu> {
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
    private Button autoButton;
    private Button processButton;
    private Button outputModeButton;
    private EditBox outputPageField;
    private EditBox bufferPageField;
    private boolean showSideConfig;
    private boolean showFaceColors;
    private UiLayoutEditor layoutEditor;
    private Button layoutDebugButton;
    private Button layoutExportButton;
    private Button layoutResetButton;

    public EnchantmentExtractorScreen(EnchantmentExtractorMenu menu, Inventory inventory, Component title) {
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
        return Component.literal(menu.isAutoMode() ? "自动：开启" : "自动：关闭");
    }

    private Component outputModeLabel() {
        return Component.translatable(switch (menu.getOutputMode()) {
            case 0 -> "screen.whimsy_automation.mode_combined_short";
            case 1 -> "screen.whimsy_automation.mode_enchantments_short";
            default -> "screen.whimsy_automation.mode_levels_short";
        });
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.blitSprite(SLOT, x, y, 16, 16);
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
                int index = 46 + row * 9 + col;
                drawLayoutSlot(g, "player_slot_" + index, index, 8 + col * 18, 139 + row * 18);
            }
        for (int col = 0; col < 9; col++) {
            int index = 73 + col;
            drawLayoutSlot(g, "player_slot_" + index, index, 8 + col * 18, 197);
        }
    }

    @Override
    protected void init() {
        super.init();
        // Face-color guidance is enabled by default whenever this machine GUI opens.
        // Previously isActive() was false on a fresh open, which silently hid all six colored face frames.
        showFaceColors = menu.machine() != null;
        syncSideOverlay();
        topPos -= 27;
        layoutEditor = new UiLayoutEditor("enchantment_extractor");
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
            send(3); outputPageField.setValue(Integer.toString(Math.max(1, menu.getPage())));
        }).tooltip(Tooltip.create(Component.translatable("screen.whimsy_automation.previous")))
                .bounds(leftPos + 130, topPos + 55, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> {
            send(4); outputPageField.setValue(Integer.toString(Math.min(menu.getPageCount(), menu.getPage() + 2)));
        }).tooltip(Tooltip.create(Component.translatable("screen.whimsy_automation.next")))
                .bounds(leftPos + 146, topPos + 55, 14, 14).build());

        autoButton = Button.builder(autoLabel(), b -> send(0))
                .bounds(leftPos + 184, topPos + 90, 78, 15).build();
        addRenderableWidget(autoButton);
        processButton = Button.builder(Component.translatable("screen.whimsy_automation.process"), b -> send(1))
                .bounds(leftPos + 184, topPos + 110, 78, 15).build();
        addRenderableWidget(processButton);
        outputModeButton = Button.builder(outputModeLabel(), b -> send(2))
                .bounds(leftPos + 264, topPos + 110, 80, 15).build();
        addRenderableWidget(outputModeButton);
        addRenderableWidget(Button.builder(Component.literal("六向设置"), b -> {
            showSideConfig = !showSideConfig;
        }).bounds(leftPos + 264, topPos + 90, 80, 15).build());



        bufferPageField = pageField(262, 153, menu.getBufferPage() + 1);
        addRenderableWidget(bufferPageField);
        addRenderableWidget(Button.builder(Component.literal("跳"), b -> jumpPage(bufferPageField, menu.getBufferPageCount(), 1))
                .bounds(leftPos + 286, topPos + 155, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> {
            send(5); bufferPageField.setValue(Integer.toString(Math.max(1, menu.getBufferPage())));
        }).tooltip(Tooltip.create(Component.literal("上一页缓冲物品")))
                .bounds(leftPos + 302, topPos + 155, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> {
            send(6); bufferPageField.setValue(Integer.toString(Math.min(menu.getBufferPageCount(), menu.getBufferPage() + 2)));
        }).tooltip(Tooltip.create(Component.literal("下一页缓冲物品")))
                .bounds(leftPos + 318, topPos + 155, 14, 14).build());
        layoutEditor.bindWidgets(children(), leftPos, topPos);
        for (net.minecraft.client.gui.components.AbstractWidget control : layoutEditor.createControls(font, leftPos, topPos))
            addRenderableWidget(control);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // renderBg uses screen coordinates; the menu owns its independent
        // GUI-local slot positions. Do not mix the two coordinate spaces.
        // Keep the machine area and player inventory on one continuous
        // vanilla-style container background, without a fake middle divider.
        drawLayoutPanel(graphics, "main_panel", 0, 36, imageWidth, imageHeight - 38);

        // Exactly 27 stored-output slots, one machine input slot, and the
        // player's single native 36-slot inventory. No decorative fake slots.
        for (int i = 0; i < 27; i++)
            drawLayoutSlot(graphics, "output_slot_" + i, 1 + i,
                    8 + (i % 9) * 18, 71 + (i / 9) * 18);
        drawLayoutSlot(graphics, "input_slot", 0, 184, 71);
        // Eighteen real queue slots: two rows of nine, entirely inside the
        // right machine pane and clear of the player's inventory.
        for (int i = 0; i < 18; i++)
            drawLayoutSlot(graphics, "buffer_slot_" + i, 28 + i,
                    184 + (i % 9) * 18, i / 9 == 0 ? 175 : 197);
        drawPlayerSlots(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawLayoutLabel(graphics, "title", Component.translatable("screen.whimsy_automation.extractor"),
                8, titleLabelY, 0x404040, false);
        drawLayoutLabel(graphics, "output_page_label",
                Component.literal("保存区 " + (menu.getPage() + 1) + "/" + Math.max(1, menu.getPageCount()) + "页"),
                8, 54, 0x404040, false);
        if (outputPageField != null && !outputPageField.isFocused())
            outputPageField.setValue(Integer.toString(menu.getPage() + 1));
        drawLayoutLabel(graphics, "input_label", Component.translatable("screen.whimsy_automation.book_input"),
                202, 75, 0x404040, false);
        drawLayoutLabel(graphics, "inventory_label", Component.translatable("container.inventory"),
                8, inventoryLabelY, 0x404040, false);
        drawLayoutLabel(graphics, "buffer_title", Component.literal("缓冲区"), 184, 130, 0x404040, false);
        drawLayoutLabel(graphics, "buffer_page_label",
                Component.literal("页数 " + (menu.getBufferPage() + 1) + "/" + menu.getBufferPageCount()),
                184, 156, 0x404040, false);
        if (bufferPageField != null && !bufferPageField.isFocused())
            bufferPageField.setValue(Integer.toString(menu.getBufferPage() + 1));
        if (autoButton != null) autoButton.setMessage(autoLabel());
        if (outputModeButton != null) outputModeButton.setMessage(outputModeLabel());

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
        SideConfigPanel.render(graphics, font, leftPos, topPos, mouseX, mouseY, partialTick, 176, 54, modes, showFaceColors);
    }

    private static boolean inside(double mouseX, double mouseY,
                                  double x, double y, double width, double height) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
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
        if (showSideConfig) {
            int action = SideConfigPanel.hit(mouseX - leftPos, mouseY - topPos, 176, 54);
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double x = virtualMouseX(mouseX), y = virtualMouseY(mouseY);
        if (layoutEditor != null && layoutEditor.enabled()) return layoutEditor.drag(x, y, net.minecraft.client.gui.screens.Screen.hasShiftDown());
        if (showSideConfig && x >= leftPos + 176 && y >= topPos + 54 && y < topPos + 220)
            return true;
        double scale = uiScale();
        return super.mouseDragged(x, y, button, dragX / scale, dragY / scale);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double x = virtualMouseX(mouseX), y = virtualMouseY(mouseY);
        if (layoutEditor != null && layoutEditor.enabled()) {
            if (layoutEditor.hasFocusedInput() || layoutEditor.isControlAt(x, y))
                return super.mouseReleased(x, y, button);
            return layoutEditor.release();
        }
        if (showSideConfig && x >= leftPos + 176 && y >= topPos + 54 && y < topPos + 148)
            return true;
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        mouseX = virtualMouseX(mouseX);
        mouseY = virtualMouseY(mouseY);
        if (layoutEditor != null && layoutEditor.enabled()) return true;
        boolean overBuffer = false;
        for (int i = 0; i < 18; i++) {
            UiLayoutProfile.Element cell = layoutEditor.element("buffer_slot_" + i, "slot",
                    184 + (i % 9) * 18, i / 9 == 0 ? 175 : 197, 16, 16);
            int x = leftPos + cell.x, y = topPos + cell.y;
            if (mouseX >= x && mouseX < x + cell.width && mouseY >= y && mouseY < y + cell.height) {
                overBuffer = true;
                break;
            }
        }
        if (overBuffer && menu.getBufferPageCount() > 1) {
            send(deltaY < 0 ? 6 : 5);
            return true;
        }
        if (showSideConfig) return true;
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
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
        if (showSideConfig) {
            if (keyCode == 256) {
                showSideConfig = false;
            }
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            if (outputPageField != null && outputPageField.isFocused()) {
                jumpPage(outputPageField, menu.getPageCount(), 0);
                return true;
            }
            if (bufferPageField != null && bufferPageField.isFocused()) {
                jumpPage(bufferPageField, menu.getBufferPageCount(), 1);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        graphics.pose().translate(width / 2.0 * (1.0 - scale),
                height / 2.0 * (1.0 - scale), 0.0);
        graphics.pose().scale((float) scale, (float) scale, 1.0F);
        if (showSideConfig) {
            // The full interface is uniformly scaled to fit the available GUI
            // viewport; the modal only replaces the upper control area.
            super.render(graphics, -10000, -10000, partialTick);
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos, topPos, 500.0F);
            renderSideConfig(graphics, virtualX, virtualY, partialTick);
            graphics.flush();
            graphics.pose().popPose();
            graphics.pose().popPose();
            return;
        }
        if (layoutExportButton != null) layoutExportButton.visible = layoutEditor != null && layoutEditor.enabled();
        if (layoutResetButton != null) layoutResetButton.visible = layoutEditor != null && layoutEditor.enabled();
        super.render(graphics, (int) virtualX, (int) virtualY, partialTick);
        if (layoutEditor != null) layoutEditor.render(graphics, leftPos, topPos);
        renderTooltip(graphics, (int) virtualX, (int) virtualY);
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
                    stack.getTooltipImage(), stack, mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }
}
