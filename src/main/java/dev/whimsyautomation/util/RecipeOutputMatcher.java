package dev.whimsyautomation.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

/**
 * Compares a dismantling input with a recipe output while accounting for
 * modded items whose identity is stored in mutable item data.
 */
public final class RecipeOutputMatcher {
    private RecipeOutputMatcher() {}

    public static boolean matches(ItemStack recipeResult, ItemStack input) {
        if (recipeResult.isEmpty() || input.isEmpty()
                || !ItemStack.isSameItem(recipeResult, input)) {
            return false;
        }

        // SlashBlade: Resharped stores the named blade identity and mutable
        // combat progress in one BLADE_STATE_DATA component. Comparing that
        // entire component rejects a valid blade as soon as its kill count,
        // proud souls, refine, durability state, etc. differ from the recipe
        // template. Compare the stable translation key instead.
        String itemId = BuiltInRegistries.ITEM.getKey(input.getItem()).toString();
        if ("slashblade:slashblade".equals(itemId)) {
            String expectedBlade = getSlashBladeTranslationKey(recipeResult);
            String actualBlade = getSlashBladeTranslationKey(input);
            if (expectedBlade != null && actualBlade != null) {
                return expectedBlade.equals(actualBlade);
            }
        }

        // Generic component-aware matching: recipe-specified components must
        // match, while extra state on the player's stack is allowed.
        java.util.Set<net.minecraft.core.component.DataComponentType<?>> ignored = java.util.Set.of(
                net.minecraft.core.component.DataComponents.DAMAGE,
                net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.core.component.DataComponents.ITEM_NAME,
                net.minecraft.core.component.DataComponents.REPAIR_COST,
                net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        for (var component : recipeResult.getComponents().keySet()) {
            if (ignored.contains(component)) continue;
            if (!Objects.equals(recipeResult.get(component), input.get(component))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Uses reflection so the automation mod does not acquire a hard dependency
     * on SlashBlade. The accessor is part of SlashBlade: Resharped's public API.
     */
    private static String getSlashBladeTranslationKey(ItemStack stack) {
        try {
            Class<?> accessClass = Class.forName(
                    "mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess");
            Method getData = accessClass.getMethod("getData", ItemStack.class);
            Object result = getData.invoke(null, stack);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return null;
            Object data = optional.get();
            Method translationKey = data.getClass().getMethod("translationKey");
            Object value = translationKey.invoke(data);
            return value instanceof String key ? key : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
