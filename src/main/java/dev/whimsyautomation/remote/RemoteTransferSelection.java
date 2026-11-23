package dev.whimsyautomation.remote;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Server-side, per-player one-shot target selection state. */
public final class RemoteTransferSelection {
    private static final ConcurrentMap<UUID, UUID> PENDING = new ConcurrentHashMap<>();
    private RemoteTransferSelection() {}
    public static void begin(UUID player, UUID network) { PENDING.put(player, network); }
    public static UUID get(UUID player) { return PENDING.get(player); }
    public static void clear(UUID player) { PENDING.remove(player); }
}
