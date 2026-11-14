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

public final class EnchantmentExtractorScreen extends AbstractContainerScreen<EnchantmentExtractorMenu> {
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
    private Button autoButton;
    private Button processButton;
    private Button outputModeButton;
    private EditBox outputPageField;
    private EditBox bufferPageField;
    private boolean showSideConfig;

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

    private void drawPlayerSlots(GuiGraphics g) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawSlot(g, leftPos + 8 + col * 18, topPos + 139 + row * 18);
        for (int col = 0; col < 9; col++)
            drawSlot(g, leftPos + 8 + col * 18, topPos + 197);
    }

    @Override
    protected void init() {
        super.init();
        topPos -= 27;
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
        addRenderableWidget(Button.builder(Component.literal("六向设置"), b -> showSideConfig = !showSideConfig)
                .bounds(leftPos + 264, topPos + 90, 80, 15).build());

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
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // renderBg uses screen coordinates; the menu owns its independent
        // GUI-local slot positions. Do not mix the two coordinate spaces.
        // Keep the machine area and player inventory on one continuous
        // vanilla-style container background, without a fake middle divider.
        panel(graphics, leftPos, topPos + 36, imageWidth, imageHeight - 38);

        // Exactly 27 stored-output slots, one machine input slot, and the
        // player's single native 36-slot inventory. No decorative fake slots.
        for (int i = 0; i < 27; i++)
            drawSlot(graphics, leftPos + 8 + (i % 9) * 18, topPos + 71 + (i / 9) * 18);
        drawSlot(graphics, leftPos + 184, topPos + 71);
        // Eighteen real queue slots: two rows of nine, entirely inside the
        // right machine pane and clear of the player's inventory.
        for (int i = 0; i < 18; i++)
            drawSlot(graphics, leftPos + 184 + (i % 9) * 18, topPos + (i / 9 == 0 ? 175 : 197));
        drawPlayerSlots(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.extractor"),
                8, titleLabelY, 0x404040, false);
        graphics.drawString(font, Component.literal("保存区 " + (menu.getPage() + 1) + "/" + Math.max(1, menu.getPageCount()) + "页"), 8, 54, 0x404040, false);
        if (outputPageField != null && !outputPageField.isFocused())
            outputPageField.setValue(Integer.toString(menu.getPage() + 1));
        graphics.drawString(font, Component.translatable("screen.whimsy_automation.book_input"),
                202, 75, 0x404040, false);
        graphics.drawString(font, Component.translatable("container.inventory"),
                8, inventoryLabelY, 0x404040, false);
        graphics.drawString(font, Component.literal("缓冲区"), 184, 130, 0x404040, false);
        graphics.drawString(font, Component.literal("页数 " + (menu.getBufferPage() + 1) + "/" + menu.getBufferPageCount()), 184, 156, 0x404040, false);
        if (bufferPageField != null && !bufferPageField.isFocused())
            bufferPageField.setValue(Integer.toString(menu.getBufferPage() + 1));
        if (autoButton != null) autoButton.setMessage(autoLabel());
        if (outputModeButton != null) outputModeButton.setMessage(outputModeLabel());

    }

    private void renderSideConfig(GuiGraphics graphics) {
        if (!showSideConfig) return;
        // renderLabels is already translated to the GUI origin.
        int x = 176, y = 54;
        // The direction editor occupies only the upper control region.
        // The expandable input buffer remains visible and interactive below it.
        panel(graphics, x, y, 176, 71);
        graphics.fill(x + 2, y + 2, x + 174, y + 69, 0xFFE0E0E0);
        graphics.drawString(font, Component.literal("六向设置"), x + 8, y + 8, 0xFF202020, false);
        graphics.drawString(font, Component.literal("×"), x + 162, y + 7, 0xFF202020, false);
        String[] names = {"下", "上", "北", "南", "西", "东"};
        String[] modes = {"关", "入", "出"};
        int[] colors = {0xFF993333, 0xFF7FCC19, 0xFF334CB2, 0xFFE5E533, 0xFF7F3FB2, 0xFF4C7F99};
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            int cellX = x + 8 + col * 55, cellY = y + 28 + row * 20;
            graphics.fill(cellX, cellY + 1, cellX + 8, cellY + 9, colors[i]);
            graphics.drawString(font, Component.literal(names[i] + "：" + modes[Math.min(2, menu.getSideMode(i))]),
                    cellX + 12, cellY, 0xFF303030, false);
        }
        graphics.drawString(font, Component.literal("点击切换输入/输出/关闭"), x + 8, y + 63, 0xFF606060, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX = virtualMouseX(mouseX);
        mouseY = virtualMouseY(mouseY);
        if (showSideConfig) {
            int x = leftPos + 176, y = topPos + 54;
            if (mouseX >= x + 150 && mouseX < x + 176 && mouseY >= y && mouseY < y + 24) {
                showSideConfig = false;
                return true;
            }
            if (mouseX >= x + 5 && mouseX < x + 171 && mouseY >= y + 24 && mouseY < y + 66) {
                int col = Math.min(2, Math.max(0, (int) ((mouseX - (x + 8)) / 55)));
                int row = mouseY < y + 45 ? 0 : 1;
                send(10 + row * 3 + col);
                return true;
            }
            // Leave the buffer slots and their page controls usable while the
            // six-direction editor is open.
            if (mouseX < leftPos + 176 || mouseY >= topPos + 130)
                return super.mouseClicked(mouseX, mouseY, button);
            showSideConfig = false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double x = virtualMouseX(mouseX), y = virtualMouseY(mouseY);
        if (showSideConfig && x >= leftPos + 176 && y >= topPos + 54 && y < topPos + 162)
            return true;
        double scale = uiScale();
        return super.mouseDragged(x, y, button, dragX / scale, dragY / scale);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double x = virtualMouseX(mouseX), y = virtualMouseY(mouseY);
        if (showSideConfig && x >= leftPos + 176 && y >= topPos + 54 && y < topPos + 162)
            return true;
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        mouseX = virtualMouseX(mouseX);
        mouseY = virtualMouseY(mouseY);
        boolean overBuffer = mouseX >= leftPos + 180 && mouseX < leftPos + 352
                && mouseY >= topPos + 175 && mouseY < topPos + 213;
        if (overBuffer && menu.getBufferPageCount() > 1) {
            send(deltaY < 0 ? 6 : 5);
            return true;
        }
        if (showSideConfig) return true;
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showSideConfig) {
            if (keyCode == 256) showSideConfig = false;
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
            renderSideConfig(graphics);
            graphics.flush();
            graphics.pose().popPose();
            graphics.pose().popPose();
            return;
        }
        super.render(graphics, (int) virtualX, (int) virtualY, partialTick);
        renderTooltip(graphics, (int) virtualX, (int) virtualY);
        graphics.pose().popPose();
    }
}
