package dev.whimsyautomation.remote;

import com.mojang.logging.LogUtils;
import dev.whimsyautomation.WhimsyAutomation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import dev.whimsyautomation.util.LongCountItemStackHandler;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Executes saved remote-network links on the logical server.
 *
 * The ticker never force-loads chunks. A link is simply skipped until both
 * endpoints are in already-loaded chunks and expose item-handler capabilities.
 */
@EventBusSubscriber(modid = WhimsyAutomation.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class RemoteTransferTicker {
    private static final int MAX_ITEMS_PER_LINK_PER_TICK = Integer.MAX_VALUE;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> INVALID_STACK_WARNINGS = new HashSet<>();
    private static long serverTickCounter;

    private RemoteTransferTicker() {}

    @SubscribeEvent
    public static void onBoundBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // BreakEvent runs before the block is removed, so use its exact position
        // to delete the endpoint and every connection that references it.
        RemoteTransferNetworkData.get(level.getServer())
                .removeEndpointsAt(level.dimension(), event.getPos());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long tick = ++serverTickCounter;
        RemoteTransferNetworkData data = RemoteTransferNetworkData.get(server);
        for (RemoteTransferNetworkData.Network network : data.networks()) {
            Map<UUID, RemoteTransferNetworkData.Endpoint> members = new HashMap<>();
            for (RemoteTransferNetworkData.Endpoint endpoint : network.members()) {
                members.put(endpoint.id(), endpoint);
            }
            for (RemoteTransferNetworkData.Connection connection : network.connections()) {
                if (!connection.enabled()) continue;
                RemoteTransferNetworkData.Endpoint source = members.get(connection.sourceMember());
                RemoteTransferNetworkData.Endpoint target = members.get(connection.targetMember());
                if (source == null || target == null) continue;

                if (connection.direction() == RemoteTransferNetworkData.TransferDirection.OUTPUT) {
                    transfer(server, source, target, connection.outputFilter(), connection.inputFilter());
                } else if (connection.direction() == RemoteTransferNetworkData.TransferDirection.INPUT) {
                    transfer(server, target, source, connection.inputFilter(), connection.outputFilter());
                } else {
                    // Do not immediately pull back items just inserted in the
                    // opposite direction during the same tick.
                    if ((tick & 1L) == 0L)
                        transfer(server, source, target, connection.outputFilter(), connection.inputFilter());
                    else
                        transfer(server, target, source, connection.inputFilter(), connection.outputFilter());
                }
            }

            // Slot bindings are the new routing unit: each workstation slot has
            // its own direction and target inventory. Legacy whole-endpoint
            // connections remain readable for existing worlds during migration.
            for (RemoteTransferNetworkData.SlotBinding binding : network.slotBindings()) {
                if (!binding.enabled()) continue;
                RemoteTransferNetworkData.Endpoint workstation = members.get(binding.workstationMember());
                RemoteTransferNetworkData.Endpoint target = members.get(binding.targetMember());
                if (workstation == null || target == null) continue;
                if (workstation.dimension().equals(target.dimension())
                        && workstation.pos().equals(target.pos())) continue;

                RemoteTransferNetworkData.Endpoint workstationSlot = new RemoteTransferNetworkData.Endpoint(
                        workstation.id(), workstation.dimension(), workstation.pos(), workstation.kind(),
                        workstation.blockId(), binding.workstationSlot(), workstation.face());
                RemoteTransferNetworkData.Endpoint targetSlot = new RemoteTransferNetworkData.Endpoint(
                        target.id(), target.dimension(), target.pos(), target.kind(), target.blockId(),
                        binding.targetSlot(), binding.targetFace());
                if (binding.direction() == RemoteTransferNetworkData.TransferDirection.INPUT) {
                    transfer(server, targetSlot, workstationSlot,
                            RemoteTransferNetworkData.Filter.none(),
                            data.getSlotFilter(network.id(), workstation.id(), binding.workstationSlot()));
                } else if (binding.direction() == RemoteTransferNetworkData.TransferDirection.OUTPUT) {
                    transfer(server, workstationSlot, targetSlot,
                            data.getSlotFilter(network.id(), workstation.id(), binding.workstationSlot()),
                            RemoteTransferNetworkData.Filter.none());
                }
            }
        }
    }

    private static void transfer(MinecraftServer server,
                                 RemoteTransferNetworkData.Endpoint source,
                                 RemoteTransferNetworkData.Endpoint target,
                                 RemoteTransferNetworkData.Filter sourceFilter,
                                 RemoteTransferNetworkData.Filter targetFilter) {
        ServerLevel sourceLevel = server.getLevel(source.dimension());
        ServerLevel targetLevel = server.getLevel(target.dimension());
        if (sourceLevel == null || targetLevel == null
                || !sourceLevel.hasChunkAt(source.pos()) || !targetLevel.hasChunkAt(target.pos())) return;

        IItemHandler sourceHandler = findHandler(sourceLevel, source, true, ItemStack.EMPTY);
        if (sourceHandler == null) return;

        int remainingBudget = MAX_ITEMS_PER_LINK_PER_TICK;
        int firstSourceSlot = getFirstHandlerSlot(sourceHandler, source);
        int lastSourceSlot = source.slotIndex() >= 0 ? firstSourceSlot + 1 : sourceHandler.getSlots();
        if (lastSourceSlot > sourceHandler.getSlots()) return;
        for (int slot = firstSourceSlot; slot < lastSourceSlot && remainingBudget > 0; slot++) {
            ItemStack offered = sourceHandler.getStackInSlot(slot);
            if (offered.isEmpty() || !sourceFilter.allows(offered)) continue;
            // Keep every ordinary ItemStack crossing a capability boundary within
            // the external handler's serializable count range. Long-backed
            // source inventories expose this bounded view and retain the rest
            // of their authoritative long count internally.
            int request = Math.min(Math.min(remainingBudget, offered.getCount()),
                    Math.min(99, offered.getMaxStackSize()));
            ItemStack simulated = sourceHandler.extractItem(slot, request, true);
            if (simulated.isEmpty() || simulated.getCount() > request || !targetFilter.allows(simulated)) continue;
            if (!isSerializablePrototype(simulated, sourceLevel)) continue;
            // Pick the target face that can actually accept this item in the
            // requested slot. This is required for sided inventories such as
            // vanilla furnaces (top=input, sides=fuel, bottom=output).
            IItemHandler targetHandler = findHandler(targetLevel, target, false, simulated);
            if (targetHandler == null) continue;

            int firstTargetSlot = getFirstHandlerSlot(targetHandler, target);
            int lastTargetSlot = target.slotIndex() >= 0 ? firstTargetSlot + 1 : targetHandler.getSlots();
            if (lastTargetSlot > targetHandler.getSlots()) continue;

            // Never pass an oversized stack directly into a third-party handler.
            // Some handlers (including Sophisticated Storage) validate the count
            // before applying their own slot limit and reject Integer.MAX_VALUE.
            // Simulate using the same bounded per-slot batches used below.
            int accepted = 0;
            for (int targetSlot = firstTargetSlot;
                 targetSlot < lastTargetSlot && accepted < Math.min(simulated.getCount(), remainingBudget);
                 targetSlot++) {
                ItemStack present = targetHandler.getStackInSlot(targetSlot);
                if (!present.isEmpty() && !ItemStack.isSameItemSameComponents(present, simulated)) continue;
                int slotLimit = Math.max(0, targetHandler.getSlotLimit(targetSlot));
                int occupied = present.isEmpty() ? 0 : present.getCount();
                int free = getAvailableSpace(targetHandler, targetSlot, present, slotLimit);
                int batchLimit = getHandlerBatchLimit(targetHandler, slotLimit, simulated);
                int batch = Math.min(Math.min(free, batchLimit),
                        Math.min(simulated.getCount() - accepted, remainingBudget - accepted));
                if (batch <= 0) continue;
                ItemStack offeredBatch = simulated.copyWithCount(batch);
                ItemStack remainder = RemoteTransferInsertion.insert(targetHandler, targetSlot, offeredBatch, true);
                accepted += batch - remainder.getCount();
            }
            if (accepted <= 0) continue;

            ItemStack extracted = sourceHandler.extractItem(slot, accepted, false);
            if (extracted.isEmpty()) continue;
            ItemStack notInserted = extracted;
            int insertedTotal = 0;
            for (int targetSlot = firstTargetSlot;
                 targetSlot < lastTargetSlot && !notInserted.isEmpty(); targetSlot++) {
                ItemStack present = targetHandler.getStackInSlot(targetSlot);
                if (!present.isEmpty() && !ItemStack.isSameItemSameComponents(present, notInserted)) continue;
                int slotLimit = Math.max(0, targetHandler.getSlotLimit(targetSlot));
                int occupied = present.isEmpty() ? 0 : present.getCount();
                int free = getAvailableSpace(targetHandler, targetSlot, present, slotLimit);
                int batchLimit = getHandlerBatchLimit(targetHandler, slotLimit, notInserted);
                int batch = Math.min(free, Math.min(batchLimit, notInserted.getCount()));
                if (batch <= 0) continue;
                ItemStack batchStack = notInserted.copyWithCount(batch);
                ItemStack remainder = RemoteTransferInsertion.insert(targetHandler, targetSlot, batchStack, false);
                int inserted = batch - remainder.getCount();
                insertedTotal += inserted;
                if (inserted >= notInserted.getCount()) notInserted = ItemStack.EMPTY;
                else if (inserted > 0) notInserted.shrink(inserted);
            }
            // If a handler changed between simulation and execution, put any
            // unexpected remainder back into its original source slot.
            if (!notInserted.isEmpty()) {
                ItemStack refund = sourceHandler.insertItem(slot, notInserted, false);
                if (!refund.isEmpty()) {
                    sourceLevel.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                            sourceLevel, source.pos().getX() + 0.5, source.pos().getY() + 0.5,
                            source.pos().getZ() + 0.5, refund));
                }
            }
            remainingBudget -= insertedTotal;
        }
    }

    /**
     * Storage Drawers exposes capability slot 0 as a virtual full-inventory
     * insertion slot. Its real drawers start at capability slot 1. Treating
     * slot 0 as a physical slot makes slot-local simulation/capacity checks lie.
     */
    private static int getHandlerSlotOffset(IItemHandler handler) {
        for (Class<?> type = handler.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(
                    "com.jaquadro.minecraft.storagedrawers.capabilities.DrawerItemHandler")) {
                return 1;
            }
        }
        return 0;
    }

    private static int getFirstHandlerSlot(IItemHandler handler,
                                           RemoteTransferNetworkData.Endpoint endpoint) {
        int offset = getHandlerSlotOffset(handler);
        return endpoint.slotIndex() >= 0 ? endpoint.slotIndex() + offset : offset;
    }

    /**
     * Validate the exact one-item prototype Storage Drawers will persist.
     * ItemStack.save validates both the count and every serialized component;
     * checking a count-99 transfer batch alone cannot detect an invalid
     * component carried by the item itself.
     */
    private static boolean isSerializablePrototype(ItemStack stack, ServerLevel level) {
        try {
            stack.copyWithCount(1).save(level.registryAccess(), new CompoundTag());
            return true;
        } catch (RuntimeException exception) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (INVALID_STACK_WARNINGS.add(itemId)) {
                LOGGER.error("Remote transfer skipped non-serializable item prototype {}. "
                        + "The source item contains data that Minecraft 1.21.1 cannot encode.",
                        itemId, exception);
            }
            return false;
        }
    }

    /** Long-backed machine slots expose a capped ItemStack view but retain independent long capacity. */
    private static int getAvailableSpace(IItemHandler handler, int slot, ItemStack present, int slotLimit) {
        if (handler instanceof LongCountItemStackHandler longHandler) {
            long stored = longHandler.getStoredCount(slot);
            return (int) Math.min((long) Integer.MAX_VALUE, Long.MAX_VALUE - stored);
        }
        int occupied = present.isEmpty() ? 0 : present.getCount();
        // Sophisticated Core stores the complete count in an ItemStack and
        // validates that stack against Minecraft's [1, 99] codec while saving.
        // Storage Drawers is different: its physical drawer stores the amount
        // in a separate int field and serializes a one-item prototype. Applying
        // the Sophisticated limit to every capability incorrectly treats those
        // independent storage models as if they were identical.
        int effectiveLimit = RemoteTransferInsertion.requiresBoundedStoredStack(handler)
                ? Math.min(slotLimit, 99) : slotLimit;
        return Math.max(0, effectiveLimit - occupied);
    }

    /**
     * ItemStack counts crossing an inventory capability boundary must remain
     * legal for vanilla and third-party handlers. In particular, Sophisticated
     * Core validates its serialized slot count in the range [1, 99], even when
     * a wrapper advertises a much larger slot limit. Do not identify handlers
     * by implementation class: capabilities may be wrapped by caches/delegates
     * whose runtime class is outside the owning mod's package.
     *
     * The machine's authoritative long count is retained by LongCountItemStackHandler;
     * this is only the maximum size of one external IItemHandler call.
     */
    private static int getHandlerBatchLimit(IItemHandler handler, int slotLimit, ItemStack stack) {
        return Math.max(0, Math.min(Math.min(slotLimit, 99), stack.getMaxStackSize()));
    }

    private static IItemHandler findHandler(ServerLevel level,
                                             RemoteTransferNetworkData.Endpoint endpoint,
                                             boolean extracting, ItemStack probe) {
        var blockEntity = level.getBlockEntity(endpoint.pos());
        boolean workstation = endpoint.kind() == RemoteTransferNetworkData.EndpointKind.WORKSTATION;
        // A workstation binding addresses the machine's real GUI inventory slot.
        // Do not route it through its automation capability: those handlers may
        // hide empty outputs, compact occupied slots, or expose a paged projection.
        if (workstation && blockEntity instanceof RemoteTransferInventoryProvider provider) {
            if (endpoint.face() != null
                    && blockEntity instanceof dev.whimsyautomation.block.entity.SideConfigurableBlockEntity config) {
                int mode = config.getSideMode(endpoint.face());
                if (extracting ? mode != 2 : mode != 1) return null;
            }
            return provider.getRemoteTransferInventory();
        }
        if (endpoint.face() != null) {
            Direction side = endpoint.face();
            if (workstation && blockEntity instanceof dev.whimsyautomation.block.entity.SideConfigurableBlockEntity config) {
                int mode = config.getSideMode(side);
                if (extracting ? mode != 2 : mode != 1) return null;
            }
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, endpoint.pos(), side);
            // Some inventories expose only an unsided capability. Use it only
            // when the clicked face has no capability at all; never bypass an
            // existing sided handler's insertion/extraction restrictions.
            if (handler == null) {
                handler = level.getCapability(Capabilities.ItemHandler.BLOCK, endpoint.pos(), null);
            }
            return supports(handler, endpoint, extracting, probe) ? handler : null;
        }
        for (Direction side : Direction.values()) {
            if (workstation && blockEntity instanceof dev.whimsyautomation.block.entity.SideConfigurableBlockEntity config) {
                int mode = config.getSideMode(side);
                if (extracting ? mode != 2 : mode != 1) continue;
            }
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, endpoint.pos(), side);
            if (supports(handler, endpoint, extracting, probe)) return handler;
        }
        if (workstation && blockEntity instanceof dev.whimsyautomation.block.entity.SideConfigurableBlockEntity) {
            return null;
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, endpoint.pos(), null);
        return supports(handler, endpoint, extracting, probe) ? handler : null;
    }

    private static boolean supports(IItemHandler handler,
                                    RemoteTransferNetworkData.Endpoint endpoint,
                                    boolean extracting, ItemStack probe) {
        if (handler == null) return false;
        int first = getFirstHandlerSlot(handler, endpoint);
        int last = endpoint.slotIndex() >= 0 ? first + 1 : handler.getSlots();
        if (last > handler.getSlots()) return false;
        for (int slot = first; slot < last; slot++) {
            if (extracting) {
                if (!handler.extractItem(slot, 1, true).isEmpty()) return true;
            } else if (probe.isEmpty()
                    || handler.insertItem(slot, probe.copyWithCount(1), true).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
