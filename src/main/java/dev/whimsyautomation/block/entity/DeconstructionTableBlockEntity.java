package dev.whimsyautomation.block.entity;

import dev.whimsyautomation.menu.DeconstructionTableMenu;
import dev.whimsyautomation.registry.ModBlockEntities;
import dev.whimsyautomation.util.SidedMachineItemHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reverse-processes supported registered recipes. Recipe preview, recipe lock and
 * automation are deliberately separate states.
 */
public final class DeconstructionTableBlockEntity extends BlockEntity implements MenuProvider {
    public static final int OUTPUT_START = 1;
    private static final int PAGE_SIZE = 27;

    private String selectedRecipe = "";
    private String lockedRecipe = "";
    private int workTicks;
    private boolean autoMode = false;

    private final DynamicItemStackHandler inventory = new DynamicItemStackHandler(OUTPUT_START + PAGE_SIZE * 10) {
        @Override public int getSlotLimit(int slot) { return slot == 0 ? 1 : Integer.MAX_VALUE; }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty()) return false;
            // Output storage accepts manual returns. Input accepts any item; a locked recipe
            // is enforced by processOneItem rather than preventing recovery from accidental removal.
        }

        @Override protected void onContentsChanged(int slot) {
            if (slot == 0 && lockedRecipe.isEmpty()) selectedRecipe = "";
            setChanged();
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
            super.deserializeNBT(registries, tag);
        }
    };

    private static class DynamicItemStackHandler extends ItemStackHandler {
        private DynamicItemStackHandler(int size) { super(size); }

        private void resize(int size) {
            int targetSize = Math.max(OUTPUT_START + PAGE_SIZE * 10, size);
            if (targetSize == stacks.size()) return;
            NonNullList<ItemStack> resized = NonNullList.withSize(targetSize, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(stacks.size(), targetSize); i++) {
                resized.set(i, stacks.get(i));
            }
            stacks = resized;
        }
    }

    private final IItemHandler topHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, false);
    private final IItemHandler bottomHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, false, true);
    private final IItemHandler sideHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, false);
    private final IItemHandler unsidedHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, true);

    public DeconstructionTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECONSTRUCTION_TABLE.get(), pos, state);
    }

    public ItemStackHandler getInventory() { return inventory; }

    /** Expands the real output inventory on demand; it never discards existing stacks. */
    public void ensureOutputInventorySize(int size) {
        if (size > inventory.getSlots()) {
            inventory.resize(size);
            setChanged();
        }
    }
    public boolean isAutoMode() { return autoMode; }
    public void setAutoMode(boolean enabled) { autoMode = enabled; setChanged(); }
    public String getSelectedRecipe() { return selectedRecipe; }
    public String getLockedRecipe() { return lockedRecipe; }

    public void setSelectedRecipe(@Nullable ResourceLocation recipeId) {
        selectedRecipe = recipeId == null ? "" : recipeId.toString();
        setChanged();
    }

    public void setLockedRecipe(@Nullable ResourceLocation recipeId) {
        lockedRecipe = recipeId == null ? "" : recipeId.toString();
        setChanged();
    }

    public void processOnce() {
        if (level != null && !level.isClientSide) processOneItem(false);
    }

    public void dropAllContents() {
        if (level == null || level.isClientSide) return;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override public Component getDisplayName() {
        return Component.translatable("container.whimsy_automation.deconstruction_table");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new DeconstructionTableMenu(id, playerInventory,
                new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(worldPosition));
    }

    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        if (side == null) return unsidedHandler;
        if (side == Direction.DOWN) return bottomHandler;
        if (side == Direction.UP) return topHandler;
        return sideHandler;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DeconstructionTableBlockEntity machine) {
        if (level.isClientSide || !machine.autoMode) return;
        // Batch processing makes a full hopper-fed stack progress in one tick
        // instead of requiring one ten-tick cycle per item.
        machine.processOneItem(true);
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

    private boolean matchesRecipeIdOutput(String recipeId, ItemStack input) {
        if (level == null || recipeId == null || recipeId.isEmpty() || input.isEmpty()) return false;
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!holder.id().toString().equals(recipeId)) continue;
            return matchesRecipeOutput(holder.value().getResultItem(level.registryAccess()), input);
        }
        return false;
    }

    private RecipeHolder<?> findRecipe(String recipeId) {
        if (level == null || recipeId == null || recipeId.isEmpty()) return null;
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (holder.id().toString().equals(recipeId)) return holder;
        }
        return null;
    }

    private void processOneItem(boolean automatic) {
        if (level == null) return;
        ItemStack input = inventory.getStackInSlot(0);
        if (input.isEmpty()) return;

        // A recipe must be explicitly selected. A locked recipe always takes
        // priority, and a mismatch is a hard stop: never fall back to another recipe.
        String chosenId = !lockedRecipe.isEmpty() ? lockedRecipe : selectedRecipe;
        RecipeHolder<?> selected = chosenId.isEmpty() ? null : findRecipe(chosenId);
        if (selected == null && lockedRecipe.isEmpty()) {
            List<RecipeHolder<?>> matches = new ArrayList<>();
            for (RecipeHolder<?> candidate : level.getRecipeManager().getRecipes()) {
                if (matchesRecipeOutput(candidate.value().getResultItem(level.registryAccess()), input)
                        && !dev.whimsyautomation.util.RecipeIngredientResolver.getIngredients(candidate.value()).isEmpty()) matches.add(candidate);
            }
            matches.sort((a, b) -> a.id().compareTo(b.id()));
            if (!matches.isEmpty()) {
                selected = matches.get(0);
                selectedRecipe = selected.id().toString();
            }
        }
        if (selected == null) {
            // In automatic mode, an item with no dismantling recipe must not
            // remain in the input slot and block an automation line. Pass the
            // original stack through the normal output inventory unchanged.
            // A locked recipe is intentionally excluded: its input filter
            // already rejects unrelated items, and a missing locked recipe
            // must never silently turn into a pass-through mode.
            if (automatic && lockedRecipe.isEmpty()) {
                passThroughInput(input);
            }
            return;
        }
        Recipe<?> recipe = selected.value();
        ItemStack recipeResult = recipe.getResultItem(level.registryAccess());
        if (!matchesRecipeOutput(recipeResult, input)) return;

        // Reverse dismantling is item-based, not craft-batch-based. A recipe
        // that outputs four planks must still accept a single plank; requiring
        // the full output count here strands valid items in the input slot.
        // Each input item independently yields the recipe's recovered ingredient
        // set, matching the table's unrestricted dismantling behavior.
        int processedItems = Math.min(input.getCount(), 64);
        if (processedItems <= 0) return;

        List<ItemStack> recoveredPerBatch = new ArrayList<>();
        for (Ingredient ingredient : dev.whimsyautomation.util.RecipeIngredientResolver.getIngredients(recipe)) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] options = ingredient.getItems();
            if (options.length == 0) return;
            recoveredPerBatch.add(options[0].copy());
        }

        // Enchantments travel with each dismantled item and are returned as books.
        ItemEnchantments itemEnchantments = input.is(Items.ENCHANTED_BOOK)
                ? input.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                : input.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            stored.set(entry.getKey(), entry.getIntValue());
            book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
            recoveredPerBatch.add(book);
        }
        if (recoveredPerBatch.isEmpty()) return;

        List<ItemStack> outputPlan = new ArrayList<>(Math.max(27, inventory.getSlots() - OUTPUT_START));
        for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++) {
            outputPlan.add(inventory.getStackInSlot(slot).copy());
        }

        // Scale the per-craft recovery list once, then insert each aggregated
        // result. This avoids cloning the entire output inventory for every batch.
        for (ItemStack result : recoveredPerBatch) {
            ItemStack total = result.copy();
            total.setCount(Math.multiplyExact(result.getCount(), processedItems));
            insertIntoOutputPlan(outputPlan, total);
        }

        inventory.resize(OUTPUT_START + outputPlan.size());
        for (int i = 0; i < outputPlan.size(); i++) {
            ItemStack before = inventory.getStackInSlot(OUTPUT_START + i);
            ItemStack after = outputPlan.get(i);
            if (!ItemStack.matches(before, after)) inventory.setStackInSlot(OUTPUT_START + i, after);
        }
        input.shrink(processedItems);
        inventory.setStackInSlot(0, input.isEmpty() ? ItemStack.EMPTY : input);
        setChanged();
    }
    private void passThroughInput(ItemStack input) {
        if (input.isEmpty()) return;

        List<ItemStack> outputPlan = new ArrayList<>(Math.max(27, inventory.getSlots() - OUTPUT_START));
        for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++) {
            outputPlan.add(inventory.getStackInSlot(slot).copy());
        }

        // insertIntoOutputPlan merges identical stacks and expands the output
        // inventory when necessary, so an unprocessable item is never trapped
        // merely because the visible output page is full.
        insertIntoOutputPlan(outputPlan, input.copy());

        inventory.resize(OUTPUT_START + outputPlan.size());
        for (int i = 0; i < outputPlan.size(); i++) {
            ItemStack before = inventory.getStackInSlot(OUTPUT_START + i);
            ItemStack after = outputPlan.get(i);
            if (!ItemStack.matches(before, after)) {
                inventory.setStackInSlot(OUTPUT_START + i, after);
            }
        }

        inventory.setStackInSlot(0, ItemStack.EMPTY);
        setChanged();
    }

    private boolean insertIntoOutputPlan(List<ItemStack> plan, ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        incoming.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, Integer.MAX_VALUE);
        for (ItemStack existing : plan) {
            if (!existing.isEmpty()) existing.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, Integer.MAX_VALUE);
            if (incoming.isEmpty()) return true;
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, incoming)) continue;
            int limit = Integer.MAX_VALUE;
            int moved = Math.min(incoming.getCount(), Math.max(0, limit - existing.getCount()));
            if (moved > 0) {
                existing.grow(moved);
                incoming.shrink(moved);
            }
        }
        for (int i = 0; i < plan.size() && !incoming.isEmpty(); i++) {
            if (!plan.get(i).isEmpty()) continue;
            int moved = incoming.getCount();
            plan.set(i, incoming.copyWithCount(moved));
            incoming.shrink(moved);
        }
        // The output inventory grows to fit large machine recipes instead of
        // rejecting an otherwise valid dismantle when all existing slots fill.
        while (!incoming.isEmpty()) {
            int moved = Math.min(incoming.getCount(), Math.min(incoming.getMaxStackSize(), inventory.getSlotLimit(OUTPUT_START)));
            plan.add(incoming.copyWithCount(moved));
            incoming.shrink(moved);
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putString("SelectedRecipe", selectedRecipe);
        tag.putString("LockedRecipe", lockedRecipe);
        tag.putInt("WorkTicks", workTicks);
        tag.putBoolean("AutoMode", autoMode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        int requiredOutputSize = OUTPUT_START + PAGE_SIZE * 10;
        for (int slot = inventory.getSlots() - 1; slot >= OUTPUT_START; slot--) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                requiredOutputSize = Math.max(requiredOutputSize, slot + 1);
                break;
            }
        }
        inventory.resize(requiredOutputSize);
        selectedRecipe = tag.getString("SelectedRecipe");
        lockedRecipe = tag.getString("LockedRecipe");
        workTicks = tag.getInt("WorkTicks");
        autoMode = tag.getBoolean("AutoMode");
    }
}