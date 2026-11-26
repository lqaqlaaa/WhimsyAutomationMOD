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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Displays and selects the selected member's live server-side ItemHandler slots. */
public final class RemoteTransferInventoryScreen extends Screen {
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLUMNS = 9;
    private static final int MAX_GRID_ROWS = 6;
    private static final int PANEL_WIDTH = 178;
    private static final int HEADER_HEIGHT = 16;
    private static final int FOOTER_HEIGHT = 54;

    private final RemoteTransferScreen parent;
    private final UUID networkId;
    private final UUID memberId;
    private final ListTag slots;
    private final boolean available;
    private final String blockId;
    private final boolean furnaceLayout;
    private final boolean deconstructionLayout;
    private final boolean conversionLayout;
    private final boolean extractorLayout;

    private int page;
    private int visibleRows = MAX_GRID_ROWS;
    private int pageCapacity = GRID_COLUMNS * MAX_GRID_ROWS;
    private boolean multiSelectMode;
    private int selectedSlot = -1;
    private final Set<Integer> selectedSlots = new HashSet<>();
    private boolean dragSelecting;
    private final Set<Integer> dragVisitedSlots = new HashSet<>();
    private final Map<Integer, RemoteTransferNetworkData.Filter> localFilters = new HashMap<>();

    private int panelX;
    private int panelY;
    private int panelHeight;
    private Button multiSelectButton;
    private Button selectAllButton;
    private Button bindInputButton;
    private Button bindOutputButton;
    private Button filterButton;

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
        this.deconstructionLayout = blockId.equals("whimsy_automation:deconstruction_table");
        this.conversionLayout = blockId.equals("whimsy_automation:enchantment_conversion_table");
        this.extractorLayout = blockId.equals("whimsy_automation:enchantment_extractor");
        this.slots = inventory.getList("Slots", net.minecraft.nbt.Tag.TAG_COMPOUND).copy();
    }

    @Override
    protected void init() {
        // Reserve a fixed footer for two button rows. The inventory grid uses
        // as many as six rows, but shrinks on short GUI viewports to avoid overlap.
        int availableGridHeight = Math.max(SLOT_SIZE,
                height - 44 - FOOTER_HEIGHT - 8 - HEADER_HEIGHT);
        visibleRows = Math.max(1, Math.min(MAX_GRID_ROWS, availableGridHeight / SLOT_SIZE));
        pageCapacity = GRID_COLUMNS * visibleRows;
        int pageCount = pageCount();
        page = Math.max(0, Math.min(page, pageCount - 1));

        panelX = (width - PANEL_WIDTH) / 2;
        panelY = 44;
        panelHeight = HEADER_HEIGHT + visibleRows * SLOT_SIZE + 8;

        int gap = 4;
        int firstRowWidth = Math.min(width - 24, 300);
        int firstButtonWidth = (firstRowWidth - gap * 2) / 3;
        int firstStartX = (width - firstRowWidth) / 2;
        int buttonWidth = Math.max(42, Math.min(76, (width - 24 - gap * 3) / 4));
        int totalWidth = buttonWidth * 4 + gap * 3;
        int startX = (width - totalWidth) / 2;
        int firstRowY = height - 48;
        int secondRowY = height - 24;
        multiSelectButton = TechButton.create(Component.literal(multiSelectMode ? "多选：开" : "多选：关"), b -> {
            multiSelectMode = !multiSelectMode; selectedSlots.clear(); selectedSlot = -1; dragSelecting = false;
            updateSelectionButtons(); init();
        }).bounds(firstStartX, firstRowY, firstButtonWidth, 20).build();
        addRenderableWidget(multiSelectButton);
        selectAllButton = TechButton.create(Component.literal("全选格子"), b -> selectAllSlots())
            .bounds(firstStartX + firstButtonWidth + gap, firstRowY, firstButtonWidth, 20).build();
        selectAllButton.visible = multiSelectMode; addRenderableWidget(selectAllButton);
        addRenderableWidget(TechButton.create(Component.literal("清空"), b -> {
            selectedSlots.clear(); selectedSlot = -1; updateSelectionButtons();
        }).bounds(firstStartX + (firstButtonWidth + gap) * 2, firstRowY, firstButtonWidth, 20).build());
        addRenderableWidget(TechButton.create(Component.literal("返回"), b -> minecraft.setScreen(parent))
            .bounds(startX, secondRowY, buttonWidth, 20).build());
        bindInputButton = TechButton.create(Component.literal("绑定输入"), b -> beginBinding("INPUT"))
            .bounds(startX + buttonWidth + gap, secondRowY, buttonWidth, 20).build(); addRenderableWidget(bindInputButton);
        bindOutputButton = TechButton.create(Component.literal("绑定输出"), b -> beginBinding("OUTPUT"))
            .bounds(startX + (buttonWidth + gap) * 2, secondRowY, buttonWidth, 20).build(); addRenderableWidget(bindOutputButton);
        filterButton = TechButton.create(Component.literal("过滤"), b -> openFilter())
            .bounds(startX + (buttonWidth + gap) * 3, secondRowY, buttonWidth, 20).build(); addRenderableWidget(filterButton);
        updateSelectionButtons();
        if (pageCount > 1) {
            addRenderableWidget(TechButton.create(Component.literal("‹"), b -> {
                        page = Math.max(0, page - 1);
                        init();
                    }).bounds(panelX + PANEL_WIDTH - 40, panelY + 1, 17, 14).build());
            addRenderableWidget(TechButton.create(Component.literal("›"), b -> {
                        page = Math.min(pageCount - 1, page + 1);
                        init();
                    }).bounds(panelX + PANEL_WIDTH - 20, panelY + 1, 17, 14).build());
        }
    }

    private int pageCount() {
        return Math.max(1, (slots.size() + pageCapacity - 1) / pageCapacity);
    }

    private boolean isSlotVisible(int index) {
        return index >= page * pageCapacity && index < Math.min(slots.size(), (page + 1) * pageCapacity);
    }

    private int visibleSlotX(int index) {
        int localIndex = index - page * pageCapacity;
        return 8 + (localIndex % GRID_COLUMNS) * SLOT_SIZE;
    }

    private int visibleSlotY(int index) {
        int localIndex = index - page * pageCapacity;
        return HEADER_HEIGHT + (localIndex / GRID_COLUMNS) * SLOT_SIZE;
    }

    private void updateSelectionButtons() {
        boolean hasSelection = multiSelectMode ? !selectedSlots.isEmpty() : selectedSlot >= 0;
        if (bindInputButton != null) bindInputButton.active = available && hasSelection;
        if (bindOutputButton != null) bindOutputButton.active = available && hasSelection;
        if (filterButton != null) filterButton.active = available && selectedSlot >= 0;
        if (multiSelectButton != null) multiSelectButton.setMessage(
                Component.literal(multiSelectMode ? "多选：开" : "多选：关"));
        if (selectAllButton != null) selectAllButton.visible = multiSelectMode;
    }

    private void selectAllSlots() {
        if (!multiSelectMode) return;
        selectedSlots.clear();
        for (int i = 0; i < slots.size(); i++) selectedSlots.add(slots.getCompound(i).getInt("Index"));
        selectedSlot = -1; updateSelectionButtons();
    }
    private void toggleSlotSelection(int index) {
        if (!multiSelectMode) {
            selectedSlot = index;
            selectedSlots.clear();
        } else {
            if (!selectedSlots.add(index)) selectedSlots.remove(index);
        }
        updateSelectionButtons();
    }

    private int slotAt(double mouseX, double mouseY) {
        if (mouseX < panelX || mouseX >= panelX + PANEL_WIDTH
                || mouseY < panelY + HEADER_HEIGHT || mouseY >= panelY + panelHeight) return -1;
        int column = (int) ((mouseX - panelX - 8) / SLOT_SIZE);
        int row = (int) ((mouseY - panelY - HEADER_HEIGHT) / SLOT_SIZE);
        if (column < 0 || column >= GRID_COLUMNS || row < 0 || row >= visibleRows) return -1;
        int index = page * pageCapacity + row * GRID_COLUMNS + column;
        return index >= 0 && index < slots.size() ? index : -1;
    }

    private void openFilter() {
        if (selectedSlot < 0 || networkId == null || memberId == null) return;
        RemoteTransferNetworkData.Filter filter = localFilters.getOrDefault(selectedSlot,
                readFilter(selectedSlot));
        minecraft.setScreen(new RemoteTransferFilterScreen(this, networkId, memberId,
                selectedSlot, filter, updated -> localFilters.put(selectedSlot, updated)));
    }

    private RemoteTransferNetworkData.Filter readFilter(int slotIndex) {
        ListTag networks = parent.snapshotCopy().getList("Networks", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < networks.size(); i++) {
            CompoundTag candidate = networks.getCompound(i);
            if (!candidate.hasUUID("Id") || !candidate.getUUID("Id").equals(networkId)) continue;
            ListTag filters = candidate.getList("SlotFilters", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int j = 0; j < filters.size(); j++) {
                CompoundTag entry = filters.getCompound(j);
                if (!entry.hasUUID("Workstation") || !entry.getUUID("Workstation").equals(memberId)
                        || entry.getInt("Slot") != slotIndex) continue;
                ListTag values = entry.getList("Items", net.minecraft.nbt.Tag.TAG_STRING);
                java.util.ArrayList<String> items = new java.util.ArrayList<>();
                for (int k = 0; k < values.size(); k++) items.add(values.getString(k));
                RemoteTransferNetworkData.FilterMode mode;
                try {
                    mode = RemoteTransferNetworkData.FilterMode.valueOf(entry.getString("Mode"));
                } catch (RuntimeException ignored) {
                    mode = RemoteTransferNetworkData.FilterMode.NONE;
                }
                return new RemoteTransferNetworkData.Filter(mode, entry.getBoolean("Exact"), items,
                        mode != RemoteTransferNetworkData.FilterMode.NONE,
                        entry.getBoolean("IgnoreDurability"), entry.getBoolean("IgnoreNbt"),
                        entry.getBoolean("MatchTags"));
            }
        }
        return RemoteTransferNetworkData.Filter.none();
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

    private String slotRole(int index) {
        if (furnaceLayout) return switch (index) {
            case 0 -> "原料";
            case 1 -> "燃料";
            case 2 -> "成品";
            default -> "槽位";
        };
        if (deconstructionLayout || extractorLayout) {
            if (index == 0) return deconstructionLayout ? "拆解输入" : "待处理物品";
            return "输出槽位";
        }
        if (conversionLayout) return index == 0 ? "复制输入" : "复制结果";
        return "槽位";
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF101722);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF101722);
        g.drawCenteredString(font, "工作方块库存槽位", width / 2, 16, 0xFFFFFFFF);
        g.drawCenteredString(font, available
                        ? "完整存储可用 · 蓝框输入 / 绿框输出"
                        : "该方块当前没有可读取的 ItemHandler 库存接口。",
                width / 2, 30, available ? 0xFFD0D9E5 : 0xFFFF9A8B);

        g.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, 0xFF192638);
        g.renderOutline(panelX, panelY, PANEL_WIDTH, panelHeight, 0xFF58718F);
        g.drawString(font, "第 " + (page + 1) + " / " + pageCount() + " 页",
                panelX + 8, panelY + 4, 0xFFBFCBDD, false);

        super.render(g, mouseX, mouseY, partialTick);
        for (int i = page * pageCapacity; i < Math.min(slots.size(), (page + 1) * pageCapacity); i++) {
            CompoundTag slot = findSlot(i);
            int index = slot.getInt("Index");
            int x = panelX + visibleSlotX(index);
            int y = panelY + visibleSlotY(index);
            boolean selected = multiSelectMode ? selectedSlots.contains(index) : index == selectedSlot;
            g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF080D14);
            g.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1,
                    selected ? 0xFF315C88 : 0xFF71849A);
            int roleBorder = slot.getBoolean("InputRole") ? 0xFF398BFF
                    : slot.getBoolean("OutputRole") ? 0xFF39C77A : 0xFFE0E8F0;
            g.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, selected ? 0xFFFFFFFF : roleBorder);
            ItemStack stack = slotStack(slot);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 1, y + 1);
                g.renderItemDecorations(font, stack, x + 1, y + 1);
            }
            String slotNumber = Integer.toString(index + 1);
            int numberWidth = font.width(slotNumber);
            int numberX = x + 2;
            int numberY = y + 1;
            g.fill(numberX - 1, numberY - 1, numberX + numberWidth + 1, numberY + 8, 0xB9000000);
            g.drawString(font, slotNumber, numberX, numberY, 0xFFEAF0F7, false);
        }

        String selectionText = multiSelectMode
                ? "多选模式 · 已选择 " + selectedSlots.size() + " 格（Shift+拖动可连续选择）"
                : selectedSlot >= 0 ? "当前选择：" + slotRole(selectedSlot) : "点击格子选择；Shift+拖动可快速多选";
        int selectionY = Math.min(height - 62, panelY + panelHeight + 2);
        g.drawCenteredString(font, selectionText, width / 2, selectionY, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = slotAt(mouseX, mouseY);
            if (index >= 0) {
                if (hasShiftDown()) {
                    multiSelectMode = true;
                    dragSelecting = true;
                    dragVisitedSlots.clear();
                    dragVisitedSlots.add(index);
                    toggleSlotSelection(index);
                } else {
                    dragSelecting = false;
                    toggleSlotSelection(index);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragSelecting) {
            int index = slotAt(mouseX, mouseY);
            if (index >= 0 && dragVisitedSlots.add(index)) {
                toggleSlotSelection(index);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragSelecting = false;
            dragVisitedSlots.clear();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private CompoundTag findSlot(int index) {
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag slot = slots.getCompound(i);
            if (slot.getInt("Index") == index) return slot;
        }
        return new CompoundTag();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
