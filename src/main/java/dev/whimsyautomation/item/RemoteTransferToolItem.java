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

    private static boolean hasItemHandler(Level level, net.minecraft.core.BlockPos pos) {
        var capability = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK;
        if (level.getCapability(capability, pos, null) != null) return true;
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            if (level.getCapability(capability, pos, side) != null) return true;
        }
        return false;
    }

    private static net.neoforged.neoforge.items.IItemHandler findAnyHandler(
            Level level, net.minecraft.core.BlockPos pos) {
        var capability = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK;
        var handler = level.getCapability(capability, pos, null);
        if (handler != null) return handler;
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            handler = level.getCapability(capability, pos, side);
            if (handler != null) return handler;
        }
        return null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.CONSUME;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;

        Level level = context.getLevel();
        var pos = context.getClickedPos();
        RemoteTransferSelection.Pending pending = RemoteTransferSelection.get(player.getUUID());
        if (pending == null) {
            player.displayClientMessage(Component.literal("请先在网络界面选择网络，并启动“添加成员”或具体槽位的绑定操作。"), true);
            return InteractionResult.SUCCESS;
        }

        RemoteTransferNetworkData data = RemoteTransferNetworkData.get(player.server);
        var network = data.getNetwork(pending.networkId());
        if (network == null || !network.owner().equals(player.getUUID())) {
            RemoteTransferSelection.clear(player.getUUID());
            player.displayClientMessage(Component.literal("网络不存在或不属于你，请重新选择网络。"), true);
            return InteractionResult.SUCCESS;
        }

        if (pending.mode() == RemoteTransferSelection.Mode.ADD_MEMBER) {
            var existing = network.members().stream()
                    .filter(member -> member.dimension().equals(level.dimension()) && member.pos().equals(pos))
                    .findFirst().orElse(null);
            if (existing == null) {
                var member = RemoteTransferNetworkData.Endpoint.create(
                        level, pos, RemoteTransferNetworkData.EndpointKind.CONTAINER);
                if (!data.addMember(pending.networkId(), member)) {
                    player.displayClientMessage(Component.literal("该方块已经在当前网络中。"), true);
                    return InteractionResult.SUCCESS;
                }
            }
            RemoteTransferSelection.clear(player.getUUID());
            player.displayClientMessage(Component.literal("方块已加入网络成员。"), true);
        } else if (pending.mode() == RemoteTransferSelection.Mode.BIND_SLOT) {
            var workstation = network.members().stream()
                    .filter(member -> member.id().equals(pending.workstationId()))
                    .findFirst().orElse(null);
            if (workstation == null) {
                RemoteTransferSelection.clear(player.getUUID());
                player.displayClientMessage(Component.literal("选中的工作方块已不在网络中，请重新选择槽位。"), true);
                return InteractionResult.SUCCESS;
            }
            if (workstation.dimension().equals(level.dimension()) && workstation.pos().equals(pos)) {
                player.displayClientMessage(Component.literal("目标容器不能与工作方块是同一个方块。"), true);
                return InteractionResult.SUCCESS;
            }
            if (findAnyHandler(level, pos) == null) {
                player.displayClientMessage(Component.literal("这个方块没有可用的物品库存接口，不能作为槽位绑定容器。"), true);
                return InteractionResult.SUCCESS;
            }

            var target = network.members().stream()
                    .filter(member -> member.dimension().equals(level.dimension()) && member.pos().equals(pos))
                    .findFirst().orElse(null);
            if (target == null) {
                target = RemoteTransferNetworkData.Endpoint.create(
                        level, pos, RemoteTransferNetworkData.EndpointKind.CONTAINER);
                if (!data.addMember(pending.networkId(), target)) {
                    player.displayClientMessage(Component.literal("无法将目标方块加入网络。"), true);
                    return InteractionResult.SUCCESS;
                }
            }

            var binding = RemoteTransferNetworkData.SlotBinding.create(
                    workstation.id(), pending.workstationSlot(), pending.direction(), target.id(),
                    context.getClickedFace());
            if (!data.addSlotBinding(pending.networkId(), binding)) {
                player.displayClientMessage(Component.literal("该工作槽位与目标容器之间已经存在相同绑定。"), true);
            } else {
                player.displayClientMessage(Component.literal(pending.direction()
                        == RemoteTransferNetworkData.TransferDirection.INPUT
                        ? "槽位输入绑定已建立。" : "槽位输出绑定已建立。"), true);
            }
            RemoteTransferSelection.clear(player.getUUID());
        }

        PacketDistributor.sendToPlayer(player, RemoteTransferSnapshotPayload.from(data, player.getUUID()));
        return InteractionResult.SUCCESS;
    }
}
