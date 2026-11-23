package dev.whimsyautomation.item;

import dev.whimsyautomation.client.RemoteTransferScreen;
import dev.whimsyautomation.network.RemoteTransferActionPayload;
import dev.whimsyautomation.network.RemoteTransferSnapshotPayload;
import dev.whimsyautomation.remote.RemoteTransferNetworkData;
import dev.whimsyautomation.remote.RemoteTransferSelection;
import dev.whimsyautomation.block.entity.DeconstructionTableBlockEntity;
import dev.whimsyautomation.block.entity.EnchantmentConversionTableBlockEntity;
import dev.whimsyautomation.block.entity.EnchantmentExtractorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RemoteTransferToolItem extends Item {
    public RemoteTransferToolItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
                                                   InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            Minecraft.getInstance().setScreen(new RemoteTransferScreen());
            PacketDistributor.sendToServer(new RemoteTransferActionPayload(0, null, null, null, 0, ""));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getPlayer().isShiftKeyDown()) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var be = level.getBlockEntity(pos);
        RemoteTransferNetworkData.EndpointKind kind;
        if (be instanceof DeconstructionTableBlockEntity
                || be instanceof EnchantmentExtractorBlockEntity
                || be instanceof EnchantmentConversionTableBlockEntity) {
            kind = RemoteTransferNetworkData.EndpointKind.WORKSTATION;
        } else if (level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                pos, context.getClickedFace()) != null
                || level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                pos, null) != null) {
            kind = RemoteTransferNetworkData.EndpointKind.CONTAINER;
        } else {
            player.displayClientMessage(Component.translatable("message.whimsy_automation.remote.not_container"), true);
            return InteractionResult.FAIL;
        }
        var networkId = RemoteTransferSelection.get(player.getUUID());
        if (networkId == null) {
            player.displayClientMessage(Component.translatable("message.whimsy_automation.remote.select_network_first"), true);
            return InteractionResult.FAIL;
        }
        var data = RemoteTransferNetworkData.get(player.server);
        var network = data.getNetwork(networkId);
        if (network == null || !network.owner().equals(player.getUUID())) {
            RemoteTransferSelection.clear(player.getUUID());
            return InteractionResult.FAIL;
        }
        boolean added = data.addMember(networkId, RemoteTransferNetworkData.Endpoint.create(
                level.dimension(), pos, kind));
        RemoteTransferSelection.clear(player.getUUID());
        player.displayClientMessage(Component.translatable(added
                ? "message.whimsy_automation.remote.target_added"
                : "message.whimsy_automation.remote.target_exists"), true);
        PacketDistributor.sendToPlayer(player, RemoteTransferSnapshotPayload.from(data, player.getUUID()));
        return InteractionResult.SUCCESS;
    }
}
