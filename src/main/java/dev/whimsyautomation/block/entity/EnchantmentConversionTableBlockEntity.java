package dev.whimsyautomation.block.entity;

import dev.whimsyautomation.menu.EnchantmentConversionTableMenu;
import dev.whimsyautomation.registry.ModBlockEntities;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class EnchantmentConversionTableBlockEntity extends BlockEntity implements MenuProvider {
    public static final int BOOK_SLOT = 0, PAYMENT_SLOT = 1, TEMPLATE_SLOT = 2, RESULT_SLOT = 3, SLOT_COUNT = 4;
    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override public int getSlotLimit(int slot) { return slot == TEMPLATE_SLOT || slot == RESULT_SLOT ? 1 : 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case BOOK_SLOT -> stack.is(Items.BOOK);
                case PAYMENT_SLOT -> stack.is(Items.EMERALD) || stack.is(Items.EMERALD_BLOCK);
                case TEMPLATE_SLOT -> isValidTemplate(stack);
                default -> false;
            };
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); if (slot != RESULT_SLOT) refreshCopyResult(); }
    };
    public EnchantmentConversionTableBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.ENCHANTMENT_CONVERSION_TABLE.get(), pos, state); }
    public ItemStackHandler getInventory() { return inventory; }
    public static boolean isValidTemplate(ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) return false;
        ItemEnchantments ench = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (ench.entrySet().size() != 1) return false;
        var entry = ench.entrySet().iterator().next();
        return entry.getIntValue() > 0 && entry.getIntValue() <= entry.getKey().value().getMaxLevel();
    }
    public boolean hasPayment() {
        ItemStack books = inventory.getStackInSlot(BOOK_SLOT), payment = inventory.getStackInSlot(PAYMENT_SLOT);
        return !books.isEmpty() && (payment.is(Items.EMERALD) && payment.getCount() >= 36
                || payment.is(Items.EMERALD_BLOCK) && payment.getCount() >= 4);
    }
    public void refreshCopyResult() {
        ItemStack result = inventory.getStackInSlot(RESULT_SLOT);
        if (!result.isEmpty()) return;
        ItemStack template = inventory.getStackInSlot(TEMPLATE_SLOT);
        if (!isValidTemplate(template) || !hasPayment()) return;
        inventory.setStackInSlot(RESULT_SLOT, template.copyWithCount(1));
        ItemStack books = inventory.getStackInSlot(BOOK_SLOT); books.shrink(1);
        inventory.setStackInSlot(BOOK_SLOT, books);
        ItemStack payment = inventory.getStackInSlot(PAYMENT_SLOT);
        payment.shrink(payment.is(Items.EMERALD) ? 36 : 4);
        inventory.setStackInSlot(PAYMENT_SLOT, payment);
        setChanged();
    }
    public void dropAllContents() {
        if (level == null || level.isClientSide) return;
        for (int i=0;i<SLOT_COUNT;i++) { ItemStack stack=inventory.getStackInSlot(i); if(!stack.isEmpty()) { Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,stack); inventory.setStackInSlot(i,ItemStack.EMPTY); } }
    }
    @Override public Component getDisplayName() { return Component.translatable("container.whimsy_automation.enchantment_conversion_table"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new EnchantmentConversionTableMenu(id, playerInventory, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(worldPosition));
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag,registries); tag.put("Inventory",inventory.serializeNBT(registries)); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag,registries); if(tag.contains("Inventory")) inventory.deserializeNBT(registries,tag.getCompound("Inventory")); }
}
