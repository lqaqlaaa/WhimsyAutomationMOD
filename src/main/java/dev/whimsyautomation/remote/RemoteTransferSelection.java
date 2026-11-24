package dev.whimsyautomation.remote;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Server-side, per-player context for the next remote-tool block interaction. */
public final class RemoteTransferSelection {
    private static final ConcurrentMap<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, UUID> ACTIVE_NETWORK = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Boolean> CONNECTION_LINES_VISIBLE = new ConcurrentHashMap<>();

    private RemoteTransferSelection() {}

    public static void beginAddMember(UUID player, UUID network) {
        ACTIVE_NETWORK.put(player, network);
        PENDING.put(player, new Pending(network, Mode.ADD_MEMBER, null, -1, null, List.of()));
    }

    public static void beginSlotBinding(UUID player, UUID network, UUID workstation,
                                        int workstationSlot,
                                        RemoteTransferNetworkData.TransferDirection direction) {
        ACTIVE_NETWORK.put(player, network);
        if (workstationSlot < 0) throw new IllegalArgumentException("Workstation slot must be non-negative");
        PENDING.put(player, new Pending(network, Mode.BIND_SLOT, workstation, workstationSlot, direction, List.of(workstationSlot)));
    }

    public static void beginSlotBindings(UUID player, UUID network, UUID workstation,
                                          List<Integer> workstationSlots,
                                          RemoteTransferNetworkData.TransferDirection direction) {
        ACTIVE_NETWORK.put(player, network);
        List<Integer> validSlots = workstationSlots == null ? List.of()
                : workstationSlots.stream().filter(slot -> slot != null && slot >= 0).distinct().sorted().toList();
        if (validSlots.isEmpty()) throw new IllegalArgumentException("At least one workstation slot is required");
        PENDING.put(player, new Pending(network, Mode.BIND_SLOT_BATCH, workstation, -1, direction, validSlots));
    }

    public static UUID activeNetwork(UUID player) { return ACTIVE_NETWORK.get(player); }

    /** Server-authoritative copy of the client's global connection-line visibility toggle. */
    public static boolean connectionsVisible(UUID player) {
        return CONNECTION_LINES_VISIBLE.getOrDefault(player, true);
    }

    public static void setConnectionsVisible(UUID player, boolean visible) {
        CONNECTION_LINES_VISIBLE.put(player, visible);
    }

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
        CONNECTION_LINES_VISIBLE.remove(player);
    }

    public enum Mode {
        ADD_MEMBER,
        BIND_SLOT,
        BIND_SLOT_BATCH
    }

    public record Pending(UUID networkId, Mode mode, UUID workstationId, int workstationSlot,
                          RemoteTransferNetworkData.TransferDirection direction, List<Integer> workstationSlots) {
        public Pending {
            workstationSlots = workstationSlots == null ? List.of() : List.copyOf(workstationSlots);
        }
    }
}
