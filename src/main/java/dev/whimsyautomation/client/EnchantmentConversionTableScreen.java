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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

public final class EnchantmentConversionTableScreen extends AbstractContainerScreen<EnchantmentConversionTableMenu> {
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
    private EditBox amountInput;
    private Button autoButton;
    private Button sideButton;
    private Button confirmButton;
    private Button layoutDebugButton;
    private Button layoutExportButton;
    private Button layoutResetButton;
    private UiLayoutEditor layoutEditor;
    private boolean showSideConfig;
    private boolean showFaceColors;
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
            if (count >= 1 && count <= Integer.MAX_VALUE
                    && count != lastSentCount) {
                ConfigureItemCopyPayload.send(menu.containerId, menu.getBlockPos(), 1, count);
                lastSentCount = count;
            }
        } catch (NumberFormatException ignored) { }
    }

    @Override
    protected void init() {
        super.init();
        // Read the preference directly from this machine's client-side block-entity instance.
        showFaceColors = menu.machine() instanceof dev.whimsyautomation.block.entity.SideConfigurableBlockEntity machine
                && machine.isFaceColorDisplayEnabled();
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

        amountInput = new EditBox(font, leftPos + 47, topPos + 49, 82, 18,
                Component.translatable("screen.whimsy_automation.copy_count"));
        amountInput.setValue(Integer.toString(menu.getCopyCount()));
        lastSentCount = menu.getCopyCount();
        amountInput.setMaxLength(10);
        amountInput.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        addRenderableWidget(amountInput);

        confirmButton = Button.builder(Component.translatable("screen.whimsy_automation.confirm_copy"), button -> {
            sendCountIfChanged();
            try {
                int count = Integer.parseInt(amountInput.getValue());
                if (count >= 1 && count <= Integer.MAX_VALUE)
                    ConfirmItemCopyPayload.send(menu.containerId, menu.getBlockPos(), count);
            } catch (NumberFormatException ignored) { }
        }).bounds(leftPos + 57, topPos + 72, 62, 20).build();
        addRenderableWidget(confirmButton);

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
        if (confirmButton != null) confirmButton.active = !menu.isAutoMode();
        // DataSlots may arrive after init; refresh only while the player is not editing.
        if (amountInput != null && !amountInput.isFocused()) {
            int syncedCount = menu.getCopyCount();
            if (syncedCount >= 1 && syncedCount <= Integer.MAX_VALUE && syncedCount != lastSentCount) {
                amountInput.setValue(Integer.toString(syncedCount));
                lastSentCount = syncedCount;
            }
        }
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
        panel(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

    private void drawLayoutSlot(GuiGraphics graphics, String id, int x, int y) {
        // Slot hitboxes are owned by the menu and remain fixed; the layout
        // debugger edits controls and labels without separating icons from slots.
        graphics.blitSprite(SLOT, leftPos + x, topPos + y, 16, 16);
    }

    private void drawLayoutLabel(GuiGraphics graphics, String id, Component value, int x, int y, int color) {
        UiLayoutProfile.Element e = layoutEditor.element(id, "label", x, y,
                Math.max(1, font.width(value)), 9);
        float sx = e.width / (float) Math.max(1, font.width(value)) * e.textScale;
        float sy = e.height / 9.0F * e.textScale;
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + e.x, topPos + e.y, 0.0F);
        graphics.pose().scale(sx, sy, 1.0F);
        graphics.drawString(font, value, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawLayoutLabelLocal(GuiGraphics graphics, String id, Component value, int x, int y, int color) {
        UiLayoutProfile.Element e = layoutEditor.element(id, "label", x, y,
                Math.max(1, font.width(value)), 9);
        float sx = e.width / (float) Math.max(1, font.width(value)) * e.textScale;
        float sy = e.height / 9.0F * e.textScale;
        graphics.pose().pushPose();
        graphics.pose().translate(e.x, e.y, 0.0F);
        graphics.pose().scale(sx, sy, 1.0F);
        graphics.drawString(font, value, 0, 0, color, false);
        graphics.pose().popPose();
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


        drawLayoutLabel(graphics, "copy_count_label",
                Component.translatable("screen.whimsy_automation.copy_count"), 57, 39, 0x404040);
        drawLayoutLabel(graphics, "input_arrow", Component.literal("→"), 91, 52, 0x404040);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawLayoutLabelLocal(graphics, "title",
                Component.translatable("screen.whimsy_automation.conversion_table"), 8, titleLabelY, 0x404040);
        drawLayoutLabelLocal(graphics, "inventory_label",
                Component.translatable("container.inventory"), 8, inventoryLabelY, 0x404040);
    }

    private void syncFaceColorDisplayPreference() {
        if (menu.machine() instanceof dev.whimsyautomation.block.entity.SideConfigurableBlockEntity machine) {
            machine.setFaceColorDisplayEnabled(showFaceColors);
        }
    }

    private void renderSideConfig(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int[] modes = new int[6];
        for (int i = 0; i < modes.length; i++) modes[i] = menu.getSideMode(i);
        SideConfigPanel.render(graphics, font, leftPos, topPos, mouseX, mouseY, partialTick, 0, 10, modes, showFaceColors);
    }

    private static boolean inside(double x, double y, double left, double top, double width, double height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // The modal owns the entire screen while open. This check must precede
        // layout-editor and widget handling, otherwise clicks can reach controls
        // underneath the six-face panel.
        if (showSideConfig) {
            int action = SideConfigPanel.hit(mouseX - leftPos, mouseY - topPos, 0, 10);
            if (action == 7) {
                showSideConfig = false;
                setFocused(null);
            } else if (action >= 0 && action < 6) {
                sendButton(10 + action);
                if (menu.machine() instanceof dev.whimsyautomation.block.entity.SideConfigurableBlockEntity machine) {
                net.minecraft.core.Direction side = net.minecraft.core.Direction.values()[action];
                machine.setSideModeForDisplay(side, (machine.getSideMode(side) + 1) % 3);
            }
            } else if (action == 6) {
                showFaceColors = !showFaceColors;
                syncFaceColorDisplayPreference();
            }
            return true;
        }
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (showSideConfig) return true;
        if (layoutEditor != null && layoutEditor.enabled())
            return layoutEditor.drag(mouseX, mouseY, Screen.hasShiftDown());
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (showSideConfig) return true;
        if (layoutEditor != null && layoutEditor.enabled()) {
            if (layoutEditor.hasFocusedInput() || layoutEditor.isControlAt(mouseX, mouseY))
                return super.mouseReleased(mouseX, mouseY, button);
            return layoutEditor.release();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (showSideConfig) return true;
        if (layoutEditor != null && layoutEditor.enabled() && !layoutEditor.hasFocusedInput()) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showSideConfig) {
            if (keyCode == 256) {
                showSideConfig = false;
                setFocused(null);
            }
            return true;
        }
        if (layoutEditor != null && layoutEditor.enabled()) {
            if (layoutEditor.hasFocusedInput()) return super.keyPressed(keyCode, scanCode, modifiers);
            if (layoutEditor.keyPressed(keyCode, modifiers)) return true;
            if (keyCode == 69) { try { layoutEditor.export(); } catch (java.io.IOException ignored) { } return true; }
            if (keyCode == 82) { layoutEditor.reset(); return true; }
            if (keyCode == 256) { layoutEditor.toggle(); return true; }
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
            super.render(graphics, -10000, -10000, partialTick);
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos, topPos, 500.0F);
            graphics.fill(0, 0, imageWidth, imageHeight, 0xFF202020);
            renderSideConfig(graphics, mouseX, mouseY, partialTick);
            graphics.flush();
            graphics.pose().popPose();
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (layoutEditor != null) layoutEditor.render(graphics, leftPos, topPos);
        renderTooltip(graphics, mouseX, mouseY);
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
