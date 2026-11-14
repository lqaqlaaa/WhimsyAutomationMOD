package dev.whimsyautomation.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientSupplier;
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
 * Queries JEI's own recipe index for the item currently being dismantled.
 * JEI is the discovery source; the server RecipeManager remains authoritative
 * for executing a dismantling operation.
 */
@JeiPlugin
public final class WhimsyJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("whimsy_automation", "recipe_catalog");
    private static volatile IJeiRuntime runtime;
    private static volatile List<IRecipeCategory<?>> categories = List.of();

    public record Entry(String recipeId, String categoryId, Component categoryTitle,
                        ItemStack output, List<ItemStack> inputs) {
        public Entry {
            output = output.copy();
            inputs = inputs.stream().map(ItemStack::copy).toList();
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
     * Uses an OUTPUT focus against JEI's recipe lookup for each registered
     * category. The focus stack deliberately contains only the target item:
     * mutable data on a real stack (durability, counters, custom state) must
     * not prevent JEI from discovering recipes for that item type.
     */
    public static List<Entry> findByOutput(ItemStack target) {
        IJeiRuntime active = runtime;
        if (active == null || target.isEmpty()) return List.of();

        ItemStack focusStack = new ItemStack(target.getItem());
        IFocus<ItemStack> focus = active.getJeiHelpers().getFocusFactory()
                .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, focusStack);
        IRecipeManager manager = active.getRecipeManager();
        Map<String, Entry> found = new LinkedHashMap<>();

        for (IRecipeCategory<?> category : categories) {
            collectFocusedMatches(manager, category, focus, found);
        }
        return List.copyOf(found.values());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void collectFocusedMatches(IRecipeManager manager, IRecipeCategory category,
                                              IFocus<ItemStack> focus, Map<String, Entry> found) {
        manager.createRecipeLookup(category.getRecipeType())
                .limitFocus(List.of(focus))
                .includeHidden()
                .get()
                .forEach(recipe -> {
                    IIngredientSupplier ingredients = manager.getRecipeIngredients(category, recipe);
                    List<ItemStack> outputs = ingredients.getIngredients(RecipeIngredientRole.OUTPUT).stream()
                            .map(typed -> typed.getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY))
                            .filter(stack -> !stack.isEmpty())
                            .map(ItemStack::copy)
                            .toList();
                    List<ItemStack> inputs = ingredients.getIngredients(RecipeIngredientRole.INPUT).stream()
                            .map(typed -> typed.getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY))
                            .filter(stack -> !stack.isEmpty())
                            .map(ItemStack::copy)
                            .toList();
                    if (outputs.isEmpty() || inputs.isEmpty()) return;

                    ResourceLocation sourceId = category.getRegistryName(recipe);
                    String id = sourceId == null
                            ? category.getRecipeType().getUid() + "/jei/" + Integer.toUnsignedString(recipe.hashCode())
                            : sourceId.toString();
                    String categoryId = category.getRecipeType().getUid().toString();
                    for (ItemStack output : outputs) {
                        String key = id + "|" + output.getItem() + "|" + output.getComponents().hashCode();
                        found.putIfAbsent(key, new Entry(id, categoryId, category.getTitle(), output, inputs));
                    }
                });
    }
}
