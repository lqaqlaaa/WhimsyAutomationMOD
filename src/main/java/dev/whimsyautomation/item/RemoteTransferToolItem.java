package dev.whimsyautomation.item;

import dev.whimsyautomation.network.RemoteTransferActionPayload;
import dev.whimsyautomation.network.RemoteTransferSnapshotPayload;
import dev.whimsyautomation.remote.RemoteTransferNetworkData;
import dev.whimsyautomation.remote.ExternalStorageBridge;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RemoteTransferToolItem extends Item {
    public RemoteTransferToolItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
                                                   InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            // Shift+right-click in the world removes the slot-binding line under
            // the crosshair. useOn performs the same test when a block is behind it.
            if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
            if (player instanceof ServerPlayer serverPlayer) {
                RemoteTransferNetworkData data = RemoteTransferNetworkData.get(serverPlayer.server);
                java.util.UUID networkId = RemoteTransferSelection.activeNetwork(serverPlayer.getUUID());
                var network = networkId == null ? null : data.getNetwork(networkId);
                java.util.UUID bindingId = network == null
                        || !RemoteTransferSelection.connectionsVisible(serverPlayer.getUUID())
                        ? null : findBindingUnderRay(serverPlayer, network, 64.0D);
                if (bindingId != null && data.removeSlotBinding(networkId, bindingId)) {
                    serverPlayer.displayClientMessage(Component.literal("已删除准星指向的槽位连接。"), true);
                    PacketDistributor.sendToPlayer(serverPlayer, RemoteTransferSnapshotPayload.from(data, serverPlayer.getUUID()));
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, false);
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
        RemoteTransferNetworkData.EndpointKind externalKind = ExternalStorageBridge.detectKind(level, pos);
        if (externalKind != null) return ExternalStorageBridge.getHandler(level, pos, externalKind);
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
        RemoteTransferNetworkData data = RemoteTransferNetworkData.get(player.server);
        var activeId = RemoteTransferSelection.activeNetwork(player.getUUID());
        var activeNetwork = activeId == null ? null : data.getNetwork(activeId);
        java.util.UUID lineBinding = activeNetwork == null
                || !RemoteTransferSelection.connectionsVisible(player.getUUID()) ? null
                : findBindingUnderRay(player, activeNetwork,
                    player.getEyePosition().distanceTo(context.getClickLocation()) + 0.08D);
        if (lineBinding != null && data.removeSlotBinding(activeId, lineBinding)) {
            player.displayClientMessage(Component.literal("已删除准星指向的槽位连接。"), true);
            PacketDistributor.sendToPlayer(player, RemoteTransferSnapshotPayload.from(data, player.getUUID()));
            return InteractionResult.SUCCESS;
        }
        if (pending == null) {
            // Do not remove network members by clicking their blocks anymore.
            player.displayClientMessage(Component.literal("请在网络界面选择添加或绑定操作；删除连接请 Shift+右键连接线。"), true);
            return InteractionResult.SUCCESS;
        }

        var network = data.getNetwork(pending.networkId());
        if (network == null || !network.owner().equals(player.getUUID())) {
            RemoteTransferSelection.clear(player.getUUID());
            player.displayClientMessage(Component.literal("网络不存在或不属于你，请重新选择网络。"), true);
            return InteractionResult.SUCCESS;
        }

        boolean reopenConfigurator = false;
        if (pending.mode() == RemoteTransferSelection.Mode.ADD_MEMBER) {
            RemoteTransferNetworkData.EndpointKind detectedKind = ExternalStorageBridge.detectKind(level, pos);
            RemoteTransferNetworkData.EndpointKind endpointKind = detectedKind == null
                    ? RemoteTransferNetworkData.EndpointKind.CONTAINER : detectedKind;
            if (detectedKind != null && ExternalStorageBridge.getHandler(level, pos, detectedKind) == null) {
                player.displayClientMessage(Component.literal(ExternalStorageBridge.unavailableMessage(detectedKind)), true);
                return InteractionResult.SUCCESS;
            }
            var existing = network.members().stream()
                    .filter(member -> member.dimension().equals(level.dimension()) && member.pos().equals(pos))
                    .findFirst().orElse(null);
            if (existing == null) {
                var member = RemoteTransferNetworkData.Endpoint.create(level, pos, endpointKind);
                if (!data.addMember(pending.networkId(), member)) {
                    player.displayClientMessage(Component.literal("该方块已经在当前网络中。"), true);
                    return InteractionResult.SUCCESS;
                }
            } else if (detectedKind != null && existing.kind() != detectedKind) {
                data.setMemberKind(pending.networkId(), existing.id(), detectedKind);
            }
            RemoteTransferSelection.clear(player.getUUID());
            reopenConfigurator = true;
            player.displayClientMessage(Component.literal(detectedKind == null
                    ? "方块已加入网络成员。" : ExternalStorageBridge.connectedMessage(detectedKind)), true);
        } else if (pending.mode() == RemoteTransferSelection.Mode.BIND_SLOT
                || pending.mode() == RemoteTransferSelection.Mode.BIND_SLOT_BATCH) {
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
            RemoteTransferNetworkData.EndpointKind detectedTargetKind = ExternalStorageBridge.detectKind(level, pos);
            if (detectedTargetKind != null) {
                if (ExternalStorageBridge.getHandler(level, pos, detectedTargetKind) == null) {
                    player.displayClientMessage(Component.literal(ExternalStorageBridge.unavailableMessage(detectedTargetKind)), true);
                    return InteractionResult.SUCCESS;
                }
            } else if (findAnyHandler(level, pos) == null) {
                player.displayClientMessage(Component.literal("这个方块没有可用的物品库存接口，不能作为槽位绑定容器。"), true);
                return InteractionResult.SUCCESS;
            }

            var target = network.members().stream()
                    .filter(member -> member.dimension().equals(level.dimension()) && member.pos().equals(pos))
                    .findFirst().orElse(null);
            if (target == null) {
                target = RemoteTransferNetworkData.Endpoint.create(level, pos, detectedTargetKind == null
                        ? RemoteTransferNetworkData.EndpointKind.CONTAINER : detectedTargetKind);
                if (!data.addMember(pending.networkId(), target)) {
                    player.displayClientMessage(Component.literal("无法将目标方块加入网络。"), true);
                    return InteractionResult.SUCCESS;
                }
            } else if (detectedTargetKind != null && target.kind() != detectedTargetKind) {
                java.util.UUID targetId = target.id();
                data.setMemberKind(pending.networkId(), targetId, detectedTargetKind);
                target = network.members().stream().filter(member -> member.id().equals(targetId))
                        .findFirst().orElse(target);
            }

            java.util.List<Integer> slotsToBind = pending.workstationSlots().isEmpty()
                    ? java.util.List.of(pending.workstationSlot()) : pending.workstationSlots();
            int created = 0;
            int skipped = 0;
            for (int workstationSlot : slotsToBind) {
                var binding = RemoteTransferNetworkData.SlotBinding.create(
                        workstation.id(), workstationSlot, pending.direction(), target.id(),
                        context.getClickedFace());
                if (data.addSlotBinding(pending.networkId(), binding)) created++;
                else skipped++;
            }
            if (pending.mode() == RemoteTransferSelection.Mode.BIND_SLOT_BATCH) {
                player.displayClientMessage(Component.literal("批量绑定完成：成功 "
                        + created + " 个，已存在/跳过 " + skipped + " 个槽位。"), true);
            } else if (created == 0) {
                player.displayClientMessage(Component.literal("该工作槽位与目标容器之间已经存在相同绑定。"), true);
            } else {
                player.displayClientMessage(Component.literal(pending.direction()
                        == RemoteTransferNetworkData.TransferDirection.INPUT
                        ? "槽位输入绑定已建立。" : "槽位输出绑定已建立。"), true);
            }
            RemoteTransferSelection.clear(player.getUUID());
        }

        PacketDistributor.sendToPlayer(player, reopenConfigurator
                ? RemoteTransferSnapshotPayload.openConfigurator(data, player.getUUID())
                : RemoteTransferSnapshotPayload.from(data, player.getUUID()));
        return InteractionResult.SUCCESS;
    }

    private static java.util.UUID findBindingUnderRay(ServerPlayer player,
                                             RemoteTransferNetworkData.Network network,
                                             double maxDistance) {
        Vec3 origin = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        java.util.UUID bestId = null;
        double bestAlongRay = maxDistance;
        for (var binding : network.slotBindings()) {
            if (!binding.enabled()) continue;
            var workstation = network.members().stream()
                    .filter(member -> member.id().equals(binding.workstationMember()))
                    .findFirst().orElse(null);
            var target = network.members().stream()
                    .filter(member -> member.id().equals(binding.targetMember()))
                    .findFirst().orElse(null);
            if (workstation == null || target == null
                    || !workstation.dimension().equals(player.level().dimension())
                    || !target.dimension().equals(player.level().dimension())
                    || !player.level().hasChunkAt(workstation.pos())
                    || !player.level().hasChunkAt(target.pos())) continue;

            Vec3 a = Vec3.atCenterOf(workstation.pos());
            Vec3 b = Vec3.atCenterOf(target.pos());
            Vec3 segment = b.subtract(a);
            if (segment.lengthSqr() < 1.0E-6D) continue;
            Vec3 unit = segment.normalize();
            Vec3 reference = Math.abs(unit.y) < 0.9D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 lateral = unit.cross(reference).normalize()
                    .scale(((binding.workstationSlot() % 7) - 3) * 0.065D);
            a = a.add(lateral);
            b = b.add(lateral);
            segment = b.subtract(a);

            Vec3 w = origin.subtract(a);
            double dot = direction.dot(segment);
            double c = segment.lengthSqr();
            double d = direction.dot(w);
            double e = segment.dot(w);
            double denominator = c - dot * dot;
            if (Math.abs(denominator) < 1.0E-8D) continue;
            double t = (e - dot * d) / denominator;
            if (t < 0.08D || t > 0.92D) continue;
            double along = Math.max(0.0D, dot * t - d);
            if (along > maxDistance || along >= bestAlongRay) continue;
            Vec3 rayPoint = origin.add(direction.scale(along));
            Vec3 linePoint = a.add(segment.scale(t));
            if (rayPoint.distanceToSqr(linePoint) <= 0.20D * 0.20D) {
                bestAlongRay = along;
                bestId = binding.id();
            }
        }
        return bestId;
    }
}
