package dev.whimsyautomation.network;

import dev.whimsyautomation.remote.RemoteTransferNetworkData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.UUID;

public record RemoteTransferSnapshotPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<RemoteTransferSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("whimsy_automation", "remote_transfer_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteTransferSnapshotPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> buf.writeNbt(p.data()), RemoteTransferSnapshotPayload::decode);
    private static RemoteTransferSnapshotPayload decode(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new RemoteTransferSnapshotPayload(tag == null ? new CompoundTag() : tag);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static RemoteTransferSnapshotPayload from(RemoteTransferNetworkData source, UUID owner) {
        CompoundTag root = new CompoundTag();
        net.minecraft.nbt.ListTag networks = new net.minecraft.nbt.ListTag();
        for (var network : source.networks()) {
            if (!network.owner().equals(owner)) continue;
            CompoundTag n = new CompoundTag();
            n.putUUID("Id", network.id()); n.putString("Name", network.name());
            net.minecraft.nbt.ListTag members = new net.minecraft.nbt.ListTag();
            for (var member : network.members()) {
                CompoundTag m = new CompoundTag();
                m.putUUID("Id", member.id()); m.putString("Dimension", member.dimension().location().toString());
                m.putLong("Pos", member.pos().asLong()); m.putString("Kind", member.kind().name());
                members.add(m);
            }
            n.put("Members", members);
            net.minecraft.nbt.ListTag links = new net.minecraft.nbt.ListTag();
            for (var link : network.connections()) {
                CompoundTag c = new CompoundTag();
                c.putUUID("Id", link.id()); c.putUUID("Source", link.sourceMember());
                c.putUUID("Target", link.targetMember()); c.putString("Direction", link.direction().name());
                c.putBoolean("Enabled", link.enabled());
                c.putString("InputMode", link.inputFilter().mode().name());
                c.putBoolean("InputExact", link.inputFilter().exactComponents());
                c.putString("InputItems", String.join(",", link.inputFilter().items()));
                c.putString("OutputMode", link.outputFilter().mode().name());
                c.putBoolean("OutputExact", link.outputFilter().exactComponents());
                c.putString("OutputItems", String.join(",", link.outputFilter().items()));
                links.add(c);
            }
            n.put("Connections", links); networks.add(n);
        }
        root.put("Networks", networks);
        return new RemoteTransferSnapshotPayload(root);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> {
            context.enqueueWork(() -> net.minecraft.client.Minecraft.getInstance().setScreen(
                    net.minecraft.client.Minecraft.getInstance().screen instanceof dev.whimsyautomation.client.RemoteTransferScreen screen
                            ? screen.applySnapshot(payload.data()) : new dev.whimsyautomation.client.RemoteTransferScreen(payload.data())));
        });
    }
}
