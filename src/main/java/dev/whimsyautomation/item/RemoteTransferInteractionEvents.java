package dev.whimsyautomation.item;

import dev.whimsyautomation.WhimsyAutomation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Gives the remote-transfer tool exclusive control of sneaking block clicks.
 * The block interaction must be denied before Block#use can open a machine GUI,
 * while the item interaction remains enabled so RemoteTransferToolItem.useOn
 * can register the selected endpoint.
 */
@EventBusSubscriber(modid = WhimsyAutomation.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class RemoteTransferInteractionEvents {
    private RemoteTransferInteractionEvents() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()
                || !(player.getItemInHand(event.getHand()).getItem() instanceof RemoteTransferToolItem)) {
            return;
        }

        // Prevent workstation/container block handlers from opening their GUI.
        event.setUseBlock(TriState.FALSE);
        // Still run Item.useOn on both logical sides to perform network binding.
        event.setUseItem(TriState.TRUE);
    }
}
