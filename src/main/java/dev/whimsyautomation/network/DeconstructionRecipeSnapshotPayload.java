package dev.whimsyautomation.network;

import dev.whimsyautomation.block.entity.DeconstructionTableBlockEntity;
import dev.whimsyautomation.menu.DeconstructionTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

public record DeconstructionRecipeSnapshotPayload(
        int containerId, BlockPos pos, String recipeId, ItemStack output, List<List<ItemStack>> inputGroups
) implements CustomPacketPayload {
    public static final Type<DeconstructionRecipeSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("whimsy_automation", "deconstruction_recipe_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeconstructionRecipeSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(DeconstructionRecipeSnapshotPayload::encode, DeconstructionRecipeSnapshotPayload::decode);

    public DeconstructionRecipeSnapshotPayload {
        output = output.copy();
        inputGroups = inputGroups.stream()
                .map(group -> group.stream().map(ItemStack::copy).toList())
                .toList();
    }

    private static void encode(RegistryFriendlyByteBuf buf, DeconstructionRecipeSnapshotPayload payload) {
        buf.writeVarInt(payload.containerId);
        buf.writeBlockPos(payload.pos);
        buf.writeUtf(payload.recipeId, 256);
        ItemStack.STREAM_CODEC.encode(buf, payload.output);
        buf.writeVarInt(payload.inputGroups.size());
        for (List<ItemStack> group : payload.inputGroups) {
            buf.writeVarInt(group.size());
            for (ItemStack stack : group) ItemStack.STREAM_CODEC.encode(buf, stack);
        }
    }

    private static DeconstructionRecipeSnapshotPayload decode(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        BlockPos pos = buf.readBlockPos();
        String recipeId = buf.readUtf(256);
        ItemStack output = ItemStack.STREAM_CODEC.decode(buf);
        int groupCount = buf.readVarInt();
        if (groupCount < 0 || groupCount > 4096) {
            throw new DecoderException("Invalid deconstruction snapshot group count: " + groupCount);
        }
        List<List<ItemStack>> groups = new ArrayList<>(groupCount);
        for (int i = 0; i < groupCount; i++) {
            int optionCount = buf.readVarInt();
            if (optionCount < 0 || optionCount > 4096) {
                throw new DecoderException("Invalid deconstruction snapshot option count: " + optionCount);
            }
            List<ItemStack> options = new ArrayList<>(optionCount);
            for (int j = 0; j < optionCount; j++) options.add(ItemStack.STREAM_CODEC.decode(buf));
            groups.add(List.copyOf(options));
        }
        return new DeconstructionRecipeSnapshotPayload(containerId, pos, recipeId, output, groups);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof DeconstructionTableMenu menu)
                    || menu.containerId != payload.containerId()) return;
            if (!menu.stillValid(player) || !menu.matchesMachinePosition(payload.pos())) return;
            if (payload.recipeId().isBlank() || payload.output().isEmpty()
                    || payload.inputGroups().isEmpty()) return;

            DeconstructionTableBlockEntity machine = menu.machine();
            if (machine == null || player.level().getBlockEntity(payload.pos()) != machine) return;

            // The visible JEI preview is the source of truth. Do not re-check its
            // output against RecipeManager or the machine input before accepting it.
            ItemStack input = machine.getInventory().getStackInSlot(0);
            if (input.isEmpty()) return;

            for (List<ItemStack> group : payload.inputGroups()) {
                if (group.isEmpty()) return;
                for (ItemStack stack : group) if (stack.isEmpty()) return;
            }
            machine.cacheJeiRecipeSnapshot(payload.recipeId(), payload.output(), payload.inputGroups(), input);
            menu.invalidateCandidateCache();
        });
    }

    public static void send(int containerId, BlockPos pos, String recipeId, ItemStack output,
                            List<List<ItemStack>> inputGroups) {
        PacketDistributor.sendToServer(new DeconstructionRecipeSnapshotPayload(
                containerId, pos, recipeId, output, inputGroups));
    }
}
