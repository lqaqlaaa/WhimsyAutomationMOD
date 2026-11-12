package dev.whimsyautomation.util;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads ingredients from ordinary recipes and optional-mod recipe formats.
 * TACZ's GunSmithTableRecipe stores ingredients in getInputs(), where each
 * entry exposes getIngredient() and getCount(), rather than getIngredients().
 * Reflection keeps TACZ an optional dependency.
 */
public final class RecipeIngredientResolver {
    private RecipeIngredientResolver() {}

    public static List<Ingredient> getIngredients(Recipe<?> recipe) {
        try {
            Method inputsMethod = recipe.getClass().getMethod("getInputs");
            Object rawInputs = inputsMethod.invoke(recipe);
            if (rawInputs instanceof Iterable<?> inputs) {
                List<Ingredient> resolved = new ArrayList<>();
                for (Object input : inputs) {
                    if (input == null) continue;
                    Method ingredientMethod = input.getClass().getMethod("getIngredient");
                    Object rawIngredient = ingredientMethod.invoke(input);
                    if (!(rawIngredient instanceof Ingredient ingredient) || ingredient.isEmpty()) continue;
                    int count = 1;
                    try {
                        Object rawCount = input.getClass().getMethod("getCount").invoke(input);
                        if (rawCount instanceof Number number) count = Math.max(1, number.intValue());
                    } catch (ReflectiveOperationException ignored) {
                        // Some optional recipe formats do not specify per-entry counts.
                    }
                    for (int i = 0; i < count; i++) resolved.add(ingredient);
                }
                return resolved;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to the vanilla/NeoForge Recipe ingredient API.
        }
        return recipe.getIngredients();
    }
}
