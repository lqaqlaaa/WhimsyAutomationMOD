package dev.whimsyautomation.network;

import dev.whimsyautomation.remote.RemoteTransferNetworkData;
import dev.whimsyautomation.remote.RemoteTransferSelection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.UUID;

public record RemoteTransferActionPayload(int action, UUID networkId, UUID firstId, UUID secondId,
                                          int value, String text) implements CustomPacketPayload {
    public static final Type<RemoteTransferActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("whimsy_automation", "remote_transfer_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteTransferActionPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {
                buf.writeVarInt(p.action());
                writeUuid(buf, p.networkId()); writeUuid(buf, p.firstId()); writeUuid(buf, p.secondId());
                buf.writeVarInt(p.value()); buf.writeUtf(p.text(), 256);
            }, buf -> new RemoteTransferActionPayload(buf.readVarInt(), readUuid(buf), readUuid(buf),
                    readUuid(buf), buf.readVarInt(), buf.readUtf(256)));

    private static void writeUuid(RegistryFriendlyByteBuf buf, UUID id) {
        buf.writeBoolean(id != null); if (id != null) buf.writeUUID(id);
    }
    private static UUID readUuid(RegistryFriendlyByteBuf buf) { return buf.readBoolean() ? buf.readUUID() : null; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            context.enqueueWork(() -> handle(payload, player));
        });
    }

    private static void handle(RemoteTransferActionPayload p, ServerPlayer player) {
        RemoteTransferNetworkData data = RemoteTransferNetworkData.get(player.server);
        switch (p.action()) {
            case 0 -> { /* request current snapshot */ }
            case 1 -> data.createNetwork(player.getUUID(), p.text().isBlank()
                    ? "新建网络" : p.text());
            case 2 -> {
                if (owned(data, p.networkId(), player)) data.renameNetwork(p.networkId(), p.text());
            }
            case 3 -> {
                if (owned(data, p.networkId(), player)) {
                    data.deleteNetwork(p.networkId(), player.getUUID());
                    RemoteTransferSelection.clear(player.getUUID());
                }
            }
            case 4 -> {
                if (owned(data, p.networkId(), player) && (p.value() == 0 || p.value() == 1)) {
                    var direction = p.value() == 0
                            ? RemoteTransferNetworkData.TransferDirection.INPUT
                            : RemoteTransferNetworkData.TransferDirection.OUTPUT;
                    RemoteTransferSelection.begin(player.getUUID(), p.networkId(), direction);
                    player.displayClientMessage(Component.literal(p.value() == 0
                            ? "输入绑定已启动：先 Shift+右键工作方块，再 Shift+右键连接容器。"
                            : "输出绑定已启动：先 Shift+右键工作方块，再 Shift+右键连接容器。"), true);
                }
            }
            case 5 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null)
                    data.removeMember(p.networkId(), p.firstId());
            }
            case 6 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null && p.secondId() != null
                        && p.value() >= 0 && p.value() <= 2) {
                    data.addConnection(p.networkId(), RemoteTransferNetworkData.Connection.create(
                            p.firstId(), p.secondId(), RemoteTransferNetworkData.TransferDirection.values()[p.value()]));
                }
            }
            case 7 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null)
                    data.removeConnection(p.networkId(), p.firstId());
            }
            case 8 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null) {
                    var n = data.getNetwork(p.networkId());
                    if (n != null) for (var old : n.connections()) if (old.id().equals(p.firstId())) {
                        var next = RemoteTransferNetworkData.TransferDirection.values()[
                                (old.direction().ordinal() + 1) % RemoteTransferNetworkData.TransferDirection.values().length];
                        data.updateConnection(p.networkId(), new RemoteTransferNetworkData.Connection(old.id(),
                                old.sourceMember(), old.targetMember(), next, old.enabled(), old.inputFilter(), old.outputFilter()));
                        break;
                    }
                }
            }
            case 12 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null) {
                    var network = data.getNetwork(p.networkId());
                    if (network != null) for (var old : network.connections()) if (old.id().equals(p.firstId())) {
                        data.updateConnection(p.networkId(), new RemoteTransferNetworkData.Connection(old.id(),
                                old.sourceMember(), old.targetMember(), old.direction(), !old.enabled(),
                                old.inputFilter(), old.outputFilter(), old.lineVisible()));
                        break;
                    }
                }
            }
            case 13 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null) {
                    var network = data.getNetwork(p.networkId());
                    if (network != null) for (var old : network.connections()) if (old.id().equals(p.firstId())) {
                        var next = old.direction() == RemoteTransferNetworkData.TransferDirection.INPUT
                                ? RemoteTransferNetworkData.TransferDirection.OUTPUT
                                : RemoteTransferNetworkData.TransferDirection.INPUT;
                        data.updateConnection(p.networkId(), new RemoteTransferNetworkData.Connection(old.id(),
                                old.sourceMember(), old.targetMember(), next, old.enabled(),
                                old.inputFilter(), old.outputFilter(), old.lineVisible()));
                        break;
                    }
                }
            }
            case 14 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null) {
                    var network = data.getNetwork(p.networkId());
                    if (network != null) for (var old : network.connections()) if (old.id().equals(p.firstId())) {
                        data.updateConnection(p.networkId(), new RemoteTransferNetworkData.Connection(old.id(),
                                old.sourceMember(), old.targetMember(), old.direction(), old.enabled(),
                                old.inputFilter(), old.outputFilter(), !old.lineVisible()));
                        break;
                    }
                }
            }
            case 11 -> {
                if (!owned(data, p.networkId(), player) || p.firstId() == null) return;
                if (!player.hasPermissions(2)) {
                    player.displayClientMessage(Component.literal("需要开启作弊或拥有管理员权限才能传送到网络方块。"), true);
                    return;
                }
                var network = data.getNetwork(p.networkId());
                if (network == null) return;
                for (var member : network.members()) {
                    if (member.id().equals(p.firstId())) {
                        teleportNearMember(player, member);
                        break;
                    }
                }
            }
            case 10 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null && !player.getMainHandItem().isEmpty()) {
                    var n = data.getNetwork(p.networkId());
                    if (n != null) for (var old : n.connections()) if (old.id().equals(p.firstId())) {
                        boolean inputSide = p.value() == 0;
                        var current = inputSide ? old.inputFilter() : old.outputFilter();
                        String fingerprint = RemoteTransferNetworkData.Filter.fingerprint(player.getMainHandItem(),
                                current.ignoreDurability(), current.ignoreNbt());
                        java.util.ArrayList<String> entries = new java.util.ArrayList<>(current.items());
                        if (!entries.contains(fingerprint)) entries.add(fingerprint);
                        var updated = new RemoteTransferNetworkData.Filter(
                                current.mode() == RemoteTransferNetworkData.FilterMode.NONE
                                        ? RemoteTransferNetworkData.FilterMode.WHITELIST : current.mode(),
                                true, entries, true, current.ignoreDurability(), current.ignoreNbt(), current.matchTags());
                        data.updateConnection(p.networkId(), new RemoteTransferNetworkData.Connection(old.id(),
                                old.sourceMember(), old.targetMember(), old.direction(), old.enabled(),
                                inputSide ? updated : old.inputFilter(), inputSide ? old.outputFilter() : updated));
                        break;
                    }
                }
            }
            case 9 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null) {
                    var n = data.getNetwork(p.networkId());
                    if (n != null) for (var old : n.connections()) if (old.id().equals(p.firstId())) {
                        int side = p.value() & 1;
                        int mode = (p.value() >> 1) & 3;
                        boolean exact = (p.value() & 8) != 0;
                        boolean ignoreDurability = (p.value() & 16) != 0;
                        boolean ignoreNbt = (p.value() & 32) != 0;
                        boolean matchTags = (p.value() & 64) != 0;
                        var current = side == 0 ? old.inputFilter() : old.outputFilter();
                        java.util.ArrayList<String> entries = new java.util.ArrayList<>();
                        if (exact) current.items().stream().filter(s -> s.contains("|")).forEach(entries::add);
                        java.util.Arrays.stream(p.text().split(","))
                                .map(String::trim).filter(s -> !s.isEmpty()).distinct().limit(64)
                                .forEach(entries::add);
                        var filter = new RemoteTransferNetworkData.Filter(
                                RemoteTransferNetworkData.FilterMode.values()[Math.min(2, mode)],
                                exact, entries, mode != 0, ignoreDurability, ignoreNbt, matchTags);
                        data.updateConnection(p.networkId(), new RemoteTransferNetworkData.Connection(old.id(),
                                old.sourceMember(), old.targetMember(), old.direction(), old.enabled(),
                                side == 0 ? filter : old.inputFilter(), side == 1 ? filter : old.outputFilter()));
                        break;
                    }
                }
            }
            default -> { return; }
        }
        PacketDistributor.sendToPlayer(player, RemoteTransferSnapshotPayload.from(data, player.getUUID()));
    }


    /** Teleports only to an already-loaded, collision-free position with a solid floor and 2-block clearance. */
    private static void teleportNearMember(ServerPlayer player, RemoteTransferNetworkData.Endpoint member) {
        ServerLevel targetLevel = player.serverLevel().getServer().getLevel(member.dimension());
        if (targetLevel == null || !targetLevel.hasChunkAt(member.pos())) {
            player.displayClientMessage(Component.literal("目标方块所在维度或区块当前不可用。"), true);
            return;
        }

        BlockPos origin = member.pos();
        double width = player.getBbWidth();
        double height = player.getBbHeight();
        for (int radius = 0; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    // dy=0 is essential for adjacent ground-level positions:
                    // the floor is then at originY-1. dy=1 is the position directly
                    // above the target block itself.
                    for (int dy = 0; dy <= 4; dy++) {
                        BlockPos feet = origin.offset(dx, dy, dz);
                        if (!targetLevel.hasChunkAt(feet) || !targetLevel.hasChunkAt(feet.below())) continue;
                        if (!targetLevel.getBlockState(feet.below()).isFaceSturdy(targetLevel, feet.below(), Direction.UP)) continue;
                        double x = feet.getX() + 0.5D;
                        double y = feet.getY();
                        double z = feet.getZ() + 0.5D;
                        AABB box = new AABB(x - width / 2.0D, y, z - width / 2.0D,
                                x + width / 2.0D, y + height, z + width / 2.0D);
                        if (!targetLevel.noCollision(player, box)) continue;
                        player.teleportTo(targetLevel, x, y, z, java.util.Set.of(),
                                player.getYRot(), player.getXRot());
                        player.displayClientMessage(Component.literal("已传送到网络方块附近。"), true);
                        return;
                    }
                }
            }
        }
        player.displayClientMessage(Component.literal("目标方块附近没有安全位置（需要地面和至少两格空间）。"), true);
    }

    private static boolean owned(RemoteTransferNetworkData data, UUID id, ServerPlayer player) {
        var n = id == null ? null : data.getNetwork(id);
        return n != null && n.owner().equals(player.getUUID());
    }
}
