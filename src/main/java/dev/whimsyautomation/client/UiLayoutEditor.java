package dev.whimsyautomation.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;

/** Mouse-driven editor for any registered logical UI element. */
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
    private final Map<String, AbstractWidget> widgets = new LinkedHashMap<>();
    private final Map<String, UiLayoutProfile.Element> defaults = new LinkedHashMap<>();

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

    private void apply(AbstractWidget widget, UiLayoutProfile.Element e) {
        widget.setX(originX + e.x);
        widget.setY(originY + e.y);
        widget.setWidth(e.width);
        widget.setHeight(e.height);
    }

    public boolean enabled() { return enabled; }
    public void toggle() { enabled = !enabled; selected = null; dragging = false; resizing = false; }
    public void setEnabled(boolean value) { enabled = value; }

    public boolean click(double mouseX, double mouseY, int originX, int originY, int button) {
        if (!enabled) return false;
        if (button == 1) {
            selected = null;
            dragging = false;
            resizing = false;
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
        UiLayoutProfile.Element e = profile.elements().get(hit);
        double right = originX + e.x + e.width;
        double bottom = originY + e.y + e.height;
        resizing = mouseX >= right - 6 && mouseY >= bottom - 6;
        dragging = !resizing;
        lastX = mouseX;
        lastY = mouseY;
        return true;
    }

    public boolean drag(double mouseX, double mouseY) {
        if (!enabled || selected == null || (!dragging && !resizing)) return false;
        UiLayoutProfile.Element e = profile.elements().get(selected);
        int dx = (int) Math.round(mouseX - lastX);
        int dy = (int) Math.round(mouseY - lastY);
        if (resizing) {
            e.width = Math.max(1, e.width + dx);
            e.height = Math.max(1, e.height + dy);
        } else {
            e.x += dx;
            e.y += dy;
        }
        lastX = mouseX;
        lastY = mouseY;
        AbstractWidget widget = widgets.get(selected);
        if (widget != null) apply(widget, e);
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

    public void render(GuiGraphics graphics, int originX, int originY) {
        if (!enabled) return;
        graphics.fill(originX, originY + 20, originX + 350, originY + 34, 0xCC202020);
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                Component.literal("拖动移动 · 右下角缩放 · E导出JSON · R恢复默认 · 点击“布局”退出"),
                originX + 3, originY + 23, 0xFFFFFFFF, false);

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
                        Component.literal(entry.getKey() + "  " + e.x + "," + e.y
                                + "  " + e.width + "×" + e.height),
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
    }
}
