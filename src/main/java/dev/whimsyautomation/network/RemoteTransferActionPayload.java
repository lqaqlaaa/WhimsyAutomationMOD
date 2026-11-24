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
                buf.writeVarInt(p.value()); buf.writeUtf(p.text(), 32767);
            }, buf -> new RemoteTransferActionPayload(buf.readVarInt(), readUuid(buf), readUuid(buf),
                    readUuid(buf), buf.readVarInt(), buf.readUtf(32767)));

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
        UUID inventoryRequestMember = null;
        boolean suppressSnapshot = false;
        boolean closeConfigurator = false;
        switch (p.action()) {
            case 0 -> { /* request current snapshot */ }
            case 20 -> {
                if (owned(data, p.networkId(), player))
                    RemoteTransferSelection.setActiveNetwork(player.getUUID(), p.networkId());
            }
            case 21 -> RemoteTransferSelection.setConnectionsVisible(player.getUUID(), p.value() != 0);
            case 22 -> {
                if (owned(data, p.networkId(), player))
                    data.setMemberBordersVisible(p.networkId(), p.value() != 0);
            }
            case 23 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null && p.value() >= 0) {
                    try {
                        String[] parts = p.text().split("\\|", 3);
                        var mode = RemoteTransferNetworkData.FilterMode.valueOf(parts[0]);
                        int flags = Integer.parseInt(parts[1]);
                        java.util.ArrayList<String> entries = new java.util.ArrayList<>();
                        if (parts.length > 2 && !parts[2].isBlank()) {
                            for (String encoded : parts[2].split(",")) {
                                if (entries.size() >= 10) break;
                                if (!encoded.isBlank()) entries.add(new String(
                                        java.util.Base64.getDecoder().decode(encoded),
                                        java.nio.charset.StandardCharsets.UTF_8));
                            }
                        }
                        var filter = new RemoteTransferNetworkData.Filter(mode, true, entries,
                                !entries.isEmpty() && mode != RemoteTransferNetworkData.FilterMode.NONE,
                                (flags & 2) == 0, (flags & 8) == 0, (flags & 4) != 0);
                        data.setSlotFilter(p.networkId(), p.firstId(), p.value(), filter);
                    } catch (RuntimeException ignored) {
                        player.displayClientMessage(Component.literal("过滤规则格式无效，未保存。"), true);
                    }
                }
            }
            case 1 -> data.createNetwork(player.getUUID(), p.text().isBlank()
                    ? "新建网络" : p.text());
            case 2 -> {
                if (owned(data, p.networkId(), player)) data.renameNetwork(p.networkId(), p.text());
            }
            case 3 -> {
                if (owned(data, p.networkId(), player)) {
                    data.deleteNetwork(p.networkId(), player.getUUID());
                    RemoteTransferSelection.clearAll(player.getUUID());
                }
            }
            case 4, 15 -> {
                if (owned(data, p.networkId(), player)) {
                    RemoteTransferSelection.beginAddMember(player.getUUID(), p.networkId());
                    closeConfigurator = true;
                    player.displayClientMessage(Component.literal("添加成员模式已启动：Shift+右键任意方块即可加入当前网络。"), true);
                }
            }
            case 16 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null && p.value() >= 0
                        && ("INPUT".equals(p.text()) || "OUTPUT".equals(p.text()))) {
                    var network = data.getNetwork(p.networkId());
                    var workstation = network == null ? null : network.members().stream()
                            .filter(member -> member.id().equals(p.firstId())).findFirst().orElse(null);
                    if (workstation != null) {
                        ServerLevel workstationLevel = player.server.getLevel(workstation.dimension());
                        if (workstationLevel == null || !workstationLevel.hasChunkAt(workstation.pos())) {
                            player.displayClientMessage(Component.literal("工作方块所在区块未加载，无法读取库存槽位。"), true);
                            return;
                        }
                        var workstationEntity = workstationLevel.getBlockEntity(workstation.pos());
                        net.neoforged.neoforge.items.IItemHandler handler = null;
                        if (workstationEntity instanceof dev.whimsyautomation.remote.RemoteTransferInventoryProvider provider) {
                            handler = provider.getRemoteTransferInventory();
                        } else {
                            handler = workstationLevel.getCapability(
                                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                    workstation.pos(), null);
                            if (handler == null) {
                                for (Direction side : Direction.values()) {
                                    handler = workstationLevel.getCapability(
                                            net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                            workstation.pos(), side);
                                    if (handler != null) break;
                                }
                            }
                        }
                        if (handler == null || p.value() >= handler.getSlots()) {
                            player.displayClientMessage(Component.literal("该方块没有对应的物品库存槽位。"), true);
                            return;
                        }
                        data.setMemberKind(p.networkId(), workstation.id(),
                                RemoteTransferNetworkData.EndpointKind.WORKSTATION);
                        var direction = "INPUT".equals(p.text())
                                ? RemoteTransferNetworkData.TransferDirection.INPUT
                                : RemoteTransferNetworkData.TransferDirection.OUTPUT;
                        RemoteTransferSelection.beginSlotBinding(player.getUUID(), p.networkId(),
                                workstation.id(), p.value(), direction);
                        player.displayClientMessage(Component.literal("槽位绑定模式已启动：Shift+右键选择目标容器。"), true);
                        suppressSnapshot = true;
                    }
                }
            }
            case 24 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null
                        && ("INPUT".equals(p.text()) || "OUTPUT".equals(p.text()))) {
                    var network = data.getNetwork(p.networkId());
                    var workstation = network == null ? null : network.members().stream()
                            .filter(member -> member.id().equals(p.firstId())).findFirst().orElse(null);
                    if (workstation == null) return;
                    ServerLevel workstationLevel = player.server.getLevel(workstation.dimension());
                    if (workstationLevel == null || !workstationLevel.hasChunkAt(workstation.pos())) {
                        player.displayClientMessage(Component.literal("工作方块所在区块未加载，无法自动识别槽位。"), true);
                        return;
                    }
                    var blockEntity = workstationLevel.getBlockEntity(workstation.pos());
                    net.neoforged.neoforge.items.IItemHandler handler = null;
                    if (blockEntity instanceof dev.whimsyautomation.remote.RemoteTransferInventoryProvider provider) {
                        handler = provider.getRemoteTransferInventory();
                    } else {
                        handler = workstationLevel.getCapability(
                                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                workstation.pos(), null);
                        if (handler == null) for (Direction side : Direction.values()) {
                            handler = workstationLevel.getCapability(
                                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                    workstation.pos(), side);
                            if (handler != null) break;
                        }
                    }
                    if (handler == null) {
                        player.displayClientMessage(Component.literal("该工作方块没有可识别的物品库存接口。"), true);
                        return;
                    }
                    String blockId = workstation.blockId();
                    java.util.ArrayList<Integer> matched = new java.util.ArrayList<>();
                    int count = handler.getSlots();
                    if ("minecraft:furnace".equals(blockId)
                            || "minecraft:blast_furnace".equals(blockId)
                            || "minecraft:smoker".equals(blockId)) {
                        if ("INPUT".equals(p.text())) matched.addAll(java.util.List.of(0, 1));
                        else matched.add(2);
                    } else if ("whimsy_automation:deconstruction_table".equals(blockId)
                            || "whimsy_automation:enchantment_extractor".equals(blockId)) {
                        if ("INPUT".equals(p.text())) matched.add(0);
                        else for (int slot = 1; slot < count; slot++) matched.add(slot);
                    } else if ("whimsy_automation:enchantment_conversion_table".equals(blockId)) {
                        matched.add("INPUT".equals(p.text()) ? 0 : 1);
                    } else {
                        player.displayClientMessage(Component.literal(
                                "暂不支持自动识别这台机器的槽位规则；请继续使用原来的手动选择。"), true);
                        return;
                    }
                    matched.removeIf(slot -> slot < 0 || slot >= count);
                    if (matched.isEmpty()) {
                        player.displayClientMessage(Component.literal("没有识别到符合条件的槽位。"), true);
                        return;
                    }
                    data.setMemberKind(p.networkId(), workstation.id(),
                            RemoteTransferNetworkData.EndpointKind.WORKSTATION);
                    var direction = "INPUT".equals(p.text())
                            ? RemoteTransferNetworkData.TransferDirection.INPUT
                            : RemoteTransferNetworkData.TransferDirection.OUTPUT;
                    RemoteTransferSelection.beginSlotBindings(player.getUUID(), p.networkId(),
                            workstation.id(), matched, direction);
                    player.displayClientMessage(Component.literal("已识别 " + matched.size()
                            + " 个" + ("INPUT".equals(p.text()) ? "输入" : "输出")
                            + "槽位。Shift+右键目标容器即可一次性绑定。"), true);
                    suppressSnapshot = true;
                }
            }
            case 17 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null) {
                    var network = data.getNetwork(p.networkId());
                    if (network != null && network.members().stream()
                            .anyMatch(member -> member.id().equals(p.firstId()))) {
                        inventoryRequestMember = p.firstId();
                    }
                }
            }
            case 18 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null) {
                    var network = data.getNetwork(p.networkId());
                    if (network != null) for (var binding : network.slotBindings()) {
                        if (binding.id().equals(p.firstId())) {
                            data.updateSlotBinding(p.networkId(), binding.withEnabled(!binding.enabled()));
                            break;
                        }
                    }
                }
            }
            case 19 -> {
                if (owned(data, p.networkId(), player) && p.firstId() != null)
                    data.removeSlotBinding(p.networkId(), p.firstId());
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
                                inputSide ? updated : old.inputFilter(), inputSide ? old.outputFilter() : updated,
                                old.lineVisible()));
                        break;
                    }
                }
            }
            // Keep the per-container line visibility flag intact while changing filters.
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
                                side == 0 ? filter : old.inputFilter(), side == 1 ? filter : old.outputFilter(),
                                old.lineVisible()));
                        break;
                    }
                }
            }
            default -> { return; }
        }
        if (!suppressSnapshot) {
            RemoteTransferSnapshotPayload response = inventoryRequestMember == null
                    ? RemoteTransferSnapshotPayload.from(data, player.getUUID())
                    : RemoteTransferSnapshotPayload.withInventory(data, player, p.networkId(), inventoryRequestMember);
            if (closeConfigurator) {
                net.minecraft.nbt.CompoundTag root = response.data().copy();
                root.putBoolean("CloseConfigurator", true);
                response = new RemoteTransferSnapshotPayload(root);
            }
            PacketDistributor.sendToPlayer(player, response);
        }
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
