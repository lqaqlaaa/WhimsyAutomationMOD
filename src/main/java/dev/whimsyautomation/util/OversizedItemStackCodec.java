package dev.whimsyautomation.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Stores ItemStacks with an integer count instead of vanilla's [1, 99] stack codec.
 * The handler still exposes normal, legal-sized extractions to players and automation.
 */
public final class OversizedItemStackCodec {
    public static final int MAX_COUNT = 1_000_000;
    private static final Codec<ItemStack> CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(instance ->
            instance.group(
                    ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
                    Codec.INT.fieldOf("count").orElse(1).forGetter(ItemStack::getCount),
                    net.minecraft.core.component.DataComponentPatch.CODEC
                            .optionalFieldOf("components", net.minecraft.core.component.DataComponentPatch.EMPTY)
                            .forGetter(stack -> stack.getComponentsPatch())
            ).apply(instance, ItemStack::new)));

    private OversizedItemStackCodec() {}

    public static CompoundTag serialize(ItemStackHandler handler, HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        root.putInt("Size", handler.getSlots());
        ListTag items = new ListTag();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            CompoundTag entry = (CompoundTag) CODEC.encodeStart(
                    registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
            entry.putInt("Slot", slot);
            items.add(entry);
        }
        root.put("Items", items);
        return root;
    }

    public static void deserialize(ItemStackHandler handler, HolderLookup.Provider registries, CompoundTag root) {
        ListTag items = root.getList("Items", 10);
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= handler.getSlots()) continue;
            ItemStack stack = CODEC.parse(
                    registries.createSerializationContext(NbtOps.INSTANCE), entry).result().orElse(ItemStack.EMPTY);
            if (!stack.isEmpty() && stack.getCount() <= MAX_COUNT) handler.setStackInSlot(slot, stack);
        }
    }
}
