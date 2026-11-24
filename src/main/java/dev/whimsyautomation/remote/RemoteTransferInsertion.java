package dev.whimsyautomation.remote;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Bridges Sophisticated Core's slot-specific insertion API without taking a
 * compile-time dependency on that optional mod.
 *
 * Sophisticated Core exposes CachedFailedInsertInventoryHandler through some
 * capabilities. That cache delegates ordinary insertItem calls to the whole
 * inventory, so we must reach its wrapped InventoryHandler and call
 * insertItemOnlyToSlot for both simulation and execution.
 */
final class RemoteTransferInsertion {
    private static final String SOPHISTICATED_PACKAGE =
            "net.p3pp3rf1y.sophisticatedcore.inventory.";
    private static final String NEOFORGE_WRAPPER_PACKAGE =
            "net.neoforged.neoforge.items.wrapper.";

    private RemoteTransferInsertion() {}

    /** True when the underlying handler persists full slot counts inside ItemStacks. */
    static boolean requiresBoundedStoredStack(IItemHandler handler) {
        return findSophisticatedHandler(handler) != null;
    }

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
     * Follow only known delegation layers. A NeoForge capability wrapper can
     * sit outside Sophisticated Core's cache, so checking only the first
     * runtime class is insufficient.
     */
    private static Object findSophisticatedHandler(Object handler) {
        Queue<Object> pending = new ArrayDeque<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(handler);
        boolean sawSophisticatedLayer = false;

        while (!pending.isEmpty() && visited.size() < 32) {
            Object current = pending.remove();
            if (current == null || !visited.add(current)) continue;

            Class<?> type = current.getClass();
            String className = type.getName();

            try {
                return type.getMethod("insertItemOnlyToSlot",
                        int.class, ItemStack.class, boolean.class).getDeclaringClass() != null
                        ? current : null;
            } catch (NoSuchMethodException ignored) {
                // This layer is a delegate/cache rather than the real inventory.
            }

            if (className.startsWith(SOPHISTICATED_PACKAGE)) {
                sawSophisticatedLayer = true;
                collectSupplierDelegates(current, type, pending);
            } else if (className.startsWith(NEOFORGE_WRAPPER_PACKAGE)) {
                collectHandlerDelegates(current, type, pending);
            }
        }

        // Never silently fall back to the unsafe whole-inventory insertion
        // when a Sophisticated wrapper was present but could not be unwrapped.
        if (sawSophisticatedLayer) {
            throw new IllegalStateException(
                    "Could not unwrap Sophisticated Core capability to its InventoryHandler; "
                            + "refusing unsafe whole-inventory insertion");
        }
        return null;
    }

    private static void collectSupplierDelegates(Object instance, Class<?> type, Queue<Object> pending) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Supplier.class.isAssignableFrom(field.getType())) continue;
                try {
                    if (!field.trySetAccessible()) continue;
                    Object value = field.get(instance);
                    if (value instanceof Supplier<?> supplier) {
                        Object supplied = supplier.get();
                        if (supplied instanceof IItemHandler) pending.add(supplied);
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    throw new IllegalStateException(
                            "Failed to unwrap Sophisticated Core inventory cache", e);
                }
            }
        }
    }

    private static void collectHandlerDelegates(Object instance, Class<?> type, Queue<Object> pending) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!IItemHandler.class.isAssignableFrom(field.getType())) continue;
                try {
                    if (!field.trySetAccessible()) continue;
                    Object value = field.get(instance);
                    if (value instanceof IItemHandler delegate) pending.add(delegate);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    throw new IllegalStateException(
                            "Failed to inspect NeoForge item-handler wrapper", e);
                }
            }
        }
    }
}
