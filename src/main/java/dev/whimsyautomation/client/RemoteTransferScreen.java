package dev.whimsyautomation.client;

import dev.whimsyautomation.network.RemoteTransferActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RemoteTransferScreen extends Screen {
    private CompoundTag snapshot = new CompoundTag();
    private UUID selectedNetwork, selectedMember, selectedSecond, selectedConnection;
    private int scroll;
    private EditBox nameBox;
    private List<CompoundTag> networks = List.of(), members = List.of(), links = List.of();

    public RemoteTransferScreen() { super(Component.translatable("screen.whimsy_automation.remote.title")); }
    public RemoteTransferScreen(CompoundTag data) { this(); applySnapshot(data); }

    public RemoteTransferScreen applySnapshot(CompoundTag data) {
        snapshot = data.copy();
        rebuildData();
        if (minecraft != null) init(minecraft, width, height);
        return this;
    }

    private void rebuildData() {
        networks = compounds(snapshot.getList("Networks", Tag.TAG_COMPOUND));
        CompoundTag selected = findNetwork();
        members = selected == null ? List.of() : compounds(selected.getList("Members", Tag.TAG_COMPOUND));
        links = selected == null ? List.of() : compounds(selected.getList("Connections", Tag.TAG_COMPOUND));
        if (selectedNetwork != null && selected == null) selectedNetwork = null;
    }

    private CompoundTag findNetwork() {
        for (CompoundTag n : networks) if (n.getUUID("Id").equals(selectedNetwork)) return n;
        return null;
    }
    private static List<CompoundTag> compounds(ListTag list) {
        List<CompoundTag> out = new ArrayList<>();
        for (int i=0;i<list.size();i++) out.add(list.getCompound(i).copy());
        return out;
    }
    private void send(int action, UUID network, UUID a, UUID b, int value, String text) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new RemoteTransferActionPayload(action, network, a, b, value, text));
    }

    @Override protected void init() {
        int left = 12, top = 34;
        nameBox = new EditBox(font, left, height - 28, 130, 18, Component.translatable("screen.whimsy_automation.remote.name"));
        nameBox.setMaxLength(48); nameBox.setValue("新建网络"); addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.remote.create"),
                b -> send(1, null, null, null, 0, nameBox.getValue())).bounds(148, height-29, 54, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.remote.rename"),
                b -> { if(selectedNetwork!=null) send(2, selectedNetwork, null, null, 0, nameBox.getValue()); })
                .bounds(205,height-29,54,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.remote.delete"),
                b -> { if(selectedNetwork!=null) { send(3,selectedNetwork,null,null,0,""); selectedNetwork=null; } })
                .bounds(262,height-29,54,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.remote.bind"),
                b -> { if(selectedNetwork!=null) send(4,selectedNetwork,null,null,0,""); })
                .bounds(width-116,height-29,104,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.remote.connect"),
                b -> { if(selectedNetwork!=null && selectedMember!=null && selectedSecond!=null)
                    send(6,selectedNetwork,selectedMember,selectedSecond,0,""); })
                .bounds(width-116,height-53,104,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.remote.remove"),
                b -> { if(selectedNetwork!=null && selectedMember!=null) send(5,selectedNetwork,selectedMember,null,0,""); })
                .bounds(width-116,height-77,104,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.whimsy_automation.remote.remove_link"),
                b -> { if(selectedNetwork!=null && selectedConnection!=null) send(7,selectedNetwork,selectedConnection,null,0,""); })
                .bounds(width-116,height-101,104,20).build());
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width/2, 10, 0xFFFFFF);
        g.drawString(font, Component.translatable("screen.whimsy_automation.remote.networks"), 12, 24, 0xE0E0E0);
        int y=38;
        for (CompoundTag n : networks) {
            boolean selected=n.getUUID("Id").equals(selectedNetwork);
            g.fill(10,y-2,Math.min(width/2-8,190),y+14,selected?0xFF526B83:0xFF303030);
            g.drawString(font,n.getString("Name"),16,y,0xFFFFFF);
            y+=18;
        }
        g.drawString(font,Component.translatable("screen.whimsy_automation.remote.members"),width/2+4,24,0xE0E0E0);
        y=38;
        for (CompoundTag m : members) {
            UUID id=m.getUUID("Id"); boolean selected=id.equals(selectedMember)||id.equals(selectedSecond);
            g.fill(width/2+2,y-2,width-122,y+14,selected?0xFF526B83:0xFF303030);
            String kind=m.getString("Kind").equals("WORKSTATION")?"工作方块":"容器";
            String label=kind+" "+m.getString("Dimension")+" "+net.minecraft.core.BlockPos.of(m.getLong("Pos")).toShortString();
            g.drawString(font,label,width/2+7,y,0xFFFFFF);
            y+=18;
        }
        g.drawString(font,Component.translatable("screen.whimsy_automation.remote.links"),width/2+4,Math.max(150,y+4),0xE0E0E0);
        y=Math.max(164,y+18);
        for (CompoundTag c:links) {
            String line=c.getString("Direction")+"  "+c.getUUID("Source").toString().substring(0,8)+" → "+c.getUUID("Target").toString().substring(0,8);
            g.drawString(font,line,width/2+7,y,0xCFCFCF); y+=16;
        }
        g.drawString(font,Component.translatable("screen.whimsy_automation.remote.select_hint"),12,height-43,0xAAAAAA);
        super.render(g,mouseX,mouseY,partialTick);
    }

    @Override public boolean mouseClicked(double x,double y,int button) {
        if(button==0 && y>=36 && y<height-34) {
            if(x<width/2) {
                int index=(int)((y-36)/18);
                if(index>=0 && index<networks.size()) {
                    selectedNetwork=networks.get(index).getUUID("Id");
                    selectedMember=null; selectedSecond=null; selectedConnection=null; rebuildData();
                }
            } else if(x<width-120) {
                int memberStart=38;
                int index=(int)((y-memberStart)/18);
                if(index>=0 && index<members.size()) {
                    UUID id=members.get(index).getUUID("Id");
                    if(selectedMember==null || selectedMember.equals(id)) selectedMember=id;
                    else selectedSecond=id;
                }
            }
        }
        return super.mouseClicked(x,y,button);
    }

    @Override public boolean isPauseScreen() { return false; }
}
