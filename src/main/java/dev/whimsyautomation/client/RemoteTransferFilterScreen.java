package dev.whimsyautomation.client;

import dev.whimsyautomation.network.RemoteTransferActionPayload;
import dev.whimsyautomation.remote.RemoteTransferNetworkData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.UUID;

/** Ten non-consuming ghost item slots plus the player's inventory. */
public final class RemoteTransferFilterScreen extends Screen {
    private static final int SLOT = 18;
    private final Screen parent;
    private final UUID networkId, workstationId;
    private final int workstationSlot;
    private final Consumer<RemoteTransferNetworkData.Filter> onSaved;
    private final List<String> entries = new ArrayList<>();
    private RemoteTransferNetworkData.FilterMode mode;
    private boolean matchDurability, matchTags, matchNbt;
    private int selectedFilterSlot = -1;
    private int inventoryX, inventoryY;

    public RemoteTransferFilterScreen(Screen parent, UUID networkId, UUID workstationId,
                                      int workstationSlot, RemoteTransferNetworkData.Filter filter,
                                      Consumer<RemoteTransferNetworkData.Filter> onSaved) {
        super(Component.literal("槽位过滤规则"));
        this.parent = parent;
        this.networkId = networkId;
        this.workstationId = workstationId;
        this.workstationSlot = workstationSlot;
        this.onSaved = onSaved;
        this.mode = filter.mode() == RemoteTransferNetworkData.FilterMode.BLACKLIST
                ? RemoteTransferNetworkData.FilterMode.BLACKLIST
                : RemoteTransferNetworkData.FilterMode.WHITELIST;
        this.matchDurability = !filter.ignoreDurability();
        this.matchNbt = !filter.ignoreNbt();
        this.matchTags = filter.matchTags();
        for (int i = 0; i < 10; i++)
            entries.add(i < filter.items().size() ? filter.items().get(i) : "");
    }

    @Override
    protected void init() {
        clearWidgets();
        int buttonY = 108;
        int controlGap = 3;
        int controlW = Math.max(36, (width - 16 - controlGap * 3) / 4);
        addRenderableWidget(Button.builder(Component.literal(
                mode == RemoteTransferNetworkData.FilterMode.WHITELIST ? "白名单" : "黑名单"), b -> {
            mode = mode == RemoteTransferNetworkData.FilterMode.WHITELIST
                    ? RemoteTransferNetworkData.FilterMode.BLACKLIST
                    : RemoteTransferNetworkData.FilterMode.WHITELIST;
            b.setMessage(Component.literal(mode == RemoteTransferNetworkData.FilterMode.WHITELIST
                    ? "白名单" : "黑名单"));
        }).bounds(8, buttonY, controlW, 20).build());
        addRenderableWidget(toggle("耐久", matchDurability, v -> matchDurability = v)
                .bounds(8 + controlW + controlGap, buttonY, controlW, 20).build());
        addRenderableWidget(toggle("物品标签", matchTags, v -> matchTags = v)
                .bounds(8 + (controlW + controlGap) * 2, buttonY, controlW, 20).build());
        addRenderableWidget(toggle("NBT", matchNbt, v -> matchNbt = v)
                .bounds(8 + (controlW + controlGap) * 3, buttonY, controlW, 20).build());

        inventoryX = (width - 9 * SLOT) / 2;
        inventoryY = height - 104;
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> minecraft.setScreen(parent))
                .bounds(8, height - 24, Math.max(64, (width - 24) / 2), 20).build());
        addRenderableWidget(Button.builder(Component.literal("保存过滤规则"), b -> save())
                .bounds(width / 2 + 4, height - 24, Math.max(64, (width - 24) / 2), 20).build());
    }

    private interface BoolSetter { void set(boolean value); }
    private Button.Builder toggle(String label, boolean value, BoolSetter setter) {
        return Button.builder(Component.literal(label + "：" + (value ? "开" : "关")), b -> {
            boolean next = !b.getMessage().getString().endsWith("开");
            setter.set(next);
            b.setMessage(Component.literal(label + "：" + (next ? "开" : "关")));
        });
    }

    private void save() {
        int flags = (matchDurability ? 2 : 0) | (matchTags ? 4 : 0) | (matchNbt ? 8 : 0);
        StringBuilder encoded = new StringBuilder(mode.name()).append('|').append(flags).append('|');
        boolean first = true;
        for (String entry : entries) {
            if (entry.isBlank()) continue;
            if (!first) encoded.append(',');
            first = false;
            encoded.append(Base64.getEncoder().encodeToString(entry.getBytes(StandardCharsets.UTF_8)));
        }
        RemoteTransferNetworkData.Filter updated = new RemoteTransferNetworkData.Filter(mode, true,
                entries.stream().filter(e -> !e.isBlank()).toList(), !entries.stream().allMatch(String::isBlank),
                !matchDurability, !matchNbt, matchTags);
        PacketDistributor.sendToServer(new RemoteTransferActionPayload(
                23, networkId, workstationId, null, workstationSlot, encoded.toString()));
        if (onSaved != null) onSaved.accept(updated);
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF101722);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF101722);
        g.drawCenteredString(font, "槽位过滤规则 · 槽位 " + workstationSlot, width / 2, 12, 0xFFFFFFFF);
        g.drawCenteredString(font, entries.stream().anyMatch(e -> !e.isBlank())
                ? "点击有物品的过滤格可清空；选择空格后点击背包物品可复制模板"
                : "过滤槽全部为空：当前不启用过滤", width / 2, 28, 0xFFD0D9E5);

        int filterX = (width - 5 * SLOT) / 2;
        int filterY = 52;
        for (int i = 0; i < 10; i++) {
            int x = filterX + (i % 5) * SLOT;
            int y = filterY + (i / 5) * SLOT;
            g.fill(x, y, x + SLOT, y + SLOT, 0xFF080D14);
            g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1,
                    i == selectedFilterSlot ? 0xFF315C88 : 0xFF71849A);
            g.renderOutline(x, y, SLOT, SLOT, i == selectedFilterSlot ? 0xFFFFFFFF : 0xFFE0E8F0);
            ItemStack stack = stackFromEntry(entries.get(i));
            if (!stack.isEmpty()) g.renderItem(stack, x + 1, y + 1);
            g.drawString(font, Integer.toString(i + 1), x + 5, y + SLOT + 1, 0xFFBFCBDD, false);
        }

        g.drawString(font, "玩家背包（点击物品复制到选中的过滤格，不会消耗）",
                8, inventoryY - 12, 0xFFFFFFFF, false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int inventoryIndex = 9 + row * 9 + col;
                drawPlayerSlot(g, inventoryIndex, inventoryX + col * SLOT, inventoryY + row * SLOT, mouseX, mouseY);
            }
        }
        for (int col = 0; col < 9; col++)
            drawPlayerSlot(g, col, inventoryX + col * SLOT, inventoryY + 3 * SLOT + 6, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawPlayerSlot(GuiGraphics g, int index, int x, int y, int mouseX, int mouseY) {
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
        if (entry == null || entry.isBlank()) return ItemStack.EMPTY;
        String id = entry.substring(0, entry.indexOf('|') >= 0 ? entry.indexOf('|') : entry.length());
        try {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
                    net.minecraft.resources.ResourceLocation.parse(id)).orElse(null);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (RuntimeException ignored) { return ItemStack.EMPTY; }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int filterX = (width - 5 * SLOT) / 2;
            int filterY = 52;
            for (int i = 0; i < 10; i++) {
                int x = filterX + (i % 5) * SLOT;
                int y = filterY + (i / 5) * SLOT;
                if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                    if (!entries.get(i).isBlank()) {
                        entries.set(i, "");
                        selectedFilterSlot = -1;
                    } else selectedFilterSlot = i;
                    return true;
                }
            }
            if (selectedFilterSlot >= 0 && mouseY >= inventoryY - 2 && mouseY < inventoryY + 3 * SLOT + 6 + SLOT) {
                int col = (int) ((mouseX - inventoryX) / SLOT);
                if (col >= 0 && col < 9) {
                    int row = (int) ((mouseY - inventoryY) / SLOT);
                    int index;
                    if (row >= 0 && row < 3) index = 9 + row * 9 + col;
                    else if (mouseY >= inventoryY + 3 * SLOT + 6) index = col;
                    else return super.mouseClicked(mouseX, mouseY, button);
                    ItemStack picked = minecraft.player.getInventory().getItem(index);
                    if (!picked.isEmpty()) {
                        entries.set(selectedFilterSlot,
                                RemoteTransferNetworkData.Filter.fingerprint(picked, false, false));
                        selectedFilterSlot = -1;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean isPauseScreen() { return false; }
}
