package dev.whimsyautomation.init;

import dev.whimsyautomation.registry.ModBlockEntities;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class ModCapabilities {
    private ModCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ENCHANTMENT_EXTRACTOR.get(),
                (blockEntity, side) -> blockEntity.getAutomationItemHandler(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.DECONSTRUCTION_TABLE.get(),
                (blockEntity, side) -> blockEntity.getAutomationItemHandler(side));
    }
}
