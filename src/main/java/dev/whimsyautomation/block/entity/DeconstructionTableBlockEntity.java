package dev.whimsyautomation.block.entity;

import dev.whimsyautomation.menu.DeconstructionTableMenu;
import dev.whimsyautomation.registry.ModBlockEntities;
import dev.whimsyautomation.util.SidedMachineItemHandler;
import dev.whimsyautomation.util.RecipeIngredientResolver;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/** Reverse-processes all registered recipes that expose a result and ingredients. */
public final class DeconstructionTableBlockEntity extends BlockEntity implements MenuProvider {
    public static final int OUTPUT_START = 1;
    public static final int OUTPUT_SLOTS = 108;

    private final ItemStackHandler inventory = new ItemStackHandler(OUTPUT_START + OUTPUT_SLOTS) {
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && !stack.isEmpty(); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };

    private final IItemHandler topHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, false);
    private final IItemHandler bottomHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, false, true);
    private final IItemHandler sideHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, false);
    private final IItemHandler unsidedHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, true);

    private String lockedRecipe = "";
    private int workTicks;
    private boolean autoMode = false;

    public boolean isAutoMode() { return autoMode; }
    public void setAutoMode(boolean enabled) { autoMode = enabled; setChanged(); }
    public void processOnce() { if (level != null && !level.isClientSide) processOneItem(); }
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

    public DeconstructionTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECONSTRUCTION_TABLE.get(), pos, state);
    }

    public ItemStackHandler getInventory() { return inventory; }

    @Override
    public Component getDisplayName() {
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

    public String getLockedRecipe() { return lockedRecipe; }

    public void setLockedRecipe(@Nullable ResourceLocation recipeId) {
        lockedRecipe = recipeId == null ? "" : recipeId.toString();
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DeconstructionTableBlockEntity machine) {
        if (level.isClientSide) return;
        if (!machine.autoMode) return;
        if (++machine.workTicks < 10) return;
        machine.workTicks = 0;
        machine.processOneItem();
    }

    private void processOneItem() {
        if (level == null) return;
        ItemStack input = inventory.getStackInSlot(0);
        if (input.isEmpty() || lockedRecipe.isEmpty()) return;

        List<RecipeHolder<?>> candidates = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!lockedRecipe.isEmpty() && !holder.id().toString().equals(lockedRecipe)) continue;
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            if (result.isEmpty() || !ItemStack.isSameItemSameComponents(result, input)) continue;
            if (RecipeIngredientResolver.getIngredients(holder.value()).isEmpty()) continue;
            candidates.add(holder);
        }
        if (candidates.isEmpty()) return;
        candidates.sort((a, b) -> a.id().compareTo(b.id()));

        RecipeHolder<?> selected = candidates.getFirst();
        ItemStack recipeResult = selected.value().getResultItem(level.registryAccess());
        int inputCost = Math.max(1, recipeResult.getCount());
        if (input.getCount() < inputCost) return;

        List<ItemStack> recovered = new ArrayList<>();
        for (Ingredient ingredient : RecipeIngredientResolver.getIngredients(selected.value())) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] options = ingredient.getItems();
            if (options.length == 0) return;
            recovered.add(options[0].copy());
        }
        if (recovered.isEmpty()) return;

        // Plan all output changes on copies first. Existing compatible stacks are
        // filled before empty slots, so repeated ingredients do not clutter pages.
        List<ItemStack> outputPlan = new ArrayList<>(OUTPUT_SLOTS);
        for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++) {
            outputPlan.add(inventory.getStackInSlot(slot).copy());
        }
        for (ItemStack result : recovered) {
            if (!insertIntoOutputPlan(outputPlan, result.copy())) return;
        }

        // Commit outputs and input consumption only after the complete plan fits.
        for (int i = 0; i < outputPlan.size(); i++) {
            ItemStack before = inventory.getStackInSlot(OUTPUT_START + i);
            ItemStack after = outputPlan.get(i);
            if (!ItemStack.matches(before, after)) {
                inventory.setStackInSlot(OUTPUT_START + i, after);
            }
        }
        input.shrink(inputCost);
        inventory.setStackInSlot(0, input.isEmpty() ? ItemStack.EMPTY : input);
        setChanged();
    }

    private boolean insertIntoOutputPlan(List<ItemStack> plan, ItemStack incoming) {
        // Merge with matching item+components first.
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
        // Then distribute any remainder across empty slots.
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
        tag.putString("LockedRecipe", lockedRecipe);
        tag.putInt("WorkTicks", workTicks);
        tag.putBoolean("AutoMode", autoMode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        lockedRecipe = tag.getString("LockedRecipe");
        workTicks = tag.getInt("WorkTicks");
        autoMode = tag.getBoolean("AutoMode");
    }
}
