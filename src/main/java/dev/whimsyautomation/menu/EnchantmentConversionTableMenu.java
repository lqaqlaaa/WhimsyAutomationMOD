package dev.whimsyautomation.menu;

import dev.whimsyautomation.block.entity.EnchantmentConversionTableBlockEntity;
import dev.whimsyautomation.registry.ModBlocks;
import dev.whimsyautomation.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class EnchantmentConversionTableMenu extends AbstractContainerMenu {
    private final EnchantmentConversionTableBlockEntity machine;
    private final ContainerLevelAccess access;
    public EnchantmentConversionTableMenu(int id, Inventory inv, FriendlyByteBuf data) { this(id,inv,data.readBlockPos()); }
    public EnchantmentConversionTableMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.ENCHANTMENT_CONVERSION_TABLE.get(),id);
        machine=inv.player.level().getBlockEntity(pos) instanceof EnchantmentConversionTableBlockEntity be?be:null;
        access=ContainerLevelAccess.create(inv.player.level(),pos);
        ItemStackHandler handler=machine==null?new ItemStackHandler(4):machine.getInventory();
        for(int i=0;i<4;i++) {
            final int slotIndex=i;
            addSlot(new net.neoforged.neoforge.items.SlotItemHandler(handler,i,35+i*36,52) {
                @Override public boolean mayPickup(Player player) { return slotIndex != EnchantmentConversionTableBlockEntity.RESULT_SLOT || !getItem().isEmpty(); }
                @Override public ItemStack onTake(Player player, ItemStack stack) {
                    ItemStack taken=super.onTake(player,stack);
                    if(slotIndex==EnchantmentConversionTableBlockEntity.RESULT_SLOT && machine!=null) machine.refreshCopyResult();
                    return taken;
                }
            });
        }
        for(int row=0;row<3;row++) for(int col=0;col<9;col++) addSlot(new Slot(inv,col+row*9+9,8+col*18,112+row*18));
        for(int col=0;col<9;col++) addSlot(new Slot(inv,col,8+col*18,170));
    }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(index<0||index>=slots.size()) return ItemStack.EMPTY;
        Slot slot=slots.get(index); if(!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source=slot.getItem(), copy=source.copy();
        if(index<4) { if(!moveItemStackTo(source,4,slots.size(),true)) return ItemStack.EMPTY; }
        else {
            int target=source.is(net.minecraft.world.item.Items.BOOK)?0:
                    source.is(net.minecraft.world.item.Items.EMERALD)||source.is(net.minecraft.world.item.Items.EMERALD_BLOCK)?1:
                    source.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)?2:-1;
            if(target<0||!moveItemStackTo(source,target,target+1,false)) return ItemStack.EMPTY;
        }
        if(source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
    @Override public boolean stillValid(Player player) { return machine!=null&&stillValid(access,player,ModBlocks.ENCHANTMENT_CONVERSION_TABLE.get()); }
}
