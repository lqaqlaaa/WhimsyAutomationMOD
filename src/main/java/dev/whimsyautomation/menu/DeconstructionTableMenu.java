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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.Arrays;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class DeconstructionTableMenu extends AbstractContainerMenu {
    private static final int PAGE_SIZE = 27;
    private static final int MIN_OUTPUT_PAGES = 10;
    private static final int OUTPUT_START = 1;
    private static final int MACHINE_SLOT_COUNT = 1 + PAGE_SIZE;
    private final DeconstructionTableBlockEntity machine;
    private final ContainerLevelAccess access;
    private final Level level;
    private final IItemHandler pageHandler;
    private int syncedOutputSlotCount = 1 + PAGE_SIZE * MIN_OUTPUT_PAGES;
    private int syncedRecipeIndex = -1;
    private int syncedPage;
    private int syncedPreviewPage;
    private boolean syncedAutoMode;
    private boolean syncedLocked;
    private int syncedLockedRecipeIndex = -1;
    private ItemStack cachedCandidateInput = ItemStack.EMPTY;
    private List<RecipeHolder<?>> cachedCandidateRecipes = List.of();
    private List<RecipeHolder<?>> cachedAllRecipes;
    private ItemStack cachedPreviewInput = ItemStack.EMPTY;
    private String cachedPreviewRecipeId = "";
    private List<ItemStack> cachedPreviewStacks = List.of();

    public DeconstructionTableMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(id, playerInventory, data.readBlockPos());
    }

    public DeconstructionTableMenu(int id, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.DECONSTRUCTION_TABLE.get(), id);
        this.level = playerInventory.player.level();
        this.machine = level.getBlockEntity(pos) instanceof DeconstructionTableBlockEntity be ? be : null;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        this.pageHandler = new IItemHandlerModifiable() {
            private int actualSlot(int pageSlot) { return OUTPUT_START + syncedPage * PAGE_SIZE + pageSlot; }
            @Override public int getSlots() { return PAGE_SIZE; }
            @Override public void setStackInSlot(int slot, ItemStack stack) {
                if (machine == null || slot < 0 || slot >= PAGE_SIZE) return;
                int actual = actualSlot(slot);
                machine.ensureOutputInventorySize(actual + 1);
                machine.getInventory().setStackInSlot(actual, stack);
            }
            @Override public ItemStack getStackInSlot(int slot) {
                if (machine == null || slot < 0 || slot >= PAGE_SIZE) return ItemStack.EMPTY;
                int actual = actualSlot(slot);
                machine.ensureOutputInventorySize(actual + 1);
                return machine.getInventory().getStackInSlot(actual);
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (machine == null || slot < 0 || slot >= PAGE_SIZE) return stack;
                int actual = actualSlot(slot);
                machine.ensureOutputInventorySize(actual + 1);
                return machine.getInventory().insertItem(actual, stack, simulate);
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (machine == null || slot < 0 || slot >= PAGE_SIZE) return ItemStack.EMPTY;
                int actual = actualSlot(slot);
                machine.ensureOutputInventorySize(actual + 1);
                return machine.getInventory().extractItem(actual, amount, simulate);
            }
            @Override public int getSlotLimit(int slot) { return 64; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
        };
        if (machine != null) {
            addSlot(new SlotItemHandler(machine.getInventory(), 0, 22, 60));
        } else {
            addSlot(new Slot(new net.minecraft.world.SimpleContainer(1), 0, 22, 60));
        }
        for (int i = 0; i < PAGE_SIZE; i++) addPagedOutputSlot(i);
        addPlayerInventory(playerInventory, 26, 178);
        addDataSlot(new DataSlot() {
            @Override public int get() { return getServerRecipeIndex(); }
            @Override public void set(int value) { syncedRecipeIndex = value; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return syncedPage; }
            @Override public void set(int value) {
                // DataSlots are synchronized in declaration order. The page can
                // arrive before the output-size DataSlot, so do not clamp it
                // against a stale client page count here.
                syncedPage = Math.max(0, value);
            }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return machine == null ? 1 + PAGE_SIZE * MIN_OUTPUT_PAGES : Math.min(32767, machine.getInventory().getSlots()); }
            @Override public void set(int value) {
                syncedOutputSlotCount = Math.max(OUTPUT_START + PAGE_SIZE * MIN_OUTPUT_PAGES, value);
                if (machine != null) machine.ensureOutputInventorySize(syncedOutputSlotCount);
            }
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

    private void addPagedOutputSlot(int outputIndex) {
        int x = 50 + (outputIndex % 9) * 18;
        int y = 42 + (outputIndex / 9) * 18;
        addSlot(new SlotItemHandler(pageHandler, outputIndex, x, y));
    }

    private void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, x + col * 18, y + 58));
    }

    private static boolean matchesRecipeOutput(ItemStack recipeResult, ItemStack input) {
        if (recipeResult.isEmpty() || input.isEmpty()
                || !ItemStack.isSameItem(recipeResult, input)) return false;

        // Treat the recipe result as the identity template. Every component
        // explicitly supplied by the recipe (including TACZ variant IDs and
        // other modded item data) must match, but extra state on the player's
        // stack must not hide an otherwise valid recipe.
        java.util.Set<net.minecraft.core.component.DataComponentType<?>> ignored = java.util.Set.of(
                net.minecraft.core.component.DataComponents.DAMAGE,
                net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.core.component.DataComponents.ITEM_NAME,
                net.minecraft.core.component.DataComponents.REPAIR_COST,
                net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        final boolean[] matches = {true};
        recipeResult.getComponents().keySet().forEach(component -> {
            if (ignored.contains(component)) return;
            if (!java.util.Objects.equals(recipeResult.get(component), input.get(component))) {
                matches[0] = false;
            }
        });
        return matches[0];
    }

    private List<RecipeHolder<?>> candidates() {
        ItemStack input = slots.isEmpty() ? ItemStack.EMPTY : slots.get(0).getItem();
        if (ItemStack.matches(cachedCandidateInput, input)) return cachedCandidateRecipes;
        cachedCandidateInput = input.copy();
        if (input.isEmpty()) {
            cachedCandidateRecipes = List.of();
            return cachedCandidateRecipes;
        }
        List<RecipeHolder<?>> found = new ArrayList<>();
        for (RecipeHolder<?> holder : allRecipes()) {
            Recipe<?> recipe = holder.value();
            if (dev.whimsyautomation.util.RecipeIngredientResolver.getIngredients(recipe).isEmpty()) continue;
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!matchesRecipeOutput(result, input)) continue;
            found.add(holder);
        }
        found.sort((a, b) -> a.id().compareTo(b.id()));
        cachedCandidateRecipes = List.copyOf(found);
        return cachedCandidateRecipes;
    }

    private List<RecipeHolder<?>> allRecipes() {
        if (cachedAllRecipes != null) return cachedAllRecipes;
        List<RecipeHolder<?>> all = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) all.add(holder);
        all.sort((a, b) -> a.id().compareTo(b.id()));
        cachedAllRecipes = List.copyOf(all);
        return cachedAllRecipes;
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
        String recipeId = holder.id().toString();
        if (recipeId.equals(cachedPreviewRecipeId) && ItemStack.matches(cachedPreviewInput, target)) {
            return cachedPreviewStacks;
        }

        // Preview and execution use the same server-registered Recipe ingredients.
        // JEI display alternatives can be client-only or cycle through choices that
        // the actual recipe does not consume, so they must not override this list.
        List<ItemStack> recovered = new ArrayList<>();
        for (Ingredient ingredient : dev.whimsyautomation.util.RecipeIngredientResolver.getIngredients(holder.value())) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] choices = ingredient.getItems();
            if (choices.length > 0 && !choices[0].isEmpty()) recovered.add(choices[0].copy());
        }
        List<ItemStack> result = mergeIdenticalStacks(recovered);
        cachedPreviewRecipeId = recipeId;
        cachedPreviewInput = target.copy();
        cachedPreviewStacks = result;
        return cachedPreviewStacks;
    }

    private static List<ItemStack> mergeIdenticalStacks(List<ItemStack> source) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack candidate : source) {
            if (candidate.isEmpty()) continue;
            ItemStack existingMatch = null;
            for (ItemStack existing : merged) {
                if (ItemStack.isSameItemSameComponents(existing, candidate)) {
                    existingMatch = existing;
                    break;
                }
            }
            if (existingMatch == null) merged.add(candidate.copy());
            else existingMatch.grow(candidate.getCount());
        }
        // Preview-only stacks keep their aggregate count even above normal stack limits.
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
                return Component.translatable("screen.whimsy_automation.recipe_locked_count", candidates().size()).getString();
            }
            return Component.translatable("screen.whimsy_automation.recipe_locked_count", candidates().size()).getString();
        }
        List<RecipeHolder<?>> found = candidates();
        if (found.isEmpty()) return Component.translatable("screen.whimsy_automation.recipe_none").getString();
        if (getPreviewRecipe() == null) return Component.translatable("screen.whimsy_automation.recipe_count", found.size()).getString();
        return Component.translatable("screen.whimsy_automation.recipe_count", found.size()).getString();
    }

    public int getPage() { return Math.min(syncedPage, getPageCount() - 1); }
    public int getPageCount() {
        int slotsCount = machine == null ? syncedOutputSlotCount : machine.getInventory().getSlots();
        return Math.max(1, (Math.max(PAGE_SIZE, slotsCount - OUTPUT_START) + PAGE_SIZE - 1) / PAGE_SIZE);
    }
    public boolean isAutoMode() { return syncedAutoMode; }
    public boolean isLocked() { return syncedLocked; }
    

    @Override public boolean clickMenuButton(Player player, int buttonId) {
        if (machine == null || player.level().isClientSide) return false;
        if (buttonId == 2) { machine.setAutoMode(!machine.isAutoMode()); return true; }
        if (buttonId == 3) { machine.processOnce(); return true; }
        if (buttonId == 4) { syncedPage = Math.max(0, syncedPage - 1); return true; }
        if (buttonId == 5) { syncedPage = Math.min(getPageCount() - 1, syncedPage + 1); return true; }
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
        if (index >= OUTPUT_START && index < MACHINE_SLOT_COUNT) {
            int actualOutput = OUTPUT_START + syncedPage * PAGE_SIZE + (index - OUTPUT_START);
            ItemStack moving = source.copy();
            if (!moveItemStackTo(moving, MACHINE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
            int movedCount = copy.getCount() - moving.getCount();
            if (movedCount <= 0) return ItemStack.EMPTY;
            machine.getInventory().extractItem(actualOutput, movedCount, false);
            return copy.copyWithCount(movedCount);
        }
        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(source, MACHINE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return machine != null && stillValid(access, player, ModBlocks.DECONSTRUCTION_TABLE.get()); }
    public DeconstructionTableBlockEntity machine() { return machine; }
}