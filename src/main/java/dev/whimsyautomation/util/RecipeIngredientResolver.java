package dev.whimsyautomation.util;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Resolves recipe ingredient groups without losing alternatives or required counts. */
public final class RecipeIngredientResolver {
    private RecipeIngredientResolver() {}

    public record IngredientGroup(Ingredient ingredient, int count) {
        public IngredientGroup { count = Math.max(1, count); }
    }

    public static List<IngredientGroup> getGroups(Recipe<?> recipe) {
        try {
            Method inputsMethod = recipe.getClass().getMethod("getInputs");
            Object rawInputs = inputsMethod.invoke(recipe);
            if (rawInputs instanceof Iterable<?> inputs) {
                List<IngredientGroup> resolved = new ArrayList<>();
                for (Object input : inputs) {
                    if (input == null) continue;
                    Object rawIngredient = input.getClass().getMethod("getIngredient").invoke(input);
                    if (!(rawIngredient instanceof Ingredient ingredient) || ingredient.isEmpty()) continue;
                    int count = 1;
                    try {
                        Object rawCount = input.getClass().getMethod("getCount").invoke(input);
                        if (rawCount instanceof Number number) count = Math.max(1, number.intValue());
                    } catch (ReflectiveOperationException ignored) { }
                    resolved.add(new IngredientGroup(ingredient, count));
                }
                if (!resolved.isEmpty()) return List.copyOf(resolved);
            }
        } catch (ReflectiveOperationException | SecurityException ignored) { }
        List<IngredientGroup> fallback = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) fallback.add(new IngredientGroup(ingredient, 1));
        }
        return List.copyOf(fallback);
    }

    /** Compatibility view for callers that only need ingredient predicates. */
    public static List<Ingredient> getIngredients(Recipe<?> recipe) {
        return getGroups(recipe).stream().map(IngredientGroup::ingredient).toList();
    }
}
