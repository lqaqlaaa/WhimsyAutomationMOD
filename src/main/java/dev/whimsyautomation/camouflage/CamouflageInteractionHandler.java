package dev.whimsyautomation.camouflage;

import dev.whimsyautomation.WhimsyAutomation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Shared sneak-right-click camouflage interaction for all supported machines. */
@EventBusSubscriber(modid = WhimsyAutomation.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class CamouflageInteractionHandler {
    private CamouflageInteractionHandler() {}

    @SubscribeEvent
    public static void onRightClickMachine(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof CamouflageableBlockEntity machine)) return;

        ItemStack held = event.getItemStack();
        BlockState current = machine.getCamouflageState();
        if (!held.isEmpty()) {
            if (!(held.getItem() instanceof BlockItem blockItem)) return;
            Block block = blockItem.getBlock();
            BlockPlaceContext placement = new BlockPlaceContext(new UseOnContext(player, event.getHand(), event.getHitVec()));
            BlockState camouflage = block.getStateForPlacement(placement);
            if (camouflage == null) camouflage = block.defaultBlockState();
            if (camouflage.isAir() || !current.isAir()) return;

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            if (!event.getLevel().isClientSide) {
                machine.setCamouflageState(camouflage);
                CamouflageStateUpdater.updateAllNeighbors(event.getLevel(), event.getPos());
                if (!player.getAbilities().instabuild) held.shrink(1);
            }
            return;
        }

        if (current.isAir()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!event.getLevel().isClientSide) {
            ItemStack returned = new ItemStack(current.getBlock().asItem());
            if (!returned.isEmpty() && !player.getInventory().add(returned)) Block.popResource(event.getLevel(), event.getPos(), returned);
            machine.setCamouflageState(Blocks.AIR.defaultBlockState());
        }
    }
}
