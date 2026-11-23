package dev.whimsyautomation.block.entity;

import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Exact-count storage for the large chest.
 *
 * ItemStack#getCount() is an int, so ItemStack is deliberately used only as an
 * item-and-components template. The authoritative quantity is kept separately
 * as a BigInteger and is never inferred from a display or transfer stack.
 */
public final class BigCapacityInventory {
    public static final BigInteger MAX_SLOT_COUNT = BigInteger.TEN.pow(308);
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger DISPLAY_MAX = BigInteger.valueOf(64);

    private final ItemStack[] templates;
    private final BigInteger[] counts;

    public BigCapacityInventory(int slots) {
        if (slots <= 0) {
            throw new IllegalArgumentException("Inventory must contain at least one slot");
        }
        templates = new ItemStack[slots];
        counts = new BigInteger[slots];
        Arrays.fill(templates, ItemStack.EMPTY);
        Arrays.fill(counts, BigInteger.ZERO);
    }

    public int slots() {
        return counts.length;
    }

    public BigInteger count(int slot) {
        checkSlot(slot);
        return counts[slot];
    }

    public boolean isEmpty(int slot) {
        return count(slot).signum() == 0;
    }

    /** Returns a defensive, count-one copy suitable for identifying the stored item. */
    public ItemStack template(int slot) {
        checkSlot(slot);
        return templates[slot].isEmpty() ? ItemStack.EMPTY : templates[slot].copyWithCount(1);
    }

    /** Returns a bounded stack for a vanilla GUI slot. It is not authoritative storage. */
    public ItemStack displayStack(int slot) {
        checkSlot(slot);
        if (counts[slot].signum() == 0 || templates[slot].isEmpty()) {
            return ItemStack.EMPTY;
        }
        return templates[slot].copyWithCount(counts[slot].min(DISPLAY_MAX).intValue());
    }

    /** Returns at most one legal Java ItemStack batch for automation. */
    public ItemStack transferStack(int slot) {
        checkSlot(slot);
        if (counts[slot].signum() == 0 || templates[slot].isEmpty()) {
            return ItemStack.EMPTY;
        }
        return templates[slot].copyWithCount(counts[slot].min(INT_MAX).intValue());
    }

    /**
     * Inserts no more than offeredCount. Simulation is side-effect free.
     * Returns the exact accepted amount, which always fits in an int because a
     * single ItemStack transfer cannot offer more than Integer.MAX_VALUE.
     */
    public int insert(int slot, ItemStack offered, int offeredCount, boolean simulate) {
        checkSlot(slot);
        if (offered == null || offered.isEmpty() || offeredCount <= 0) {
            return 0;
        }
        if (!templates[slot].isEmpty()
                && !ItemStack.isSameItemSameComponents(templates[slot], offered)) {
            return 0;
        }
        int accepted = BigInteger.valueOf(offeredCount)
                .min(MAX_SLOT_COUNT.subtract(counts[slot]).max(BigInteger.ZERO))
                .intValue();
        if (accepted > 0 && !simulate) {
            if (templates[slot].isEmpty()) {
                templates[slot] = offered.copyWithCount(1);
            }
            counts[slot] = counts[slot].add(BigInteger.valueOf(accepted));
        }
        return accepted;
    }

    /** Extracts a bounded legal ItemStack batch; simulation does not mutate storage. */
    public ItemStack extract(int slot, int requested, boolean simulate) {
        checkSlot(slot);
        if (requested <= 0 || counts[slot].signum() == 0 || templates[slot].isEmpty()) {
            return ItemStack.EMPTY;
        }
        int extracted = counts[slot].min(BigInteger.valueOf(requested)).min(INT_MAX).intValue();
        ItemStack result = templates[slot].copyWithCount(extracted);
        if (!simulate) {
            counts[slot] = counts[slot].subtract(BigInteger.valueOf(extracted));
            if (counts[slot].signum() == 0) {
                templates[slot] = ItemStack.EMPTY;
            }
        }
        return result;
    }

    public void set(int slot, ItemStack template, BigInteger exactCount) {
        checkSlot(slot);
        if (template == null || template.isEmpty() || exactCount == null || exactCount.signum() <= 0) {
            templates[slot] = ItemStack.EMPTY;
            counts[slot] = BigInteger.ZERO;
            return;
        }
        templates[slot] = template.copyWithCount(1);
        counts[slot] = exactCount.min(MAX_SLOT_COUNT);
    }

    public void clear() {
        Arrays.fill(templates, ItemStack.EMPTY);
        Arrays.fill(counts, BigInteger.ZERO);
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= counts.length) {
            throw new IndexOutOfBoundsException("Slot " + slot + " outside inventory size " + counts.length);
        }
    }
}
