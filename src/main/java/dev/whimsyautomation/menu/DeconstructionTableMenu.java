package dev.whimsyautomation.menu;

import dev.whimsyautomation.block.entity.DeconstructionTableBlockEntity;
import dev.whimsyautomation.registry.ModBlocks;
import dev.whimsyautomation.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.List;

public final class DeconstructionTableMenu extends AbstractContainerMenu {
    private final DeconstructionTableBlockEntity machine;
    private final ContainerLevelAccess access;
    private int syncedRecipeIndex = -1;

    public DeconstructionTableMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(id, playerInventory, data.readBlockPos());
    }

    public DeconstructionTableMenu(int id, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.DECONSTRUCTION_TABLE.get(), id);
        this.machine = playerInventory.player.level().getBlockEntity(pos) instanceof DeconstructionTableBlockEntity be ? be : null;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);

        if (machine != null) {
            addSlot(new SlotItemHandler(machine.getInventory(), 0, 22, 52));
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new SlotItemHandler(machine.getInventory(), 1 + row * 9 + col, 66 + col * 18, 42 + row * 18));
                }
            }
        } else {
            addSlot(new Slot(new net.minecraft.world.SimpleContainer(1), 0, 22, 52));
            for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
                addSlot(new Slot(new net.minecraft.world.SimpleContainer(27), row * 9 + col, 66 + col * 18, 42 + row * 18));
        }

        addPlayerInventory(playerInventory, 26, 124);
        addDataSlot(new DataSlot() {
            @Override public int get() { return getServerRecipeIndex(); }
            @Override public void set(int value) { syncedRecipeIndex = value; }
        });
    }

    private void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, x + col * 18, y + 58));
    }

    private List<RecipeHolder<?>> candidates() {
        ItemStack input = slots.isEmpty() ? ItemStack.EMPTY : slots.get(0).getItem();
        if (input.isEmpty()) return List.of();
        Level level = player.level();
        List<RecipeHolder<?>> found = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            if (result.isEmpty() || !ItemStack.isSameItemSameComponents(result, input)) continue;
            if (holder.value().getIngredients().isEmpty()) continue;
            found.add(holder);
        }
        found.sort((a, b) -> a.id().compareTo(b.id()));
        return found;
    }

    private int getServerRecipeIndex() {
        if (machine == null || machine.getLockedRecipe().isEmpty()) return -1;
        List<RecipeHolder<?>> found = candidates();
        for (int i = 0; i < found.size(); i++) {
            if (found.get(i).id().toString().equals(machine.getLockedRecipe())) return i;
        }
        return -1;
    }

    public String getRecipeStatus() {
        List<RecipeHolder<?>> found = candidates();
        if (found.isEmpty()) return "No matching recipe";
        if (syncedRecipeIndex < 0) {
            if (machine != null && !machine.getLockedRecipe().isEmpty()) return "Locked recipe not in current list";
            return "Automatic selection (" + found.size() + ")";
        }
        if (syncedRecipeIndex >= found.size()) return "Recipe list changed";
        return "Locked: " + found.get(syncedRecipeIndex).id();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (machine == null || player.level().isClientSide) return false;
        if (buttonId == 2) {
            machine.setLockedRecipe(null);
            return true;
        }
        List<RecipeHolder<?>> found = candidates();
        if (found.isEmpty()) return false;
        int index = getServerRecipeIndex();
        if (index < 0) index = 0;
        else index = Math.floorMod(index + (buttonId == 0 ? -1 : 1), found.size());
        machine.setLockedRecipe(found.get(index).id());
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < 28) {
            if (!moveItemStackTo(source, 28, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return machine != null && stillValid(access, player, ModBlocks.DECONSTRUCTION_TABLE.get());
    }

    public DeconstructionTableBlockEntity machine() { return machine; }
}
