package dev.whimsyautomation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public final class RemoteTransferClient {
    private RemoteTransferClient() {}
    public static void open() {
        Minecraft.getInstance().setScreen(new RemoteTransferScreen());
    }
    public static void applySnapshot(CompoundTag data) {
        RemoteTransferOverlay.applySnapshot(data);
        Minecraft minecraft = Minecraft.getInstance();
        // Server snapshots update an open configurator, but must not reopen the
        // screen after the player has closed it to perform a world interaction.
        if (minecraft.screen instanceof RemoteTransferScreen screen) screen.applySnapshot(data);
    }
}
