package dev.whimsyautomation.network;

import dev.whimsyautomation.remote.RemoteTransferNetworkData;
import dev.whimsyautomation.remote.RemoteTransferSelection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
                if (owned(data, p.networkId(), player)) {
                    RemoteTransferSelection.begin(player.getUUID(), p.networkId());
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "已进入添加目标模式：Shift+右键点击工作方块或容器。"), true);
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
            case 10 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null && !player.getMainHandItem().isEmpty()) {
                    var n = data.getNetwork(p.networkId());
                    if (n != null) for (var old : n.connections()) if (old.id().equals(p.firstId())) {
                        boolean inputSide = p.value() == 0;
                        var current = inputSide ? old.inputFilter() : old.outputFilter();
                        String fingerprint = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem())
                                + "|" + player.getMainHandItem().getComponents();
                        java.util.ArrayList<String> entries = new java.util.ArrayList<>(current.items());
                        if (!entries.contains(fingerprint)) entries.add(fingerprint);
                        var updated = new RemoteTransferNetworkData.Filter(
                                current.mode() == RemoteTransferNetworkData.FilterMode.NONE
                                        ? RemoteTransferNetworkData.FilterMode.WHITELIST : current.mode(),
                                true, entries);
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
                        var current = side == 0 ? old.inputFilter() : old.outputFilter();
                        java.util.ArrayList<String> entries = new java.util.ArrayList<>();
                        if (exact) current.items().stream().filter(s -> s.contains("|")).forEach(entries::add);
                        java.util.Arrays.stream(p.text().split(","))
                                .map(String::trim).filter(s -> !s.isEmpty()).distinct().limit(64)
                                .forEach(entries::add);
                        var filter = new RemoteTransferNetworkData.Filter(
                                RemoteTransferNetworkData.FilterMode.values()[Math.min(2, mode)],
                                exact, entries);
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

    private static boolean owned(RemoteTransferNetworkData data, UUID id, ServerPlayer player) {
        var n = id == null ? null : data.getNetwork(id);
        return n != null && n.owner().equals(player.getUUID());
    }
}
