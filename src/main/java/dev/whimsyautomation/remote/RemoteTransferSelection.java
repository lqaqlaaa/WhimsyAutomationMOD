package dev.whimsyautomation.remote;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Server-side, per-player context for the next remote-tool block interaction. */
public final class RemoteTransferSelection {
    private static final ConcurrentMap<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private RemoteTransferSelection() {}

    public static void beginAddMember(UUID player, UUID network) {
        PENDING.put(player, new Pending(network, Mode.ADD_MEMBER, null, -1, null));
    }

    public static void beginSlotBinding(UUID player, UUID network, UUID workstation,
                                        int workstationSlot,
                                        RemoteTransferNetworkData.TransferDirection direction) {
        if (workstationSlot < 0) throw new IllegalArgumentException("Workstation slot must be non-negative");
        PENDING.put(player, new Pending(network, Mode.BIND_SLOT, workstation, workstationSlot, direction));
    }

    public static Pending get(UUID player) {
        return PENDING.get(player);
    }

    public static void clear(UUID player) {
        PENDING.remove(player);
    }

    public enum Mode {
        ADD_MEMBER,
        BIND_SLOT
    }

    public record Pending(UUID networkId, Mode mode, UUID workstationId, int workstationSlot,
                          RemoteTransferNetworkData.TransferDirection direction) {}
}
