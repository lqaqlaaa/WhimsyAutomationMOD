package dev.whimsyautomation.registry;

import dev.whimsyautomation.WhimsyAutomation;
import dev.whimsyautomation.menu.DeconstructionTableMenu;
import dev.whimsyautomation.menu.EnchantmentExtractorMenu;
import dev.whimsyautomation.menu.EnchantmentConversionTableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, WhimsyAutomation.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<EnchantmentExtractorMenu>> ENCHANTMENT_EXTRACTOR =
            MENUS.register("enchantment_extractor", () -> IMenuTypeExtension.create(EnchantmentExtractorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<EnchantmentConversionTableMenu>> ENCHANTMENT_CONVERSION_TABLE =
            MENUS.register("enchantment_conversion_table", () -> IMenuTypeExtension.create(EnchantmentConversionTableMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<DeconstructionTableMenu>> DECONSTRUCTION_TABLE =
            MENUS.register("deconstruction_table", () -> IMenuTypeExtension.create(DeconstructionTableMenu::new));

    private ModMenus() {}

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        MENUS.register(bus);
    }
}
