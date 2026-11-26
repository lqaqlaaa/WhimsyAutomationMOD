package dev.whimsyautomation.client;

import dev.whimsyautomation.network.RemoteTransferActionPayload;
import dev.whimsyautomation.remote.RemoteTransferNetworkData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Displays the selected member's live server-side ItemHandler slots. */
public final class RemoteTransferInventoryScreen extends Screen {
    private static final int CELL = 18;
    private static final int SLOT_SIZE = 18;
    private final RemoteTransferScreen parent;
    private final UUID networkId;
    private final UUID memberId;
    private final ListTag slots;
    private final boolean available;
    private final String blockId;
    private final boolean furnaceLayout;
    private final boolean menuLayout;
    private final boolean deconstructionLayout;
    private final boolean conversionLayout;
    private final boolean extractorLayout;
    private final boolean pagedMachineLayout;
    private int page;
    private int selectedSlot = -1;
    private boolean multiSelectMode;
    private final Set<Integer> selectedSlots = new HashSet<>();
    private final Map<Integer, RemoteTransferNetworkData.Filter> localFilters = new HashMap<>();
    private int panelX, panelY, panelWidth = 176, panelHeight = 166;

    public RemoteTransferInventoryScreen(RemoteTransferScreen parent, CompoundTag snapshot) {
        super(Component.literal("工作方块库存槽位"));
        this.parent = parent;
        this.networkId = parent.selectedNetworkId();
        CompoundTag inventory = snapshot.getCompound("Inventory");
        this.memberId = inventory.hasUUID("Member") ? inventory.getUUID("Member") : null;
        this.available = inventory.getBoolean("Available");
        this.blockId = inventory.getString("Block");
        this.furnaceLayout = blockId.equals("minecraft:furnace")
                || blockId.equals("minecraft:blast_furnace") || blockId.equals("minecraft:smoker");
        this.menuLayout = inventory.getBoolean("MenuLayout");
        this.deconstructionLayout = blockId.equals("whimsy_automation:deconstruction_table");
        this.conversionLayout = blockId.equals("whimsy_automation:enchantment_conversion_table");
        this.extractorLayout = blockId.equals("whimsy_automation:enchantment_extractor");
        this.pagedMachineLayout = deconstructionLayout || extractorLayout;
        this.slots = inventory.getList("Slots", net.minecraft.nbt.Tag.TAG_COMPOUND).copy();
    }

    @Override
    protected void init() {
        int pageCount = pageCount();
        page = Math.max(0, Math.min(page, pageCount - 1));
        int rows = Math.max(1, (Math.min(27, slots.size()) + 8) / 9);
        int maxGuiX = 0, maxGuiY = 0;
        if (menuLayout) {
            for (int i = 0; i < slots.size(); i++) {
                CompoundTag slot = slots.getCompound(i);
                maxGuiX = Math.max(maxGuiX, slot.getInt("GuiX") + SLOT_SIZE);
                maxGuiY = Math.max(maxGuiY, slot.getInt("GuiY") + SLOT_SIZE);
            }
        }
        panelWidth = menuLayout ? Math.max(176, maxGuiX + 8)
                : pagedMachineLayout ? 224 : 176;
        panelHeight = menuLayout ? Math.max(166, maxGuiY + 8)
                : furnaceLayout || pagedMachineLayout ? 166
                : Math.max(92, 52 + rows * SLOT_SIZE);
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(42, (height - panelHeight) / 2 - 12);

        int buttonY = height - 28;
        int gap = 3;
        int buttonW = Math.max(42, Math.min(96, (width - 24 - gap * 3) / 4));
        int totalW = buttonW * 4 + gap * 3;
        int startX = (width - totalW) / 2;
        addRenderableWidget(Button.builder(Component.literal("返回"),
                b -> minecraft.setScreen(parent))
                .bounds(startX, buttonY, buttonW, 20).build());
        Button input = Button.builder(Component.literal("绑定输入"),
                b -> beginBinding("INPUT"))
                .bounds(startX + buttonW + gap, buttonY, buttonW, 20).build();
        input.active = hasBindingSelection() && available;
        addRenderableWidget(input);
        Button output = Button.builder(Component.literal("绑定输出"),
                b -> beginBinding("OUTPUT"))
                .bounds(startX + (buttonW + gap) * 2, buttonY, buttonW, 20).build();
        output.active = hasBindingSelection() && available;
        addRenderableWidget(output);
        Button filter = Button.builder(Component.literal("过滤"),
                b -> openFilter())
                .bounds(startX + (buttonW + gap) * 3, buttonY, buttonW, 20).build();
        filter.active = selectedSlot >= 0 && available;
        addRenderableWidget(filter);
        int autoY = height - 52;
        int autoGap = 6;
        int autoW = Math.min(150, (width - 32 - autoGap) / 2);
        int autoStartX = (width - (autoW * 2 + autoGap)) / 2;
        Button autoInput = Button.builder(Component.literal("自动识别输入"),
                b -> beginAutoBinding("INPUT"))
                .bounds(autoStartX, autoY, autoW, 20).build();
        autoInput.active = available && memberId != null && supportsAutoRecognition();
        addRenderableWidget(autoInput);
        Button autoOutput = Button.builder(Component.literal("自动识别输出"),
                b -> beginAutoBinding("OUTPUT"))
                .bounds(autoStartX + autoW + autoGap, autoY, autoW, 20).build();
        autoOutput.active = available && memberId != null && supportsAutoRecognition();
        addRenderableWidget(autoOutput);
        int selectY = height - 76;
        int selectGap = 6;
        int selectW = Math.min(132, (width - 32 - selectGap) / 2);
        int selectX = (width - (selectW * 2 + selectGap)) / 2;
        addRenderableWidget(Button.builder(Component.literal(
                multiSelectMode ? "多选模式：开启" : "多选模式：关闭"), b -> {
            multiSelectMode = !multiSelectMode;
            selectedSlots.clear();
            selectedSlot = -1;
            init();
        }).bounds(selectX, selectY, selectW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("清空选择"), b -> {
            selectedSlots.clear();
            selectedSlot = -1;
            init();
        }).bounds(selectX + selectW + selectGap, selectY, selectW, 20).build());
        if (pagedMachineLayout && pageCount > 1) {
            addRenderableWidget(Button.builder(Component.literal("‹"), b -> {
                page = Math.max(0, page - 1);
                selectedSlot = -1;
                init();
            }).bounds(panelX + panelWidth - 44, panelY + 3, 18, 14).build());
            addRenderableWidget(Button.builder(Component.literal("›"), b -> {
                page = Math.min(pageCount - 1, page + 1);
                selectedSlot = -1;
                init();
            }).bounds(panelX + panelWidth - 22, panelY + 3, 18, 14).build());
        }
    }

    private int pageCount() {
        if (!pagedMachineLayout || slots.size() <= 1) return 1;
        return Math.max(1, (slots.size() - 1 + 26) / 27);
    }

    private boolean isSlotVisible(int index) {
        if (!pagedMachineLayout) return true;
        if (index == 0) return page == 0;
        return (index - 1) / 27 == page;
    }

    private void openFilter() {
        if (selectedSlot < 0 || networkId == null || memberId == null) return;
        RemoteTransferNetworkData.Filter filter = localFilters.getOrDefault(selectedSlot,
                readFilter(selectedSlot));
        minecraft.setScreen(new RemoteTransferFilterScreen(this, networkId, memberId,
                selectedSlot, filter, updated -> localFilters.put(selectedSlot, updated)));
    }

    private RemoteTransferNetworkData.Filter readFilter(int slotIndex) {
        CompoundTag network = null;
        ListTag networks = parent.snapshotCopy().getList("Networks", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < networks.size(); i++) {
            CompoundTag candidate = networks.getCompound(i);
            if (candidate.hasUUID("Id") && candidate.getUUID("Id").equals(networkId)) {
                network = candidate; break;
            }
        }
        if (network == null) return RemoteTransferNetworkData.Filter.none();
        ListTag filters = network.getList("SlotFilters", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < filters.size(); i++) {
            CompoundTag entry = filters.getCompound(i);
            if (!entry.hasUUID("Workstation") || !entry.getUUID("Workstation").equals(memberId)
                    || entry.getInt("Slot") != slotIndex) continue;
            ListTag values = entry.getList("Items", net.minecraft.nbt.Tag.TAG_STRING);
            java.util.ArrayList<String> items = new java.util.ArrayList<>();
            for (int j = 0; j < values.size(); j++) items.add(values.getString(j));
            RemoteTransferNetworkData.FilterMode mode;
            try { mode = RemoteTransferNetworkData.FilterMode.valueOf(entry.getString("Mode")); }
            catch (RuntimeException ignored) { mode = RemoteTransferNetworkData.FilterMode.NONE; }
            return new RemoteTransferNetworkData.Filter(mode, entry.getBoolean("Exact"), items,
                    mode != RemoteTransferNetworkData.FilterMode.NONE,
                    entry.getBoolean("IgnoreDurability"), entry.getBoolean("IgnoreNbt"),
                    entry.getBoolean("MatchTags"));
        }
        return RemoteTransferNetworkData.Filter.none();
    }

    private boolean supportsAutoRecognition() {
        // The server first applies known machine layouts, then probes generic
        // ItemHandler insertion/extraction behavior for modded inventories.
        return true;
    }

    private void beginAutoBinding(String direction) {
        if (!supportsAutoRecognition() || !available || networkId == null || memberId == null) return;
        PacketDistributor.sendToServer(new RemoteTransferActionPayload(
                24, networkId, memberId, null, 0, direction));
        // The server determines the authoritative slot list; the next
        // Shift+right-click applies one target to every recognized slot.
        minecraft.setScreen(null);
    }

    private boolean hasBindingSelection() {
        return multiSelectMode ? !selectedSlots.isEmpty() : selectedSlot >= 0;
    }

    private void beginBinding(String direction) {
        if (!hasBindingSelection() || !available || networkId == null || memberId == null) return;
        if (multiSelectMode) {
            String indices = selectedSlots.stream().sorted().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
            PacketDistributor.sendToServer(new RemoteTransferActionPayload(
                    25, networkId, memberId, null, 0, direction + "|" + indices));
        } else {
            PacketDistributor.sendToServer(new RemoteTransferActionPayload(
                    16, networkId, memberId, null, selectedSlot, direction));
        }
        // The server stores the binding context. Close the screen so the next
        // Shift+right-click can target the container in the world.
        minecraft.setScreen(null);
    }

    private ItemStack slotStack(CompoundTag slot) {
        String id = slot.getString("Item");
        if (id.isBlank()) return ItemStack.EMPTY;
        try {
            var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(Items.BARRIER);
            return new ItemStack(item, Math.max(1, slot.getInt("Count")));
        } catch (RuntimeException ignored) {
            return new ItemStack(Items.BARRIER);
        }
    }

    private int slotX(int index) {
        if (menuLayout) return findSlot(index).getInt("GuiX");
        if (furnaceLayout) {
            return switch (index) {
                case 0, 1 -> 56;
                case 2 -> 116;
                default -> -1000;
            };
        }
        if (conversionLayout) return index == 0 ? 28 : index == 1 ? 132 : -1000;
        if (pagedMachineLayout) {
            if (index == 0) return 184;
            return 8 + ((index - 1) % 9) * SLOT_SIZE;
        }
        return 7 + (index % 9) * SLOT_SIZE;
    }

    private int slotY(int index) {
        if (menuLayout) return findSlot(index).getInt("GuiY");
        if (furnaceLayout) {
            return switch (index) {
                case 0 -> 17;
                case 1 -> 53;
                case 2 -> 35;
                default -> -1000;
            };
        }
        if (conversionLayout) return 50;
        if (pagedMachineLayout) {
            if (index == 0) return deconstructionLayout ? 53 : 71;
            return 71 + (((index - 1) % 27) / 9) * SLOT_SIZE;
        }
        return 42 + (index / 9) * SLOT_SIZE;
    }

    private CompoundTag findSlot(int index) {
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag slot = slots.getCompound(i);
            if (slot.getInt("Index") == index) return slot;
        }
        return new CompoundTag();
    }

    private String slotRole(int index) {
        if (furnaceLayout) return switch (index) {
            case 0 -> "原料";
            case 1 -> "燃料";
            case 2 -> "成品";
            default -> "槽位 " + index;
        };
        if (deconstructionLayout || extractorLayout) {
            if (index == 0) return deconstructionLayout ? "拆解输入" : "待处理物品";
            return "输出槽位";
        }
        if (conversionLayout) return index == 0 ? "复制输入" : "复制结果";
        return "槽位 " + index;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Override Screen's vanilla blurred menu background. This screen is a
        // custom inventory selector, so the world must never be visible through it.
        g.fill(0, 0, width, height, 0xFF101722);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Draw the same opaque base again before panels and widgets.
        g.fill(0, 0, width, height, 0xFF101722);
        g.drawCenteredString(font, "工作方块库存槽位", width / 2, 16, 0xFFFFFFFF);
        g.drawCenteredString(font, available
                ? ("存储：完整库存可用 · " + (menuLayout
                    ? "槽位位置取自该方块原生菜单；编号对应真实库存槽位。"
                    : furnaceLayout
                        ? "原料、燃料、成品按熔炉原生槽位位置显示。"
                        : deconstructionLayout
                            ? "拆解台：输入槽与当前输出页按原生菜单位置显示。"
                            : extractorLayout
                                ? "附魔提取台：处理槽与当前输出页按原生菜单位置显示。"
                                : conversionLayout
                                    ? "复制台：输入与结果槽按原生菜单位置显示。"
                                    : "通用库存视图：编号对应服务端 ItemHandler 的真实槽位索引。"))
                : "该方块当前没有可读取的 ItemHandler 库存接口。",
                width / 2, 30, available ? 0xFFD0D9E5 : 0xFFFF9A8B);

        g.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF192638);
        g.renderOutline(panelX, panelY, panelWidth, panelHeight, 0xFF58718F);

        if (furnaceLayout) {
            g.drawString(font, "原料", panelX + 24, panelY + 22, 0xFFBFCBDD, false);
            g.drawString(font, "燃料", panelX + 24, panelY + 58, 0xFFBFCBDD, false);
            g.drawString(font, "成品", panelX + 137, panelY + 40, 0xFFBFCBDD, false);
        } else if (deconstructionLayout || extractorLayout) {
            g.drawString(font, deconstructionLayout ? "输入" : "处理物品",
                    panelX + 184, panelY + (deconstructionLayout ? 42 : 60), 0xFFBFCBDD, false);
            g.drawString(font, "输出区", panelX + 8, panelY + 57, 0xFFBFCBDD, false);
            g.drawString(font, "第 " + (page + 1) + " / " + pageCount() + " 页",
                    panelX + panelWidth - 100, panelY + 6, 0xFFBFCBDD, false);
        } else if (conversionLayout) {
            g.drawString(font, "输入物品", panelX + 22, panelY + 34, 0xFFBFCBDD, false);
            g.drawString(font, "复制结果", panelX + 126, panelY + 34, 0xFFBFCBDD, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag slot = slots.getCompound(i);
            if (!isSlotVisible(slot.getInt("Index"))) continue;
            int index = slot.getInt("Index");
            int x = panelX + slotX(index);
            int y = panelY + slotY(index);
            if (x < panelX || x + SLOT_SIZE > panelX + panelWidth) continue;
            boolean selected = multiSelectMode ? selectedSlots.contains(index) : index == selectedSlot;
            // High-contrast two-layer slot wells remain visible even when empty.
            g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF080D14);
            g.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1,
                    selected ? 0xFF315C88 : 0xFF71849A);
            g.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE,
                    selected ? 0xFFFFFFFF : 0xFFE0E8F0);
            ItemStack stack = slotStack(slot);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 1, y + 1);
                g.renderItemDecorations(font, stack, x + 1, y + 1);
            }
            g.drawCenteredString(font, Integer.toString(index), x + SLOT_SIZE / 2,
                    y + SLOT_SIZE + 2, 0xFFBFCBDD);
        }

        if (multiSelectMode) {
            g.drawCenteredString(font, "多选模式：已选择 " + selectedSlots.size() + " 个槽位",
                    width / 2, height - 100, 0xFFFFFFFF);
        } else if (selectedSlot >= 0) {
            g.drawCenteredString(font, "当前选择：" + slotRole(selectedSlot)
                    + "（槽位 " + selectedSlot + "）", width / 2, height - 100, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < slots.size(); i++) {
                CompoundTag slot = slots.getCompound(i);
                int index = slot.getInt("Index");
                if (!isSlotVisible(index)) continue;
                int x = panelX + slotX(index);
                int y = panelY + slotY(index);
                if (mouseX >= x && mouseX < x + SLOT_SIZE
                        && mouseY >= y && mouseY < y + SLOT_SIZE) {
                    if (multiSelectMode) {
                        if (!selectedSlots.add(index)) selectedSlots.remove(index);
                    } else {
                        selectedSlot = index;
                        selectedSlots.clear();
                    }
                    init();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
