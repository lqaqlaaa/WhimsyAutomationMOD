package dev.whimsyautomation.registry;

import dev.whimsyautomation.WhimsyAutomation;
import dev.whimsyautomation.item.MachineBlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WhimsyAutomation.MOD_ID);

    public static final DeferredItem<MachineBlockItem> ENCHANTMENT_EXTRACTOR =
            ITEMS.register("enchantment_extractor", () -> new MachineBlockItem(
                    ModBlocks.ENCHANTMENT_EXTRACTOR.get(), new net.minecraft.world.item.Item.Properties(),
                    "tooltip.whimsy_automation.enchantment_extractor"));
    public static final DeferredItem<MachineBlockItem> ENCHANTMENT_CONVERSION_TABLE =
            ITEMS.register("enchantment_conversion_table", () -> new MachineBlockItem(
                    ModBlocks.ENCHANTMENT_CONVERSION_TABLE.get(), new net.minecraft.world.item.Item.Properties(),
                    "tooltip.whimsy_automation.enchantment_conversion_table"));
    public static final DeferredItem<MachineBlockItem> DECONSTRUCTION_TABLE =
            ITEMS.register("deconstruction_table", () -> new MachineBlockItem(
                    ModBlocks.DECONSTRUCTION_TABLE.get(), new net.minecraft.world.item.Item.Properties(),
                    "tooltip.whimsy_automation.deconstruction_table"));

    private ModItems() {}

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        ITEMS.register(bus);
    }
}
