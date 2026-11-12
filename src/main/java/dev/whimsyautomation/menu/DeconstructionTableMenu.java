package dev.whimsyautomation.menu;

import dev.whimsyautomation.block.entity.DeconstructionTableBlockEntity;
import dev.whimsyautomation.registry.ModBlocks;
import dev.whimsyautomation.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public final class DeconstructionTableMenu extends AbstractContainerMenu {
    private static final int PAGE_SIZE = 27;
    private static final int OUTPUT_START = 1;
    private static final int OUTPUT_SLOTS = 108;
    private static final int MACHINE_SLOT_COUNT = 109;
    private static final int OUTPUT_PAGES = 4;
    private final DeconstructionTableBlockEntity machine;
    private final ContainerLevelAccess access;
    private final Level level;
    private int syncedRecipeIndex = -1;
    private int syncedPage;
    private boolean syncedAutoMode;

    public DeconstructionTableMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(id, playerInventory, data.readBlockPos());
    }

    public DeconstructionTableMenu(int id, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.DECONSTRUCTION_TABLE.get(), id);
        this.level = playerInventory.player.level();
        this.machine = level.getBlockEntity(pos) instanceof DeconstructionTableBlockEntity be ? be : null;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        if (machine != null) {
            addSlot(new net.neoforged.neoforge.items.SlotItemHandler(machine.getInventory(), 0, 22, 52));
            for (int i = 0; i < 108; i++) addPagedOutputSlot(machine.getInventory(), i);
        } else {
            addSlot(new Slot(new net.minecraft.world.SimpleContainer(1), 0, 22, 52));
            ItemStackHandler dummyHandler = new ItemStackHandler(OUTPUT_START + OUTPUT_SLOTS);
            for (int i = 0; i < OUTPUT_SLOTS; i++) addPagedOutputSlot(dummyHandler, i);
        }
        addPlayerInventory(playerInventory, 26, 158);
        addDataSlot(new DataSlot() {
            @Override public int get() { return getServerRecipeIndex(); }
            @Override public void set(int value) { syncedRecipeIndex = value; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return syncedPage; }
            @Override public void set(int value) { syncedPage = Math.max(0, Math.min(OUTPUT_PAGES - 1, value)); }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return machine != null && machine.isAutoMode() ? 1 : 0; }
            @Override public void set(int value) { syncedAutoMode = value != 0; }
        });
    }

    private void addPagedOutputSlot(ItemStackHandler handler, int outputIndex) {
        int actualSlot = 1 + outputIndex;
        int visibleIndex = outputIndex % PAGE_SIZE;
        int x = 66 + (visibleIndex % 9) * 18;
        int y = 42 + (visibleIndex / 9) * 18;
        addSlot(new net.neoforged.neoforge.items.SlotItemHandler(handler, actualSlot, x, y) {
            @Override public boolean isActive() {
                return outputIndex / PAGE_SIZE == syncedPage;
            }
        });
    }

    private void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, x + col * 18, y + 58));
    }

    private static boolean matchesRecipeOutput(ItemStack recipeResult, ItemStack input) {
        // Durability and other runtime components do not change the recipe result item.
        return ItemStack.isSameItem(recipeResult, input);
    }

    private List<RecipeHolder<?>> candidates() {
        ItemStack input = slots.isEmpty() ? ItemStack.EMPTY : slots.get(0).getItem();
        if (input.isEmpty()) return List.of();
        List<RecipeHolder<?>> found = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            if (!result.isEmpty() && matchesRecipeOutput(result, input) && !holder.value().getIngredients().isEmpty()) found.add(holder);
        }
        found.sort((a, b) -> a.id().compareTo(b.id()));
        return found;
    }

    private int getServerRecipeIndex() {
        if (machine == null || machine.getLockedRecipe().isEmpty()) return -1;
        List<RecipeHolder<?>> found = candidates();
        for (int i = 0; i < found.size(); i++) if (found.get(i).id().toString().equals(machine.getLockedRecipe())) return i;
        return -1;
    }

    public String getRecipeStatus() {
        List<RecipeHolder<?>> found = candidates();
        if (found.isEmpty()) return Component.translatable("screen.whimsy_automation.recipe_none").getString();
        if (syncedRecipeIndex < 0) {
            if (machine != null && !machine.getLockedRecipe().isEmpty()) return Component.translatable("screen.whimsy_automation.recipe_unavailable").getString();
            if (found.size() == 1) return Component.translatable("screen.whimsy_automation.recipe_auto", found.getFirst().id()).getString();
            return Component.translatable("screen.whimsy_automation.recipe_default", found.getFirst().id(), found.size()).getString();
        }
        if (syncedRecipeIndex >= found.size()) return Component.translatable("screen.whimsy_automation.recipe_changed").getString();
        return Component.translatable("screen.whimsy_automation.recipe_locked", found.get(syncedRecipeIndex).id()).getString();
    }
    public int getPage() { return syncedPage; }
    public boolean isAutoMode() { return syncedAutoMode; }
    public int getPageCount() { return OUTPUT_PAGES; }

    @Override public boolean clickMenuButton(Player player, int buttonId) {
        if (machine == null || player.level().isClientSide) return false;
        if (buttonId == 2) { machine.setAutoMode(!machine.isAutoMode()); return true; }
        if (buttonId == 3) { machine.processOnce(); return true; }
        if (buttonId == 4) { syncedPage = Math.max(0, syncedPage - 1); return true; }
        if (buttonId == 5) { syncedPage = Math.min(OUTPUT_PAGES - 1, syncedPage + 1); return true; }
        List<RecipeHolder<?>> found = candidates();
        if (found.isEmpty() || (buttonId != 0 && buttonId != 1)) return false;
        int index = getServerRecipeIndex();
        if (index < 0) index = buttonId == 0 ? found.size() - 1 : 0;
        else index = Math.floorMod(index + (buttonId == 0 ? -1 : 1), found.size());
        machine.setLockedRecipe(found.get(index).id());
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(source, MACHINE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return machine != null && stillValid(access, player, ModBlocks.DECONSTRUCTION_TABLE.get()); }
    public DeconstructionTableBlockEntity machine() { return machine; }
}
