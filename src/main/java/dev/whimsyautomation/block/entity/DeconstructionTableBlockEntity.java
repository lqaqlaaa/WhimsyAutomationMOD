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
    public static final int OUTPUT_SLOTS = 108;

    private String selectedRecipe = "";
    private String lockedRecipe = "";
    private int workTicks;
    private boolean autoMode = false;

    private final ItemStackHandler inventory = new ItemStackHandler(OUTPUT_START + OUTPUT_SLOTS) {
        @Override public int getSlotLimit(int slot) { return 64; }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot != 0 || stack.isEmpty()) return false;
            // A locked recipe also constrains insertion, not just processing.
            // This prevents hoppers from filling the input with unrelated items.
            return lockedRecipe.isEmpty() || matchesRecipeIdOutput(lockedRecipe, stack);
        }

        @Override protected void onContentsChanged(int slot) {
            if (slot == 0 && lockedRecipe.isEmpty()) selectedRecipe = "";
            setChanged();
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
            CompoundTag migrated = tag.copy();
            migrated.putInt("Size", OUTPUT_START + OUTPUT_SLOTS);
            super.deserializeNBT(registries, migrated);
        }
    };

    private final IItemHandler topHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, false);
    private final IItemHandler bottomHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, false, true);
    private final IItemHandler sideHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, false);
    private final IItemHandler unsidedHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, true);

    public DeconstructionTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECONSTRUCTION_TABLE.get(), pos, state);
    }

    public ItemStackHandler getInventory() { return inventory; }
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
        if (++machine.workTicks < 10) return;
        machine.workTicks = 0;
        machine.processOneItem(true);
    }

    private static boolean matchesRecipeOutput(ItemStack recipeResult, ItemStack input) {
        if (recipeResult.isEmpty() || input.isEmpty() || !ItemStack.isSameItem(recipeResult, input)) return false;
        ItemStack expected = recipeResult.copy();
        ItemStack supplied = input.copy();
        // Durability is consumed by normal use and must not invalidate an otherwise
        // identical recipe output. Other data components remain significant.
        expected.remove(DataComponents.DAMAGE);
        supplied.remove(DataComponents.DAMAGE);
        expected.remove(DataComponents.ENCHANTMENTS);
        supplied.remove(DataComponents.ENCHANTMENTS);
        expected.remove(DataComponents.STORED_ENCHANTMENTS);
        supplied.remove(DataComponents.STORED_ENCHANTMENTS);
        return ItemStack.isSameItemSameComponents(expected, supplied);
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
                        && !candidate.value().getIngredients().isEmpty()) matches.add(candidate);
            }
            matches.sort((a, b) -> a.id().compareTo(b.id()));
            if (!matches.isEmpty()) {
                selected = matches.get(0);
                selectedRecipe = selected.id().toString();
            }
        }
        if (selected == null) return;
        Recipe<?> recipe = selected.value();

        ItemStack recipeResult = selected.value().getResultItem(level.registryAccess());
        if (!matchesRecipeOutput(recipeResult, input)) return;


        List<ItemStack> recovered = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] options = ingredient.getItems();
            if (options.length == 0) return;
            // The recipe API does not retain which tag alternative was originally
            // used in the crafted item. Until a player-selected alternative is
            // stored, use the first registered representative consistently.
            recovered.add(options[0].copy());
        }

        // Enchantments are item data, not recipe ingredients. Preserve every
        // enchantment as its own enchanted book when the source item is dismantled.
        ItemEnchantments itemEnchantments = input.is(Items.ENCHANTED_BOOK)
                ? input.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                : input.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            stored.set(entry.getKey(), entry.getIntValue());
            book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
            recovered.add(book);
        }
        if (recovered.isEmpty()) return;

        List<ItemStack> outputPlan = new ArrayList<>(OUTPUT_SLOTS);
        for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++) {
            outputPlan.add(inventory.getStackInSlot(slot).copy());
        }
        for (ItemStack result : recovered) {
            if (!insertIntoOutputPlan(outputPlan, result.copy())) return;
        }

        // Commit only after every ingredient fits. This prevents partial
        // output delivery followed by input consumption.
        for (int i = 0; i < outputPlan.size(); i++) {
            ItemStack before = inventory.getStackInSlot(OUTPUT_START + i);
            ItemStack after = outputPlan.get(i);
            if (!ItemStack.matches(before, after)) inventory.setStackInSlot(OUTPUT_START + i, after);
        }
        // One crafted output stack item represents one completed recipe batch,
        // regardless of the recipe's output stack size.
        input.shrink(1);
        inventory.setStackInSlot(0, input.isEmpty() ? ItemStack.EMPTY : input);
        setChanged();
    }
    private boolean insertIntoOutputPlan(List<ItemStack> plan, ItemStack incoming) {
        for (ItemStack existing : plan) {
            if (incoming.isEmpty()) return true;
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, incoming)) continue;
            int limit = Math.min(existing.getMaxStackSize(), inventory.getSlotLimit(OUTPUT_START));
            int moved = Math.min(incoming.getCount(), Math.max(0, limit - existing.getCount()));
            if (moved > 0) {
                existing.grow(moved);
                incoming.shrink(moved);
            }
        }
        for (int i = 0; i < plan.size() && !incoming.isEmpty(); i++) {
            if (!plan.get(i).isEmpty()) continue;
            int moved = Math.min(incoming.getCount(), Math.min(incoming.getMaxStackSize(), inventory.getSlotLimit(OUTPUT_START)));
            plan.set(i, incoming.copyWithCount(moved));
            incoming.shrink(moved);
        }
        return incoming.isEmpty();
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
        selectedRecipe = tag.getString("SelectedRecipe");
        lockedRecipe = tag.getString("LockedRecipe");
        workTicks = tag.getInt("WorkTicks");
        autoMode = tag.getBoolean("AutoMode");
    }
}