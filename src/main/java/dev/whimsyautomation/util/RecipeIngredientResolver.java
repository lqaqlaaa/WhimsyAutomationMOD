package dev.whimsyautomation.util;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves vanilla recipe ingredients and optional-mod recipe inputs.
 * TACZ exposes its ingredients through getInputs(); each input provides
 * getIngredient() and getCount(). Reflection keeps TACZ optional.
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
                        // Inputs without an explicit count represent one ingredient.
                    }
                    for (int i = 0; i < count; i++) resolved.add(ingredient);
                }
                if (!resolved.isEmpty()) return List.copyOf(resolved);
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Ordinary recipes use the standard Recipe#getIngredients API.
        }
        return recipe.getIngredients();
    }
}
