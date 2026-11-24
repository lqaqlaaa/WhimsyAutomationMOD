package dev.whimsyautomation.client;

import dev.whimsyautomation.network.RemoteTransferActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Responsive, paged remote-network workspace. The left rail selects a network;
 * the right workspace separates overview, endpoints, connections and filters.
 */
public final class RemoteTransferScreen extends Screen {
    // Opaque surfaces are intentional: translucent panels let the world's blur show
    // through and make manually rendered labels/items look washed out on mobile.
    private static final int BG = 0xFF101722;
    private static final int PANEL = 0xFF192638;
    private static final int ROW = 0xFF26384D;
    private static final int ROW_SELECTED = 0xFF304E70;
    private static final int ACCENT = 0xFF398BFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFD0D9E5;
    private static final int BORDER = 0xFF58718F;

    private CompoundTag snapshot = new CompoundTag();
    private UUID selectedNetwork, selectedMember, selectedSecond, selectedConnection;
    private int activePage;
    private int networkScroll, memberScroll, connectionScroll;
    private EditBox createNameBox, renameBox, inputFilterBox, outputFilterBox;
    private String inputFilterText, outputFilterText;
    private int connectionDirection;
    private int filterModeInput, filterModeOutput;
    private boolean exactInput, exactOutput;
    private List<CompoundTag> networks = List.of(), members = List.of(), links = List.of();
    private int sideW, contentX, contentW, listTop, rowH;

    public RemoteTransferScreen() { super(Component.literal("远程传输网络")); }
    public RemoteTransferScreen(CompoundTag data) { this(); applySnapshot(data); }

    public RemoteTransferScreen applySnapshot(CompoundTag data) {
        snapshot = data == null ? new CompoundTag() : data.copy();
        rebuildData();
        if (minecraft != null && minecraft.screen == this) init();
        return this;
    }

    private void rebuildData() {
        networks = compounds(snapshot.getList("Networks", Tag.TAG_COMPOUND));
        if (selectedNetwork == null && !networks.isEmpty()) selectedNetwork = networks.get(0).getUUID("Id");
        CompoundTag selected = findNetwork();
        members = selected == null ? List.of() : compounds(selected.getList("Members", Tag.TAG_COMPOUND));
        links = selected == null ? List.of() : compounds(selected.getList("Connections", Tag.TAG_COMPOUND));
        if (selectedNetwork != null && selected == null) selectedNetwork = null;
        if (selectedMember != null && members.stream().noneMatch(m -> m.getUUID("Id").equals(selectedMember))) selectedMember = null;
        if (selectedSecond != null && members.stream().noneMatch(m -> m.getUUID("Id").equals(selectedSecond))) selectedSecond = null;
        if (selectedConnection != null && links.stream().noneMatch(c -> c.getUUID("Id").equals(selectedConnection))) selectedConnection = null;
        RemoteTransferOverlay.selectNetwork(selectedNetwork);
    }

    private CompoundTag findNetwork() {
        for (CompoundTag n : networks) if (n.hasUUID("Id") && n.getUUID("Id").equals(selectedNetwork)) return n;
        return null;
    }

    private static List<CompoundTag> compounds(ListTag list) {
        List<CompoundTag> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) out.add(list.getCompound(i).copy());
        return out;
    }

    private void send(int action, UUID network, UUID a, UUID b, int value, String text) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new RemoteTransferActionPayload(action, network, a, b, value, text));
    }

    @Override
    protected void init() {
        clearWidgets();
        int margin = RemoteTransferGuiLayout.fit(8, width, height, 4);
        sideW = Math.max(96, Math.min(136,
                Math.max(width / 5, RemoteTransferGuiLayout.fit(108, width, height, 84))));
        contentX = sideW + margin * 2;
        contentW = Math.max(90, width - contentX - margin);
        listTop = Math.max(84, Math.min(104,
                RemoteTransferGuiLayout.fit(64, width, height, 52)));
        int availableListHeight = Math.max(1, height - listTop - 12);
        int desiredRowHeight = Math.max(30, Math.min(48,
                RemoteTransferGuiLayout.fit(36, width, height, 28)));
        int visibleRows = RemoteTransferGuiLayout.maxRows(
                availableListHeight, desiredRowHeight, 28);
        rowH = RemoteTransferGuiLayout.actualRowH(
                availableListHeight, visibleRows, desiredRowHeight, 28);

        // Persistent network rail.
        int navTop = 42;
        int navRowH = 22;
        int visibleNetworks = Math.max(1, (height - 112) / navRowH);
        for (int i = networkScroll; i < Math.min(networks.size(), networkScroll + visibleNetworks); i++) {
            CompoundTag n = networks.get(i);
            UUID id = n.getUUID("Id");
            int y = navTop + (i - networkScroll) * navRowH;
            String label = trim(n.getString("Name"), 14);
            addRenderableWidget(Button.builder(Component.literal((id.equals(selectedNetwork) ? "▸ " : "  ") + label), b -> {
                selectedNetwork = id;
                selectedMember = selectedSecond = selectedConnection = null;
                memberScroll = connectionScroll = 0;
                rebuildData();
                init();
            }).bounds(8, y, sideW, 20).build());
        }

        createNameBox = new EditBox(font, 8, height - 48, sideW, 18, Component.literal("网络名称"));
        createNameBox.setMaxLength(48);
        createNameBox.setHint(Component.literal("新网络名称"));
        addRenderableWidget(createNameBox);
        addRenderableWidget(Button.builder(Component.literal("+ 创建网络"), b -> {
            if (!createNameBox.getValue().isBlank()) send(1, null, null, null, 0, createNameBox.getValue());
        }).bounds(8, height - 26, sideW, 20).build());

        // Main navigation tabs.
        String[] tabs = {"网络概览", "成员方块", "连接管理", "过滤规则"};
        int tabGap = 3;
        int tabW = Math.max(42, (contentW - tabGap * 3) / 4);
        for (int i = 0; i < tabs.length; i++) {
            final int page = i;
            addRenderableWidget(Button.builder(Component.literal((activePage == i ? "◆ " : "") + tabs[i]), b -> {
                activePage = page;
                init();
            }).bounds(contentX + i * (tabW + tabGap), 38, tabW, 20).build());
        }

        if (selectedNetwork == null) return;
        if (activePage == 0) buildOverviewWidgets();
        else if (activePage == 1) buildMemberWidgets();
        else if (activePage == 2) buildConnectionWidgets();
        else buildFilterWidgets();
    }

    private void buildOverviewWidgets() {
        CompoundTag network = findNetwork();
        if (network == null) return;
        renameBox = new EditBox(font, contentX + 8, 112, Math.max(70, contentW - 112), 20, Component.literal("网络名称"));
        renameBox.setMaxLength(48);
        renameBox.setValue(network.getString("Name"));
        addRenderableWidget(renameBox);
        addRenderableWidget(Button.builder(Component.literal("重命名"), b ->
                send(2, selectedNetwork, null, null, 0, renameBox.getValue()))
                .bounds(contentX + contentW - 96, 112, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal("删除当前网络"), b ->
                send(3, selectedNetwork, null, null, 0, ""))
                .bounds(contentX + 8, 148, Math.min(110, contentW - 16), 20).build());
        int bindW = Math.max(44, (contentW - 22) / 2);
        addRenderableWidget(Button.builder(Component.literal("绑定输入"), b ->
                send(4, selectedNetwork, null, null, 0, ""))
                .bounds(contentX + 8, 178, bindW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("绑定输出"), b ->
                send(4, selectedNetwork, null, null, 1, ""))
                .bounds(contentX + 14 + bindW, 178, bindW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("打开成员管理 ›"), b -> {
            activePage = 1; init();
        }).bounds(contentX + 8, 208, Math.min(130, contentW - 16), 20).build());
        addRenderableWidget(Button.builder(Component.literal(RemoteTransferOverlay.connectionsVisible()
                ? "关闭连接线显示" : "开启连接线显示"), b -> {
            RemoteTransferOverlay.toggleConnectionsVisible();
            b.setMessage(Component.literal(RemoteTransferOverlay.connectionsVisible()
                    ? "关闭连接线显示" : "开启连接线显示"));
        }).bounds(contentX + 8, Math.min(height - 28, 238), Math.min(130, contentW - 16), 20).build());
    }

    private void buildMemberWidgets() {
        int third = Math.max(28, (contentW - 24) / 3);
        addRenderableWidget(Button.builder(Component.literal("绑定输入"), b ->
                send(4, selectedNetwork, null, null, 0, ""))
                .bounds(contentX + 6, 62, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("绑定输出"), b ->
                send(4, selectedNetwork, null, null, 1, ""))
                .bounds(contentX + 12 + third, 62, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("移除成员"), b -> {
            if (selectedMember != null) send(5, selectedNetwork, selectedMember, null, 0, "");
        }).bounds(contentX + 18 + third * 2, 62, Math.max(28, contentW - third * 2 - 24), 20).build());

        int visible = visibleMemberRows();
        for (int row = 0; row < visible && row + memberScroll < members.size(); row++) {
            CompoundTag member = members.get(row + memberScroll);
            UUID id = member.getUUID("Id");
            int y = listTop + row * rowH;
            int actionW = 48;
            boolean canTeleport = minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
            Button teleportButton = Button.builder(Component.literal("传送"), b ->
                    send(11, selectedNetwork, id, null, 0, ""))
                    .bounds(contentX + contentW - actionW - 6, y + 5, actionW, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                            canTeleport ? "传送到该方块附近（需要安全落脚位置）" : "需要开启作弊或管理员权限")))
                    .build();
            teleportButton.active = canTeleport;
            addRenderableWidget(teleportButton);
        }
    }

    private void buildConnectionWidgets() {
        int visible = Math.max(1, Math.min(7, (height - 132) / 24));
        for (int row = 0; row < visible && row + connectionScroll < links.size(); row++) {
            CompoundTag link = links.get(row + connectionScroll);
            UUID id = link.getUUID("Id");
            int y = 70 + row * 24;
            String state = link.getBoolean("Enabled") ? "运行中" : "已关闭";
            addRenderableWidget(Button.builder(Component.literal(
                    (id.equals(selectedConnection) ? "▸ " : "") + connectionLabel(link) + " · " + state), b -> {
                selectedConnection = id;
                loadSelectedFilter(link);
                init();
            }).bounds(contentX + 6, y, Math.max(48, contentW - 12), 20).build());
        }
        if (selectedConnection == null) return;
        CompoundTag selected = findSelectedLink();
        if (selected == null) return;
        int actionY = Math.max(76, Math.min(height - 56, 76 + visible * 24));
        int half = Math.max(40, (contentW - 18) / 2);
        addRenderableWidget(Button.builder(Component.literal(
                selected.getBoolean("Enabled") ? "关闭此连接" : "开启此连接"), b ->
                send(12, selectedNetwork, selectedConnection, null, 0, ""))
                .bounds(contentX + 6, actionY, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("移除此连接"), b ->
                send(7, selectedNetwork, selectedConnection, null, 0, ""))
                .bounds(contentX + 12 + half, actionY, Math.max(40, contentW - half - 18), 20).build());
    }

    private void buildFilterWidgets() {
        int y = 66;
        int connectionW = Math.max(60, Math.min(contentW - 12, 250));
        for (int i = 0; i < Math.min(links.size(), Math.max(1, (height - 168) / 22)); i++) {
            CompoundTag link = links.get(i);
            UUID id = link.getUUID("Id");
            addRenderableWidget(Button.builder(Component.literal(
                    (id.equals(selectedConnection) ? "▸ " : "") + connectionLabel(link)), b -> {
                selectedConnection = id;
                loadSelectedFilter(link);
                init();
            }).bounds(contentX + 6, y, connectionW, 20).build());
            y += 22;
        }
        if (selectedConnection == null) return;
        int gap = 6;
        int columns = RemoteTransferGuiLayout.cols(contentW - 18, 92);
        int labelW = columns == 2 ? (contentW - 18) / 2 : contentW - 18;
        int inputX = contentX + 6;
        int outputX = inputX + labelW + gap;
        int fieldY = Math.max(82, Math.min(height - 118, Math.max(y + 8, 100)));
        addRenderableWidget(Button.builder(Component.literal(filterLabel("输入", filterModeInput, exactInput)), b -> {
            filterModeInput = (filterModeInput + 1) % 3;
            if (filterModeInput == 0) exactInput = !exactInput;
            b.setMessage(Component.literal(filterLabel("输入", filterModeInput, exactInput)));
        }).bounds(inputX, fieldY, labelW, 20).build());
        addRenderableWidget(Button.builder(Component.literal(filterLabel("输出", filterModeOutput, exactOutput)), b -> {
            filterModeOutput = (filterModeOutput + 1) % 3;
            if (filterModeOutput == 0) exactOutput = !exactOutput;
            b.setMessage(Component.literal(filterLabel("输出", filterModeOutput, exactOutput)));
        }).bounds(outputX, fieldY, labelW, 20).build());
        CompoundTag selectedLink = findSelectedLink();
        if (selectedLink != null) {
            if (inputFilterText == null) inputFilterText = selectedLink.getString("InputItems");
            if (outputFilterText == null) outputFilterText = selectedLink.getString("OutputItems");
        }
        inputFilterBox = new EditBox(font, inputX, fieldY + 24, labelW, 20, Component.literal("输入物品 ID"));
        inputFilterBox.setMaxLength(256);
        inputFilterBox.setValue(inputFilterText == null ? "" : inputFilterText);
        inputFilterBox.setResponder(value -> inputFilterText = value);
        addRenderableWidget(inputFilterBox);
        outputFilterBox = new EditBox(font, outputX, fieldY + 24, labelW, 20, Component.literal("输出物品 ID"));
        outputFilterBox.setMaxLength(256);
        outputFilterBox.setValue(outputFilterText == null ? "" : outputFilterText);
        outputFilterBox.setResponder(value -> outputFilterText = value);
        addRenderableWidget(outputFilterBox);
        addRenderableWidget(Button.builder(Component.literal("选择输入物品"), b ->
                minecraft.setScreen(new RemoteTransferItemPickerScreen(this, inputFilterBox.getValue(), value -> {
                    inputFilterText = value;
                    init();
                }))).bounds(inputX, fieldY + 48, labelW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("选择输出物品"), b ->
                minecraft.setScreen(new RemoteTransferItemPickerScreen(this, outputFilterBox.getValue(), value -> {
                    outputFilterText = value;
                    init();
                }))).bounds(outputX, fieldY + 48, labelW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("应用输入规则"), b ->
                send(9, selectedNetwork, selectedConnection, null,
                        (filterModeInput << 1) | (exactInput ? 8 : 0), inputFilterBox.getValue()))
                .bounds(inputX, fieldY + 72, labelW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("应用输出规则"), b ->
                send(9, selectedNetwork, selectedConnection, null,
                        1 | (filterModeOutput << 1) | (exactOutput ? 8 : 0), outputFilterBox.getValue()))
                .bounds(outputX, fieldY + 72, labelW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("手持物品加入输入精确规则"), b ->
                send(10, selectedNetwork, selectedConnection, null, 0, ""))
                .bounds(inputX, fieldY + 96, labelW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("手持物品加入输出精确规则"), b ->
                send(10, selectedNetwork, selectedConnection, null, 1, ""))
                .bounds(outputX, fieldY + 96, labelW, 20).build());
    }

    private void loadSelectedFilter(CompoundTag link) {
        filterModeInput = Math.max(0, java.util.Arrays.asList("NONE", "WHITELIST", "BLACKLIST").indexOf(link.getString("InputMode")));
        filterModeOutput = Math.max(0, java.util.Arrays.asList("NONE", "WHITELIST", "BLACKLIST").indexOf(link.getString("OutputMode")));
        exactInput = link.getBoolean("InputExact");
        exactOutput = link.getBoolean("OutputExact");
        inputFilterText = link.getString("InputItems");
        outputFilterText = link.getString("OutputItems");
    }

    private static String filterLabel(String side, int mode, boolean exact) {
        return side + "：" + new String[]{"关闭", "白名单", "黑名单"}[Math.max(0, Math.min(2, mode))]
                + (exact ? " · 精确组件" : " · 普通");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Match WardConfigScreen: render the world/background first, then opaque
        // screen surfaces, then widgets, and finally custom foreground content.
        renderBackground(g, mouseX, mouseY, partialTick);

        g.fill(0, 0, width, height, BG);
        g.fill(6, 6, sideW + 14, height - 6, PANEL);
        g.fill(contentX - 4, 28, width - 6, height - 6, 0xFF101724);
        g.renderOutline(6, 6, sideW + 8, height - 12, BORDER);
        g.renderOutline(contentX - 4, 28, width - contentX + 2, height - 34, BORDER);
        g.fill(6, 6, 9, height - 6, ACCENT);

        if (activePage == 1) drawMemberBackgrounds(g, mouseX, mouseY);

        // Native buttons and edit boxes must be rendered before custom labels/items.
        super.render(g, mouseX, mouseY, partialTick);

        g.drawString(font, "远程网络", 14, 14, 0xFF70AEFF, false);
        g.drawString(font, "网络列表", 12, 30, MUTED, false);
        g.drawString(font, selectedNetwork == null ? "请选择或创建网络" : networkName(),
                contentX + 4, 14, TEXT, false);
        g.drawString(font, new String[]{"网络概览", "成员方块", "连接管理", "过滤规则"}[activePage],
                contentX + 4, 62, 0xFF70AEFF, false);

        if (activePage == 0 && selectedNetwork != null) {
            g.fill(contentX + 6, 78, contentX + contentW - 6, 104, ROW);
            g.drawString(font, "网络成员：" + members.size() + " 个", contentX + 14, 86, TEXT, false);
            g.drawString(font, "连接规则：" + links.size() + " 条", contentX + 14, 96, MUTED, false);
            g.drawString(font, "选择成员管理可以查看方块名称、坐标、图标和传送入口。",
                    contentX + 8, 246 < height ? 246 : height - 18, MUTED, false);
        } else if (activePage == 1) {
            drawMemberForeground(g, mouseX, mouseY);
        } else if (activePage == 2) {
            g.drawString(font, "先选择输入或输出，再依次 Shift+右键工作方块和连接容器。",
                    contentX + 6, 52, MUTED, false);
            g.drawString(font, "A：" + memberName(selectedMember) + "   B：" + memberName(selectedSecond),
                    contentX + 6, height - 48, TEXT, false);
            g.drawString(font, "已有连接",
                    contentX + Math.max(44, (contentW - 14) / 2) + 12, 82, MUTED, false);
        } else if (activePage == 3) {
            g.drawString(font, "先选择一条连接，再分别配置输入端与输出端。",
                    contentX + 6, 52, MUTED, false);
            g.drawString(font, "规则格式：minecraft:stone,minecraft:dirt",
                    contentX + 6, height - 16, MUTED, false);
        }
    }

    private void drawMemberBackgrounds(GuiGraphics g, int mouseX, int mouseY) {
        int visible = visibleMemberRows();
        for (int row = 0; row < visible && row + memberScroll < members.size(); row++) {
            int y = listTop + row * rowH;
            int rowBottom = Math.min(height - 8, y + rowH - 2);
            UUID id = members.get(row + memberScroll).getUUID("Id");
            boolean selected = id.equals(selectedMember);
            g.fill(contentX + 4, y, contentX + contentW - 4, rowBottom,
                    selected ? ROW_SELECTED : ROW);
            g.renderOutline(contentX + 4, y, contentW - 8, rowBottom - y,
                    selected ? 0xFF8FC5FF : BORDER);
            g.fill(contentX + 5, y + 2, contentX + 8, rowBottom - 2,
                    COLORS[(row + memberScroll) % COLORS.length]);
            if (mouseX >= contentX + 4 && mouseX < contentX + contentW - 4
                    && mouseY >= y && mouseY < rowBottom) {
                g.renderOutline(contentX + 3, y - 1, contentW - 6,
                        rowBottom - y + 2, 0xFFFFFFFF);
            }
        }
    }

    private void drawMemberForeground(GuiGraphics g, int mouseX, int mouseY) {
        int visible = visibleMemberRows();
        for (int row = 0; row < visible && row + memberScroll < members.size(); row++) {
            CompoundTag member = members.get(row + memberScroll);
            int y = listTop + row * rowH;
            int rowBottom = Math.min(height - 8, y + rowH - 2);
            UUID id = member.getUUID("Id");
            boolean selected = id.equals(selectedMember);
            ItemStack stack = memberStack(member);
            g.renderItem(stack, contentX + 12, y + Math.max(3, (rowH - 16) / 2));

            BlockPos pos = BlockPos.of(member.getLong("Pos"));
            String blockName = memberLabel(member);
            String dimension = member.getString("Dimension");
            boolean loaded = minecraft != null && minecraft.level != null
                    && minecraft.level.dimension().location().toString().equals(dimension)
                    && minecraft.level.hasChunkAt(pos);

            int textX = contentX + 34;
            int textRight = contentX + contentW - 66;
            int maxChars = Math.max(8, (textRight - textX) / 6);
            g.drawString(font, trim(blockName, maxChars), textX, y + 4,
                    selected ? 0xFFFFFFFF : TEXT, false);
            g.drawString(font, dimension + "  " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(),
                    textX, y + 16, MUTED, false);
            if (rowH >= 36) {
                String state = loaded ? "已加载" : "未加载";
                g.drawString(font, state + " · ID " + shortId(id), textX, y + 27,
                        loaded ? 0xFF8FE0A0 : 0xFFFFC078, false);
            }
        }
        if (members.isEmpty()) {
            g.drawString(font, "当前网络没有成员。点击“绑定输入”或“绑定输出”，再依次 Shift+右键工作方块和容器。",
                    contentX + 8, listTop + 8, TEXT, false);
        }
    }

    private static final int[] COLORS = {0xFFFF5555, 0xFFFFAA33, 0xFFFFFF55, 0xFF55FF77,
            0xFF55DDFF, 0xFF6688FF, 0xFFCC66FF, 0xFFFF66CC};

    private ItemStack memberStack(CompoundTag member) {
        ResourceLocation id;
        try {
            id = ResourceLocation.parse(member.getString("Block"));
        } catch (RuntimeException exception) {
            id = ResourceLocation.withDefaultNamespace(
                    member.getString("Kind").equals("WORKSTATION") ? "crafting_table" : "chest");
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.CHEST);
        ItemStack stack = new ItemStack(block);
        return stack.isEmpty() ? new ItemStack(Items.BARRIER) : stack;
    }

    private String memberLabel(CompoundTag member) {
        ResourceLocation id;
        try {
            id = ResourceLocation.parse(member.getString("Block"));
        } catch (RuntimeException exception) {
            id = ResourceLocation.withDefaultNamespace(
                    member.getString("Kind").equals("WORKSTATION") ? "crafting_table" : "chest");
        }
        return BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.CHEST).getName().getString();
    }

    private CompoundTag findSelectedLink() {
        if (selectedConnection == null) return null;
        for (CompoundTag link : links) if (link.getUUID("Id").equals(selectedConnection)) return link;
        return null;
    }

    private String connectionLabel(CompoundTag link) {
        return trim(memberName(link.getUUID("Source")), 10) + " → "
                + trim(memberName(link.getUUID("Target")), 10) + " · " + link.getString("Direction");
    }

    private String memberName(UUID id) {
        if (id == null) return "未选择";
        for (CompoundTag member : members) if (member.getUUID("Id").equals(id)) return trim(memberLabel(member), 12);
        return "未知成员";
    }

    private static String shortId(UUID id) { return id.toString().substring(0, 8); }
    private String networkName() {
        CompoundTag n = findNetwork();
        return n == null ? "未选择网络" : n.getString("Name");
    }
    private int visibleMemberRows() { return Math.max(1, (height - listTop - 12) / rowH); }
    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && activePage == 1 && mouseX >= contentX + 4
                && mouseX < contentX + contentW - 58 && mouseY >= listTop && mouseY < height - 8) {
            int row = (int) ((mouseY - listTop) / rowH);
            int index = memberScroll + row;
            if (index >= 0 && index < members.size()) {
                selectedMember = members.get(index).getUUID("Id");
                init();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = scrollY < 0 ? 1 : scrollY > 0 ? -1 : 0;
        if (delta == 0) return false;
        if (mouseX < sideW + 14) {
            int visible = Math.max(1, (height - 112) / 22);
            networkScroll = Math.max(0, Math.min(Math.max(0, networks.size() - visible), networkScroll + delta));
            init();
            return true;
        }
        if (activePage == 1) {
            memberScroll = Math.max(0, Math.min(Math.max(0, members.size() - visibleMemberRows()), memberScroll + delta));
            init();
            return true;
        }
        if (activePage == 2) {
            if (contentW > 220 && mouseX < contentX + contentW / 2) {
                memberScroll = Math.max(0, Math.min(Math.max(0, members.size() - 4), memberScroll + delta));
            } else {
                connectionScroll = Math.max(0, Math.min(Math.max(0, links.size() - 5), connectionScroll + delta));
            }
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
