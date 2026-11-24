package dev.whimsyautomation.remote;

import net.neoforged.neoforge.items.IItemHandler;

/** Stable, unsided inventory view used by per-slot remote workstation bindings. */
public interface RemoteTransferInventoryProvider {
    IItemHandler getRemoteTransferInventory();
}
