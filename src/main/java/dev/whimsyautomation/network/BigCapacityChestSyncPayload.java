package dev.whimsyautomation.network;

import dev.whimsyautomation.menu.BigCapacityChestMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

public record BigCapacityChestSyncPayload(int containerId, List<String> counts) implements CustomPacketPayload {
    public static final Type<BigCapacityChestSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("whimsy_automation", "big_capacity_chest_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BigCapacityChestSyncPayload> STREAM_CODEC =
            StreamCodec.of(BigCapacityChestSyncPayload::encode, BigCapacityChestSyncPayload::decode);

    public BigCapacityChestSyncPayload {
        counts = List.copyOf(counts);
    }

    private static void encode(RegistryFriendlyByteBuf buf, BigCapacityChestSyncPayload payload) {
        buf.writeVarInt(payload.containerId());
        buf.writeVarInt(payload.counts().size());
        for (String count : payload.counts()) buf.writeUtf(count, 320);
    }

    private static BigCapacityChestSyncPayload decode(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int size = buf.readVarInt();
        if (size < 0 || size > 54) throw new IllegalArgumentException("Invalid big chest slot count: " + size);
        java.util.ArrayList<String> counts = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) counts.add(buf.readUtf(320));
        return new BigCapacityChestSyncPayload(id, counts);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                if (context.player().containerMenu instanceof BigCapacityChestMenu menu
                        && menu.containerId == payload.containerId()) {
                    menu.applySyncedCounts(payload.counts());
                }
            });
        });
    }

    public static void send(net.minecraft.server.level.ServerPlayer player, int containerId, List<String> counts) {
        PacketDistributor.sendToPlayer(player, new BigCapacityChestSyncPayload(containerId, counts));
    }
}
