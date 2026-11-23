package dev.whimsyautomation.registry;

import dev.whimsyautomation.WhimsyAutomation;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WhimsyAutomation.MOD_ID);

    public static final net.neoforged.neoforge.registries.DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.whimsy_automation"))
                    .icon(() -> ModItems.ENCHANTMENT_EXTRACTOR.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.ENCHANTMENT_EXTRACTOR.get());
                        output.accept(ModItems.ENCHANTMENT_CONVERSION_TABLE.get());
                        output.accept(ModItems.DECONSTRUCTION_TABLE.get());
                    })
                    .build());

    private ModCreativeTabs() {}

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        TABS.register(bus);
    }
}
