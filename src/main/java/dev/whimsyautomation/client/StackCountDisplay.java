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
            lines.add(Component.literal("数量：" + format(stack.getCount())));
        }
        return lines;
    }
}
