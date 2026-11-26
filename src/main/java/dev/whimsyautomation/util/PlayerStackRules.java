package dev.whimsyautomation.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Applies the same stack-size rules to player-facing stacks as the machine
 * output planner: preserve legitimate item-defined limits (1..99), and remove
 * only invalid/legacy oversized overrides.
 */
public final class PlayerStackRules {
    private PlayerStackRules() {}

    public static int getPlayerMaxStackSize(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Integer declaredLimit = stack.get(DataComponents.MAX_STACK_SIZE);
        if (declaredLimit != null && declaredLimit >= 1 && declaredLimit <= 99) {
            return declaredLimit;
        }
        return stack.getItem().getDefaultMaxStackSize();
    }

    /** Remove only invalid or legacy machine-sized overrides; keep legitimate limits such as 99. */
    public static void normalizeStackLimit(ItemStack stack) {
        if (stack.isEmpty()) return;
        Integer declaredLimit = stack.get(DataComponents.MAX_STACK_SIZE);
        if (declaredLimit != null && (declaredLimit < 1 || declaredLimit > 99)) {
            stack.remove(DataComponents.MAX_STACK_SIZE);
        }
    }

    public static ItemStack copyForPlayer(ItemStack stack, int requestedCount) {
        if (stack.isEmpty() || requestedCount <= 0) return ItemStack.EMPTY;
        int count = Math.min(stack.getCount(), Math.min(requestedCount, getPlayerMaxStackSize(stack)));
        if (count <= 0) return ItemStack.EMPTY;
        ItemStack result = stack.copyWithCount(count);
        normalizeStackLimit(result);
        return result;
    }
}
