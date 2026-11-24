package dev.whimsyautomation.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** 移植自 TorchCampfireDamage 的九列五行物品网格选择器，用于连接过滤规则。 */
public final class RemoteTransferItemPickerScreen extends Screen {
    private static final int COLS = 9, ROWS = 5, CELL = 20;
    private final Screen parent;
    private final Consumer<String> onApply;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<Item> filtered = new ArrayList<>();
    private EditBox searchBox;
    private int cell = CELL, scrollRow, gridX, gridY;

    public RemoteTransferItemPickerScreen(Screen parent, String initial, Consumer<String> onApply) {
        super(Component.literal("选择过滤物品"));
        this.parent = parent;
        this.onApply = onApply;
        if (initial != null) for (String id : initial.split(",")) {
            String value = id.trim();
            if (!value.isEmpty()) selected.add(value);
        }
    }

    @Override protected void init() {
        rebuildList("");
        int cx = width / 2, availW = width - 16, availH = height - 70;
        cell = Math.min(CELL, Math.max(10, availW / COLS));
        if (ROWS * cell > availH) cell = Math.max(8, availH / ROWS);
        int gw = COLS * cell, gh = ROWS * cell;
        gridX = cx - gw / 2;
        gridY = Math.max(46, (height - gh) / 2);
        int searchW = Math.max(100, Math.min(300, availW));
        searchBox = new EditBox(font, cx - searchW / 2, gridY - 28, searchW, 16, Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入物品名称或 ID 搜索…"));
        searchBox.setResponder(this::rebuildList);
        addRenderableWidget(searchBox);
        int by = Math.min(height - 22, gridY + gh + 6);
        int bw = Math.max(40, Math.min(90, (width - 26) / 3)), gap = 4;
        int totalW = bw * 3 + gap * 2, bx = cx - totalW / 2;
        addRenderableWidget(Button.builder(Component.literal("上一页"), b -> scrollRow = Math.max(0, scrollRow - ROWS))
                .bounds(bx, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("下一页"), b -> scrollRow = Math.min(maxRow(), scrollRow + ROWS))
                .bounds(bx + bw + gap, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), b -> {
            onApply.accept(String.join(",", selected));
            minecraft.setScreen(parent);
        }).bounds(bx + (bw + gap) * 2, by, bw, 18).build());
    }

    private int maxRow() { return Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS); }

    private void rebuildList(String query) {
        filtered.clear();
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty() && (q.isEmpty() || id.toString().contains(q)
                    || stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(q))) filtered.add(item);
        }
        scrollRow = 0;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) for (int r = 0; r < ROWS; r++) for (int col = 0; col < COLS; col++) {
            int idx = (scrollRow + r) * COLS + col;
            if (idx >= filtered.size()) break;
            int x = gridX + col * cell, y = gridY + r * cell;
            if (mx >= x && mx < x + cell && my >= y && my < y + cell) {
                String id = BuiltInRegistries.ITEM.getKey(filtered.get(idx)).toString();
                if (!selected.add(id)) selected.remove(id);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dy > 0) scrollRow = Math.max(0, scrollRow - 1);
        else if (dy < 0) scrollRow = Math.min(maxRow(), scrollRow + 1);
        return true;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, "选择过滤物品（点击可多选）", width / 2, gridY - 42, 0xFFD700);
        g.fill(gridX - 2, gridY - 2, gridX + COLS * cell + 2, gridY + ROWS * cell + 2, 0xC0000000);
        Item hovered = null;
        for (int r = 0; r < ROWS; r++) for (int col = 0; col < COLS; col++) {
            int idx = (scrollRow + r) * COLS + col;
            if (idx >= filtered.size()) break;
            Item item = filtered.get(idx);
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            int x = gridX + col * cell, y = gridY + r * cell;
            g.renderItem(new ItemStack(item), x + 2, y + 2);
            if (selected.contains(id)) {
                g.fill(x, y, x + cell, y + 1, 0xFF00FF00);
                g.fill(x, y + cell - 1, x + cell, y + cell, 0xFF00FF00);
                g.fill(x, y, x + 1, y + cell, 0xFF00FF00);
                g.fill(x + cell - 1, y, x + cell, y + cell, 0xFF00FF00);
            }
            if (mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + cell) {
                g.fill(x, y, x + cell, y + cell, 0x60FFFFFF);
                hovered = item;
            }
        }
        if (hovered != null) g.renderTooltip(font, new ItemStack(hovered), mouseX, mouseY);
        int totalRows = Math.max(1, (filtered.size() + COLS - 1) / COLS);
        g.drawCenteredString(font, "第 " + (scrollRow / ROWS + 1) + " / "
                + Math.max(1, (totalRows + ROWS - 1) / ROWS) + " 页  已选 " + selected.size() + " 项",
                width / 2, gridY - 14, 0xAAAAAA);
    }

    @Override public boolean isPauseScreen() { return false; }
}
