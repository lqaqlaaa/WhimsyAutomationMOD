package dev.whimsyautomation.registry;

import dev.whimsyautomation.WhimsyAutomation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WhimsyAutomation.MOD_ID);

    public static final DeferredItem<BlockItem> ENCHANTMENT_EXTRACTOR =
            ITEMS.register("enchantment_extractor", () -> new BlockItem(ModBlocks.ENCHANTMENT_EXTRACTOR.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> DECONSTRUCTION_TABLE =
            ITEMS.register("deconstruction_table", () -> new BlockItem(ModBlocks.DECONSTRUCTION_TABLE.get(), new Item.Properties()));

    private ModItems() {}

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        ITEMS.register(bus);
    }
}
