package dev.whimsyautomation.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the same JEI recipe index used by the R-key recipe browser.
 *
 * Inputs are retained as JEI recipe slots (one group per displayed slot), not
 * flattened into a single list. A slot may contain several interchangeable
 * ItemStacks, and those alternatives must remain together.
 */
@JeiPlugin
public final class WhimsyJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("whimsy_automation", "recipe_catalog");
    private static volatile IJeiRuntime runtime;
    private static volatile List<IRecipeCategory<?>> categories = List.of();

    public record Entry(String recipeId, String categoryId, Component categoryTitle,
                        ItemStack output, List<List<ItemStack>> inputGroups) {
        public Entry {
            output = output.copy();
            inputGroups = inputGroups.stream()
                    .map(group -> group.stream().map(ItemStack::copy).toList())
                    .toList();
        }

        /** Flattened compatibility view; consumers needing alternatives use inputGroups(). */
        public List<ItemStack> inputs() {
            return inputGroups.stream().flatMap(List::stream).map(ItemStack::copy).toList();
        }
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<IRecipeCategory<?>> loaded = (List) jeiRuntime.getRecipeManager()
                .createRecipeCategoryLookup().includeHidden().get().toList();
        categories = List.copyOf(loaded);
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        categories = List.of();
    }

    public static boolean isAvailable() {
        return runtime != null;
    }

    /**
     * Queries JEI with the complete input stack as OUTPUT focus. In particular,
     * data components are retained for JEI subtype matching.
     */
    public static List<Entry> findByOutput(ItemStack target) {
        IJeiRuntime active = runtime;
        if (active == null || target.isEmpty()) return List.of();

        ItemStack focusStack = target.copyWithCount(1);
        IFocus<ItemStack> focus = active.getJeiHelpers().getFocusFactory()
                .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, focusStack);
        IRecipeManager manager = active.getRecipeManager();
        Map<String, Entry> found = new LinkedHashMap<>();

        for (IRecipeCategory<?> category : categories) {
            collectFocusedMatches(active, manager, category, focus, found);
        }
        return List.copyOf(found.values());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void collectFocusedMatches(IJeiRuntime active, IRecipeManager manager,
                                              IRecipeCategory category, IFocus<ItemStack> focus,
                                              Map<String, Entry> found) {
        manager.createRecipeLookup(category.getRecipeType())
                .limitFocus(List.of(focus))
                .includeHidden()
                .get()
                .forEach(recipe -> {
                    ItemStack output = null;
                    List<List<ItemStack>> inputGroups = new ArrayList<>();

                    // The ingredient supplier is intentionally not used here:
                    // it flattens ingredients and loses recipe-slot boundaries.
                    // The drawable layout exposes each JEI slot and all of its
                    // alternatives, matching what JEI actually displays.
                    IRecipeLayoutDrawable layout = (IRecipeLayoutDrawable) manager.createRecipeLayoutDrawable(
                            category, recipe, active.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElse(null);
                    if (layout == null) return;

                    for (IRecipeSlotView slot : layout.getRecipeSlotsView().getSlotViews()) {
                        List<ItemStack> stacks = slot.getItemStacks()
                                .filter(stack -> !stack.isEmpty())
                                .map(ItemStack::copy)
                                .toList();
                        if (stacks.isEmpty()) continue;
                        if (slot.getRole() == RecipeIngredientRole.OUTPUT) {
                            if (output == null) output = stacks.get(0);
                        } else if (slot.getRole() == RecipeIngredientRole.INPUT) {
                            inputGroups.add(stacks);
                        }
                    }
                    if (output == null || inputGroups.isEmpty()) return;

                    ResourceLocation sourceId = category.getRegistryName(recipe);
                    String id = sourceId == null
                            ? category.getRecipeType().getUid() + "/jei/" + Integer.toUnsignedString(recipe.hashCode())
                            : sourceId.toString();
                    String categoryId = category.getRecipeType().getUid().toString();
                    String key = id + "|" + output.getItem() + "|" + output.getComponents().hashCode();
                    found.putIfAbsent(key, new Entry(id, categoryId, category.getTitle(), output, inputGroups));
                });
    }
}
