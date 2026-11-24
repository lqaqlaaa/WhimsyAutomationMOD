package dev.whimsyautomation.remote;

import dev.whimsyautomation.WhimsyAutomation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Executes saved remote-network links on the logical server.
 *
 * The ticker never force-loads chunks. A link is simply skipped until both
 * endpoints are in already-loaded chunks and expose item-handler capabilities.
 */
@EventBusSubscriber(modid = WhimsyAutomation.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class RemoteTransferTicker {
    private static final int MAX_ITEMS_PER_LINK_PER_TICK = Integer.MAX_VALUE;
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
                            RemoteTransferNetworkData.Filter.none(), RemoteTransferNetworkData.Filter.none());
                } else if (binding.direction() == RemoteTransferNetworkData.TransferDirection.OUTPUT) {
                    transfer(server, workstationSlot, targetSlot,
                            RemoteTransferNetworkData.Filter.none(), RemoteTransferNetworkData.Filter.none());
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
        int firstSourceSlot = source.slotIndex() >= 0 ? source.slotIndex() : 0;
        int lastSourceSlot = source.slotIndex() >= 0 ? source.slotIndex() + 1 : sourceHandler.getSlots();
        if (lastSourceSlot > sourceHandler.getSlots()) return;
        for (int slot = firstSourceSlot; slot < lastSourceSlot && remainingBudget > 0; slot++) {
            ItemStack offered = sourceHandler.getStackInSlot(slot);
            if (offered.isEmpty() || !sourceFilter.allows(offered)) continue;
            int request = Math.min(remainingBudget, offered.getCount());
            ItemStack simulated = sourceHandler.extractItem(slot, request, true);
            if (simulated.isEmpty() || !targetFilter.allows(simulated)) continue;
            // Pick the target face that can actually accept this item in the
            // requested slot. This is required for sided inventories such as
            // vanilla furnaces (top=input, sides=fuel, bottom=output).
            IItemHandler targetHandler = findHandler(targetLevel, target, false, simulated);
            if (targetHandler == null) continue;

            int accepted = 0;
            int firstTargetSlot = target.slotIndex() >= 0 ? target.slotIndex() : 0;
            int lastTargetSlot = target.slotIndex() >= 0 ? target.slotIndex() + 1 : targetHandler.getSlots();
            if (lastTargetSlot > targetHandler.getSlots()) continue;
            for (int targetSlot = firstTargetSlot; targetSlot < lastTargetSlot; targetSlot++) {
                ItemStack remainder = targetHandler.insertItem(targetSlot, simulated.copyWithCount(
                        Math.min(simulated.getCount() - accepted, remainingBudget)), true);
                int inserted = Math.min(simulated.getCount() - accepted, remainingBudget) - remainder.getCount();
                accepted += Math.max(0, inserted);
                if (accepted >= simulated.getCount() || accepted >= remainingBudget) break;
            }
            if (accepted <= 0) continue;

            ItemStack extracted = sourceHandler.extractItem(slot, accepted, false);
            if (extracted.isEmpty()) continue;
            ItemStack notInserted = extracted;
            for (int targetSlot = firstTargetSlot; targetSlot < lastTargetSlot && !notInserted.isEmpty(); targetSlot++) {
                notInserted = targetHandler.insertItem(targetSlot, notInserted, false);
            }
            // If a handler changed between simulation and execution, put any
            // unexpected remainder back into its original source slot.
            if (!notInserted.isEmpty()) {
                ItemStack refund = sourceHandler.insertItem(slot, notInserted, false);
                if (!refund.isEmpty()) {
                    // No item is discarded: retain it at the source block by
                    // dropping only as a last resort when a non-conforming
                    // handler refuses its own extracted stack.
                    sourceLevel.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                            sourceLevel, source.pos().getX() + 0.5, source.pos().getY() + 0.5,
                            source.pos().getZ() + 0.5, refund));
                }
            }
            remainingBudget -= Math.min(accepted, extracted.getCount() - notInserted.getCount());
        }
    }

    private static IItemHandler findHandler(ServerLevel level,
                                             RemoteTransferNetworkData.Endpoint endpoint,
                                             boolean extracting, ItemStack probe) {
        var blockEntity = level.getBlockEntity(endpoint.pos());
        boolean workstation = endpoint.kind() == RemoteTransferNetworkData.EndpointKind.WORKSTATION;
        if (endpoint.face() != null) {
            Direction side = endpoint.face();
            if (workstation && blockEntity instanceof dev.whimsyautomation.block.entity.SideConfigurableBlockEntity config) {
                int mode = config.getSideMode(side);
                if (extracting ? mode != 2 : mode != 1) return null;
            }
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, endpoint.pos(), side);
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
        int first = endpoint.slotIndex() >= 0 ? endpoint.slotIndex() : 0;
        int last = endpoint.slotIndex() >= 0 ? endpoint.slotIndex() + 1 : handler.getSlots();
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
