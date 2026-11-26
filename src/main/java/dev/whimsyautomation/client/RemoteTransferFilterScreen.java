package dev.whimsyautomation.client;

import dev.whimsyautomation.network.RemoteTransferActionPayload;
import dev.whimsyautomation.remote.RemoteTransferNetworkData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Paged 9x3 ghost-item filter grid with Shift+right-click inventory painting. */
public final class RemoteTransferFilterScreen extends Screen {
    private static final int SLOT = 18;
    private static final int PAGE_SIZE = 27;
    private static final String PAGE_MARKER = "#FILTER_PAGE#";
    private static final String EMPTY_MARKER = "#FILTER_EMPTY#";
    private final Screen parent;
    private final UUID networkId, workstationId;
    private final List<Integer> workstationSlots;
    private final Consumer<RemoteTransferNetworkData.Filter> onSaved;
    private final List<String> entries = new ArrayList<>();
    private RemoteTransferNetworkData.FilterMode mode;
    private boolean matchDurability, matchTags, matchNbt;
    private int page;
    private int pageCount = 1;
    private int gridX, filterY, inventoryX, inventoryY;
    private int lastPaintedInventory = -1, lastPaintedFilter = -1;
    private boolean painting;

    public RemoteTransferFilterScreen(Screen parent, UUID networkId, UUID workstationId,
                                      List<Integer> workstationSlots, RemoteTransferNetworkData.Filter filter,
                                      Consumer<RemoteTransferNetworkData.Filter> onSaved) {
        super(Component.literal("槽位过滤规则"));
        this.parent = parent;
        this.networkId = networkId;
        this.workstationId = workstationId;
        this.workstationSlots = List.copyOf(workstationSlots);
        this.onSaved = onSaved;
        this.mode = filter.mode() == RemoteTransferNetworkData.FilterMode.BLACKLIST
                ? RemoteTransferNetworkData.FilterMode.BLACKLIST
                : RemoteTransferNetworkData.FilterMode.WHITELIST;
        this.matchDurability = !filter.ignoreDurability();
        this.matchNbt = !filter.ignoreNbt();
        this.matchTags = filter.matchTags();
        for (String entry : filter.items()) {
            if (PAGE_MARKER.equals(entry)) pageCount++;
            else entries.add(EMPTY_MARKER.equals(entry) ? "" : entry);
        }
        pageCount = Math.max(1, pageCount);
        while (entries.size() < pageCount * PAGE_SIZE) entries.add("");
        while (entries.size() > pageCount * PAGE_SIZE) entries.remove(entries.size() - 1);
    }

    @Override
    protected void init() {
        clearWidgets();
        int gridWidth = 9 * SLOT;
        gridX = width - gridWidth - 8;
        if (gridX < 8) gridX = 8;
        inventoryX = gridX;
        inventoryY = height - 82;
        filterY = inventoryY - 3 * SLOT - 12;

        int leftX = 8;
        int leftW = Math.max(28, (gridX - 20) / 2);
        int rightX = leftX + leftW + 4;
        int y = Math.max(38, filterY);
        addRenderableWidget(TechButton.create(Component.literal(mode == RemoteTransferNetworkData.FilterMode.WHITELIST
                ? "白名单" : "黑名单"), b -> {
            mode = mode == RemoteTransferNetworkData.FilterMode.WHITELIST
                    ? RemoteTransferNetworkData.FilterMode.BLACKLIST
                    : RemoteTransferNetworkData.FilterMode.WHITELIST;
            b.setMessage(Component.literal(mode == RemoteTransferNetworkData.FilterMode.WHITELIST ? "白名单" : "黑名单"));
        }).bounds(leftX, y, leftW, 18).build());
        addRenderableWidget(toggle("耐久", matchDurability, v -> matchDurability = v)
                .bounds(rightX, y, leftW, 18).build());
        addRenderableWidget(toggle("标签", matchTags, v -> matchTags = v)
                .bounds(leftX, y + 22, leftW, 18).build());
        addRenderableWidget(toggle("NBT", matchNbt, v -> matchNbt = v)
                .bounds(rightX, y + 22, leftW, 18).build());
        addRenderableWidget(TechButton.create(Component.literal("上一页"), b -> {
            page = Math.max(0, page - 1); init();
        }).bounds(leftX, y + 44, leftW, 18).build());
        addRenderableWidget(TechButton.create(Component.literal("下一页"), b -> {
            page = Math.min(pageCount - 1, page + 1); init();
        }).bounds(rightX, y + 44, leftW, 18).build());
        addRenderableWidget(TechButton.create(Component.literal("+ 添加页"), b -> {
            if (pageCount < 8) {
                pageCount++;
                while (entries.size() < pageCount * PAGE_SIZE) entries.add("");
                page = pageCount - 1;
                init();
            }
        }).bounds(leftX, y + 66, leftW, 18).build());
        addRenderableWidget(TechButton.create(Component.literal("− 删除页"), b -> {
            if (pageCount > 1) {
                entries.subList(page * PAGE_SIZE, (page + 1) * PAGE_SIZE).clear();
                pageCount--;
                while (entries.size() < pageCount * PAGE_SIZE) entries.add("");
                page = Math.min(page, pageCount - 1);
                init();
            }
        }).bounds(rightX, y + 66, leftW, 18).build());
        addRenderableWidget(TechButton.create(Component.literal("返回"), b -> minecraft.setScreen(parent))
                .bounds(leftX, height - 24, leftW, 20).build());
        addRenderableWidget(TechButton.create(Component.literal("保存"), b -> save())
                .bounds(gridX + gridWidth - 54, height - 24, 54, 20).build());
    }

    private interface BoolSetter { void set(boolean value); }
    private TechButton.Builder toggle(String label, boolean value, BoolSetter setter) {
        return TechButton.create(Component.literal(label + "：" + (value ? "开" : "关")), b -> {
            boolean next = !b.getMessage().getString().endsWith("开");
            setter.set(next);
            b.setMessage(Component.literal(label + "：" + (next ? "开" : "关")));
        });
    }

    private void save() {
        int flags = (matchDurability ? 2 : 0) | (matchTags ? 4 : 0) | (matchNbt ? 8 : 0);
        StringBuilder encoded = new StringBuilder(mode.name()).append('|').append(flags).append('|');
        List<String> saved = new ArrayList<>();
        for (int p = 0; p < pageCount; p++) {
            if (p > 0) {
                if (!encoded.toString().endsWith("|")) encoded.append(',');
                encoded.append(Base64.getEncoder().encodeToString(PAGE_MARKER.getBytes(StandardCharsets.UTF_8)));
                saved.add(PAGE_MARKER);
            }
            for (int i = 0; i < PAGE_SIZE; i++) {
                String entry = entries.get(p * PAGE_SIZE + i);
                if (!encoded.toString().endsWith("|")) encoded.append(',');
                String stored = entry.isBlank() ? EMPTY_MARKER : entry;
                encoded.append(Base64.getEncoder().encodeToString(stored.getBytes(StandardCharsets.UTF_8)));
                saved.add(stored);
            }
        }
        RemoteTransferNetworkData.Filter updated = new RemoteTransferNetworkData.Filter(mode, true,
                saved, !saved.isEmpty(), !matchDurability, !matchNbt, matchTags);
        for (int slot : workstationSlots) {
            PacketDistributor.sendToServer(new RemoteTransferActionPayload(23, networkId, workstationId, null,
                    slot, encoded.toString()));
        }
        if (onSaved != null) onSaved.accept(updated);
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF101722);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        String target = workstationSlots.size() == 1 ? "槽位 " + (workstationSlots.get(0) + 1)
                : workstationSlots.size() + " 个槽位";
        g.drawCenteredString(font, "槽位过滤规则 · " + target, width / 2, 10, 0xFFFFFFFF);
        g.drawString(font, "第 " + (page + 1) + " / " + pageCount + " 页", 8,
                Math.max(20, filterY - 12), 0xFFD0D9E5, false);
        g.drawString(font, "过滤栏位（Shift+右键滑动添加；点击或Shift+右键移除）",
                gridX, filterY - 12, 0xFFFFFFFF, false);
        for (int i = 0; i < PAGE_SIZE; i++) {
            int x = gridX + (i % 9) * SLOT;
            int y = filterY + (i / 9) * SLOT;
            g.fill(x, y, x + SLOT, y + SLOT, 0xFF080D14);
            g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xFF526477);
            g.renderOutline(x, y, SLOT, SLOT, 0xFFBAC8D8);
            ItemStack stack = stackFromEntry(entries.get(page * PAGE_SIZE + i));
            if (!stack.isEmpty()) g.renderItem(stack, x + 1, y + 1);
        }
        g.drawString(font, "玩家背包", inventoryX, inventoryY - 12, 0xFFFFFFFF, false);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawPlayerSlot(g, 9 + row * 9 + col, inventoryX + col * SLOT, inventoryY + row * SLOT);
        for (int col = 0; col < 9; col++)
            drawPlayerSlot(g, col, inventoryX + col * SLOT, inventoryY + 3 * SLOT + 6);
    }

    private void drawPlayerSlot(GuiGraphics g, int index, int x, int y) {
        g.fill(x, y, x + SLOT, y + SLOT, 0xFF080D14);
        g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xFF526477);
        g.renderOutline(x, y, SLOT, SLOT, 0xFFBAC8D8);
        ItemStack stack = Minecraft.getInstance().player == null ? ItemStack.EMPTY
                : Minecraft.getInstance().player.getInventory().getItem(index);
        if (!stack.isEmpty()) {
            g.renderItem(stack, x + 1, y + 1);
            g.renderItemDecorations(font, stack, x + 1, y + 1);
        }
    }

    private ItemStack stackFromEntry(String entry) {
        if (entry == null || entry.isBlank() || PAGE_MARKER.equals(entry)) return ItemStack.EMPTY;
        String id = entry.substring(0, entry.indexOf('|') >= 0 ? entry.indexOf('|') : entry.length());
        try {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
                    net.minecraft.resources.ResourceLocation.parse(id)).orElse(null);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (RuntimeException ignored) { return ItemStack.EMPTY; }
    }

    private int inventoryIndexAt(double mouseX, double mouseY) {
        int col = (int) ((mouseX - inventoryX) / SLOT);
        if (col < 0 || col >= 9) return -1;
        int row = (int) ((mouseY - inventoryY) / SLOT);
        if (row >= 0 && row < 3) return 9 + row * 9 + col;
        if (mouseY >= inventoryY + 3 * SLOT + 6 && mouseY < inventoryY + 4 * SLOT + 6) return col;
        return -1;
    }

    private int filterIndexAt(double mouseX, double mouseY) {
        int col = (int) ((mouseX - gridX) / SLOT);
        int row = (int) ((mouseY - filterY) / SLOT);
        return col >= 0 && col < 9 && row >= 0 && row < 3 ? row * 9 + col : -1;
    }

    private void addFromInventory(int inventoryIndex) {
        ItemStack stack = minecraft.player.getInventory().getItem(inventoryIndex);
        if (stack.isEmpty()) return;
        String fingerprint = RemoteTransferNetworkData.Filter.fingerprint(stack, !matchDurability, !matchNbt);
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            if (entries.get(start + i).equals(fingerprint)) return;
            if (entries.get(start + i).isBlank()) {
                entries.set(start + i, fingerprint);
                return;
            }
        }
    }

    private void removeAt(int index) {
        if (index >= 0 && !entries.get(page * PAGE_SIZE + index).isBlank())
            entries.set(page * PAGE_SIZE + index, "");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int filterIndex = filterIndexAt(mouseX, mouseY);
            if (filterIndex >= 0) {
                removeAt(filterIndex);
                return true;
            }
        }
        if (button == 1 && hasShiftDown()) {
            int filterIndex = filterIndexAt(mouseX, mouseY);
            if (filterIndex >= 0) {
                removeAt(filterIndex);
                painting = true;
                lastPaintedFilter = filterIndex;
                return true;
            }
            int inventoryIndex = inventoryIndexAt(mouseX, mouseY);
            if (inventoryIndex >= 0) {
                addFromInventory(inventoryIndex);
                painting = true;
                lastPaintedInventory = inventoryIndex;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (painting && button == 1 && hasShiftDown()) {
            int filterIndex = filterIndexAt(mouseX, mouseY);
            if (filterIndex >= 0 && filterIndex != lastPaintedFilter) {
                removeAt(filterIndex);
                lastPaintedFilter = filterIndex;
            }
            int inventoryIndex = inventoryIndexAt(mouseX, mouseY);
            if (inventoryIndex >= 0 && inventoryIndex != lastPaintedInventory) {
                addFromInventory(inventoryIndex);
                lastPaintedInventory = inventoryIndex;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1) {
            painting = false;
            lastPaintedInventory = -1;
            lastPaintedFilter = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean isPauseScreen() { return false; }
}
