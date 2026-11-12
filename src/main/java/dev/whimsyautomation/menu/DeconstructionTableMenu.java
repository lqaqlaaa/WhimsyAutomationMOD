package dev.whimsyautomation.menu;

import dev.whimsyautomation.block.entity.DeconstructionTableBlockEntity;
import dev.whimsyautomation.registry.ModBlocks;
import dev.whimsyautomation.registry.ModMenus;
import dev.whimsyautomation.integration.jei.WhimsyJeiPlugin;
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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.Arrays;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.resources.ResourceLocation;

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
    private int syncedPreviewPage;
    private boolean syncedAutoMode;
    private boolean syncedLocked;
    private int syncedLockedRecipeIndex = -1;

    public DeconstructionTableMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(id, playerInventory, data.readBlockPos());
    }

    public DeconstructionTableMenu(int id, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.DECONSTRUCTION_TABLE.get(), id);
        this.level = playerInventory.player.level();
        this.machine = level.getBlockEntity(pos) instanceof DeconstructionTableBlockEntity be ? be : null;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        if (machine != null) {
            addSlot(new net.neoforged.neoforge.items.SlotItemHandler(machine.getInventory(), 0, 22, 60));
            for (int i = 0; i < OUTPUT_SLOTS; i++) addPagedOutputSlot(machine.getInventory(), i);
        } else {
            addSlot(new Slot(new net.minecraft.world.SimpleContainer(1), 0, 22, 60));
            ItemStackHandler dummyHandler = new ItemStackHandler(OUTPUT_START + OUTPUT_SLOTS);
            for (int i = 0; i < OUTPUT_SLOTS; i++) addPagedOutputSlot(dummyHandler, i);
        }
        addPlayerInventory(playerInventory, 26, 182);
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
        addDataSlot(new DataSlot() {
            @Override public int get() { return machine != null && !machine.getLockedRecipe().isEmpty() ? 1 : 0; }
            @Override public void set(int value) { syncedLocked = value != 0; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return getServerLockedRecipeIndex(); }
            @Override public void set(int value) { syncedLockedRecipeIndex = value; }
        });
    }

    private void addPagedOutputSlot(ItemStackHandler handler, int outputIndex) {
        int actualSlot = OUTPUT_START + outputIndex;
        int visibleIndex = outputIndex % PAGE_SIZE;
        int x = 66 + (visibleIndex % 9) * 18;
        int y = 42 + (visibleIndex / 9) * 18;
        addSlot(new net.neoforged.neoforge.items.SlotItemHandler(handler, actualSlot, x, y) {
            @Override public boolean isActive() { return outputIndex / PAGE_SIZE == syncedPage; }
        });
    }

    private void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, x + col * 18, y + 58));
    }

    private static boolean matchesRecipeOutput(ItemStack recipeResult, ItemStack input) {
        if (recipeResult.isEmpty() || input.isEmpty() || !ItemStack.isSameItem(recipeResult, input)) return false;
        ItemStack expected = recipeResult.copy();
        ItemStack supplied = input.copy();
        // Durability is consumed by normal use and must not invalidate an otherwise
        // identical recipe output. Other data components remain significant.
        expected.remove(net.minecraft.core.component.DataComponents.DAMAGE);
        supplied.remove(net.minecraft.core.component.DataComponents.DAMAGE);
        expected.remove(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        supplied.remove(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        expected.remove(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        supplied.remove(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        expected.remove(net.minecraft.core.component.DataComponents.REPAIR_COST);
        supplied.remove(net.minecraft.core.component.DataComponents.REPAIR_COST);
        return ItemStack.isSameItemSameComponents(expected, supplied);
    }

    private List<RecipeHolder<?>> candidates() {
        ItemStack input = slots.isEmpty() ? ItemStack.EMPTY : slots.get(0).getItem();
        if (input.isEmpty()) return List.of();
        List<RecipeHolder<?>> found = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!matchesRecipeOutput(result, input)) continue;
            found.add(holder);
        }
        found.sort((a, b) -> a.id().compareTo(b.id()));
        return found;
    }

    private List<RecipeHolder<?>> allRecipes() {
        List<RecipeHolder<?>> all = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            all.add(holder);
        }
        all.sort((a, b) -> a.id().compareTo(b.id()));
        return all;
    }

    private int getServerLockedRecipeIndex() {
        if (machine == null || machine.getLockedRecipe().isEmpty()) return -1;
        List<RecipeHolder<?>> all = allRecipes();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().toString().equals(machine.getLockedRecipe())) return i;
        }
        return -1;
    }

    private RecipeHolder<?> getLockedRecipeHolder() {
        List<RecipeHolder<?>> all = allRecipes();
        int index = level.isClientSide ? syncedLockedRecipeIndex : getServerLockedRecipeIndex();
        return index >= 0 && index < all.size() ? all.get(index) : null;
    }

    private int getServerRecipeIndex() {
        if (machine == null) return -1;
        List<RecipeHolder<?>> found = candidates();
        if (found.isEmpty()) return -1;
        if (machine.getSelectedRecipe().isEmpty()) return 0;
        for (int i = 0; i < found.size(); i++) {
            if (found.get(i).id().toString().equals(machine.getSelectedRecipe())) return i;
        }
        return -1;
    }

    private RecipeHolder<?> getPreviewRecipe() {
        if (syncedLocked) return getLockedRecipeHolder();
        List<RecipeHolder<?>> found = candidates();
        if (found.isEmpty()) return null;
        int index = level.isClientSide ? syncedRecipeIndex : getServerRecipeIndex();
        if (index < 0 || index >= found.size()) index = 0;
        return found.get(index);
    }

    /** Updates the client preview immediately; the server selection remains authoritative. */
    public void cyclePreview(int delta) {
        List<RecipeHolder<?>> found = candidates();
        if (found.isEmpty()) {
            syncedRecipeIndex = -1;
            return;
        }
        int index = syncedRecipeIndex;
        if (index < 0 || index >= found.size()) {
            index = delta < 0 ? 0 : -1;
        }
        syncedRecipeIndex = Math.floorMod(index + delta, found.size());
        syncedPreviewPage = 0;
    }

    private List<ItemStack> getAllPreviewStacks() {
        RecipeHolder<?> holder = getPreviewRecipe();
        if (holder == null) return List.of();

        ItemStack target = slots.isEmpty() ? ItemStack.EMPTY : slots.get(0).getItem();
        if (level.isClientSide && !target.isEmpty()) {
            for (WhimsyJeiPlugin.Entry entry : WhimsyJeiPlugin.findByOutput(target)) {
                if (entry.recipeId().equals(holder.id().toString())) {
                    return mergeIdenticalStacks(entry.inputs());
                }
            }
        }

        List<ItemStack> recovered = new ArrayList<>();
        for (Ingredient ingredient : holder.value().getIngredients()) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] choices = ingredient.getItems();
            // A tag/ingredient alternative is one recipe slot, not a list of
            // simultaneously consumed items. Preview one representative just as
            // JEI shows one cycling ingredient slot.
            if (choices.length > 0 && !choices[0].isEmpty()) recovered.add(choices[0].copy());
        }
        return mergeIdenticalStacks(recovered);
    }

    private static List<ItemStack> mergeIdenticalStacks(List<ItemStack> source) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack candidate : source) {
            if (candidate.isEmpty()) continue;
            ItemStack remaining = candidate.copy();
            for (ItemStack existing : merged) {
                if (!ItemStack.isSameItemSameComponents(existing, remaining)) continue;
                int room = existing.getMaxStackSize() - existing.getCount();
                int moved = Math.min(room, remaining.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    remaining.shrink(moved);
                }
                if (remaining.isEmpty()) break;
            }
            while (!remaining.isEmpty()) {
                int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                merged.add(remaining.copyWithCount(moved));
                remaining.shrink(moved);
            }
        }
        return List.copyOf(merged);
    }

    public List<ItemStack> getPreviewStacks() {
        List<ItemStack> all = getAllPreviewStacks();
        int start = getPreviewPage() * 18;
        int end = Math.min(all.size(), start + 18);
        List<ItemStack> page = new ArrayList<>();
        for (int index = start; index < end; index++) page.add(all.get(index).copy());
        return List.copyOf(page);
    }

    public int getPreviewColumns() {
        int count = getPreviewStacks().size();
        if (count <= 9) return 3;
        if (count <= 16) return 4;
        if (count <= 25) return 5;
        if (count <= 36) return 6;
        if (count <= 49) return 7;
        return 9;
    }

    public int getPreviewPage() {
        return Math.min(syncedPreviewPage, getPreviewPageCount() - 1);
    }
    public void cyclePreviewPage(int delta) {
        int pages = getPreviewPageCount();
        syncedPreviewPage = Math.max(0, Math.min(pages - 1, getPreviewPage() + delta));
    }
    public int getPreviewPageCount() {
        return Math.max(1, (getAllPreviewStacks().size() + 17) / 18);
    }

    public ItemStack getPreviewResult() {
        RecipeHolder<?> recipe = getPreviewRecipe();
        return recipe == null ? ItemStack.EMPTY : recipe.value().getResultItem(level.registryAccess()).copy();
    }

    public int getCandidateCount() {
        return candidates().size();
    }

    public String getPreviewRecipeId() {
        RecipeHolder<?> recipe = getPreviewRecipe();
        return recipe == null ? "" : recipe.id().toString();
    }

    public String getRecipeStatus() {
        if (syncedLocked) {
            RecipeHolder<?> locked = getLockedRecipeHolder();
            if (locked == null) return Component.translatable("screen.whimsy_automation.recipe_locked_missing").getString();
            ItemStack input = slots.isEmpty() ? ItemStack.EMPTY : slots.get(0).getItem();
            if (!input.isEmpty() && !matchesRecipeOutput(locked.value().getResultItem(level.registryAccess()), input)) {
                return Component.translatable("screen.whimsy_automation.recipe_locked_mismatch", locked.id()).getString();
            }
            return Component.translatable("screen.whimsy_automation.recipe_locked", locked.id()).getString();
        }
        List<RecipeHolder<?>> found = candidates();
        if (found.isEmpty()) return Component.translatable("screen.whimsy_automation.recipe_none").getString();
        if (getPreviewRecipe() == null) return Component.translatable("screen.whimsy_automation.recipe_count", found.size()).getString();
        return Component.translatable("screen.whimsy_automation.recipe_count", found.size()).getString();
    }

    public int getPage() { return syncedPage; }
    public boolean isAutoMode() { return syncedAutoMode; }
    public boolean isLocked() { return syncedLocked; }
    public int getPageCount() { return OUTPUT_PAGES; }

    @Override public boolean clickMenuButton(Player player, int buttonId) {
        if (machine == null || player.level().isClientSide) return false;
        if (buttonId == 2) { machine.setAutoMode(!machine.isAutoMode()); return true; }
        if (buttonId == 3) { machine.processOnce(); return true; }
        if (buttonId == 4) { syncedPage = Math.max(0, syncedPage - 1); return true; }
        if (buttonId == 5) { syncedPage = Math.min(OUTPUT_PAGES - 1, syncedPage + 1); return true; }
        if (buttonId == 6) {
            if (!machine.getLockedRecipe().isEmpty()) {
                machine.setLockedRecipe(null);
            } else {
                RecipeHolder<?> selected = getPreviewRecipe();
                if (selected == null) return false;
                machine.setLockedRecipe(selected.id());
            }
            return true;
        }
        if (buttonId != 0 && buttonId != 1) return false;
        List<RecipeHolder<?>> found = candidates();
        if (found.isEmpty()) return false;
        int index = getServerRecipeIndex();
        if (index < 0) index = buttonId == 0 ? found.size() - 1 : 0;
        else index = Math.floorMod(index + (buttonId == 0 ? -1 : 1), found.size());
        machine.setSelectedRecipe(found.get(index).id());
        syncedPreviewPage = 0;
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