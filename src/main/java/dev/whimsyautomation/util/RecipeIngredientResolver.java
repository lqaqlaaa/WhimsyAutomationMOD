package dev.whimsyautomation.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Resolves logical recipe ingredient groups while preserving repeated ingredient counts. */
public final class RecipeIngredientResolver {
    private RecipeIngredientResolver() {}

    public record IngredientGroup(Ingredient ingredient, int count) {
        public IngredientGroup { count = Math.max(1, count); }
    }

    public static List<IngredientGroup> getGroups(Recipe<?> recipe) {
        // Explicit NeoForge/modded input groups and counts are authoritative.
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

        // Vanilla recipes expose one Ingredient per occupied crafting position.
        // Collapse identical alternative sets into one selectable material group.
        Map<String, IngredientGroup> grouped = new LinkedHashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            String key = canonicalKey(ingredient);
            IngredientGroup previous = grouped.get(key);
            grouped.put(key, previous == null ? new IngredientGroup(ingredient, 1)
                    : new IngredientGroup(previous.ingredient(), previous.count() + 1));
        }
        return List.copyOf(grouped.values());
    }

    private static String canonicalKey(Ingredient ingredient) {
        TreeSet<String> ids = new TreeSet<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (!stack.isEmpty()) ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return String.join("|", ids);
    }

    public static List<Ingredient> getIngredients(Recipe<?> recipe) {
        return getGroups(recipe).stream().map(IngredientGroup::ingredient).toList();
    }
}