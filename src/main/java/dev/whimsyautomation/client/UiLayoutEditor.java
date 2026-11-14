package dev.whimsyautomation.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Mouse, snap and numeric editor for any registered logical UI element. */
public final class UiLayoutEditor {
    private final UiLayoutProfile profile;
    private boolean enabled;
    private String selected;
    private boolean resizing;
    private boolean dragging;
    private double lastX;
    private double lastY;
    private int originX;
    private int originY;
    private int snapGuideX = Integer.MIN_VALUE;
    private int snapGuideY = Integer.MIN_VALUE;
    private final Map<String, AbstractWidget> widgets = new LinkedHashMap<>();
    private final Map<String, UiLayoutProfile.Element> defaults = new LinkedHashMap<>();
    private final List<AbstractWidget> controls = new ArrayList<>();
    private EditBox xField, yField, widthField, heightField;

    public UiLayoutEditor(String screenId) {
        profile = UiLayoutProfile.load(screenId);
    }

    public UiLayoutProfile.Element element(String id, String type,
                                            int x, int y, int width, int height) {
        return profile.element(id, type, x, y, width, height);
    }

    public void bindWidgets(List<? extends GuiEventListener> children, int originX, int originY) {
        this.originX = originX;
        this.originY = originY;
        widgets.clear();
        int index = 0;
        for (GuiEventListener child : children) {
            if (!(child instanceof AbstractWidget widget)) continue;
            String label = widget.getMessage().getString().replaceAll("[^a-zA-Z0-9]+", "_");
            if (java.util.Set.of("布局", "导出", "复位").contains(widget.getMessage().getString())) continue;
            String id = "widget_" + index++;
            UiLayoutProfile.Element e = profile.element(id, "widget:" + label,
                    widget.getX() - originX, widget.getY() - originY,
                    widget.getWidth(), widget.getHeight());
            widgets.put(id, widget);
            defaults.putIfAbsent(id, new UiLayoutProfile.Element("widget:" + label,
                    widget.getX() - originX, widget.getY() - originY,
                    widget.getWidth(), widget.getHeight()));
            apply(widget, e);
        }
    }

    /** Adds compact numeric controls to the screen's normal widget event/render path. */
    public List<AbstractWidget> createControls(Font font, int originX, int originY) {
        this.originX = originX;
        this.originY = originY;
        controls.clear();
        xField = numberField(font, originX + 4, originY + 10, "X");
        yField = numberField(font, originX + 44, originY + 10, "Y");
        widthField = numberField(font, originX + 84, originY + 10, "宽");
        heightField = numberField(font, originX + 124, originY + 10, "高");
        Button applyButton = Button.builder(Component.literal("应用"), b -> applyNumeric())
                .bounds(originX + 164, originY + 10, 34, 14)
                .tooltip(Tooltip.create(Component.literal("应用 X、Y、宽度和高度数值"))).build();
        controls.add(xField);
        controls.add(yField);
        controls.add(widthField);
        controls.add(heightField);
        controls.add(applyButton);
        setControlsVisible(false);
        return List.copyOf(controls);
    }

    private EditBox numberField(Font font, int x, int y, String hint) {
        EditBox field = new EditBox(font, x, y, 38, 14, Component.literal(hint));
        field.setMaxLength(6);
        field.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        field.setHint(Component.literal(hint));
        field.setTooltip(Tooltip.create(Component.literal(hint + "（像素）")));
        return field;
    }

    private void setControlsVisible(boolean visible) {
        for (AbstractWidget control : controls) control.visible = visible;
    }

    public boolean isControlAt(double mouseX, double mouseY) {
        for (AbstractWidget control : controls)
            if (control.visible && control.isMouseOver(mouseX, mouseY)) return true;
        return false;
    }

    public boolean hasFocusedInput() {
        return (xField != null && xField.isFocused())
                || (yField != null && yField.isFocused())
                || (widthField != null && widthField.isFocused())
                || (heightField != null && heightField.isFocused());
    }

    private void syncFields() {
        UiLayoutProfile.Element e = selected == null ? null : profile.elements().get(selected);
        if (e == null || xField == null) return;
        xField.setValue(Integer.toString(e.x));
        yField.setValue(Integer.toString(e.y));
        widthField.setValue(Integer.toString(e.width));
        heightField.setValue(Integer.toString(e.height));
    }

    private int parse(EditBox field, int fallback) {
        try { return field == null || field.getValue().isBlank() ? fallback : Integer.parseInt(field.getValue()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private void applyNumeric() {
        if (selected == null) return;
        UiLayoutProfile.Element e = profile.elements().get(selected);
        if (e == null) return;
        e.x = parse(xField, e.x);
        e.y = parse(yField, e.y);
        e.width = Math.max(1, parse(widthField, e.width));
        e.height = Math.max(1, parse(heightField, e.height));
        AbstractWidget widget = widgets.get(selected);
        if (widget != null) apply(widget, e);
        profile.save();
        syncFields();
    }

    private void apply(AbstractWidget widget, UiLayoutProfile.Element e) {
        widget.setX(originX + e.x);
        widget.setY(originY + e.y);
        widget.setWidth(e.width);
        widget.setHeight(e.height);
    }

    public boolean enabled() { return enabled; }
    public void toggle() {
        enabled = !enabled;
        selected = null;
        dragging = false;
        resizing = false;
        snapGuideX = snapGuideY = Integer.MIN_VALUE;
        setControlsVisible(enabled);
    }
    public void setEnabled(boolean value) {
        enabled = value;
        setControlsVisible(value);
    }

    public boolean click(double mouseX, double mouseY, int originX, int originY, int button) {
        if (!enabled) return false;
        if (button == 1) {
            selected = null;
            dragging = false;
            resizing = false;
            syncFields();
            return true;
        }
        String hit = null;
        long bestArea = Long.MAX_VALUE;
        for (Map.Entry<String, UiLayoutProfile.Element> entry : profile.elements().entrySet()) {
            UiLayoutProfile.Element e = entry.getValue();
            double x = originX + e.x, y = originY + e.y;
            if (mouseX >= x && mouseX < x + e.width && mouseY >= y && mouseY < y + e.height) {
                long area = (long) e.width * e.height;
                if (area <= bestArea) {
                    bestArea = area;
                    hit = entry.getKey();
                }
            }
        }
        if (hit == null) return true;
        selected = hit;
        syncFields();
        UiLayoutProfile.Element e = profile.elements().get(hit);
        double right = originX + e.x + e.width;
        double bottom = originY + e.y + e.height;
        resizing = mouseX >= right - 6 && mouseY >= bottom - 6;
        dragging = !resizing;
        lastX = mouseX;
        lastY = mouseY;
        return true;
    }

    private int snapX(UiLayoutProfile.Element moving) {
        int best = moving.x, bestDistance = 6, guide = Integer.MIN_VALUE;
        int[] own = {0, moving.width / 2, moving.width};
        for (Map.Entry<String, UiLayoutProfile.Element> entry : profile.elements().entrySet()) {
            if (entry.getKey().equals(selected)) continue;
            UiLayoutProfile.Element target = entry.getValue();
            int[] anchors = {target.x, target.x + target.width / 2, target.x + target.width};
            for (int a : own) for (int b : anchors) {
                int candidate = b - a;
                int distance = Math.abs(candidate - moving.x);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                    guide = b;
                }
            }
        }
        snapGuideX = guide;
        return best;
    }

    private int snapY(UiLayoutProfile.Element moving) {
        int best = moving.y, bestDistance = 6, guide = Integer.MIN_VALUE;
        int[] own = {0, moving.height / 2, moving.height};
        for (Map.Entry<String, UiLayoutProfile.Element> entry : profile.elements().entrySet()) {
            if (entry.getKey().equals(selected)) continue;
            UiLayoutProfile.Element target = entry.getValue();
            int[] anchors = {target.y, target.y + target.height / 2, target.y + target.height};
            for (int a : own) for (int b : anchors) {
                int candidate = b - a;
                int distance = Math.abs(candidate - moving.y);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                    guide = b;
                }
            }
        }
        snapGuideY = guide;
        return best;
    }

    public boolean drag(double mouseX, double mouseY) {
        if (!enabled || selected == null || (!dragging && !resizing)) return false;
        UiLayoutProfile.Element e = profile.elements().get(selected);
        int dx = (int) Math.round(mouseX - lastX);
        int dy = (int) Math.round(mouseY - lastY);
        if (resizing) {
            e.width = Math.max(1, e.width + dx);
            e.height = Math.max(1, e.height + dy);
            snapGuideX = snapGuideY = Integer.MIN_VALUE;
        } else {
            e.x += dx;
            e.y += dy;
            e.x = snapX(e);
            e.y = snapY(e);
        }
        lastX = mouseX;
        lastY = mouseY;
        AbstractWidget widget = widgets.get(selected);
        if (widget != null) apply(widget, e);
        syncFields();
        return true;
    }

    public boolean release() {
        if (!enabled) return false;
        boolean changed = dragging || resizing;
        dragging = false;
        resizing = false;
        if (changed) profile.save();
        return true;
    }

    /** Arrow keys nudge the selected item by one pixel; Shift uses ten pixels. */
    public boolean keyPressed(int keyCode, int modifiers) {
        if (!enabled || selected == null) return false;
        int step = (modifiers & 1) != 0 ? 10 : 1;
        UiLayoutProfile.Element e = profile.elements().get(selected);
        if (e == null) return false;
        switch (keyCode) {
            case 263 -> e.x -= step;
            case 262 -> e.x += step;
            case 265 -> e.y -= step;
            case 264 -> e.y += step;
            default -> { return false; }
        }
        AbstractWidget widget = widgets.get(selected);
        if (widget != null) apply(widget, e);
        syncFields();
        profile.save();
        return true;
    }

    public void render(GuiGraphics graphics, int originX, int originY) {
        if (!enabled) return;
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, Component.literal("X"), originX + 4, originY + 1, 0xFFFFFFFF, false);
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Y"), originX + 44, originY + 1, 0xFFFFFFFF, false);
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, Component.literal("宽"), originX + 84, originY + 1, 0xFFFFFFFF, false);
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, Component.literal("高"), originX + 124, originY + 1, 0xFFFFFFFF, false);
        graphics.fill(originX, originY + 27, originX + 350, originY + 41, 0xCC202020);
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                Component.literal("拖动移动 · 右下角缩放 · 数值栏可精确编辑 · 方向键微调 · Shift+方向键步进10px"),
                originX + 3, originY + 30, 0xFFFFFFFF, false);

        if (snapGuideX != Integer.MIN_VALUE)
            graphics.fill(originX + snapGuideX, originY - 2000,
                    originX + snapGuideX + 1, originY + 2000, 0xFF20D9E8);
        if (snapGuideY != Integer.MIN_VALUE)
            graphics.fill(originX - 2000, originY + snapGuideY,
                    originX + 2000, originY + snapGuideY + 1, 0xFF20D9E8);

        for (Map.Entry<String, UiLayoutProfile.Element> entry : profile.elements().entrySet()) {
            UiLayoutProfile.Element e = entry.getValue();
            int x = originX + e.x, y = originY + e.y;
            boolean active = entry.getKey().equals(selected);
            int color = active ? 0xFFFFB52E : 0xCC35A8FF;
            graphics.fill(x, y, x + e.width, y + 1, color);
            graphics.fill(x, y + e.height - 1, x + e.width, y + e.height, color);
            graphics.fill(x, y, x + 1, y + e.height, color);
            graphics.fill(x + e.width - 1, y, x + e.width, y + e.height, color);
            if (active) {
                graphics.fill(x + e.width - 5, y + e.height - 5,
                        x + e.width, y + e.height, 0xFFFFB52E);
                graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                        Component.literal(entry.getKey() + "  X:" + e.x + " Y:" + e.y
                                + "  W:" + e.width + " H:" + e.height),
                        x, Math.max(originY, y - 10), 0xFFFFD27A, false);
            }
        }
    }

    public void export() throws java.io.IOException { profile.export(); }
    public void reset() {
        profile.reset();
        selected = null;
        for (Map.Entry<String, AbstractWidget> entry : widgets.entrySet()) {
            UiLayoutProfile.Element original = defaults.get(entry.getKey());
            if (original == null) continue;
            UiLayoutProfile.Element restored = profile.element(entry.getKey(), original.type,
                    original.x, original.y, original.width, original.height);
            apply(entry.getValue(), restored);
        }
        profile.save();
        syncFields();
    }
}
