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
    private static final int CELL = 24;
    private final RemoteTransferScreen parent;
    private final UUID networkId;
    private final UUID memberId;
    private final ListTag slots;
    private final boolean available;
    private int selectedSlot = -1;
    private int columns, cell, gridX, gridY;

    public RemoteTransferInventoryScreen(RemoteTransferScreen parent, CompoundTag snapshot) {
        super(Component.literal("工作方块库存槽位"));
        this.parent = parent;
        this.networkId = parent.selectedNetworkId();
        CompoundTag inventory = snapshot.getCompound("Inventory");
        this.memberId = inventory.hasUUID("Member") ? inventory.getUUID("Member") : null;
        this.available = inventory.getBoolean("Available");
        this.slots = inventory.getList("Slots", net.minecraft.nbt.Tag.TAG_COMPOUND).copy();
    }

    @Override
    protected void init() {
        cell = Math.max(18, Math.min(CELL, (width - 24) / 9));
        columns = Math.max(1, Math.min(9, (width - 24) / cell));
        int rows = Math.max(1, (slots.size() + columns - 1) / columns);
        gridX = (width - columns * cell) / 2;
        gridY = Math.max(48, (height - rows * cell) / 2 - 8);

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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, 0xE9101722);
        g.drawCenteredString(font, "工作方块真实库存槽位", width / 2, 16, 0xFFFFFFFF);
        g.drawCenteredString(font, available
                ? "点击一个槽位，再选择输入或输出；随后 Shift+右键目标容器。"
                : "该方块当前没有可读取的 ItemHandler 库存接口。",
                width / 2, 30, available ? 0xFFD0D9E5 : 0xFFFF9A8B);

        for (int i = 0; i < slots.size(); i++) {
            CompoundTag slot = slots.getCompound(i);
            int x = gridX + (i % columns) * cell;
            int y = gridY + (i / columns) * cell;
            boolean selected = slot.getInt("Index") == selectedSlot;
            g.fill(x, y, x + cell - 2, y + cell - 2, selected ? 0xFF315C88 : 0xFF26384D);
            g.renderOutline(x, y, cell - 2, cell - 2, selected ? 0xFF8FC5FF : 0xFF58718F);
            ItemStack stack = slotStack(slot);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 3, y + 3);
                g.renderItemDecorations(font, stack, x + 3, y + 3);
            }
            g.drawString(font, Integer.toString(slot.getInt("Index")), x + 2, y + cell - 9, 0xFFBFCBDD, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
        if (selectedSlot >= 0) {
            g.drawCenteredString(font, "当前选择槽位：" + selectedSlot, width / 2, height - 42, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < slots.size(); i++) {
                CompoundTag slot = slots.getCompound(i);
                int x = gridX + (i % columns) * cell;
                int y = gridY + (i / columns) * cell;
                if (mouseX >= x && mouseX < x + cell - 2
                        && mouseY >= y && mouseY < y + cell - 2) {
                    selectedSlot = slot.getInt("Index");
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
