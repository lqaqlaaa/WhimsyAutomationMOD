package dev.whimsyautomation.block.entity;

import dev.whimsyautomation.menu.DeconstructionTableMenu;
import dev.whimsyautomation.registry.ModBlockEntities;
import dev.whimsyautomation.util.SidedMachineItemHandler;
import dev.whimsyautomation.util.OversizedItemStackCodec;
import dev.whimsyautomation.util.PlayerStackRules;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Reverse-processes supported registered recipes. Recipe preview, recipe lock and
 * automation are deliberately separate states.
 */
public final class DeconstructionTableBlockEntity extends BlockEntity implements MenuProvider, SideConfigurableBlockEntity {
        
    
    public static final int OUTPUT_START = 1;
    private static final int PAGE_SIZE = 27;

    private String selectedRecipe = "";
    private String lockedRecipe = "";
    private int workTicks;
    private boolean autoMode = false;
    private final Map<String, List<Integer>> ingredientVariants = new HashMap<>();
    private final Map<String, List<List<ItemStack>>> jeiRecipeSnapshots = new HashMap<>();
    private final Map<String, ItemStack> jeiRecipeOutputs = new HashMap<>();
    private ItemStack jeiSnapshotInput = ItemStack.EMPTY;
    // 0=disabled, 1=input, 2=output; Direction ordinal order (DOWN, UP, NORTH, SOUTH, WEST, EAST).
    private final int[] sideModes = new int[] {0, 0, 0, 0, 0, 0};

    private final DynamicItemStackHandler inventory = new DynamicItemStackHandler(OUTPUT_START + PAGE_SIZE * 10) {
        @Override public int getSlotLimit(int slot) { return slot == 0 ? 64 : OversizedItemStackCodec.MAX_COUNT; }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty()) return false;
            // Output storage accepts manual returns. Input accepts any item; a locked recipe
            // is enforced by processOneItem rather than preventing recovery from accidental removal.
            return true;
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

        @Override protected int getStackLimit(int slot, ItemStack stack) {
            return slot == 0 ? 64 : OversizedItemStackCodec.MAX_COUNT;
        }

        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            ItemStack present = getStackInSlot(slot);
            int legalAmount = Math.min(amount, PlayerStackRules.getVanillaMaxStackSize(present));
            ItemStack extracted = super.extractItem(slot, legalAmount, simulate);
            return PlayerStackRules.copyForPlayer(extracted, extracted.getCount());
        }

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

    private final IItemHandler unsidedHandler = new SidedMachineItemHandler(inventory, OUTPUT_START, true, true);
    private final IItemHandler[] sidedHandlers = new IItemHandler[6];

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

    public int getIngredientVariant(String recipeId, int group) {
        List<Integer> values = ingredientVariants.get(recipeId);
        return values == null || group < 0 || group >= values.size() ? 0 : values.get(group);
    }

    public void cacheJeiRecipeSnapshot(String recipeId, ItemStack output, List<List<ItemStack>> groups, ItemStack forInput) {
        if (recipeId == null || recipeId.isBlank() || output == null || output.isEmpty()
                || groups == null || groups.isEmpty() || forInput == null || forInput.isEmpty()) return;
        if (jeiSnapshotInput.isEmpty() || !ItemStack.isSameItemSameComponents(jeiSnapshotInput, forInput)) {
            jeiRecipeSnapshots.clear();
            jeiRecipeOutputs.clear();
            jeiSnapshotInput = forInput.copyWithCount(1);
        }
        List<List<ItemStack>> copy = groups.stream()
                .map(group -> group.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList())
                .toList();
        if (copy.stream().anyMatch(List::isEmpty)) return;
        jeiRecipeSnapshots.put(recipeId, copy);
        jeiRecipeOutputs.put(recipeId, output.copyWithCount(1));
        setChanged();
    }

    public List<List<ItemStack>> getJeiRecipeSnapshot(String recipeId) {
        return jeiRecipeSnapshots.getOrDefault(recipeId, List.of());
    }

    public ItemStack getJeiRecipeOutput(String recipeId) {
        ItemStack output = jeiRecipeOutputs.get(recipeId);
        return output == null ? ItemStack.EMPTY : output.copy();
    }

    public Map<String, ItemStack> getJeiRecipeOutputs() {
        Map<String, ItemStack> copy = new HashMap<>();
        jeiRecipeOutputs.forEach((id, stack) -> copy.put(id, stack.copy()));
        return Map.copyOf(copy);
    }

    public void setIngredientVariant(String recipeId, int group, int variant) {
        if (recipeId == null || recipeId.isEmpty() || group < 0 || group >= 64 || variant < 0) return;
        List<Integer> values = new ArrayList<>(ingredientVariants.getOrDefault(recipeId, List.of()));
        while (values.size() <= group) values.add(0);
        values.set(group, variant);
        ingredientVariants.put(recipeId, List.copyOf(values));
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void setSelectedRecipe(@Nullable ResourceLocation recipeId) {
        selectedRecipe = recipeId == null ? "" : recipeId.toString();
        setChanged();
    }

    public void setSelectedRecipeId(String recipeId) {
        selectedRecipe = recipeId == null ? "" : recipeId;
        setChanged();
    }

    public void setLockedRecipe(@Nullable ResourceLocation recipeId) {
        lockedRecipe = recipeId == null ? "" : recipeId.toString();
        setChanged();
    }

    public void setLockedRecipeId(String recipeId) {
        lockedRecipe = recipeId == null ? "" : recipeId;
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
                int remaining = stack.getCount();
                int legalSize = Math.max(1, PlayerStackRules.getVanillaMaxStackSize(stack));
                while (remaining > 0) {
                    int batch = Math.min(remaining, legalSize);
                    Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                            worldPosition.getZ() + 0.5, PlayerStackRules.copyForPlayer(stack, batch));
                    remaining -= batch;
                }
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

    public int getSideMode(Direction side) { return sideModes[side.ordinal()]; }
    public void cycleSideMode(Direction side) {
        sideModes[side.ordinal()] = (sideModes[side.ordinal()] + 1) % 3;
        sidedHandlers[side.ordinal()] = null;
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        setChanged();
    }

    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        normalizeOutputInventory();
        if (side == null) return unsidedHandler;
        int index = side.ordinal();
        if (sidedHandlers[index] == null) {
            int mode = sideModes[index];
            sidedHandlers[index] = new SidedMachineItemHandler(inventory, OUTPUT_START, mode == 1, mode == 2);
        }
        return sidedHandlers[index];
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DeconstructionTableBlockEntity machine) {
        if (level.isClientSide) return;
        machine.normalizeOutputInventory();
        if (!machine.autoMode) return;
        // Batch processing makes a full hopper-fed stack progress in one tick
        // instead of requiring one ten-tick cycle per item.
        machine.processOneItem(true);
    }

    private static boolean matchesRecipeOutput(ItemStack recipeResult, ItemStack input) {
        return dev.whimsyautomation.util.RecipeOutputMatcher.matches(recipeResult, input);
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

        // The JEI material preview is the sole source of dismantling results.
        // No RecipeManager lookup or output/input matching is performed here.
        String chosenId = !lockedRecipe.isEmpty() ? lockedRecipe : selectedRecipe;
        List<List<ItemStack>> jeiGroups = getJeiRecipeSnapshot(chosenId);

        // If no recipe has ever been selected, use the same deterministic
        // first recipe order as the menu (recipe IDs are sorted there).
        // Never depend on HashMap iteration order: it can disagree with the
        // recipe currently shown in the preview.
        if (jeiGroups.isEmpty() && !jeiRecipeSnapshots.isEmpty()
                && (chosenId.isEmpty() || !jeiRecipeSnapshots.containsKey(chosenId))) {
            String firstId = jeiRecipeSnapshots.keySet().stream().sorted().findFirst().orElse("");
            if (!firstId.isEmpty()) {
                chosenId = firstId;
                jeiGroups = jeiRecipeSnapshots.getOrDefault(firstId, List.of());
                if (lockedRecipe.isEmpty()) selectedRecipe = chosenId;
            }
        }
        if (jeiGroups.isEmpty()) return;

        int processedItems = Math.min(input.getCount(), 64);
        if (processedItems <= 0) return;

        List<ItemStack> recoveredPerBatch = new ArrayList<>();
        for (int groupIndex = 0; groupIndex < jeiGroups.size(); groupIndex++) {
            List<ItemStack> options = jeiGroups.get(groupIndex);
            if (options.isEmpty()) continue;
            int variant = Math.floorMod(getIngredientVariant(chosenId, groupIndex), options.size());
            ItemStack displayed = options.get(variant).copy();
            if (!displayed.isEmpty()) recoveredPerBatch.add(displayed);
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
        normalizeOutputPlan(outputPlan);

        for (ItemStack result : recoveredPerBatch) {
            long remaining = (long) result.getCount() * processedItems;
            insertCountIntoOutputPlan(outputPlan, result, remaining);
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
        normalizeOutputPlan(outputPlan);

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

    /** Repairs legacy output entries and caps each internal machine stack at the shared oversized limit. */
    private void normalizeOutputInventory() {
        List<ItemStack> outputPlan = new ArrayList<>();
        for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++) {
            outputPlan.add(inventory.getStackInSlot(slot).copy());
        }
        normalizeOutputPlan(outputPlan);
        int oldSize = inventory.getSlots();
        inventory.resize(OUTPUT_START + outputPlan.size());
        if (inventory.getSlots() != oldSize) setChanged();
        for (int i = 0; i < outputPlan.size(); i++) {
            ItemStack before = inventory.getStackInSlot(OUTPUT_START + i);
            ItemStack after = outputPlan.get(i);
            if (!ItemStack.matches(before, after)) {
                inventory.setStackInSlot(OUTPUT_START + i, after);
            }
        }
    }

    private void normalizeOutputPlan(List<ItemStack> plan) {
        for (int i = 0; i < plan.size(); i++) {
            ItemStack stack = plan.get(i);
            if (stack.isEmpty()) continue;
            normalizeStackLimit(stack);
            int limit = OversizedItemStackCodec.MAX_COUNT;
            if (stack.getCount() <= limit) continue;

            long overflow = (long) stack.getCount() - limit;
            ItemStack template = stack.copyWithCount(1);
            stack.setCount(limit);
            insertCountIntoOutputPlan(plan, template, overflow);
        }
        // Trailing empty slots are reserved capacity, not content. Release
        // complete unused pages after an output has been extracted.
        while (!plan.isEmpty() && plan.get(plan.size() - 1).isEmpty())
            plan.remove(plan.size() - 1);
    }

    private boolean insertIntoOutputPlan(List<ItemStack> plan, ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        insertCountIntoOutputPlan(plan, incoming, incoming.getCount());
        return true;
    }

    /** Inserts aggregate machine output into oversized internal stacks without changing item components. */
    private void insertCountIntoOutputPlan(List<ItemStack> plan, ItemStack template, long amount) {
        if (template.isEmpty() || amount <= 0) return;

        ItemStack normalizedTemplate = template.copyWithCount(1);
        normalizeStackLimit(normalizedTemplate);
        int stackLimit = OversizedItemStackCodec.MAX_COUNT;
        int slotLimit = Math.max(1, inventory.getSlotLimit(OUTPUT_START));
        stackLimit = Math.min(stackLimit, slotLimit);
        long remaining = amount;

        // Merge into compatible stacks, respecting each stack's legal limit.
        for (ItemStack existing : plan) {
            if (remaining <= 0) return;
            if (existing.isEmpty()) continue;
            normalizeStackLimit(existing);
            if (!ItemStack.isSameItemSameComponents(existing, normalizedTemplate)) continue;

            int existingLimit = OversizedItemStackCodec.MAX_COUNT;
            existingLimit = Math.min(existingLimit, slotLimit);
            int space = Math.max(0, existingLimit - existing.getCount());
            int moved = (int) Math.min(remaining, (long) space);
            if (moved > 0) {
                existing.grow(moved);
                remaining -= moved;
            }
        }

        // Reuse empty slots before expanding the machine's output inventory.
        for (int i = 0; i < plan.size() && remaining > 0; i++) {
            if (!plan.get(i).isEmpty()) continue;
            int moved = (int) Math.min(remaining, (long) stackLimit);
            plan.set(i, normalizedTemplate.copyWithCount(moved));
            remaining -= moved;
        }

        // Large recipe yields are spread across ordinary legal stacks.
        while (remaining > 0) {
            int moved = (int) Math.min(remaining, (long) stackLimit);
            plan.add(normalizedTemplate.copyWithCount(moved));
            remaining -= moved;
        }
    }

    private static void normalizeStackLimit(ItemStack stack) {
        if (stack.isEmpty()) return;
        Integer declaredLimit = stack.get(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE);
        if (declaredLimit != null && (declaredLimit < 1 || declaredLimit > 99)) {
            stack.remove(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("OversizedInventory", OversizedItemStackCodec.serialize(inventory, registries));
        tag.putString("SelectedRecipe", selectedRecipe);
        tag.putString("LockedRecipe", lockedRecipe);
        tag.putInt("WorkTicks", workTicks);
        tag.putBoolean("AutoMode", autoMode);
        CompoundTag variants = new CompoundTag();
        for (var entry : ingredientVariants.entrySet()) {
            int[] values = entry.getValue().stream().mapToInt(Integer::intValue).toArray();
            variants.putIntArray(entry.getKey(), values);
        }
        tag.put("IngredientVariants", variants);
        CompoundTag jeiSnapshots = new CompoundTag();
        for (var entry : jeiRecipeSnapshots.entrySet()) {
            ListTag groups = new ListTag();
            for (List<ItemStack> options : entry.getValue()) {
                ListTag optionList = new ListTag();
                for (ItemStack stack : options) optionList.add(stack.save(registries));
                groups.add(optionList);
            }
            jeiSnapshots.put(entry.getKey(), groups);
        }
        tag.put("JeiRecipeSnapshots", jeiSnapshots);
        if (!jeiSnapshotInput.isEmpty()) tag.put("JeiSnapshotInput", jeiSnapshotInput.save(registries));
        CompoundTag jeiOutputs = new CompoundTag();
        for (var entry : jeiRecipeOutputs.entrySet()) jeiOutputs.put(entry.getKey(), entry.getValue().save(registries));
        tag.put("JeiRecipeOutputs", jeiOutputs);
        tag.putIntArray("SideModes", sideModes);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("OversizedInventory")) {
            CompoundTag oversized = tag.getCompound("OversizedInventory");
            inventory.resize(Math.max(OUTPUT_START + PAGE_SIZE * 10, oversized.getInt("Size")));
            OversizedItemStackCodec.deserialize(inventory, registries, oversized);
        } else if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
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
        ingredientVariants.clear();
        jeiRecipeSnapshots.clear();
        jeiRecipeOutputs.clear();
        jeiSnapshotInput = tag.contains("JeiSnapshotInput")
                ? ItemStack.parse(registries, tag.getCompound("JeiSnapshotInput")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        if (tag.contains("JeiRecipeOutputs")) {
            CompoundTag savedOutputs = tag.getCompound("JeiRecipeOutputs");
            for (String recipeId : savedOutputs.getAllKeys())
                ItemStack.parse(registries, savedOutputs.getCompound(recipeId))
                        .ifPresent(stack -> jeiRecipeOutputs.put(recipeId, stack));
        }
        if (tag.contains("JeiRecipeSnapshots")) {
            CompoundTag savedSnapshots = tag.getCompound("JeiRecipeSnapshots");
            for (String recipeId : savedSnapshots.getAllKeys()) {
                ListTag savedGroups = savedSnapshots.getList(recipeId, 9);
                List<List<ItemStack>> groups = new ArrayList<>();
                for (int groupIndex = 0; groupIndex < savedGroups.size() && groupIndex < 64; groupIndex++) {
                    ListTag savedOptions = savedGroups.getList(groupIndex);
                    List<ItemStack> options = new ArrayList<>();
                    for (int optionIndex = 0; optionIndex < savedOptions.size() && optionIndex < 128; optionIndex++) {
                        ItemStack.parse(registries, savedOptions.getCompound(optionIndex)).ifPresent(options::add);
                    }
                    if (!options.isEmpty()) groups.add(List.copyOf(options));
                }
                if (!groups.isEmpty()) jeiRecipeSnapshots.put(recipeId, List.copyOf(groups));
            }
        }
        int[] savedSideModes = tag.getIntArray("SideModes");
        for (int i = 0; i < Math.min(savedSideModes.length, sideModes.length); i++) sideModes[i] = Math.max(0, Math.min(2, savedSideModes[i]));
        java.util.Arrays.fill(sidedHandlers, null);
        if (tag.contains("IngredientVariants")) {
            CompoundTag variants = tag.getCompound("IngredientVariants");
            for (String key : variants.getAllKeys()) {
                int[] values = variants.getIntArray(key);
                List<Integer> stored = new ArrayList<>(values.length);
                for (int value : values) stored.add(Math.max(0, value));
                ingredientVariants.put(key, List.copyOf(stored));
            }
        }
    }
    
    
    
    
}
