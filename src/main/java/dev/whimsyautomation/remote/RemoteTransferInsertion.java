package dev.whimsyautomation.remote;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Bridges Sophisticated Core's slot-specific insertion API without taking a
 * compile-time dependency on that optional mod.
 *
 * InventoryHandler.insertItem(slot, stack, false) may route the real insertion
 * through the entire inventory, while its simulation is slot-specific.
 * insertItemOnlyToSlot keeps both phases on the exact same physical slot.
 */
final class RemoteTransferInsertion {
    private static final String SOPHISTICATED_PACKAGE =
            "net.p3pp3rf1y.sophisticatedcore.inventory.";

    private RemoteTransferInsertion() {}

    static ItemStack insert(IItemHandler handler, int slot, ItemStack stack, boolean simulate) {
        Object sophisticatedHandler = findSophisticatedHandler(handler);
        if (sophisticatedHandler == null) {
            return handler.insertItem(slot, stack, simulate);
        }

        try {
            Method method = sophisticatedHandler.getClass()
                    .getMethod("insertItemOnlyToSlot", int.class, ItemStack.class, boolean.class);
            return (ItemStack) method.invoke(sophisticatedHandler, slot, stack, simulate);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException(
                    "Sophisticated Core handler does not expose insertItemOnlyToSlot", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Sophisticated Core slot insertion failed", cause);
        }
    }

    /**
     * Capabilities can expose Sophisticated Core's CachedFailedInsert wrapper
     * instead of InventoryHandler itself. Follow only Sophisticated Core's
     * supplier-backed wrapper, never arbitrary third-party object fields.
     */
    private static Object findSophisticatedHandler(Object handler) {
        Object current = handler;
        for (int depth = 0; current != null && depth < 8; depth++) {
            Class<?> type = current.getClass();
            if (!type.getName().startsWith(SOPHISTICATED_PACKAGE)) {
                return null;
            }

            try {
                type.getMethod("insertItemOnlyToSlot",
                        int.class, ItemStack.class, boolean.class);
                return current;
            } catch (NoSuchMethodException ignored) {
                // Continue through the known Sophisticated Core wrapper.
            }

            Object next = unwrapSupplierField(current, type);
            if (next == current) return null;
            current = next;
        }
        return null;
    }

    private static Object unwrapSupplierField(Object instance, Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Supplier.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(instance);
                    if (value instanceof Supplier<?> supplier) {
                        Object supplied = supplier.get();
                        if (supplied instanceof IItemHandler) return supplied;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // If the optional wrapper cannot be unwrapped, use the
                    // standard IItemHandler contract rather than guessing.
                }
            }
        }
        return null;
    }
}
