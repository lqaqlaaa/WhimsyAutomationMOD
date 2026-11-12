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
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads the same recipe displays that JEI exposes to players. This includes
 * third-party JEI categories, not only vanilla RecipeManager recipe types.
 *
 * The catalog is client-side discovery data; machine execution still has to be
 * validated by the logical server before any ingredients are consumed.
 */
@JeiPlugin
public final class WhimsyJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("whimsy_automation", "recipe_catalog");
    private static volatile IJeiRuntime runtime;

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
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    public static List<Entry> findByOutput(ItemStack target) {
        IJeiRuntime active = runtime;
        if (active == null || target.isEmpty()) return List.of();

        IRecipeManager manager = active.getRecipeManager();
        List<Entry> matches = new ArrayList<>();
        manager.createRecipeCategoryLookup().get().forEach(category -> collectMatches(manager, category, target, matches));
        return List.copyOf(matches);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void collectMatches(IRecipeManager manager, IRecipeCategory category,
                                       ItemStack target, List<Entry> matches) {
        manager.createRecipeLookup(category.getRecipeType()).get().forEach(recipe -> {
            IIngredientSupplier ingredients = manager.getRecipeIngredients(category, recipe);
            List<ItemStack> outputs = ingredients.getIngredients(RecipeIngredientRole.OUTPUT).stream()
                    .map(typed -> typed.getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY))
                    .filter(stack -> !stack.isEmpty())
                    .toList();
            ItemStack matchingOutput = outputs.stream()
                    .filter(stack -> ItemStack.isSameItem(stack, target))
                    .findFirst().orElse(ItemStack.EMPTY);
            if (matchingOutput.isEmpty()) return;

            List<ItemStack> inputs = ingredients.getIngredients(RecipeIngredientRole.INPUT).stream()
                    .map(typed -> typed.getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY))
                    .filter(stack -> !stack.isEmpty())
                    .map(ItemStack::copy)
                    .toList();
            if (inputs.isEmpty()) return;

            ResourceLocation sourceId = category.getRegistryName(recipe);
            String id = sourceId == null
                    ? category.getRecipeType().getUid() + "/jei/" + Integer.toUnsignedString(Objects.hash(recipe))
                    : sourceId.toString();
            matches.add(new Entry(id, category.getRecipeType().getUid().toString(),
                    category.getTitle(), matchingOutput, inputs));
        });
    }
}
