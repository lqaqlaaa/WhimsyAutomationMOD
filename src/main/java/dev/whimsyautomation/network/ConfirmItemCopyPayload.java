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

public record ConfirmItemCopyPayload(int containerId, BlockPos pos, int count) implements CustomPacketPayload {
    public static final Type<ConfirmItemCopyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("whimsy_automation", "confirm_item_copy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmItemCopyPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeVarInt(payload.containerId());
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(payload.count());
            }, buf -> new ConfirmItemCopyPayload(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof EnchantmentConversionTableMenu menu)
                    || menu.containerId != payload.containerId()
                    || !menu.getBlockPos().equals(payload.pos())
                    || !menu.stillValid(player)) return;
            EnchantmentConversionTableBlockEntity machine = menu.machine();
            if (machine == null || player.level().getBlockEntity(payload.pos()) != machine) return;
            machine.confirmCopy(payload.count());
        });
    }

    public static void send(int containerId, BlockPos pos, int count) {
        PacketDistributor.sendToServer(new ConfirmItemCopyPayload(containerId, pos, count));
    }
}
