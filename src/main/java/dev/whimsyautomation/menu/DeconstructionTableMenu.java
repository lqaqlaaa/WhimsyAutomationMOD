package dev.whimsyautomation.menu;

import dev.whimsyautomation.block.entity.DeconstructionTableBlockEntity;
import dev.whimsyautomation.registry.ModBlocks;
import dev.whimsyautomation.registry.ModMenus;
import dev.whimsyautomation.util.PlayerStackRules;
import dev.whimsyautomation.network.DeconstructionRecipeSnapshotPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ClickType;
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
    private final BlockPos machinePos;
    private final ContainerLevelAccess access;
    private final Level level;
    private final IItemHandler pageHandler;
    private final ItemStack[] clientPageStacks = new ItemStack[PAGE_SIZE];
    private int syncedOutputSlotCount = 1 + PAGE_SIZE * MIN_OUTPUT_PAGES;
    private int syncedRecipeIndex = -1;
    private int syncedPage;
    private int syncedPreviewPage;
    private boolean syncedAutoMode;
    private boolean syncedLocked;
    private int syncedLockedRecipeIndex = -1;
    private ItemStack cachedCandidateInput = ItemStack.EMPTY;
    private List<Candidate> cachedCandidateRecipes = List.of();
    private List<dev.whimsyautomation.integration.jei.WhimsyJeiPlugin.Entry> cachedJeiEntries = List.of();
    private List<RecipeHolder<?>> cachedAllRecipes;
    private ItemStack cachedPreviewInput = ItemStack.EMPTY;
    private String cachedPreviewRecipeId = "";
    private List<ItemStack> cachedPreviewStacks = List.of();
    private boolean quickMoveSingle;
    private final int[] syncedIngredientVariants = new int[32];
    private final int[] syncedSideModes = new int[] {0, 0, 0, 0, 0, 0};

    public DeconstructionTableMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(id, playerInventory, data.readBlockPos());
    }

    public DeconstructionTableMenu(int id, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.DECONSTRUCTION_TABLE.get(), id);
        this.level = playerInventory.player.level();
        this.machinePos = pos.immutable();
        this.machine = level.getBlockEntity(pos) instanceof DeconstructionTableBlockEntity be ? be : null;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        java.util.Arrays.fill(clientPageStacks, ItemStack.EMPTY);
        this.pageHandler = new IItemHandlerModifiable() {
            private int actualSlot(int pageSlot) { return OUTPUT_START + syncedPage * PAGE_SIZE + pageSlot; }
            @Override public int getSlots() { return PAGE_SIZE; }
            @Override public void setStackInSlot(int slot, ItemStack stack) {
                if (machine == null || slot < 0 || slot >= PAGE_SIZE) return;
                if (level.isClientSide) {
                    clientPageStacks[slot] = stack.copy();
                    return;
                }
                int actual = actualSlot(slot);
                machine.ensureOutputInventorySize(actual + 1);
                machine.getInventory().setStackInSlot(actual, stack);
            }
            @Override public ItemStack getStackInSlot(int slot) {
                if (machine == null || slot < 0 || slot >= PAGE_SIZE) return ItemStack.EMPTY;
                if (level.isClientSide) return clientPageStacks[slot];
                int actual = actualSlot(slot);
                machine.ensureOutputInventorySize(actual + 1);
                return machine.getInventory().getStackInSlot(actual);
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (machine == null || slot < 0 || slot >= PAGE_SIZE) return stack;
                if (level.isClientSide) return stack;
                int actual = actualSlot(slot);
                machine.ensureOutputInventorySize(actual + 1);
                return machine.getInventory().insertItem(actual, stack, simulate);
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (machine == null || slot < 0 || slot >= PAGE_SIZE) return ItemStack.EMPTY;
                if (level.isClientSide) {
                    ItemStack cached = clientPageStacks[slot];
                    if (cached.isEmpty()) return ItemStack.EMPTY;
                    ItemStack extracted = PlayerStackRules.copyForPlayer(cached, amount);
                    int extractedCount = extracted.getCount();
                    if (!simulate) {
                        cached.shrink(extractedCount);
                        if (cached.isEmpty()) clientPageStacks[slot] = ItemStack.EMPTY;
                    }
                    return extracted;
                }
                int actual = actualSlot(slot);
                machine.ensureOutputInventorySize(actual + 1);
                return machine.getInventory().extractItem(actual, amount, simulate);
            }
            @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return !stack.isEmpty(); }
        };
        if (machine != null) {
            addSlot(new SlotItemHandler(machine.getInventory(), 0, 184, 53));
        } else {
            addSlot(new Slot(new net.minecraft.world.SimpleContainer(1), 0, 184, 53));
        }
        for (int i = 0; i < PAGE_SIZE; i++) addPagedOutputSlot(i);
        addPlayerInventory(playerInventory, 8, 139);
        addDataSlot(new DataSlot() {
            @Override public int get() { return getServerRecipeIndex(); }
            @Override public void set(int value) { syncedRecipeIndex = value; }
        });
        addIntDataSlots(() -> syncedPage, value -> syncedPage = Math.max(0, value));
        addIntDataSlots(() -> syncedPreviewPage, value -> syncedPreviewPage = Math.max(0, value));
        addIntDataSlots(
                () -> machine == null ? OUTPUT_START + PAGE_SIZE * MIN_OUTPUT_PAGES : machine.getInventory().getSlots(),
                value -> syncedOutputSlotCount = Math.max(OUTPUT_START + PAGE_SIZE * MIN_OUTPUT_PAGES, value));
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
        for (int group = 0; group < syncedIngredientVariants.length; group++) {
            final int groupIndex = group;
            addDataSlot(new DataSlot() {
                @Override public int get() {
                    Candidate recipe = getPreviewRecipe();
                    return machine == null || recipe == null ? 0
                            : machine.getIngredientVariant(recipe.id(), groupIndex);
                }
                @Override public void set(int value) { syncedIngredientVariants[groupIndex] = Math.max(0, value); }
            });
        }
        for (int side = 0; side < 6; side++) {
            final int sideIndex = side;
            addDataSlot(new DataSlot() {
                @Override public int get() { return machine == null ? 0 : machine.getSideMode(Direction.values()[sideIndex]); }
                @Override public void set(int value) { syncedSideModes[sideIndex] = Math.max(0, Math.min(2, value)); }
            });
        }
    }

    /**
     * DataSlot carries a signed short. Pairing two slots preserves a complete
     * 32-bit page/capacity value instead of truncating storage at 32767 slots.
     */
    private void addIntDataSlots(java.util.function.IntSupplier getter,
                                 java.util.function.IntConsumer setter) {
        final int[] parts = new int[2];
        addDataSlot(new DataSlot() {
            @Override public int get() { return (getter.getAsInt() >>> 16) & 0xFFFF; }
            @Override public void set(int value) {
                parts[0] = value & 0xFFFF;
                setter.accept((parts[0] << 16) | parts[1]);
            }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return getter.getAsInt() & 0xFFFF; }
            @Override public void set(int value) {
                parts[1] = value & 0xFFFF;
                setter.accept((parts[0] << 16) | parts[1]);
            }
        });
    }

    private void addPagedOutputSlot(int outputIndex) {
        int x = 8 + (outputIndex % 9) * 18;
        int y = 71 + (outputIndex / 9) * 18;
        addSlot(new SlotItemHandler(pageHandler, outputIndex, x, y));
    }

    private void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, x + col * 18, y + 58));
    }

    public void invalidateCandidateCache() {
        cachedCandidateInput = ItemStack.EMPTY;
        cachedCandidateRecipes = List.of();
        cachedJeiEntries = List.of();
    }

    private static boolean matchesRecipeOutput(ItemStack recipeResult, ItemStack input) {
        return dev.whimsyautomation.util.RecipeOutputMatcher.matches(recipeResult, input);
    }

    private List<Candidate> candidates() {
        ItemStack input = slots.isEmpty() ? ItemStack.EMPTY : slots.get(0).getItem();
        if (ItemStack.matches(cachedCandidateInput, input)) return cachedCandidateRecipes;
        cachedCandidateInput = input.copy();
        if (input.isEmpty()) { cachedJeiEntries = List.of(); cachedCandidateRecipes = List.of(); return cachedCandidateRecipes; }
        java.util.Map<String, Candidate> found = new java.util.LinkedHashMap<>();
        if (level.isClientSide && dev.whimsyautomation.integration.jei.WhimsyJeiPlugin.isAvailable()) {
            cachedJeiEntries = dev.whimsyautomation.integration.jei.WhimsyJeiPlugin.findByOutput(input);
            for (var entry : cachedJeiEntries) {
                if (entry.output().isEmpty() || entry.inputGroups().isEmpty()) continue;
                Candidate candidate = new Candidate(entry.recipeId(), entry.output(), entry.inputGroups(), null);
                found.putIfAbsent(candidate.id(), candidate);
                DeconstructionRecipeSnapshotPayload.send(containerId, machinePos, entry.recipeId(), entry.output(), entry.inputGroups());
            }
        } else {
            cachedJeiEntries = List.of();
            if (machine != null) {
                for (var snapshot : machine.getJeiRecipeOutputs().entrySet()) {
                    List<List<ItemStack>> groups = machine.getJeiRecipeSnapshot(snapshot.getKey());
                    if (!groups.isEmpty())
                        found.put(snapshot.getKey(), new Candidate(snapshot.getKey(), snapshot.getValue(), groups, null));
                }
            }
            for (RecipeHolder<?> holder : allRecipes()) {
                ItemStack output = holder.value().getResultItem(level.registryAccess());
                if (matchesRecipeOutput(output, input))
                    found.putIfAbsent(holder.id().toString(), new Candidate(holder.id().toString(), output, List.of(), holder));
            }
        }
        List<Candidate> sorted = new ArrayList<>(found.values());
        sorted.sort((a,b) -> a.id().compareTo(b.id()));
        cachedCandidateRecipes = List.copyOf(sorted);
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
        List<Candidate> all = candidates();
        for (int i = 0; i < all.size(); i++) if (all.get(i).id().equals(machine.getLockedRecipe())) return i;
        return -1;
    }
    private Candidate getLockedRecipeHolder() {
        List<Candidate> found = candidates();
        if (level.isClientSide) {
            // The client has no BlockEntity reference. Resolve the server-synced
            // locked candidate index instead of comparing against an empty ID.
            int index = syncedLockedRecipeIndex;
            return index >= 0 && index < found.size() ? found.get(index) : null;
        }
        String lockedId = machine == null ? "" : machine.getLockedRecipe();
        for (Candidate candidate : found) {
            if (candidate.id().equals(lockedId)) return candidate;
        }
        return null;
    }

    private int getServerRecipeIndex() {
        if (machine == null) return -1;
        List<Candidate> found = candidates();
        if (found.isEmpty()) return -1;
        if (machine.getSelectedRecipe().isEmpty()) return 0;
        for (int i = 0; i < found.size(); i++) if (found.get(i).id().equals(machine.getSelectedRecipe())) return i;
        return -1;
    }
    private Candidate getPreviewRecipe() {
        if (level.isClientSide ? syncedLocked : machine != null && !machine.getLockedRecipe().isEmpty()) return getLockedRecipeHolder();
        List<Candidate> found = candidates();
        if (found.isEmpty()) return null;
        int index = level.isClientSide ? syncedRecipeIndex : getServerRecipeIndex();
        if (index < 0 || index >= found.size()) index = 0;
        return found.get(index);
    }

    /** Updates the client preview immediately; the server selection remains authoritative. */
    public void cyclePreview(int delta) {
        List<Candidate> found = candidates();
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

    private dev.whimsyautomation.integration.jei.WhimsyJeiPlugin.Entry getJeiPreviewEntry() {
        Candidate candidate = getPreviewRecipe();
        if (candidate == null || candidate.inputGroups().isEmpty()) return null;
        for (var entry : cachedJeiEntries) if (entry.recipeId().equals(candidate.id())) return entry;
        return new dev.whimsyautomation.integration.jei.WhimsyJeiPlugin.Entry(candidate.id(), "", Component.empty(), candidate.output(), candidate.inputGroups());
    }
    private record Candidate(String id, ItemStack output, List<List<ItemStack>> inputGroups, RecipeHolder<?> holder) {
        private Candidate { output = output.copy(); inputGroups = inputGroups.stream().map(g -> g.stream().map(ItemStack::copy).toList()).toList(); }
    }
    private record PreviewEntry(ItemStack stack, List<Integer> groupIndices) {}

    /**
     * Builds the visual ingredient grid while retaining the logical ingredient
     * group IDs. Repeated identical ingredients occupy one slot and aggregate
     * their real recipe count; their alternative selector still addresses all
     * equivalent logical groups.
     */
    private List<PreviewEntry> getPreviewEntries() {
        Candidate candidate = getPreviewRecipe();
        if (candidate == null) return List.of();

        List<PreviewEntry> merged = new ArrayList<>();
        var jeiEntry = getJeiPreviewEntry();
        if (jeiEntry != null) {
            for (int groupIndex = 0; groupIndex < jeiEntry.inputGroups().size(); groupIndex++) {
                List<ItemStack> options = jeiEntry.inputGroups().get(groupIndex);
                if (options.isEmpty()) continue;
                int variant = Math.floorMod(getSelectedIngredientVariant(groupIndex), options.size());
                addPreviewEntry(merged, options.get(variant).copy(), groupIndex);
            }
        } else if (candidate.holder() != null) {
            List<dev.whimsyautomation.util.RecipeIngredientResolver.IngredientGroup> groups =
                    dev.whimsyautomation.util.RecipeIngredientResolver.getGroups(candidate.holder().value());
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                var group = groups.get(groupIndex);
                ItemStack[] choices = group.ingredient().getItems();
                if (choices.length == 0) continue;
                int variant = getSelectedIngredientVariant(groupIndex);
                ItemStack selected = choices[Math.min(variant, choices.length - 1)].copy();
                selected.setCount(group.count());
                addPreviewEntry(merged, selected, groupIndex);
            }
        }
        return List.copyOf(merged);
    }

    private static void addPreviewEntry(List<PreviewEntry> entries, ItemStack candidate, int groupIndex) {
        if (candidate.isEmpty()) return;
        for (int i = 0; i < entries.size(); i++) {
            PreviewEntry existing = entries.get(i);
            if (ItemStack.isSameItemSameComponents(existing.stack(), candidate)) {
                ItemStack combined = existing.stack().copy();
                combined.grow(candidate.getCount());
                List<Integer> groups = new ArrayList<>(existing.groupIndices());
                groups.add(groupIndex);
                entries.set(i, new PreviewEntry(combined, List.copyOf(groups)));
                return;
            }
        }
        entries.add(new PreviewEntry(candidate.copy(), List.of(groupIndex)));
    }

    private List<ItemStack> getAllPreviewStacks() {
        return getPreviewEntries().stream().map(entry -> entry.stack().copy()).toList();
    }

    /** Maps a visible grid slot back to the underlying recipe ingredient group. */
    public int getPreviewGroupIndex(int displayIndex) {
        List<PreviewEntry> entries = getPreviewEntries();
        if (displayIndex < 0 || displayIndex >= entries.size()) return -1;
        // A merged display slot can represent several logical ingredient
        // groups. Open the picker for an actually replaceable group, not
        // blindly the first group (which may have only one option).
        for (int group : entries.get(displayIndex).groupIndices()) {
            if (getIngredientOptions(group).length > 1) return group;
        }
        return entries.get(displayIndex).groupIndices().get(0);
    }

    public boolean previewHasAlternatives(int displayIndex) {
        List<PreviewEntry> entries = getPreviewEntries();
        if (displayIndex < 0 || displayIndex >= entries.size()) return false;
        for (int group : entries.get(displayIndex).groupIndices()) {
            if (getIngredientOptions(group).length > 1) return true;
        }
        return false;
    }

    private List<Integer> getPreviewGroupIndices(int groupIndex) {
        for (PreviewEntry entry : getPreviewEntries()) {
            if (entry.groupIndices().contains(groupIndex)) return entry.groupIndices();
        }
        return List.of(groupIndex);
    }

    public List<dev.whimsyautomation.util.RecipeIngredientResolver.IngredientGroup> getPreviewIngredientGroups() {
        Candidate candidate = getPreviewRecipe();
        return candidate == null || candidate.holder() == null ? List.of() : dev.whimsyautomation.util.RecipeIngredientResolver.getGroups(candidate.holder().value());
    }

    public ItemStack[] getIngredientOptions(int groupIndex) {
        var jeiEntry = getJeiPreviewEntry();
        if (jeiEntry != null) {
            if (groupIndex < 0 || groupIndex >= jeiEntry.inputGroups().size()) return new ItemStack[0];
            return jeiEntry.inputGroups().get(groupIndex).stream().map(ItemStack::copy).toArray(ItemStack[]::new);
        }
        List<dev.whimsyautomation.util.RecipeIngredientResolver.IngredientGroup> groups = getPreviewIngredientGroups();
        if (groupIndex < 0 || groupIndex >= groups.size()) return new ItemStack[0];
        return groups.get(groupIndex).ingredient().getItems();
    }

    public int getSelectedIngredientVariant(int groupIndex) {
        if (groupIndex < 0 || groupIndex >= syncedIngredientVariants.length) return 0;
        return syncedIngredientVariants[groupIndex];
    }

    public void chooseIngredientVariant(int groupIndex, int variant) {
        if (minecraftServerSide() && machine != null) {
            Candidate holder = getPreviewRecipe();
            if (holder != null) machine.setIngredientVariant(holder.id(), groupIndex, variant);
        }
    }

    private boolean minecraftServerSide() { return !level.isClientSide; }
    public List<ItemStack> getPreviewStacks() {
        List<ItemStack> all = getAllPreviewStacks();
        int start = getPreviewPage() * 27;
        int end = Math.min(all.size(), start + 27);
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
        return Math.max(1, (getAllPreviewStacks().size() + 26) / 27);
    }

    public ItemStack getPreviewResult() {
        var jeiEntry = getJeiPreviewEntry();
        if (jeiEntry != null) return jeiEntry.output().copy();
        Candidate recipe = getPreviewRecipe();
        return recipe == null ? ItemStack.EMPTY : recipe.output().copy();
    }

    public int getCandidateCount() {
        return candidates().size();
    }

    /** One-based position of the currently displayed recipe among switchable candidates. */
    public int getSelectedRecipePosition() {
        List<Candidate> found = candidates();
        if (found.isEmpty()) return 0;
        if (syncedLocked) {
            Candidate locked = getLockedRecipeHolder();
            if (locked != null) {
                for (int i = 0; i < found.size(); i++) {
                    if (found.get(i).id().equals(locked.id())) return i + 1;
                }
            }
        }
        int index = level.isClientSide ? syncedRecipeIndex : getServerRecipeIndex();
        if (index < 0 || index >= found.size()) index = 0;
        return index + 1;
    }

    public String getPreviewRecipeId() {
        Candidate recipe = getPreviewRecipe();
        return recipe == null ? "" : recipe.id();
    }

    public String getRecipeStatus() {
        if (syncedLocked) {
            Candidate locked = getLockedRecipeHolder();
            if (locked == null) return Component.translatable("screen.whimsy_automation.recipe_locked_missing").getString();
            ItemStack input = slots.isEmpty() ? ItemStack.EMPTY : slots.get(0).getItem();
            if (!input.isEmpty() && !matchesRecipeOutput(locked.output(), input)) {
                return Component.translatable("screen.whimsy_automation.recipe_locked_count", candidates().size()).getString();
            }
            return Component.translatable("screen.whimsy_automation.recipe_locked_count", candidates().size()).getString();
        }
        List<Candidate> found = candidates();
        if (found.isEmpty()) return Component.translatable("screen.whimsy_automation.recipe_none").getString();
        if (getPreviewRecipe() == null) return Component.translatable("screen.whimsy_automation.recipe_count", found.size()).getString();
        return Component.translatable("screen.whimsy_automation.recipe_count", found.size()).getString();
    }

    public int getPage() { return Math.min(syncedPage, getPageCount() - 1); }
    public int getPageCount() {
        int slotsCount = machine == null || level.isClientSide
                ? syncedOutputSlotCount : machine.getInventory().getSlots();
        return Math.max(1, (int) (((long) Math.max(PAGE_SIZE, slotsCount - OUTPUT_START) + PAGE_SIZE - 1L) / PAGE_SIZE));
    }
    public boolean isAutoMode() { return syncedAutoMode; }
    public boolean isLocked() { return syncedLocked; }
    public int getSideMode(int side) { return side < 0 || side >= 6 ? 0 : syncedSideModes[side]; }
    

    @Override public boolean clickMenuButton(Player player, int buttonId) {
        if (machine == null || player.level().isClientSide) return false;
        if (buttonId < 0) {
            int encoded = -buttonId - 1;
            int targetPage = encoded >>> 1;
            if ((encoded & 1) == 0) syncedPage = Math.max(0, Math.min(getPageCount() - 1, targetPage));
            else syncedPreviewPage = Math.max(0, Math.min(getPreviewPageCount() - 1, targetPage));
            return true;
        }
        if (buttonId >= 10 && buttonId <= 15) { machine.cycleSideMode(Direction.values()[buttonId - 10]); return true; }
        if (buttonId >= 1000) {
            int encoded = buttonId - 1000;
            int group = encoded / 32768;
            int variant = encoded % 32768;
            Candidate holder = getPreviewRecipe();
            if (holder == null || group < 0 || group >= 32) return false;
            ItemStack[] options = getIngredientOptions(group);
            if (variant < 0 || variant >= options.length) return false;
            // A merged visual slot can represent several repeated recipe
            // positions. Keep their selected alternative synchronized.
            for (int groupedIndex : getPreviewGroupIndices(group)) {
                ItemStack[] groupedOptions = getIngredientOptions(groupedIndex);
                if (groupedOptions.length != options.length) continue;
                boolean sameOptions = true;
                for (int optionIndex = 0; optionIndex < options.length; optionIndex++) {
                    if (!ItemStack.isSameItemSameComponents(groupedOptions[optionIndex], options[optionIndex])) {
                        sameOptions = false;
                        break;
                    }
                }
                if (sameOptions) machine.setIngredientVariant(holder.id(), groupedIndex, variant);
            }
            return true;
        }
        if (buttonId == 2) {
            boolean enabling = !machine.isAutoMode();
            if (enabling && machine.getLockedRecipe().isEmpty()) {
                // Bind automation to the exact recipe currently shown by the
                // preview, rather than an arbitrary cached snapshot.
                Candidate preview = getPreviewRecipe();
                if (preview != null) machine.setSelectedRecipeId(preview.id());
            }
            machine.setAutoMode(enabling);
            return true;
        }
        if (buttonId == 3) {
            // The preview defaults to the first matching recipe even before
            // the player cycles the recipe buttons. Commit that exact recipe
            // before processing; otherwise the block entity may independently
            // choose a different same-output recipe with no cached JEI inputs.
            Candidate selected = getPreviewRecipe();
            if (selected != null) machine.setSelectedRecipeId(selected.id());
            machine.processOnce();
            return true;
        }
        if (buttonId == 4) { syncedPage = Math.max(0, syncedPage - 1); return true; }
        if (buttonId == 5) { syncedPage = Math.min(getPageCount() - 1, syncedPage + 1); return true; }
        if (buttonId == 6) {
            if (!machine.getLockedRecipe().isEmpty()) {
                machine.setLockedRecipe(null);
            } else {
                Candidate selected = getPreviewRecipe();
                if (selected == null) return false;
                // Keep the ordinary selection aligned with the recipe being
                // locked, so unlocking returns to the same visible preview.
                machine.setSelectedRecipeId(selected.id());
                machine.setLockedRecipeId(selected.id());
            }
            return true;
        }
        if (buttonId != 0 && buttonId != 1) return false;
        List<Candidate> found = candidates();
        if (found.isEmpty()) return false;
        int index = getServerRecipeIndex();
        if (index < 0) index = buttonId == 0 ? found.size() - 1 : 0;
        else index = Math.floorMod(index + (buttonId == 0 ? -1 : 1), found.size());
        machine.setSelectedRecipeId(found.get(index).id());
        syncedPreviewPage = 0;
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Shift + right-click extracts one item; shift + left-click moves the full stack.
        quickMoveSingle = clickType == ClickType.QUICK_MOVE && button == 1;
        try { super.clicked(slotId, button, clickType, player); }
        finally { quickMoveSingle = false; }
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index >= OUTPUT_START && index < MACHINE_SLOT_COUNT) {
            int actualOutput = OUTPUT_START + syncedPage * PAGE_SIZE + (index - OUTPUT_START);
            int requested = quickMoveSingle ? 1 : source.getCount();
            ItemStack moving = PlayerStackRules.copyForPlayer(source, requested);
            int offeredCount = moving.getCount();
            if (!moveItemStackTo(moving, MACHINE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
            int movedCount = offeredCount - moving.getCount();
            if (movedCount <= 0) return ItemStack.EMPTY;
            // Only the server mutates the block entity. The client predicts the
            // destination transfer and waits for authoritative slot synchronization.
            if (!player.level().isClientSide) {
                ItemStack extracted = machine.getInventory().extractItem(actualOutput, movedCount, false);
                if (extracted.getCount() != movedCount) return ItemStack.EMPTY;
            }
            return PlayerStackRules.copyForPlayer(copy, movedCount);
        }
        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(source, MACHINE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return machine != null && stillValid(access, player, ModBlocks.DECONSTRUCTION_TABLE.get()); }
    public DeconstructionTableBlockEntity machine() { return machine; }
    public boolean matchesMachinePosition(BlockPos pos) { return machinePos.equals(pos); }
}