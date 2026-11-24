package dev.whimsyautomation.remote;

import dev.whimsyautomation.remote.RemoteTransferNetworkData.EndpointKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Optional reflection-only bridges to Applied Energistics 2 and Refined Storage. */
public final class ExternalStorageBridge {
    private static final String AE2_WIRELESS_ACCESS_POINT = "appeng.api.implementations.blockentities.IWirelessAccessPoint";
    private static final String RS_NETWORK_NODE_PROXY = "com.refinedmods.refinedstorage.api.network.node.INetworkNodeProxy";

    private ExternalStorageBridge() {}

    public static EndpointKind detectKind(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        if (isInstance(AE2_WIRELESS_ACCESS_POINT, be)) return EndpointKind.AE2_NETWORK;
        if (isInstance(RS_NETWORK_NODE_PROXY, be)) return EndpointKind.REFINED_STORAGE_NETWORK;
        return null;
    }

    public static IItemHandler getHandler(Level level, BlockPos pos, EndpointKind kind) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        return switch (kind) {
            case AE2_NETWORK -> {
                if (!isInstance(AE2_WIRELESS_ACCESS_POINT, be)) yield null;
                Ae2Handler handler = new Ae2Handler(be);
                yield handler.resolve() == null ? null : handler;
            }
            case REFINED_STORAGE_NETWORK -> {
                if (!isInstance(RS_NETWORK_NODE_PROXY, be)) yield null;
                RsHandler handler = new RsHandler(be);
                yield handler.resolve() == null ? null : handler;
            }
            default -> null;
        };
    }

    public static String connectedMessage(EndpointKind kind) {
        return switch (kind) {
            case AE2_NETWORK -> "已绑定应用能源 2 的 ME 网络。";
            case REFINED_STORAGE_NETWORK -> "已绑定精致存储网络。";
            default -> "方块已加入网络成员。";
        };
    }

    public static String unavailableMessage(EndpointKind kind) {
        return switch (kind) {
            case AE2_NETWORK -> "这个 ME 无线接入点尚未连接到可用的 ME 网络。";
            case REFINED_STORAGE_NETWORK -> "这个精致存储设备尚未连接到可运行的存储网络。";
            default -> "目标存储网络当前不可用。";
        };
    }

    private static boolean isInstance(String className, Object value) {
        try { return Class.forName(className).isInstance(value); }
        catch (ClassNotFoundException ignored) { return false; }
    }

    private static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
        if (target == null) throw new NoSuchMethodException("Null target: " + name);
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] p = m.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < p.length; i++) if (!compatible(p[i], args[i])) { ok = false; break; }
            if (ok) return m.invoke(target, args);
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    private static Object invokeStatic(String className, String name, Object... args) throws ReflectiveOperationException {
        Class<?> type = Class.forName(className);
        for (Method m : type.getMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())
                    || !m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] p = m.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < p.length; i++) if (!compatible(p[i], args[i])) { ok = false; break; }
            if (ok) return m.invoke(null, args);
        }
        throw new NoSuchMethodException(className + "." + name);
    }

    private static boolean compatible(Class<?> p, Object a) {
        if (a == null) return !p.isPrimitive();
        if (!p.isPrimitive()) return p.isAssignableFrom(a.getClass());
        return (p == int.class && a instanceof Integer) || (p == long.class && a instanceof Long)
                || (p == boolean.class && a instanceof Boolean) || (p == double.class && a instanceof Double)
                || (p == float.class && a instanceof Float) || (p == short.class && a instanceof Short)
                || (p == byte.class && a instanceof Byte) || (p == char.class && a instanceof Character);
    }

    private static Object enumValue(String className, String name) throws ReflectiveOperationException {
        for (Object value : Class.forName(className).getEnumConstants())
            if (((Enum<?>) value).name().equals(name)) return value;
        throw new IllegalArgumentException("Missing enum value " + className + "." + name);
    }

    private static int visibleCount(long amount, ItemStack stack) {
        return (int) Math.max(0L, Math.min(Math.min(amount, 99L), stack.getMaxStackSize()));
    }

    private record Ae2Context(Object storage, Object source, Object cached) {}

    private static final class Ae2Handler implements IItemHandler {
        private static final String KEY = "appeng.api.stacks.AEItemKey";
        private static final String ACTION = "appeng.api.config.Actionable";
        private static final String SOURCE = "appeng.api.networking.security.IActionSource";
        private final BlockEntity accessPoint;

        private Ae2Handler(BlockEntity accessPoint) { this.accessPoint = accessPoint; }

        private Ae2Context resolve() {
            try {
                Object grid = invoke(accessPoint, "getGrid");
                if (grid == null) return null;
                Object service = invoke(grid, "getStorageService");
                return new Ae2Context(invoke(service, "getInventory"),
                        invokeStatic(SOURCE, "ofMachine", accessPoint), invoke(service, "getCachedInventory"));
            } catch (ReflectiveOperationException | RuntimeException e) { return null; }
        }

        private List<ItemStack> snapshot() {
            Ae2Context c = resolve();
            if (c == null || !(c.cached() instanceof Iterable<?> entries)) return List.of();
            List<ItemStack> result = new ArrayList<>();
            try {
                for (Object entry : entries) {
                    Object key = invoke(entry, "getKey");
                    if (!isInstance(KEY, key)) continue;
                    Object value = invoke(key, "getReadOnlyStack");
                    if (!(value instanceof ItemStack stack) || stack.isEmpty()) continue;
                    int count = visibleCount(((Number) invoke(entry, "getLongValue")).longValue(), stack);
                    if (count > 0) result.add(stack.copyWithCount(count));
                }
            } catch (ReflectiveOperationException | RuntimeException e) { return List.of(); }
            return result;
        }

        @Override public int getSlots() { return snapshot().size(); }
        @Override public ItemStack getStackInSlot(int slot) {
            List<ItemStack> all = snapshot();
            return slot < 0 || slot >= all.size() ? ItemStack.EMPTY : all.get(slot).copy();
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            Ae2Context c = resolve();
            if (c == null) return stack;
            try {
                Object key = invokeStatic(KEY, "of", stack);
                Object action = enumValue(ACTION, simulate ? "SIMULATE" : "MODULATE");
                long inserted = ((Number) invoke(c.storage(), "insert", key, (long) stack.getCount(), action, c.source())).longValue();
                int moved = (int) Math.max(0L, Math.min(inserted, stack.getCount()));
                return moved == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
            } catch (ReflectiveOperationException | RuntimeException e) { return stack; }
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            List<ItemStack> all = snapshot();
            if (slot < 0 || slot >= all.size()) return ItemStack.EMPTY;
            ItemStack sample = all.get(slot);
            Ae2Context c = resolve();
            if (c == null) return ItemStack.EMPTY;
            try {
                Object key = invokeStatic(KEY, "of", sample);
                Object action = enumValue(ACTION, simulate ? "SIMULATE" : "MODULATE");
                long extracted = ((Number) invoke(c.storage(), "extract", key, (long) amount, action, c.source())).longValue();
                int moved = (int) Math.max(0L, Math.min(Math.min(extracted, amount), sample.getMaxStackSize()));
                return moved <= 0 ? ItemStack.EMPTY : sample.copyWithCount(moved);
            } catch (ReflectiveOperationException | RuntimeException e) { return ItemStack.EMPTY; }
        }
        @Override public int getSlotLimit(int slot) { return 99; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return !stack.isEmpty(); }
    }

    private record RsContext(Object network, Object storages) {}

    private static final class RsHandler implements IItemHandler {
        private static final String ACTION = "com.refinedmods.refinedstorage.api.util.Action";
        private static final String COMPARER = "com.refinedmods.refinedstorage.api.util.IComparer";
        private final BlockEntity device;

        private RsHandler(BlockEntity device) { this.device = device; }

        private RsContext resolve() {
            try {
                Object node = invoke(device, "getNode");
                Object network = invoke(node, "getNetwork");
                if (network == null || !Boolean.TRUE.equals(invoke(network, "canRun"))) return null;
                Object cache = invoke(network, "getItemStorageCache");
                return new RsContext(network, invoke(cache, "getStorages"));
            } catch (ReflectiveOperationException | RuntimeException e) { return null; }
        }

        private List<ItemStack> snapshot() {
            RsContext c = resolve();
            if (c == null || !(c.storages() instanceof Iterable<?> storages)) return List.of();
            List<ItemStack> result = new ArrayList<>();
            try {
                for (Object storage : storages) {
                    Object values = invoke(storage, "getStacks");
                    if (!(values instanceof Collection<?> stacks)) continue;
                    for (Object value : stacks) {
                        if (!(value instanceof ItemStack stack) || stack.isEmpty()) continue;
                        int count = visibleCount(stack.getCount(), stack);
                        if (count > 0) result.add(stack.copyWithCount(count));
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) { return List.of(); }
            return result;
        }

        @Override public int getSlots() { return snapshot().size(); }
        @Override public ItemStack getStackInSlot(int slot) {
            List<ItemStack> all = snapshot();
            return slot < 0 || slot >= all.size() ? ItemStack.EMPTY : all.get(slot).copy();
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            RsContext c = resolve();
            if (c == null) return stack;
            try {
                Object action = enumValue(ACTION, simulate ? "SIMULATE" : "PERFORM");
                Object remainder = invoke(c.network(), "insertItem", stack, stack.getCount(), action);
                return remainder instanceof ItemStack left ? left : ItemStack.EMPTY;
            } catch (ReflectiveOperationException | RuntimeException e) { return stack; }
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            List<ItemStack> all = snapshot();
            if (slot < 0 || slot >= all.size()) return ItemStack.EMPTY;
            ItemStack sample = all.get(slot);
            RsContext c = resolve();
            if (c == null) return ItemStack.EMPTY;
            try {
                Object action = enumValue(ACTION, simulate ? "SIMULATE" : "PERFORM");
                Field f = Class.forName(COMPARER).getField("COMPARE_NBT");
                int flags = f.getInt(null);
                Object extracted = invoke(c.network(), "extractItem", sample, amount, flags, action);
                if (!(extracted instanceof ItemStack result) || result.isEmpty()) return ItemStack.EMPTY;
                int count = Math.min(amount, Math.min(result.getCount(), result.getMaxStackSize()));
                return count <= 0 ? ItemStack.EMPTY : result.copyWithCount(count);
            } catch (ReflectiveOperationException | RuntimeException e) { return ItemStack.EMPTY; }
        }
        @Override public int getSlotLimit(int slot) { return 99; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return !stack.isEmpty(); }
    }
}
