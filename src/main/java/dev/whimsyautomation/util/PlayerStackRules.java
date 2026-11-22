package dev.whimsyautomation.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Converts an internally oversized stack into a legal player-facing stack.
 * Machine storage may override MAX_STACK_SIZE for compact storage, but that
 * override must never leak into a player's inventory.
 */
public final class PlayerStackRules {
    private PlayerStackRules() {}

    public static int getVanillaMaxStackSize(ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.getItem().getDefaultMaxStackSize();
    }

    public static ItemStack copyForPlayer(ItemStack stack, int requestedCount) {
        if (stack.isEmpty() || requestedCount <= 0) return ItemStack.EMPTY;
        int count = Math.min(stack.getCount(), Math.min(requestedCount, getVanillaMaxStackSize(stack)));
        if (count <= 0) return ItemStack.EMPTY;
        ItemStack result = stack.copyWithCount(count);
        result.remove(DataComponents.MAX_STACK_SIZE);
        return result;
    }
}
