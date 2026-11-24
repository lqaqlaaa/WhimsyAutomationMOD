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
        if (data != null && data.getBoolean("OpenConfigurator")) {
            minecraft.setScreen(new RemoteTransferScreen(data));
            return;
        }
        // Ordinary snapshots update an open configurator, but must not reopen
        // the screen after the player closes it for another world interaction.
        if (minecraft.screen instanceof RemoteTransferScreen screen) screen.applySnapshot(data);
    }
}
