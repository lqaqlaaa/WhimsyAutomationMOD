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
    private static final String SLASHBLADE_ITEM_CLASS =
            "mods.flammpfeil.slashblade.item.ItemSlashBlade";
    private static final String SLASHBLADE_STATE_ACCESS =
            "mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess";
    private static volatile Class<?> slashBladeItemClass;
    private static volatile Method slashBladeStateOf;
    private static volatile Method slashBladeGetData;
    private static volatile Method slashBladeStateTranslationKey;
    private static volatile Method slashBladeDataTranslationKey;
    private static volatile boolean slashBladeResolved;

    private RecipeOutputMatcher() {}

    public static boolean matches(ItemStack recipeResult, ItemStack input) {
        if (recipeResult.isEmpty() || input.isEmpty()) return false;

        // SlashBlade: Resharped does not identify every blade solely by its
        // item registry id. In particular, addon blades may use a different
        // ItemSlashBlade implementation/item id, while their stable identity
        // is exposed by ISlashBladeState#getTranslationKey(). Combat progress,
        // proud souls, refine and enchantments are mutable and must not block
        // reverse dismantling.
        if (isSlashBlade(recipeResult) || isSlashBlade(input)) {
            if (!isSlashBlade(recipeResult) || !isSlashBlade(input)) return false;
            String expectedBlade = getSlashBladeTranslationKey(recipeResult);
            String actualBlade = getSlashBladeTranslationKey(input);
            return expectedBlade != null && actualBlade != null
                    && expectedBlade.equals(actualBlade);
        }

        if (!ItemStack.isSameItem(recipeResult, input)) return false;

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

    private static boolean isSlashBlade(ItemStack stack) {
        if (stack.isEmpty()) return false;
        resolveSlashBladeAccess();
        Class<?> itemClass = slashBladeItemClass;
        return itemClass != null && itemClass.isInstance(stack.getItem());
    }

    /**
     * Uses the 1.21.1 public SlashBlade state API when the optional mod is
     * installed. The old getData()/translationKey() path is retained as a
     * compatibility fallback for builds exposing that accessor.
     */
    private static String getSlashBladeTranslationKey(ItemStack stack) {
        resolveSlashBladeAccess();
        try {
            if (slashBladeStateOf != null && slashBladeStateTranslationKey != null) {
                Object resolved = slashBladeStateOf.invoke(null, stack);
                if (resolved instanceof Optional<?> optional && optional.isPresent()) {
                    Object state = optional.get();
                    Object value = slashBladeStateTranslationKey.invoke(state);
                    if (value instanceof String key && !key.isBlank()) return key;
                }
            }
            if (slashBladeGetData != null && slashBladeDataTranslationKey != null) {
                Object resolved = slashBladeGetData.invoke(null, stack);
                if (resolved instanceof Optional<?> optional && optional.isPresent()) {
                    Object data = optional.get();
                    Object value = slashBladeDataTranslationKey.invoke(data);
                    if (value instanceof String key && !key.isBlank()) return key;
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
        return null;
    }

    private static void resolveSlashBladeAccess() {
        if (slashBladeResolved) return;
        synchronized (RecipeOutputMatcher.class) {
            if (slashBladeResolved) return;
            try {
                Class<?> itemClass = Class.forName(SLASHBLADE_ITEM_CLASS);
                Class<?> accessClass = Class.forName(SLASHBLADE_STATE_ACCESS);
                slashBladeItemClass = itemClass;

                try {
                    slashBladeStateOf = accessClass.getMethod("of", ItemStack.class);
                    Class<?> stateClass = Class.forName(
                            "mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
                    slashBladeStateTranslationKey = stateClass.getMethod("getTranslationKey");
                } catch (ReflectiveOperationException ignored) {
                    slashBladeStateOf = null;
                    slashBladeStateTranslationKey = null;
                }

                try {
                    slashBladeGetData = accessClass.getMethod("getData", ItemStack.class);
                    Class<?> dataClass = Class.forName(
                            "mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition");
                    slashBladeDataTranslationKey = dataClass.getMethod("translationKey");
                } catch (ReflectiveOperationException ignored) {
                    slashBladeGetData = null;
                    slashBladeDataTranslationKey = null;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                slashBladeItemClass = null;
                slashBladeStateOf = null;
                slashBladeStateTranslationKey = null;
                slashBladeGetData = null;
                slashBladeDataTranslationKey = null;
            } finally {
                slashBladeResolved = true;
            }
        }
    }
}
