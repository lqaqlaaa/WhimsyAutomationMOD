package dev.whimsyautomation.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a client-side snapshot of JEI's recipe displays once when JEI becomes
 * available. Never enumerate every category/recipe from a screen render method.
 */
@JeiPlugin
public final class WhimsyJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("whimsy_automation", "recipe_catalog");
    private static volatile IJeiRuntime runtime;
    private static volatile Map<Item, List<Entry>> byOutputItem = Map.of();

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
        rebuildIndex(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        byOutputItem = Map.of();
    }

    private static void rebuildIndex(IJeiRuntime active) {
        IRecipeManager manager = active.getRecipeManager();
        Map<Item, List<Entry>> mutable = new HashMap<>();
        manager.createRecipeCategoryLookup().get().forEach(category ->
                collectMatches(manager, category, mutable));
        Map<Item, List<Entry>> frozen = new HashMap<>();
        mutable.forEach((item, entries) -> frozen.put(item, List.copyOf(entries)));
        byOutputItem = Map.copyOf(frozen);
    }

    public static List<Entry> findByOutput(ItemStack target) {
        if (target.isEmpty()) return List.of();
        return byOutputItem.getOrDefault(target.getItem(), List.of());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void collectMatches(IRecipeManager manager, IRecipeCategory category,
                                       Map<Item, List<Entry>> index) {
        manager.createRecipeLookup(category.getRecipeType()).get().forEach(recipe -> {
            IIngredientSupplier ingredients = manager.getRecipeIngredients(category, recipe);
            List<ItemStack> outputs = ingredients.getIngredients(RecipeIngredientRole.OUTPUT).stream()
                    .map(typed -> typed.getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY))
                    .filter(stack -> !stack.isEmpty())
                    .toList();

            List<ItemStack> inputs = ingredients.getIngredients(RecipeIngredientRole.INPUT).stream()
                    .map(typed -> typed.getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY))
                    .filter(stack -> !stack.isEmpty())
                    .map(ItemStack::copy)
                    .toList();
            if (outputs.isEmpty() || inputs.isEmpty()) return;

            ResourceLocation sourceId = category.getRegistryName(recipe);
            String id = sourceId == null
                    ? category.getRecipeType().getUid() + "/jei/" + Integer.toUnsignedString(Objects.hash(recipe))
                    : sourceId.toString();
            String categoryId = category.getRecipeType().getUid().toString();
            for (ItemStack output : outputs) {
                index.computeIfAbsent(output.getItem(), ignored -> new ArrayList<>())
                        .add(new Entry(id, categoryId, category.getTitle(), output, inputs));
            }
        });
    }
}
