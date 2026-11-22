package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.EnchantmentConversionTableMenu;
import dev.whimsyautomation.network.ConfigureItemCopyPayload;
import dev.whimsyautomation.network.ConfirmItemCopyPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class EnchantmentConversionTableScreen extends AbstractContainerScreen<EnchantmentConversionTableMenu> {
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
    private static final String[] SIDE_NAMES = {"下", "上", "北", "南", "西", "东"};
    private static final String[] SIDE_MODES = {"关闭", "输入", "输出"};
    private EditBox amountInput;
    private Button autoButton;
    private Button sideButton;
    private Button layoutDebugButton;
    private Button layoutExportButton;
    private Button layoutResetButton;
    private UiLayoutEditor layoutEditor;
    private boolean showSideConfig;
    private int lastSentCount = 64;

    public EnchantmentConversionTableScreen(EnchantmentConversionTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 186;
        inventoryLabelY = 82;
        titleLabelY = 6;
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void sendCountIfChanged() {
        if (amountInput == null) return;
        try {
            int count = Integer.parseInt(amountInput.getValue());
            if (count >= 1 && count <= 1_000_000
                    && count != lastSentCount) {
                ConfigureItemCopyPayload.send(menu.containerId, menu.getBlockPos(), 1, count);
                lastSentCount = count;
            }
        } catch (NumberFormatException ignored) { }
    }

    @Override
    protected void init() {
        super.init();
        layoutEditor = new UiLayoutEditor("enchantment_conversion_table");
        layoutDebugButton = Button.builder(Component.literal("布局"), b -> layoutEditor.toggle())
                .bounds(leftPos + 144, topPos + 6, 26, 14).build();
        layoutExportButton = Button.builder(Component.literal("导出"), b -> {
            try { layoutEditor.export(); } catch (java.io.IOException ignored) { }
        }).bounds(leftPos + 82, topPos + 6, 28, 14).build();
        layoutResetButton = Button.builder(Component.literal("复位"), b -> layoutEditor.reset())
                .bounds(leftPos + 112, topPos + 6, 28, 14).build();
        addRenderableWidget(layoutDebugButton);
        addRenderableWidget(layoutExportButton);
        addRenderableWidget(layoutResetButton);

        autoButton = Button.builder(autoLabel(), b -> sendButton(0))
                .bounds(leftPos + 8, topPos + 22, 62, 16).build();
        sideButton = Button.builder(Component.literal("六向设置"), b -> showSideConfig = true)
                .bounds(leftPos + 74, topPos + 22, 64, 16).build();
        addRenderableWidget(autoButton);
        addRenderableWidget(sideButton);

        amountInput = new EditBox(font, leftPos + 57, topPos + 49, 58, 18,
                Component.translatable("screen.whimsy_automation.copy_count"));
        amountInput.setValue(Integer.toString(menu.getCopyCount()));
        lastSentCount = menu.getCopyCount();
        amountInput.setMaxLength(7);
        amountInput.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        addRenderableWidget(amountInput);

        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.confirm_copy"), button -> {
            sendCountIfChanged();
            try {
                int count = Integer.parseInt(amountInput.getValue());
                if (count >= 1 && count <= EnchantmentConversionTableBlockEntityLimit.MAX_COPY_COUNT)
                    ConfirmItemCopyPayload.send(menu.containerId, menu.getBlockPos(), count);
            } catch (NumberFormatException ignored) { }
        }).bounds(leftPos + 57, topPos + 72, 62, 20).build());

        layoutEditor.bindWidgets(children(), leftPos, topPos);
        for (AbstractWidget control : layoutEditor.createControls(font, leftPos, topPos))
            addRenderableWidget(control);
    }

    private Component autoLabel() {
        return Component.literal(menu.isAutoMode() ? "自动：开启" : "自动：关闭");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (autoButton != null) autoButton.setMessage(autoLabel());
        sendCountIfChanged();
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
    }

    private void drawLayoutPanel(GuiGraphics graphics) {
        UiLayoutProfile.Element e = layoutEditor.element("main_panel", "panel", 0, 0, imageWidth, imageHeight);
        panel(graphics, leftPos + e.x, topPos + e.y, e.width, e.height);
    }

    private void drawLayoutSlot(GuiGraphics graphics, String id, int x, int y) {
        UiLayoutProfile.Element e = layoutEditor.element(id, "slot", x, y, 16, 16);
        graphics.blitSprite(SLOT, leftPos + e.x, topPos + e.y, e.width, e.height);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawLayoutPanel(graphics);
        drawLayoutSlot(graphics, "input_slot", 28, 50);
        drawLayoutSlot(graphics, "output_slot", 132, 50);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawLayoutSlot(graphics, "player_slot_" + (col + row * 9), 8 + col * 18, 104 + row * 18);
        for (int col = 0; col < 9; col++)
            drawLayoutSlot(graphics, "hotbar_slot_" + col, 8 + col * 18, 162);
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.copy_count"),
                leftPos + 57, topPos + 39, 0x404040, false);
        graphics.drawString(font, Component.literal("→"), leftPos + 91, topPos + 52, 0x404040, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.conversion_table"),
                8, titleLabelY, 0x404040, false);
        graphics.drawString(font, Component.translatable("container.inventory"),
                8, inventoryLabelY, 0x404040, false);
    }

    private void renderTotalOutputCount(GuiGraphics graphics) {
        int total = menu.getTotalOutputCount();
        if (total <= 0) return;
        String countText = Integer.toString(total);
        int x = leftPos + 132 + 16 - font.width(countText);
        int y = topPos + 50 + 9;
        graphics.fill(x - 2, y - 1, x + font.width(countText) + 1, y + 9, 0x99000000);
        graphics.drawString(font, countText, x, y, 0xFFFFFF, true);
    }

    private void renderSideConfig(GuiGraphics graphics) {
        int x = leftPos + 12, y = topPos + 42;
        panel(graphics, x, y, 152, 96);
        graphics.fill(x + 2, y + 2, x + 150, y + 94, 0xFFE0E0E0);
        graphics.drawString(font, Component.literal("六向输入 / 输出设置"), x + 8, y + 7, 0xFF202020, false);
        graphics.drawString(font, Component.literal("点击方向切换：关闭 → 输入 → 输出"), x + 8, y + 19, 0xFF505050, false);
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            int bx = x + 8 + col * 48, by = y + 37 + row * 24;
            int mode = menu.getSideMode(i);
            panel(graphics, bx, by, 44, 20);
            graphics.drawCenteredString(font, Component.literal(SIDE_NAMES[i] + " " + SIDE_MODES[mode]),
                    bx + 22, by + 6, 0xFF202020);
        }
        graphics.drawCenteredString(font, Component.literal("点击面板外关闭"), x + 76, y + 83, 0xFF555555);
    }

    private static boolean inside(double x, double y, double left, double top, double width, double height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (layoutDebugButton != null && layoutDebugButton.isMouseOver(mouseX, mouseY)) {
            layoutEditor.toggle();
            return true;
        }
        if (layoutEditor != null && layoutEditor.enabled()) {
            if (layoutEditor.isControlAt(mouseX, mouseY)
                    || layoutExportButton.isMouseOver(mouseX, mouseY)
                    || layoutResetButton.isMouseOver(mouseX, mouseY))
                return super.mouseClicked(mouseX, mouseY, button);
            return layoutEditor.click(mouseX, mouseY, leftPos, topPos, button);
        }
        if (showSideConfig) {
            double lx = mouseX - leftPos, ly = mouseY - topPos;
            if (!inside(lx, ly, 12, 42, 152, 96)) {
                showSideConfig = false;
                return true;
            }
            for (int i = 0; i < 6; i++) {
                int bx = 20 + (i % 3) * 48, by = 79 + (i / 3) * 24;
                if (inside(lx, ly, bx, by, 44, 20)) {
                    sendButton(10 + i);
                    return true;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (layoutEditor != null && layoutEditor.enabled())
            return layoutEditor.drag(mouseX, mouseY, Screen.hasShiftDown());
        if (showSideConfig) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (layoutEditor != null && layoutEditor.enabled()) {
            if (layoutEditor.hasFocusedInput() || layoutEditor.isControlAt(mouseX, mouseY))
                return super.mouseReleased(mouseX, mouseY, button);
            return layoutEditor.release();
        }
        if (showSideConfig) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (layoutEditor != null && layoutEditor.enabled() && !layoutEditor.hasFocusedInput()) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (layoutEditor != null && layoutEditor.enabled()) {
            if (layoutEditor.hasFocusedInput()) return super.keyPressed(keyCode, scanCode, modifiers);
            if (layoutEditor.keyPressed(keyCode, modifiers)) return true;
            if (keyCode == 69) { try { layoutEditor.export(); } catch (java.io.IOException ignored) { } return true; }
            if (keyCode == 82) { layoutEditor.reset(); return true; }
            if (keyCode == 256) { layoutEditor.toggle(); return true; }
            return true;
        }
        if (showSideConfig) {
            if (keyCode == 256) showSideConfig = false;
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            sendCountIfChanged();
            if (amountInput != null && amountInput.isFocused()) {
                amountInput.setFocused(false);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (layoutExportButton != null) layoutExportButton.visible = layoutEditor != null && layoutEditor.enabled();
        if (layoutResetButton != null) layoutResetButton.visible = layoutEditor != null && layoutEditor.enabled();
        if (showSideConfig) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            super.render(graphics, -10000, -10000, partialTick);
            renderSideConfig(graphics);
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTotalOutputCount(graphics);
        if (layoutEditor != null) layoutEditor.render(graphics, leftPos, topPos);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
