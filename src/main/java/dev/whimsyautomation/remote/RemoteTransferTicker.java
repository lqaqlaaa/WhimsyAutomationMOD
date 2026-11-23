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

        IItemHandler sourceHandler = findHandler(sourceLevel, source, true);
        IItemHandler targetHandler = findHandler(targetLevel, target, false);
        if (sourceHandler == null || targetHandler == null) return;

        int remainingBudget = MAX_ITEMS_PER_LINK_PER_TICK;
        for (int slot = 0; slot < sourceHandler.getSlots() && remainingBudget > 0; slot++) {
            ItemStack offered = sourceHandler.getStackInSlot(slot);
            if (offered.isEmpty() || !sourceFilter.allows(offered)) continue;
            int request = Math.min(remainingBudget, offered.getCount());
            ItemStack simulated = sourceHandler.extractItem(slot, request, true);
            if (simulated.isEmpty() || !targetFilter.allows(simulated)) continue;

            int accepted = 0;
            for (int targetSlot = 0; targetSlot < targetHandler.getSlots(); targetSlot++) {
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
            for (int targetSlot = 0; targetSlot < targetHandler.getSlots() && !notInserted.isEmpty(); targetSlot++) {
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
                                             boolean extracting) {
        var blockEntity = level.getBlockEntity(endpoint.pos());
        boolean workstation = endpoint.kind() == RemoteTransferNetworkData.EndpointKind.WORKSTATION;
        for (Direction side : Direction.values()) {
            if (workstation && blockEntity instanceof dev.whimsyautomation.block.entity.SideConfigurableBlockEntity config) {
                int mode = config.getSideMode(side);
                if (extracting ? mode != 2 : mode != 1) continue;
            }
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                    endpoint.pos(), side);
            if (handler != null) return handler;
        }
        if (workstation && blockEntity instanceof dev.whimsyautomation.block.entity.SideConfigurableBlockEntity config) {
            // Remote links still respect the six-face input/output policy. A
            // workstation with no configured eligible face is intentionally idle.
            return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, endpoint.pos(), null);
    }
}
