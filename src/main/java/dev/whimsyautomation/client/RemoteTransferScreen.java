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
    private static final int BG = 0xF20B111D;
    private static final int PANEL = 0xE6101B2B;
    private static final int ROW = 0xB5223046;
    private static final int ACCENT = 0xFF398BFF;
    private static final int TEXT = 0xFFE6EDF7;
    private static final int MUTED = 0xFF91A2B8;
    private static final int BORDER = 0xFF344C6D;

    private CompoundTag snapshot = new CompoundTag();
    private UUID selectedNetwork, selectedMember, selectedSecond, selectedConnection;
    private int activePage;
    private int networkScroll, memberScroll, connectionScroll;
    private EditBox createNameBox, renameBox, inputFilterBox, outputFilterBox;
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
        sideW = Math.max(78, Math.min(112, width / 4));
        contentX = sideW + 20;
        contentW = Math.max(90, width - contentX - 12);
        listTop = 78;
        rowH = Math.max(30, Math.min(38, (height - 112) / 5));

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
        addRenderableWidget(Button.builder(Component.literal("添加方块 / 容器"), b ->
                send(4, selectedNetwork, null, null, 0, ""))
                .bounds(contentX + 8, 178, Math.min(130, contentW - 16), 20).build());
        addRenderableWidget(Button.builder(Component.literal("打开成员管理 ›"), b -> {
            activePage = 1; init();
        }).bounds(contentX + 8, 208, Math.min(130, contentW - 16), 20).build());
    }

    private void buildMemberWidgets() {
        addRenderableWidget(Button.builder(Component.literal("＋ 进入添加目标模式（Shift+右键）"), b ->
                send(4, selectedNetwork, null, null, 0, ""))
                .bounds(contentX + 6, 62, Math.min(contentW - 12, 208), 20).build());
        int visible = visibleMemberRows();
        for (int row = 0; row < visible && row + memberScroll < members.size(); row++) {
            CompoundTag member = members.get(row + memberScroll);
            UUID id = member.getUUID("Id");
            int y = listTop + row * rowH;
            int actionW = 44;
            int teleportW = 48;
            int selectW = Math.max(32, contentW - actionW - teleportW - 22);
            addRenderableWidget(Button.builder(Component.literal(id.equals(selectedMember) ? "已选中" : "选择"), b -> {
                selectedMember = id;
                init();
            }).bounds(contentX + selectW + 10, y + 5, teleportW, 20).build());
            boolean canTeleport = minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
            addRenderableWidget(Button.builder(Component.literal("传送"), b ->
                    send(11, selectedNetwork, id, null, 0, ""))
                    .bounds(contentX + selectW + teleportW + 12, y + 5, actionW, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                            canTeleport ? "传送到该方块附近（需要安全落脚位置）" : "需要开启作弊或管理员权限")))
                    .build());
            addRenderableWidget(Button.builder(Component.literal("移除"), b ->
                    send(5, selectedNetwork, id, null, 0, ""))
                    .bounds(contentX + 6, y + 5, Math.min(42, selectW - 4), 20).build());
        }
    }

    private void buildConnectionWidgets() {
        int half = Math.max(40, (contentW - 14) / 2);
        int y = 66;
        addRenderableWidget(Button.builder(Component.literal("连接方向：" +
                new String[]{"输入", "输出", "双向"}[connectionDirection]), b -> {
            connectionDirection = (connectionDirection + 1) % 3;
            b.setMessage(Component.literal("连接方向：" + new String[]{"输入", "输出", "双向"}[connectionDirection]));
        }).bounds(contentX + 6, y, Math.min(110, half), 20).build());
        addRenderableWidget(Button.builder(Component.literal("建立所选连接"), b -> {
            if (selectedMember != null && selectedSecond != null && !selectedMember.equals(selectedSecond))
                send(6, selectedNetwork, selectedMember, selectedSecond, connectionDirection, "");
        }).bounds(contentX + 122, y, Math.max(64, Math.min(110, contentW - 128)), 20).build());

        int memberY = 96;
        int memberRows = Math.max(1, Math.min(4, (height - 160) / 22));
        for (int i = memberScroll; i < Math.min(members.size(), memberScroll + memberRows); i++) {
            CompoundTag m = members.get(i);
            UUID id = m.getUUID("Id");
            int yRow = memberY + (i - memberScroll) * 22;
            String prefix = id.equals(selectedMember) ? "A✓ " : id.equals(selectedSecond) ? "B✓ " : "";
            addRenderableWidget(Button.builder(Component.literal(prefix + memberLabel(m)), b -> {
                if (id.equals(selectedMember)) selectedMember = null;
                else if (id.equals(selectedSecond)) selectedSecond = null;
                else if (selectedMember == null) selectedMember = id;
                else if (selectedSecond == null) selectedSecond = id;
                else { selectedMember = selectedSecond; selectedSecond = id; }
                init();
            }).bounds(contentX + 6, yRow, half, 20).build());
        }

        int linkX = contentX + half + 12;
        int linkRows = Math.max(1, Math.min(5, (height - 126) / 22));
        for (int i = connectionScroll; i < Math.min(links.size(), connectionScroll + linkRows); i++) {
            CompoundTag link = links.get(i);
            UUID id = link.getUUID("Id");
            int yRow = memberY + (i - connectionScroll) * 22;
            addRenderableWidget(Button.builder(Component.literal(
                    (id.equals(selectedConnection) ? "▸ " : "") + connectionLabel(link)), b -> {
                selectedConnection = id;
                loadSelectedFilter(link);
                init();
            }).bounds(linkX, yRow, Math.max(36, contentW - half - 18), 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("移除所选连接"), b -> {
            if (selectedConnection != null) send(7, selectedNetwork, selectedConnection, null, 0, "");
        }).bounds(linkX, height - 30, Math.max(36, contentW - half - 18), 20).build());
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
        int labelW = Math.max(42, (contentW - 18) / 2);
        int inputX = contentX + 6;
        int outputX = inputX + labelW + gap;
        int fieldY = Math.min(height - 88, Math.max(y + 8, 122));
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
        inputFilterBox = new EditBox(font, inputX, fieldY + 24, labelW, 20, Component.literal("输入物品 ID"));
        inputFilterBox.setMaxLength(256);
        addRenderableWidget(inputFilterBox);
        outputFilterBox = new EditBox(font, outputX, fieldY + 24, labelW, 20, Component.literal("输出物品 ID"));
        outputFilterBox.setMaxLength(256);
        addRenderableWidget(outputFilterBox);
        addRenderableWidget(Button.builder(Component.literal("应用输入规则"), b ->
                send(9, selectedNetwork, selectedConnection, null,
                        (filterModeInput << 1) | (exactInput ? 8 : 0), inputFilterBox.getValue()))
                .bounds(inputX, fieldY + 48, labelW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("应用输出规则"), b ->
                send(9, selectedNetwork, selectedConnection, null,
                        1 | (filterModeOutput << 1) | (exactOutput ? 8 : 0), outputFilterBox.getValue()))
                .bounds(outputX, fieldY + 48, labelW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("手持物品加入输入精确规则"), b ->
                send(10, selectedNetwork, selectedConnection, null, 0, ""))
                .bounds(inputX, fieldY + 72, labelW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("手持物品加入输出精确规则"), b ->
                send(10, selectedNetwork, selectedConnection, null, 1, ""))
                .bounds(outputX, fieldY + 72, labelW, 20).build());
    }

    private void loadSelectedFilter(CompoundTag link) {
        filterModeInput = Math.max(0, java.util.Arrays.asList("NONE", "WHITELIST", "BLACKLIST").indexOf(link.getString("InputMode")));
        filterModeOutput = Math.max(0, java.util.Arrays.asList("NONE", "WHITELIST", "BLACKLIST").indexOf(link.getString("OutputMode")));
        exactInput = link.getBoolean("InputExact");
        exactOutput = link.getBoolean("OutputExact");
    }

    private static String filterLabel(String side, int mode, boolean exact) {
        return side + "：" + new String[]{"关闭", "白名单", "黑名单"}[Math.max(0, Math.min(2, mode))]
                + (exact ? " · 精确组件" : " · 普通");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, BG);
        g.fill(6, 6, sideW + 14, height - 6, PANEL);
        g.fill(contentX - 4, 28, width - 6, height - 6, 0xE6101724);
        g.renderOutline(6, 6, sideW + 8, height - 12, BORDER);
        g.renderOutline(contentX - 4, 28, width - contentX + 2, height - 34, BORDER);
        g.fill(6, 6, 9, height - 6, ACCENT);
        g.drawString(font, "远程网络", 14, 14, 0xFF70AEFF, false);
        g.drawString(font, "网络列表", 12, 30, MUTED, false);
        g.drawString(font, selectedNetwork == null ? "请选择或创建网络" : networkName(), contentX + 4, 14, TEXT, false);
        g.drawString(font, new String[]{"网络概览", "成员方块", "连接管理", "过滤规则"}[activePage],
                contentX + 4, 62, 0xFF70AEFF, false);

        if (activePage == 0 && selectedNetwork != null) {
            g.fill(contentX + 6, 78, contentX + contentW - 6, 104, ROW);
            g.drawString(font, "网络成员：" + members.size() + " 个", contentX + 14, 86, TEXT, false);
            g.drawString(font, "连接规则：" + links.size() + " 条", contentX + 14, 96, MUTED, false);
            g.drawString(font, "选择成员管理可以查看方块名称、坐标、图标和传送入口。", contentX + 8, 246 < height ? 246 : height - 18, MUTED, false);
        } else if (activePage == 1) {
            drawMembers(g, mouseX, mouseY);
        } else if (activePage == 2) {
            g.drawString(font, "左侧选择两个成员作为 A / B，右侧选择并管理已有连接。", contentX + 6, 52, MUTED, false);
            g.drawString(font, "A：" + memberName(selectedMember) + "   B：" + memberName(selectedSecond),
                    contentX + 6, height - 48, TEXT, false);
            g.drawString(font, "已有连接", contentX + Math.max(44, (contentW - 14) / 2) + 12, 82, MUTED, false);
        } else if (activePage == 3) {
            g.drawString(font, "先选择一条连接，再分别配置输入端与输出端。", contentX + 6, 52, MUTED, false);
            g.drawString(font, "规则格式：minecraft:stone,minecraft:dirt", contentX + 6, height - 16, MUTED, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawMembers(GuiGraphics g, int mouseX, int mouseY) {
        int visible = visibleMemberRows();
        for (int row = 0; row < visible && row + memberScroll < members.size(); row++) {
            CompoundTag member = members.get(row + memberScroll);
            int y = listTop + row * rowH;
            int rowBottom = Math.min(height - 8, y + rowH - 2);
            g.fill(contentX + 4, y, contentX + contentW - 4, rowBottom, ROW);
            UUID id = member.getUUID("Id");
            ItemStack stack = memberStack(member);
            g.renderItem(stack, contentX + 9, y + 5);
            g.drawString(font, trim(memberLabel(member), 28), contentX + 31, y + 5,
                    id.equals(selectedMember) ? 0xFF70AEFF : TEXT, false);
            g.drawString(font, member.getString("Dimension") + "  " +
                    BlockPos.of(member.getLong("Pos")).toShortString(), contentX + 31, y + 17, MUTED, false);
            int color = COLORS[row % COLORS.length];
            g.fill(contentX + contentW - 7, y + 2, contentX + contentW - 4, rowBottom - 2, color);
        }
        if (members.isEmpty()) g.drawString(font, "当前网络还没有成员。点击上方按钮后，Shift+右键选择方块或容器。",
                contentX + 8, listTop + 8, MUTED, false);
    }

    private static final int[] COLORS = {0xFFFF5555, 0xFFFFAA33, 0xFFFFFF55, 0xFF55FF77,
            0xFF55DDFF, 0xFF6688FF, 0xFFCC66FF, 0xFFFF66CC};

    private ItemStack memberStack(CompoundTag member) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = BlockPos.of(member.getLong("Pos"));
        if (mc.level != null && mc.level.dimension().location().toString().equals(member.getString("Dimension"))
                && mc.level.hasChunkAt(pos)) {
            ItemStack stack = new ItemStack(mc.level.getBlockState(pos).getBlock());
            if (!stack.isEmpty()) return stack;
        }
        return new ItemStack(member.getString("Kind").equals("WORKSTATION") ? Items.CRAFTING_TABLE : Items.CHEST);
    }

    private String memberLabel(CompoundTag member) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = BlockPos.of(member.getLong("Pos"));
        if (mc.level != null && mc.level.dimension().location().toString().equals(member.getString("Dimension"))
                && mc.level.hasChunkAt(pos)) {
            return mc.level.getBlockState(pos).getBlock().getName().getString();
        }
        return member.getString("Kind").equals("WORKSTATION") ? "工作方块（未加载）" : "容器（未加载）";
    }

    private String connectionLabel(CompoundTag link) {
        return shortId(link.getUUID("Source")) + " → " + shortId(link.getUUID("Target"))
                + " · " + link.getString("Direction");
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
            connectionScroll = Math.max(0, Math.min(Math.max(0, links.size() - 5), connectionScroll + delta));
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
