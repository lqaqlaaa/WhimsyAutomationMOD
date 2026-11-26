package dev.whimsyautomation.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StackCountDisplay {
    private StackCountDisplay() {}

    public static String format(int count) {
        return String.format(Locale.ROOT, "%,d", count);
    }

    public static List<Component> withCount(List<Component> original, ItemStack stack) {
        List<Component> lines = new ArrayList<>(original);
        if (!stack.isEmpty() && stack.getCount() > 1) {
            // Tooltip line 0 is the item name. Keep the count immediately below
            // it so long descriptions, attributes and modded details cannot push
            // the most important storage information to the bottom.
            lines.add(Math.min(1, lines.size()),
                    Component.literal("数量：" + format(stack.getCount())));
        }
        return lines;
    }
}
