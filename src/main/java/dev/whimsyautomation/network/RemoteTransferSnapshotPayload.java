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
                m.putString("Block", member.blockId());
                m.putInt("Slot", member.slotIndex());
                m.putInt("Face", member.face() == null ? -1 : member.face().ordinal());
                members.add(m);
            }
            n.put("Members", members);
            net.minecraft.nbt.ListTag links = new net.minecraft.nbt.ListTag();
            for (var link : network.connections()) {
                CompoundTag c = new CompoundTag();
                c.putUUID("Id", link.id()); c.putUUID("Source", link.sourceMember());
                c.putUUID("Target", link.targetMember()); c.putString("Direction", link.direction().name());
                c.putBoolean("Enabled", link.enabled());
                c.putBoolean("ShowLine", link.lineVisible());
                c.putString("InputMode", link.inputFilter().mode().name());
                c.putBoolean("InputExact", link.inputFilter().exactComponents());
                c.putBoolean("InputIgnoreDurability", link.inputFilter().ignoreDurability());
                c.putBoolean("InputIgnoreNbt", link.inputFilter().ignoreNbt());
                c.putBoolean("InputMatchTags", link.inputFilter().matchTags());
                c.putString("InputItems", link.inputFilter().items().stream().map(s -> s.contains("|") ? s.substring(0, s.indexOf('|')) : s).distinct().collect(java.util.stream.Collectors.joining(",")));
                c.putString("OutputMode", link.outputFilter().mode().name());
                c.putBoolean("OutputExact", link.outputFilter().exactComponents());
                c.putBoolean("OutputIgnoreDurability", link.outputFilter().ignoreDurability());
                c.putBoolean("OutputIgnoreNbt", link.outputFilter().ignoreNbt());
                c.putBoolean("OutputMatchTags", link.outputFilter().matchTags());
                c.putString("OutputItems", link.outputFilter().items().stream().map(s -> s.contains("|") ? s.substring(0, s.indexOf('|')) : s).distinct().collect(java.util.stream.Collectors.joining(",")));
                links.add(c);
            }
            n.put("Connections", links);

            net.minecraft.nbt.ListTag slotBindings = new net.minecraft.nbt.ListTag();
            for (var binding : network.slotBindings()) {
                CompoundTag slot = new CompoundTag();
                slot.putUUID("Id", binding.id());
                slot.putUUID("Workstation", binding.workstationMember());
                slot.putInt("WorkstationSlot", binding.workstationSlot());
                slot.putString("Direction", binding.direction().name());
                slot.putUUID("Target", binding.targetMember());
                slot.putInt("TargetFace", binding.targetFace() == null ? -1 : binding.targetFace().ordinal());
                slot.putInt("TargetSlot", binding.targetSlot());
                slot.putBoolean("Enabled", binding.enabled());
                slotBindings.add(slot);
            }
            n.put("SlotBindings", slotBindings);
            networks.add(n);
        }
        root.put("Networks", networks);
        return new RemoteTransferSnapshotPayload(root);
    }

    /** Adds a server-authoritative view of the selected member's real ItemHandler slots. */
    public static RemoteTransferSnapshotPayload withInventory(RemoteTransferNetworkData source,
                                                                ServerPlayer player,
                                                                UUID networkId, UUID memberId) {
        CompoundTag inventory = new CompoundTag();
        inventory.putBoolean("Available", false);
        var network = source.getNetwork(networkId);
        var member = network == null || !network.owner().equals(player.getUUID()) ? null
                : network.members().stream().filter(m -> m.id().equals(memberId)).findFirst().orElse(null);
        if (member != null) {
            var level = player.server.getLevel(member.dimension());
            if (level != null && level.hasChunkAt(member.pos())) {
                net.neoforged.neoforge.items.IItemHandler handler =
                        level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                member.pos(), null);
                if (handler == null) {
                    for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                        handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                member.pos(), side);
                        if (handler != null) break;
                    }
                }
                if (handler != null) {
                    source.setMemberKind(networkId, member.id(),
                            RemoteTransferNetworkData.EndpointKind.WORKSTATION);
                    inventory.putBoolean("Available", true);
                    inventory.putUUID("Member", member.id());
                    inventory.putString("Block", member.blockId());
                    net.minecraft.nbt.ListTag slots = new net.minecraft.nbt.ListTag();
                    for (int i = 0; i < handler.getSlots(); i++) {
                        net.minecraft.world.item.ItemStack stack = handler.getStackInSlot(i);
                        CompoundTag slot = new CompoundTag();
                        slot.putInt("Index", i);
                        slot.putString("Item", stack.isEmpty() ? "" :
                                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                        slot.putInt("Count", stack.getCount());
                        slots.add(slot);
                    }
                    inventory.put("Slots", slots);
                }
            }
        }
        CompoundTag root = from(source, player.getUUID()).data().copy();
        root.put("Inventory", inventory);
        return new RemoteTransferSnapshotPayload(root);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) return;
                try {
                    Class<?> client = Class.forName("dev.whimsyautomation.client.RemoteTransferClient");
                    client.getMethod("applySnapshot", CompoundTag.class).invoke(null, payload.data());
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Unable to update remote transfer screen", exception);
                }
            });
        });
    }
}
