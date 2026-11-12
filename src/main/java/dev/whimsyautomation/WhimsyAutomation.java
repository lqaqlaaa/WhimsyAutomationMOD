package dev.whimsyautomation;

import dev.whimsyautomation.registry.ModBlockEntities;
import dev.whimsyautomation.registry.ModBlocks;
import dev.whimsyautomation.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(WhimsyAutomation.MOD_ID)
public final class WhimsyAutomation {
    public static final String MOD_ID = "whimsy_automation";

    public WhimsyAutomation(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
    }
}
