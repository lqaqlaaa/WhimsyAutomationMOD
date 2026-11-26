package dev.whimsyautomation.network;

import dev.whimsyautomation.block.entity.EnchantmentConversionTableBlockEntity;
import dev.whimsyautomation.menu.EnchantmentConversionTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record ConfigureItemCopyPayload(int containerId, BlockPos pos, int action, int value)
        implements CustomPacketPayload {
    public static final Type<ConfigureItemCopyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("whimsy_automation", "configure_item_copy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureItemCopyPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeVarInt(payload.containerId());
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(payload.action());
                buf.writeVarInt(payload.value());
            }, buf -> new ConfigureItemCopyPayload(buf.readVarInt(), buf.readBlockPos(),
                    buf.readVarInt(), buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof EnchantmentConversionTableMenu menu)
                    || menu.containerId != payload.containerId()
                    || !menu.getBlockPos().equals(payload.pos())
                    || !menu.stillValid(player)) return;
            EnchantmentConversionTableBlockEntity machine = menu.machine();
            if (machine == null || player.level().getBlockEntity(payload.pos()) != machine) return;
            if (payload.action() == 1) {
                if (payload.value() >= 1 && payload.value() <= EnchantmentConversionTableBlockEntity.MAX_COPY_COUNT)
                    machine.setCopyCount(payload.value());
            }
        });
    }

    public static void send(int containerId, BlockPos pos, int action, int value) {
        PacketDistributor.sendToServer(new ConfigureItemCopyPayload(containerId, pos, action, value));
    }
}
