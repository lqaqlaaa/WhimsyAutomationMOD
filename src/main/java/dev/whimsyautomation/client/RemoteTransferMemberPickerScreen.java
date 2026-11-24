package dev.whimsyautomation.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** 将 TorchCampfireDamage BlockPickerScreen 的网格、搜索、翻页和悬停交互移植为网络成员选择器。 */
public final class RemoteTransferMemberPickerScreen extends Screen {
    private static final int COLS = 9, ROWS = 5, CELL = 20;
    private final Screen parent;
    private final List<CompoundTag> members = new ArrayList<>();
    private final UUID current;
    private final Consumer<UUID> onChoose;
    private final List<CompoundTag> filtered = new ArrayList<>();
    private EditBox searchBox;
    private int cell = CELL, scrollRow, gridX, gridY;

    public RemoteTransferMemberPickerScreen(Screen parent, List<CompoundTag> members,
                                             UUID current, Consumer<UUID> onChoose) {
        super(Component.literal("选择网络成员方块"));
        this.parent = parent;
        this.current = current;
        this.onChoose = onChoose;
        for (CompoundTag member : members) this.members.add(member.copy());
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
        searchBox.setHint(Component.literal("搜索方块名称、ID 或坐标…"));
        searchBox.setResponder(this::rebuildList);
        addRenderableWidget(searchBox);
        int by = Math.min(height - 22, gridY + gh + 6);
        int bw = Math.max(40, Math.min(90, (width - 26) / 3)), gap = 4;
        int totalW = bw * 3 + gap * 2, bx = cx - totalW / 2;
        addRenderableWidget(Button.builder(Component.literal("上一页"), b -> scrollRow = Math.max(0, scrollRow - ROWS))
                .bounds(bx, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("下一页"), b -> scrollRow = Math.min(maxRow(), scrollRow + ROWS))
                .bounds(bx + bw + gap, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> minecraft.setScreen(parent))
                .bounds(bx + (bw + gap) * 2, by, bw, 18).build());
    }

    private int maxRow() { return Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS); }

    private void rebuildList(String query) {
        filtered.clear();
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        for (CompoundTag member : members) {
            String label = blockName(member);
            String id = member.getString("Block");
            BlockPos pos = BlockPos.of(member.getLong("Pos"));
            String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            if (q.isEmpty() || label.toLowerCase(java.util.Locale.ROOT).contains(q)
                    || id.toLowerCase(java.util.Locale.ROOT).contains(q) || coords.contains(q)) filtered.add(member);
        }
        scrollRow = 0;
    }

    private static Block block(CompoundTag member) {
        try {
            ResourceLocation id = ResourceLocation.parse(member.getString("Block"));
            return BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.CHEST);
        } catch (RuntimeException e) {
            return member.getString("Kind").equals("WORKSTATION") ? Blocks.CRAFTING_TABLE : Blocks.CHEST;
        }
    }

    private static String blockName(CompoundTag member) { return block(member).getName().getString(); }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) for (int r = 0; r < ROWS; r++) for (int col = 0; col < COLS; col++) {
            int idx = (scrollRow + r) * COLS + col;
            if (idx >= filtered.size()) break;
            int x = gridX + col * cell, y = gridY + r * cell;
            if (mx >= x && mx < x + cell && my >= y && my < y + cell) {
                UUID chosen = filtered.get(idx).getUUID("Id");
                onChoose.accept(chosen);
                minecraft.setScreen(parent);
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
        g.drawCenteredString(font, "选择网络中的工作方块 / 容器", width / 2, gridY - 42, 0xFFD700);
        g.fill(gridX - 2, gridY - 2, gridX + COLS * cell + 2, gridY + ROWS * cell + 2, 0xC0000000);
        CompoundTag hovered = null;
        for (int r = 0; r < ROWS; r++) for (int col = 0; col < COLS; col++) {
            int idx = (scrollRow + r) * COLS + col;
            if (idx >= filtered.size()) break;
            CompoundTag member = filtered.get(idx);
            int x = gridX + col * cell, y = gridY + r * cell;
            g.renderItem(new ItemStack(block(member)), x + 2, y + 2);
            if (member.getUUID("Id").equals(current)) {
                g.fill(x, y, x + cell, y + 1, 0xFF00FF00);
                g.fill(x, y + cell - 1, x + cell, y + cell, 0xFF00FF00);
                g.fill(x, y, x + 1, y + cell, 0xFF00FF00);
                g.fill(x + cell - 1, y, x + cell, y + cell, 0xFF00FF00);
            }
            if (mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + cell) {
                g.fill(x, y, x + cell, y + cell, 0x60FFFFFF);
                hovered = member;
            }
        }
        if (hovered != null) {
            BlockPos p = BlockPos.of(hovered.getLong("Pos"));
            g.renderTooltip(font, Component.literal(blockName(hovered) + "\n" + hovered.getString("Dimension")
                    + "\n" + p.getX() + ", " + p.getY() + ", " + p.getZ()), mouseX, mouseY);
        }
        int totalRows = Math.max(1, (filtered.size() + COLS - 1) / COLS);
        g.drawCenteredString(font, "第 " + (scrollRow / ROWS + 1) + " / "
                + Math.max(1, (totalRows + ROWS - 1) / ROWS) + " 页  共 " + filtered.size() + " 个成员",
                width / 2, gridY - 14, 0xAAAAAA);
    }

    @Override public boolean isPauseScreen() { return false; }
}
