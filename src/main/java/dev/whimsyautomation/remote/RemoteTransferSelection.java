package dev.whimsyautomation.remote;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Two-step, per-player selection: workstation first, container second. */
public final class RemoteTransferSelection {
    private static final ConcurrentMap<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private RemoteTransferSelection() {}

    public static void begin(UUID player, UUID network, RemoteTransferNetworkData.TransferDirection direction) {
        PENDING.put(player, new Pending(network, direction, null));
    }

    public static Pending get(UUID player) { return PENDING.get(player); }
    public static void setWorkstation(UUID player, Pending pending, UUID workstationId) {
        PENDING.put(player, new Pending(pending.networkId(), pending.direction(), workstationId));
    }
    public static void clear(UUID player) { PENDING.remove(player); }

    public record Pending(UUID networkId, RemoteTransferNetworkData.TransferDirection direction, UUID workstationId) {}
}