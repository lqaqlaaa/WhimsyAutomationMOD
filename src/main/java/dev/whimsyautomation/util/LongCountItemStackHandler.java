package dev.whimsyautomation.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Long-backed machine inventory. ItemStack is only the bounded API projection;
 * the authoritative amount is stored independently as a long.
 */
public class LongCountItemStackHandler extends ItemStackHandler {
    private static final int MAX_API_STACK = 99;
    private final List<ItemStack> templates = new ArrayList<>();
    private final List<Long> counts = new ArrayList<>();

    public LongCountItemStackHandler(int size) {
        super(size);
        resizeSlots(size);
    }

    @Override
    public int getSlots() {
        return counts.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!validSlot(slot) || counts.get(slot) <= 0L) return ItemStack.EMPTY;
        ItemStack template = templates.get(slot);
        if (template.isEmpty()) return ItemStack.EMPTY;
        int visible = (int) Math.min(counts.get(slot), (long) apiLimit(slot, template));
        return visible <= 0 ? ItemStack.EMPTY : template.copyWithCount(visible);
    }

    /** Returns the complete authoritative count without narrowing it to ItemStack's int field. */
    public long getStoredCount(int slot) {
        return validSlot(slot) ? counts.get(slot) : 0L;
    }

    /** Returns a count-one copy of the stored item and its components. */
    public ItemStack getStoredTemplate(int slot) {
        if (!validSlot(slot) || counts.get(slot) <= 0L || templates.get(slot).isEmpty()) return ItemStack.EMPTY;
        return templates.get(slot).copyWithCount(1);
    }

    /** Replaces a slot using a template and a long count. */
    public void setStoredStack(int slot, ItemStack template, long count) {
        if (!validSlot(slot)) return;
        ItemStack next = template == null || template.isEmpty() || count <= 0L
                ? ItemStack.EMPTY : template.copyWithCount(1);
        long nextCount = next.isEmpty() ? 0L : count;
        if (ItemStack.isSameItemSameComponents(templates.get(slot), next)
                && counts.get(slot) == nextCount) return;
        templates.set(slot, next);
        counts.set(slot, nextCount);
        onContentsChanged(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (!validSlot(slot)) return;
        if (stack == null || stack.isEmpty()) setStoredStack(slot, ItemStack.EMPTY, 0L);
        else setStoredStack(slot, stack, stack.getCount());
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!validSlot(slot) || stack == null || stack.isEmpty() || !isItemValid(slot, stack)) return stack;
        ItemStack current = getStoredTemplate(slot);
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) return stack;
        long stored = counts.get(slot);
        long accepted = Math.min(Math.min((long) stack.getCount(), (long) apiLimit(slot, stack)), Long.MAX_VALUE - stored);
        if (accepted <= 0L) return stack;
        if (!simulate) {
            if (current.isEmpty()) templates.set(slot, stack.copyWithCount(1));
            counts.set(slot, stored + accepted);
            onContentsChanged(slot);
        }
        long left = (long) stack.getCount() - accepted;
        return left <= 0L ? ItemStack.EMPTY : stack.copyWithCount((int) left);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!validSlot(slot) || amount <= 0 || counts.get(slot) <= 0L) return ItemStack.EMPTY;
        ItemStack template = templates.get(slot);
        if (template.isEmpty()) return ItemStack.EMPTY;
        int extracted = (int) Math.min(Math.min((long) amount, counts.get(slot)), (long) apiLimit(slot, template));
        if (extracted <= 0) return ItemStack.EMPTY;
        ItemStack result = template.copyWithCount(extracted);
        if (!simulate) {
            long left = counts.get(slot) - extracted;
            counts.set(slot, left);
            if (left == 0L) templates.set(slot, ItemStack.EMPTY);
            onContentsChanged(slot);
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return validSlot(slot) ? MAX_API_STACK : 0;
    }

    @Override
    protected int getStackLimit(int slot, ItemStack stack) {
        return apiLimit(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return validSlot(slot) && stack != null && !stack.isEmpty();
    }

    /** Resizes storage while retaining all existing long counts. */
    public void resizeSlots(int size) {
        int target = Math.max(0, size);
        while (templates.size() > target) {
            templates.remove(templates.size() - 1);
            counts.remove(counts.size() - 1);
        }
        while (templates.size() < target) {
            templates.add(ItemStack.EMPTY);
            counts.add(0L);
        }
    }

    private int apiLimit(int slot, ItemStack stack) {
        int configured = Math.max(1, Math.min(MAX_API_STACK, getSlotLimit(slot)));
        return Math.max(1, Math.min(configured, stack.getMaxStackSize()));
    }

    private boolean validSlot(int slot) {
        return slot >= 0 && slot < counts.size();
    }
}
