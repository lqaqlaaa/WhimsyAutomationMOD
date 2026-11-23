package dev.whimsyautomation.client;

import dev.whimsyautomation.WhimsyAutomation;
import dev.whimsyautomation.registry.ModBlockEntities;
import dev.whimsyautomation.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = WhimsyAutomation.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ENCHANTMENT_EXTRACTOR.get(), EnchantmentExtractorScreen::new);
        event.register(ModMenus.DECONSTRUCTION_TABLE.get(), DeconstructionTableScreen::new);
        event.register(ModMenus.ENCHANTMENT_CONVERSION_TABLE.get(), EnchantmentConversionTableScreen::new);
    }

    @SubscribeEvent
    public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ENCHANTMENT_EXTRACTOR.get(), SideConfigBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DECONSTRUCTION_TABLE.get(), SideConfigBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ENCHANTMENT_CONVERSION_TABLE.get(), SideConfigBlockEntityRenderer::new);
    }
}
