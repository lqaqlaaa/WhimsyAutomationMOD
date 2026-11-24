package dev.whimsyautomation.item;

import dev.whimsyautomation.network.RemoteTransferActionPayload;
import dev.whimsyautomation.network.RemoteTransferSnapshotPayload;
import dev.whimsyautomation.remote.RemoteTransferNetworkData;
import dev.whimsyautomation.remote.RemoteTransferSelection;
import dev.whimsyautomation.block.entity.DeconstructionTableBlockEntity;
import dev.whimsyautomation.block.entity.EnchantmentConversionTableBlockEntity;
import dev.whimsyautomation.block.entity.EnchantmentExtractorBlockEntity;
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
        // Sneaking with the configurator is reserved for block binding. Never open
        // the network screen from the fallback item-use callback in this case.
        if (player.isShiftKeyDown()) {
            // Do not let the fallback item-use path open the screen after a sneaking block click.
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                try {
                    Class.forName("dev.whimsyautomation.client.RemoteTransferClient")
                            .getMethod("open").invoke(null);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Unable to open remote transfer screen", exception);
                }
            }
            PacketDistributor.sendToServer(new RemoteTransferActionPayload(0, null, null, null, 0, ""));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()) return InteractionResult.PASS;
        // Consume the interaction on the client so the target block's menu/use
        // callback cannot run after the configurator has claimed this click.
        if (context.getLevel().isClientSide) return InteractionResult.CONSUME;
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
        var pending = RemoteTransferSelection.get(player.getUUID());
        if (pending == null) {
            player.displayClientMessage(Component.translatable("message.whimsy_automation.remote.select_network_first"), true);
            return InteractionResult.SUCCESS;
        }
        var networkId = pending.networkId();
        var data = RemoteTransferNetworkData.get(player.server);
        var network = data.getNetwork(networkId);
        if (network == null || !network.owner().equals(player.getUUID())) {
            RemoteTransferSelection.clear(player.getUUID());
            return InteractionResult.SUCCESS;
        }

        if (pending.workstationId() == null) {
            if (kind != RemoteTransferNetworkData.EndpointKind.WORKSTATION) {
                player.displayClientMessage(Component.literal("请先 Shift+右键选择工作方块。"), true);
                return InteractionResult.SUCCESS;
            }
            // A workstation can serve both INPUT and OUTPUT bindings. Reuse the
            // existing endpoint in this network instead of trying to add a duplicate.
            var workstation = network.members().stream()
                    .filter(member -> member.kind() == RemoteTransferNetworkData.EndpointKind.WORKSTATION
                            && member.dimension().equals(level.dimension()) && member.pos().equals(pos))
                    .findFirst().orElse(null);
            if (workstation == null) {
                workstation = RemoteTransferNetworkData.Endpoint.create(level, pos, kind);
                if (!data.addMember(networkId, workstation)) {
                    player.displayClientMessage(Component.translatable("message.whimsy_automation.remote.target_exists"), true);
                    return InteractionResult.SUCCESS;
                }
            }
            RemoteTransferSelection.setWorkstation(player.getUUID(), pending, workstation.id());
            player.displayClientMessage(Component.literal("工作方块已标记，请继续 Shift+右键选择连接容器。"), true);
        } else {
            if (kind != RemoteTransferNetworkData.EndpointKind.CONTAINER) {
                player.displayClientMessage(Component.literal("第二个方块必须是连接容器。"), true);
                return InteractionResult.SUCCESS;
            }
            // Reuse an already registered container too, so one physical block can
            // participate in both an INPUT and an OUTPUT connection.
            var container = network.members().stream()
                    .filter(member -> member.kind() == RemoteTransferNetworkData.EndpointKind.CONTAINER
                            && member.dimension().equals(level.dimension()) && member.pos().equals(pos))
                    .findFirst().orElse(null);
            if (container == null) {
                container = RemoteTransferNetworkData.Endpoint.create(level, pos, kind);
                if (!data.addMember(networkId, container)) {
                    player.displayClientMessage(Component.translatable("message.whimsy_automation.remote.target_exists"), true);
                    return InteractionResult.SUCCESS;
                }
            }
            data.addConnection(networkId, RemoteTransferNetworkData.Connection.create(
                    pending.workstationId(), container.id(), pending.direction()));
            RemoteTransferSelection.clear(player.getUUID());
            player.displayClientMessage(Component.literal(pending.direction() == RemoteTransferNetworkData.TransferDirection.INPUT
                    ? "输入连接已建立。" : "输出连接已建立。"), true);
        }
        PacketDistributor.sendToPlayer(player, RemoteTransferSnapshotPayload.from(data, player.getUUID()));
        return InteractionResult.SUCCESS;
    }
}
