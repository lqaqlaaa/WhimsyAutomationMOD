package dev.whimsyautomation.remote;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * World-persistent registry for remote transfer networks.
 *
 * <p>Networks own their membership and connection rules, not the blocks themselves:
 * a block endpoint may therefore be referenced by any number of networks.</p>
 */
public final class RemoteTransferNetworkData extends SavedData {
    private static final String DATA_NAME = "whimsy_automation_remote_transfer_networks";
    private static final Factory<RemoteTransferNetworkData> FACTORY =
            new Factory<>(RemoteTransferNetworkData::new, RemoteTransferNetworkData::load, DataFixTypes.LEVEL);

    private final Map<UUID, Network> networks = new LinkedHashMap<>();

    public static RemoteTransferNetworkData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Collection<Network> networks() {
        return List.copyOf(networks.values());
    }

    public Network getNetwork(UUID id) {
        return networks.get(id);
    }

    public Network createNetwork(UUID owner, String name) {
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Network name cannot be empty");
        Network network = new Network(UUID.randomUUID(), owner, normalized);
        networks.put(network.id(), network);
        setDirty();
        return network;
    }

    public boolean renameNetwork(UUID id, String name) {
        Network network = networks.get(id);
        String normalized = normalizeName(name);
        if (network == null || normalized.isEmpty()) return false;
        network.setName(normalized);
        setDirty();
        return true;
    }

    public boolean deleteNetwork(UUID id, UUID requester) {
        Network network = networks.get(id);
        if (network == null || !network.owner().equals(requester)) return false;
        networks.remove(id);
        setDirty();
        return true;
    }

    public boolean addMember(UUID networkId, Endpoint endpoint) {
        Network network = networks.get(networkId);
        if (network == null || network.members().stream().anyMatch(member ->
                member.dimension().equals(endpoint.dimension()) && member.pos().equals(endpoint.pos()) && member.kind() == endpoint.kind() && member.slotIndex() == endpoint.slotIndex() && Objects.equals(member.face(), endpoint.face()))) return false;
        network.members().add(endpoint);
        setDirty();
        return true;
    }

    public boolean setMemberKind(UUID networkId, UUID memberId, EndpointKind kind) {
        Network network = networks.get(networkId);
        if (network == null || memberId == null || kind == null) return false;
        for (int i = 0; i < network.members().size(); i++) {
            Endpoint old = network.members().get(i);
            if (!old.id().equals(memberId)) continue;
            if (old.kind() == kind) return true;
            network.members().set(i, new Endpoint(old.id(), old.dimension(), old.pos(), kind,
                    old.blockId(), old.slotIndex(), old.face()));
            setDirty();
            return true;
        }
        return false;
    }

    public boolean setMemberBordersVisible(UUID networkId, boolean visible) {
        Network network = networks.get(networkId);
        if (network == null) return false;
        network.setMemberBordersVisible(visible);
        setDirty();
        return true;
    }

    public boolean setSlotFilter(UUID networkId, UUID workstation, int slot, Filter filter) {
        Network network = networks.get(networkId);
        if (network == null || workstation == null || slot < 0 || filter == null) return false;
        network.slotFilters().put(Network.slotFilterKey(workstation, slot), filter);
        setDirty();
        return true;
    }

    public Filter getSlotFilter(UUID networkId, UUID workstation, int slot) {
        Network network = networks.get(networkId);
        return network == null ? Filter.none()
                : network.slotFilters().getOrDefault(Network.slotFilterKey(workstation, slot), Filter.none());
    }

    public boolean addSlotBinding(UUID networkId, SlotBinding binding) {
        Network network = networks.get(networkId);
        if (network == null) return false;
        boolean workstationExists = network.members().stream().anyMatch(m -> m.id().equals(binding.workstationMember()));
        boolean targetExists = network.members().stream().anyMatch(m -> m.id().equals(binding.targetMember()));
        if (!workstationExists || !targetExists || binding.workstationMember().equals(binding.targetMember())
                || network.slotBindings().stream().anyMatch(existing ->
                    existing.workstationMember().equals(binding.workstationMember())
                    && existing.workstationSlot() == binding.workstationSlot()
                    && existing.direction() == binding.direction()
                    && existing.targetMember().equals(binding.targetMember())
                    && existing.targetFace() == binding.targetFace())) return false;
        network.slotBindings().add(binding);
        setDirty();
        return true;
    }

    public boolean updateSlotBinding(UUID networkId, SlotBinding replacement) {
        Network network = networks.get(networkId);
        if (network == null || replacement == null) return false;
        for (int i = 0; i < network.slotBindings().size(); i++) {
            if (network.slotBindings().get(i).id().equals(replacement.id())) {
                network.slotBindings().set(i, replacement);
                setDirty();
                return true;
            }
        }
        return false;
    }

    public boolean removeSlotBinding(UUID networkId, UUID bindingId) {
        Network network = networks.get(networkId);
        if (network == null) return false;
        boolean removed = network.slotBindings().removeIf(binding -> binding.id().equals(bindingId));
        if (removed) setDirty();
        return removed;
    }

    public boolean removeMember(UUID networkId, UUID memberId) {
        Network network = networks.get(networkId);
        if (network == null) return false;
        boolean removed = network.members().removeIf(member -> member.id().equals(memberId));
        if (removed) {
            network.connections().removeIf(connection ->
                    connection.sourceMember().equals(memberId) || connection.targetMember().equals(memberId));
            network.slotBindings().removeIf(binding ->
                    binding.workstationMember().equals(memberId) || binding.targetMember().equals(memberId));
            setDirty();
        }
        return removed;
    }

    /**
     * Remove every endpoint at a broken/replaced block position. Removing the
     * endpoint also removes all links that depend on it, across every network.
     */
    public void removeEndpointsAt(ResourceKey<Level> dimension, BlockPos pos) {
        boolean changed = false;
        for (Network network : networks.values()) {
            List<UUID> removedIds = network.members().stream()
                    .filter(member -> member.dimension().equals(dimension) && member.pos().equals(pos))
                    .map(Endpoint::id).toList();
            for (UUID memberId : removedIds) {
                changed |= network.members().removeIf(member -> member.id().equals(memberId));
                changed |= network.connections().removeIf(connection ->
                        connection.sourceMember().equals(memberId) || connection.targetMember().equals(memberId));
                changed |= network.slotBindings().removeIf(binding ->
                        binding.workstationMember().equals(memberId) || binding.targetMember().equals(memberId));
            }
        }
        if (changed) setDirty();
    }

    public boolean addConnection(UUID networkId, Connection connection) {
        Network network = networks.get(networkId);
        if (network == null || network.connections().stream().anyMatch(existing -> existing.id().equals(connection.id())))
            return false;
        boolean sourceExists = network.members().stream().anyMatch(member -> member.id().equals(connection.sourceMember()));
        boolean targetExists = network.members().stream().anyMatch(member -> member.id().equals(connection.targetMember()));
        if (!sourceExists || !targetExists || connection.sourceMember().equals(connection.targetMember())) return false;
        network.connections().add(connection);
        setDirty();
        return true;
    }

    public boolean removeConnection(UUID networkId, UUID connectionId) {
        Network network = networks.get(networkId);
        if (network == null) return false;
        boolean removed = network.connections().removeIf(connection -> connection.id().equals(connectionId));
        if (removed) setDirty();
        return removed;
    }

    public boolean updateConnection(UUID networkId, Connection replacement) {
        Network network = networks.get(networkId);
        if (network == null) return false;
        for (int i = 0; i < network.connections().size(); i++) {
            if (network.connections().get(i).id().equals(replacement.id())) {
                network.connections().set(i, replacement);
                setDirty();
                return true;
            }
        }
        return false;
    }

    public void markChanged() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag networkList = new ListTag();
        for (Network network : networks.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", network.id());
            entry.putUUID("Owner", network.owner());
            entry.putString("Name", network.name());

            ListTag members = new ListTag();
            for (Endpoint endpoint : network.members()) {
                CompoundTag member = new CompoundTag();
                member.putUUID("Id", endpoint.id());
                member.putString("Dimension", endpoint.dimension().location().toString());
                member.putLong("Pos", endpoint.pos().asLong());
                member.putString("Kind", endpoint.kind().name());
                member.putString("Block", endpoint.blockId());
                member.putInt("Slot", endpoint.slotIndex());
                member.putInt("Face", endpoint.face() == null ? -1 : endpoint.face().ordinal());
                members.add(member);
            }
            entry.put("Members", members);

            ListTag connections = new ListTag();
            for (Connection connection : network.connections()) {
                CompoundTag link = new CompoundTag();
                link.putUUID("Id", connection.id());
                link.putUUID("Source", connection.sourceMember());
                link.putUUID("Target", connection.targetMember());
                link.putString("Direction", connection.direction().name());
                link.putBoolean("Enabled", connection.enabled());
                link.putBoolean("LineVisible", connection.lineVisible());
                link.putString("InputFilterMode", connection.inputFilter().mode().name());
                link.putBoolean("InputExact", connection.inputFilter().exactComponents());
                link.putString("OutputFilterMode", connection.outputFilter().mode().name());
                link.putBoolean("OutputExact", connection.outputFilter().exactComponents());
                putStrings(link, "InputItems", connection.inputFilter().items());
                putStrings(link, "OutputItems", connection.outputFilter().items());
                connections.add(link);
            }
            entry.put("Connections", connections);

            entry.putBoolean("MemberBordersVisible", network.memberBordersVisible());
            ListTag slotFilters = new ListTag();
            for (var filterEntry : network.slotFilters().entrySet()) {
                CompoundTag filterTag = new CompoundTag();
                String[] key = filterEntry.getKey().split(":", 2);
                if (key.length != 2) continue;
                try {
                    filterTag.putUUID("Workstation", UUID.fromString(key[0]));
                    filterTag.putInt("Slot", Integer.parseInt(key[1]));
                } catch (RuntimeException ignored) { continue; }
                Filter filter = filterEntry.getValue();
                filterTag.putString("Mode", filter.mode().name());
                filterTag.putBoolean("Exact", filter.exactComponents());
                filterTag.putBoolean("IgnoreDurability", filter.ignoreDurability());
                filterTag.putBoolean("IgnoreNbt", filter.ignoreNbt());
                filterTag.putBoolean("MatchTags", filter.matchTags());
                putStrings(filterTag, "Items", filter.items());
                slotFilters.add(filterTag);
            }
            entry.put("SlotFilters", slotFilters);

            ListTag slotBindings = new ListTag();
            for (SlotBinding binding : network.slotBindings()) {
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
            entry.put("SlotBindings", slotBindings);
            networkList.add(entry);
        }
        tag.put("Networks", networkList);
        return tag;
    }

    private static RemoteTransferNetworkData load(CompoundTag tag, HolderLookup.Provider registries) {
        RemoteTransferNetworkData data = new RemoteTransferNetworkData();
        ListTag networkList = tag.getList("Networks", Tag.TAG_COMPOUND);
        for (int i = 0; i < networkList.size(); i++) {
            CompoundTag entry = networkList.getCompound(i);
            if (!entry.hasUUID("Id") || !entry.hasUUID("Owner")) continue;
            Network network = new Network(entry.getUUID("Id"), entry.getUUID("Owner"),
                    normalizeName(entry.getString("Name")));
            ListTag members = entry.getList("Members", Tag.TAG_COMPOUND);
            for (int j = 0; j < members.size(); j++) {
                CompoundTag member = members.getCompound(j);
                try {
                    ResourceLocation dimensionId = ResourceLocation.parse(member.getString("Dimension"));
                    ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId);
                    EndpointKind kind = EndpointKind.valueOf(member.getString("Kind"));
                    UUID id = member.getUUID("Id");
                    String blockId = member.getString("Block");
                    if (blockId.isBlank()) blockId = kind == EndpointKind.WORKSTATION
                            ? "minecraft:crafting_table" : "minecraft:chest";
                    network.members().add(new Endpoint(id, dimension, BlockPos.of(member.getLong("Pos")), kind, blockId, member.contains("Slot") ? member.getInt("Slot") : -1, member.contains("Face") && member.getInt("Face") >= 0 && member.getInt("Face") < net.minecraft.core.Direction.values().length ? net.minecraft.core.Direction.values()[member.getInt("Face")] : null));
                } catch (RuntimeException ignored) {
                    // Ignore malformed entries while preserving the rest of the network.
                }
            }
            ListTag connections = entry.getList("Connections", Tag.TAG_COMPOUND);
            for (int j = 0; j < connections.size(); j++) {
                CompoundTag link = connections.getCompound(j);
                try {
                    network.connections().add(new Connection(
                            link.getUUID("Id"), link.getUUID("Source"), link.getUUID("Target"),
                            TransferDirection.valueOf(link.getString("Direction")), link.getBoolean("Enabled"),
                            readFilter(link, "Input"), readFilter(link, "Output"),
                            !link.contains("LineVisible") || link.getBoolean("LineVisible")));
                } catch (RuntimeException ignored) {
                    // A malformed connection must not invalidate other saved connections.
                }
            }
            network.setMemberBordersVisible(!entry.contains("MemberBordersVisible")
                    || entry.getBoolean("MemberBordersVisible"));
            ListTag slotFilters = entry.getList("SlotFilters", Tag.TAG_COMPOUND);
            for (int j = 0; j < slotFilters.size(); j++) {
                CompoundTag filterTag = slotFilters.getCompound(j);
                try {
                    UUID workstation = filterTag.getUUID("Workstation");
                    int slot = filterTag.getInt("Slot");
                    FilterMode mode = FilterMode.valueOf(filterTag.getString("Mode"));
                    Filter filter = new Filter(mode, filterTag.getBoolean("Exact"),
                            getStrings(filterTag, "Items"), mode != FilterMode.NONE,
                            filterTag.getBoolean("IgnoreDurability"), filterTag.getBoolean("IgnoreNbt"),
                            filterTag.getBoolean("MatchTags"));
                    network.slotFilters().put(Network.slotFilterKey(workstation, slot), filter);
                } catch (RuntimeException ignored) {}
            }
            ListTag slotBindings = entry.getList("SlotBindings", Tag.TAG_COMPOUND);
            for (int j = 0; j < slotBindings.size(); j++) {
                CompoundTag slot = slotBindings.getCompound(j);
                try {
                    int face = slot.contains("TargetFace") ? slot.getInt("TargetFace") : -1;
                    net.minecraft.core.Direction targetFace = face >= 0
                            && face < net.minecraft.core.Direction.values().length
                            ? net.minecraft.core.Direction.values()[face] : null;
                    network.slotBindings().add(new SlotBinding(
                            slot.getUUID("Id"), slot.getUUID("Workstation"),
                            slot.getInt("WorkstationSlot"),
                            TransferDirection.valueOf(slot.getString("Direction")),
                            slot.getUUID("Target"), targetFace,
                            slot.contains("TargetSlot") ? slot.getInt("TargetSlot") : -1,
                            !slot.contains("Enabled") || slot.getBoolean("Enabled")));
                } catch (RuntimeException ignored) {
                    // Ignore malformed slot bindings without losing the rest of the network.
                }
            }
            if (!network.name().isEmpty()) data.networks.put(network.id(), network);
        }
        return data;
    }

    private static Filter readFilter(CompoundTag tag, String prefix) {
        FilterMode mode = FilterMode.valueOf(tag.getString(prefix + "FilterMode"));
        return new Filter(mode, tag.getBoolean(prefix + "Exact"), getStrings(tag, prefix + "Items"),
                mode != FilterMode.NONE, tag.getBoolean(prefix + "IgnoreDurability"),
                tag.getBoolean(prefix + "IgnoreNbt"), tag.getBoolean(prefix + "MatchTags"));
    }

    private static void putStrings(CompoundTag tag, String key, List<String> values) {
        ListTag list = new ListTag();
        for (String value : values) list.add(net.minecraft.nbt.StringTag.valueOf(value));
        tag.put(key, list);
    }

    private static List<String> getStrings(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        List<String> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) result.add(list.getString(i));
        return result;
    }

    private static String normalizeName(String name) {
        if (name == null) return "";
        String normalized = name.trim();
        return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
    }

    public enum EndpointKind { WORKSTATION, CONTAINER }
    public enum TransferDirection { INPUT, OUTPUT, BIDIRECTIONAL }

    /** One independently routed inventory slot on a workstation. */
    public record SlotBinding(UUID id, UUID workstationMember, int workstationSlot,
                              TransferDirection direction, UUID targetMember,
                              net.minecraft.core.Direction targetFace, int targetSlot,
                              boolean enabled) {
        public SlotBinding {
            Objects.requireNonNull(id);
            Objects.requireNonNull(workstationMember);
            Objects.requireNonNull(direction);
            Objects.requireNonNull(targetMember);
            if (workstationSlot < 0) throw new IllegalArgumentException("Workstation slot must be non-negative");
            if (targetSlot < -1) throw new IllegalArgumentException("Target slot must be -1 or non-negative");
        }

        public static SlotBinding create(UUID workstation, int workstationSlot,
                                          TransferDirection direction, UUID target,
                                          net.minecraft.core.Direction targetFace) {
            return new SlotBinding(UUID.randomUUID(), workstation, workstationSlot,
                    direction, target, targetFace, -1, true);
        }

        public SlotBinding withEnabled(boolean value) {
            return new SlotBinding(id, workstationMember, workstationSlot, direction,
                    targetMember, targetFace, targetSlot, value);
        }
    }
    public enum FilterMode { NONE, WHITELIST, BLACKLIST }

    public record Endpoint(UUID id, ResourceKey<Level> dimension, BlockPos pos, EndpointKind kind, String blockId, int slotIndex, net.minecraft.core.Direction face) {
        public Endpoint(UUID id, ResourceKey<Level> dimension, BlockPos pos, EndpointKind kind, String blockId) {
            this(id, dimension, pos, kind, blockId, -1, null);
        }

        public Endpoint {
            Objects.requireNonNull(id);
            Objects.requireNonNull(dimension);
            pos = pos.immutable();
            Objects.requireNonNull(kind);
            blockId = blockId == null || blockId.isBlank() ? "minecraft:chest" : blockId;
        }

        public static Endpoint create(Level level, BlockPos pos, EndpointKind kind) {
            return create(level, pos, kind, -1, null);
        }

        public static Endpoint create(Level level, BlockPos pos, EndpointKind kind, int slotIndex,
                                      net.minecraft.core.Direction face) {
            String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
            return new Endpoint(UUID.randomUUID(), level.dimension(), pos, kind, blockId, slotIndex, face);
        }
    }

    public record Filter(FilterMode mode, boolean exactComponents, List<String> items,
                         boolean enabled, boolean ignoreDurability, boolean ignoreNbt, boolean matchTags) {
        public Filter(FilterMode mode, boolean exactComponents, List<String> items) {
            this(mode, exactComponents, items, mode != FilterMode.NONE, false, false, false);
        }

        public Filter {
            Objects.requireNonNull(mode);
            items = List.copyOf(items);
        }

        public static Filter none() {
            return new Filter(FilterMode.NONE, false, List.of(), false, false, false, false);
        }

        public boolean allows(ResourceLocation itemId) {
            if (!enabled || mode == FilterMode.NONE) return true;
            boolean contains = items.stream().anyMatch(entry -> entry.equals(itemId.toString())
                    || entry.startsWith(itemId.toString() + "|"));
            return mode == FilterMode.WHITELIST ? contains : !contains;
        }

        public boolean allows(ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (!enabled || mode == FilterMode.NONE) return true;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            boolean contains = items.stream().anyMatch(entry -> entry.equals(itemId.toString())
                    || entry.startsWith(itemId.toString() + "|"));
            if (matchTags) {
                for (String entry : items) {
                    if (!entry.startsWith("#")) continue;
                    ResourceLocation tagId = ResourceLocation.tryParse(entry.substring(1));
                    if (tagId != null && stack.is(net.minecraft.tags.TagKey.create(
                            net.minecraft.core.registries.Registries.ITEM, tagId))) {
                        contains = true;
                        break;
                    }
                }
            }
            if (exactComponents && items.contains(fingerprint(stack, ignoreDurability, ignoreNbt))) contains = true;
            if (!exactComponents && !ignoreDurability && !ignoreNbt) {
                String actual = fingerprint(stack, false, false);
                if (items.contains(actual)) contains = true;
            }
            return mode == FilterMode.WHITELIST ? contains : !contains;
        }

        public static String fingerprint(ItemStack source, boolean ignoreDurability, boolean ignoreNbt) {
            ItemStack normalized = source.copyWithCount(1);
            if (ignoreDurability) normalized.remove(net.minecraft.core.component.DataComponents.DAMAGE);
            if (ignoreNbt) normalized.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            return BuiltInRegistries.ITEM.getKey(normalized.getItem()) + "|" + normalized.getComponents();
        }
    }

    public record Connection(UUID id, UUID sourceMember, UUID targetMember, TransferDirection direction,
                             boolean enabled, Filter inputFilter, Filter outputFilter, boolean lineVisible) {
        public Connection(UUID id, UUID sourceMember, UUID targetMember, TransferDirection direction,
                          boolean enabled, Filter inputFilter, Filter outputFilter) {
            this(id, sourceMember, targetMember, direction, enabled, inputFilter, outputFilter, true);
        }
        public Connection {
            Objects.requireNonNull(id);
            Objects.requireNonNull(sourceMember);
            Objects.requireNonNull(targetMember);
            Objects.requireNonNull(direction);
            Objects.requireNonNull(inputFilter);
            Objects.requireNonNull(outputFilter);
        }

        public static Connection create(UUID source, UUID target, TransferDirection direction) {
            return new Connection(UUID.randomUUID(), source, target, direction, true, Filter.none(), Filter.none(), true);
        }
    }

    public static final class Network {
        private final UUID id;
        private final UUID owner;
        private String name;
        private final List<Endpoint> members = new ArrayList<>();
        private final List<Connection> connections = new ArrayList<>();
        private final List<SlotBinding> slotBindings = new ArrayList<>();
        private final Map<String, Filter> slotFilters = new LinkedHashMap<>();
        private boolean memberBordersVisible = true;

        private Network(UUID id, UUID owner, String name) {
            this.id = id;
            this.owner = owner;
            this.name = name;
        }

        public UUID id() { return id; }
        public UUID owner() { return owner; }
        public String name() { return name; }
        public List<Endpoint> members() { return members; }
        public List<Connection> connections() { return connections; }
        public List<SlotBinding> slotBindings() { return slotBindings; }
        public Map<String, Filter> slotFilters() { return slotFilters; }
        public boolean memberBordersVisible() { return memberBordersVisible; }
        private void setMemberBordersVisible(boolean value) { memberBordersVisible = value; }
        private static String slotFilterKey(UUID workstation, int slot) { return workstation + ":" + slot; }
        private void setName(String name) { this.name = name; }
    }
}
