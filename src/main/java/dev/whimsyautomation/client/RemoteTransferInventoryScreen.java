package dev.whimsyautomation.client;

import dev.whimsyautomation.network.RemoteTransferActionPayload;
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
    private int selectedSlot = -1;
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
        this.slots = inventory.getList("Slots", net.minecraft.nbt.Tag.TAG_COMPOUND).copy();
    }

    @Override
    protected void init() {
        int rows = Math.max(1, (slots.size() + 8) / 9);
        int maxGuiX = 0, maxGuiY = 0;
        if (menuLayout) {
            for (int i = 0; i < slots.size(); i++) {
                CompoundTag slot = slots.getCompound(i);
                maxGuiX = Math.max(maxGuiX, slot.getInt("GuiX") + SLOT_SIZE);
                maxGuiY = Math.max(maxGuiY, slot.getInt("GuiY") + SLOT_SIZE);
            }
        }
        panelWidth = menuLayout ? Math.max(176, maxGuiX + 8) : 176;
        panelHeight = menuLayout ? Math.max(166, maxGuiY + 8)
                : furnaceLayout ? 166 : Math.max(92, 52 + rows * SLOT_SIZE);
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(42, (height - panelHeight) / 2 - 12);

        int buttonY = height - 28;
        int gap = 4;
        int buttonW = Math.max(54, Math.min(112, (width - 24 - gap * 2) / 3));
        int totalW = buttonW * 3 + gap * 2;
        int startX = (width - totalW) / 2;
        addRenderableWidget(Button.builder(Component.literal("返回成员列表"),
                b -> minecraft.setScreen(parent))
                .bounds(startX, buttonY, buttonW, 20).build());
        Button input = Button.builder(Component.literal("绑定为输入"),
                b -> beginBinding("INPUT"))
                .bounds(startX + buttonW + gap, buttonY, buttonW, 20).build();
        input.active = selectedSlot >= 0 && available;
        addRenderableWidget(input);
        Button output = Button.builder(Component.literal("绑定为输出"),
                b -> beginBinding("OUTPUT"))
                .bounds(startX + (buttonW + gap) * 2, buttonY, buttonW, 20).build();
        output.active = selectedSlot >= 0 && available;
        addRenderableWidget(output);
    }

    private void beginBinding(String direction) {
        if (selectedSlot < 0 || !available || networkId == null || memberId == null) return;
        PacketDistributor.sendToServer(new RemoteTransferActionPayload(
                16, networkId, memberId, null, selectedSlot, direction));
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
        if (menuLayout) {
            return findSlot(index).getInt("GuiX");
        }
        if (furnaceLayout) {
            return switch (index) {
                case 0, 1 -> 56;
                case 2 -> 116;
                default -> -1000;
            };
        }
        return 7 + (index % 9) * SLOT_SIZE;
    }

    private int slotY(int index) {
        if (menuLayout) {
            return findSlot(index).getInt("GuiY");
        }
        if (furnaceLayout) {
            return switch (index) {
                case 0 -> 17;
                case 1 -> 53;
                case 2 -> 35;
                default -> -1000;
            };
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
        if (!furnaceLayout) return "槽位 " + index;
        return switch (index) {
            case 0 -> "原料";
            case 1 -> "燃料";
            case 2 -> "成品";
            default -> "槽位 " + index;
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Use a fully opaque surface. Calling renderBackground() here would
        // reintroduce Minecraft's world blur behind this custom inventory view.
        g.fill(0, 0, width, height, 0xFF101722);
        g.drawCenteredString(font, "工作方块库存槽位", width / 2, 16, 0xFFFFFFFF);
        g.drawCenteredString(font, available
                ? (menuLayout
                    ? "槽位位置取自该方块原生菜单；编号对应真实库存槽位。"
                    : furnaceLayout
                        ? "原料、燃料、成品按熔炉原生槽位位置显示。"
                        : "通用库存视图：编号对应服务端 ItemHandler 的真实槽位索引。")
                : "该方块当前没有可读取的 ItemHandler 库存接口。",
                width / 2, 30, available ? 0xFFD0D9E5 : 0xFFFF9A8B);

        g.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF192638);
        g.renderOutline(panelX, panelY, panelWidth, panelHeight, 0xFF58718F);

        if (furnaceLayout) {
            g.drawString(font, "原料", panelX + 24, panelY + 22, 0xFFBFCBDD, false);
            g.drawString(font, "燃料", panelX + 24, panelY + 58, 0xFFBFCBDD, false);
            g.drawString(font, "成品", panelX + 137, panelY + 40, 0xFFBFCBDD, false);
        }

        for (int i = 0; i < slots.size(); i++) {
            CompoundTag slot = slots.getCompound(i);
            int index = slot.getInt("Index");
            int x = panelX + slotX(index);
            int y = panelY + slotY(index);
            if (x < panelX || x + SLOT_SIZE > panelX + panelWidth) continue;
            boolean selected = index == selectedSlot;
            g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE,
                    selected ? 0xFF315C88 : 0xFF26384D);
            g.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE,
                    selected ? 0xFF8FC5FF : 0xFF7890AA);
            ItemStack stack = slotStack(slot);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 1, y + 1);
                g.renderItemDecorations(font, stack, x + 1, y + 1);
            }
            g.drawCenteredString(font, Integer.toString(index), x + SLOT_SIZE / 2,
                    y + SLOT_SIZE + 2, 0xFFBFCBDD);
        }

        super.render(g, mouseX, mouseY, partialTick);
        if (selectedSlot >= 0) {
            g.drawCenteredString(font, "当前选择：" + slotRole(selectedSlot)
                    + "（槽位 " + selectedSlot + "）", width / 2, height - 42, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < slots.size(); i++) {
                CompoundTag slot = slots.getCompound(i);
                int index = slot.getInt("Index");
                int x = panelX + slotX(index);
                int y = panelY + slotY(index);
                if (mouseX >= x && mouseX < x + SLOT_SIZE
                        && mouseY >= y && mouseY < y + SLOT_SIZE) {
                    selectedSlot = index;
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
