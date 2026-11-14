package dev.whimsyautomation.client;

import dev.whimsyautomation.menu.EnchantmentConversionTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class EnchantmentConversionTableScreen extends AbstractContainerScreen<EnchantmentConversionTableMenu> {
    private static final ResourceLocation SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/slot");
    public EnchantmentConversionTableScreen(EnchantmentConversionTableMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title); imageWidth=194; imageHeight=196; inventoryLabelY=100; titleLabelY=7;
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY) {
        int x=leftPos,y=topPos;
        g.fill(x,y,x+imageWidth,y+imageHeight,0xFF21150F);
        g.fill(x+3,y+3,x+imageWidth-3,y+imageHeight-3,0xFF392319);
        g.fill(x+5,y+5,x+imageWidth-5,y+22,0xFF604021);
        for(int i=0;i<4;i++){int sx=x+35+i*36,sy=y+52;g.blitSprite(SLOT_SPRITE,sx,sy,16,16);}
        for(int i=0;i<27;i++){int sx=x+8+(i%9)*18,sy=y+112+(i/9)*18;g.blitSprite(SLOT_SPRITE,sx,sy,16,16);}
        for(int i=0;i<9;i++){int sx=x+8+i*18,sy=y+170;g.fill(sx-1,sy-1,sx+17,sy+17,0xFF17100C);}
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY) {
        g.drawString(font,Component.translatable("screen.whimsy_automation.conversion_table"),8,7,0xFFFFD27C,false);
        g.drawString(font,Component.translatable("screen.whimsy_automation.book"),29,34,0xFFE8D6B2,false);
        g.drawString(font,Component.translatable("screen.whimsy_automation.payment"),65,34,0xFFE8D6B2,false);
        g.drawString(font,Component.translatable("screen.whimsy_automation.template"),101,34,0xFFE8D6B2,false);
        g.drawString(font,Component.translatable("screen.whimsy_automation.copy_result"),137,34,0xFFE8D6B2,false);
        g.drawString(font,Component.translatable("container.inventory"),8,inventoryLabelY,0xFFE8D6B2,false);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){renderBackground(g,mouseX,mouseY,partial);super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);}
}
