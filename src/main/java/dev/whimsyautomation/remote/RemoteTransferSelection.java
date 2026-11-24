package dev.whimsyautomation.remote;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Server-side, per-player context for the next remote-tool block interaction. */
public final class RemoteTransferSelection {
    private static final ConcurrentMap<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, UUID> ACTIVE_NETWORK = new ConcurrentHashMap<>();

    private RemoteTransferSelection() {}

    public static void beginAddMember(UUID player, UUID network) {
        ACTIVE_NETWORK.put(player, network);
        PENDING.put(player, new Pending(network, Mode.ADD_MEMBER, null, -1, null));
    }

    public static void beginSlotBinding(UUID player, UUID network, UUID workstation,
                                        int workstationSlot,
                                        RemoteTransferNetworkData.TransferDirection direction) {
        ACTIVE_NETWORK.put(player, network);
        if (workstationSlot < 0) throw new IllegalArgumentException("Workstation slot must be non-negative");
        PENDING.put(player, new Pending(network, Mode.BIND_SLOT, workstation, workstationSlot, direction));
    }

    public static UUID activeNetwork(UUID player) { return ACTIVE_NETWORK.get(player); }

    public static void setActiveNetwork(UUID player, UUID network) { if (network == null) ACTIVE_NETWORK.remove(player); else ACTIVE_NETWORK.put(player, network); }

    public static Pending get(UUID player) {
        return PENDING.get(player);
    }

    public static void clear(UUID player) {
        PENDING.remove(player);
    }

    public static void clearAll(UUID player) {
        PENDING.remove(player);
        ACTIVE_NETWORK.remove(player);
    }

    public enum Mode {
        ADD_MEMBER,
        BIND_SLOT
    }

    public record Pending(UUID networkId, Mode mode, UUID workstationId, int workstationSlot,
                          RemoteTransferNetworkData.TransferDirection direction) {}
}
