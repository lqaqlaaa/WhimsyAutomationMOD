package dev.whimsyautomation.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** Mouse-driven editor for any registered logical UI element. */
public final class UiLayoutEditor {
    private final UiLayoutProfile profile;
    private boolean enabled;
    private String selected;
    private boolean resizing;
    private boolean dragging;
    private double lastX;
    private double lastY;

    public UiLayoutEditor(String screenId) {
        profile = UiLayoutProfile.load(screenId);
    }

    public UiLayoutProfile.Element element(String id, String type,
                                            int x, int y, int width, int height) {
        return profile.element(id, type, x, y, width, height);
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
        for (Map.Entry<String, UiLayoutProfile.Element> entry : profile.elements().entrySet()) {
            UiLayoutProfile.Element e = entry.getValue();
            double x = originX + e.x, y = originY + e.y;
            if (mouseX >= x && mouseX < x + e.width && mouseY >= y && mouseY < y + e.height)
                hit = entry.getKey();
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
    public void reset() { profile.reset(); selected = null; }
}
