package dev.whimsyautomation.client;

import dev.whimsyautomation.WhimsyAutomation;
import dev.whimsyautomation.registry.ModBlockEntities;
import dev.whimsyautomation.registry.ModItems;
import dev.whimsyautomation.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = WhimsyAutomation.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ENCHANTMENT_EXTRACTOR.get(), EnchantmentExtractorScreen::new);
        event.register(ModMenus.DECONSTRUCTION_TABLE.get(), DeconstructionTableScreen::new);
        event.register(ModMenus.ENCHANTMENT_CONVERSION_TABLE.get(), EnchantmentConversionTableScreen::new);
        event.register(ModMenus.BIG_CAPACITY_CHEST_3.get(), BigCapacityChestScreen::new);
        event.register(ModMenus.BIG_CAPACITY_CHEST_6.get(), BigCapacityChestScreen::new);
    }

    @SubscribeEvent
    public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ENCHANTMENT_EXTRACTOR.get(), SideConfigBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DECONSTRUCTION_TABLE.get(), SideConfigBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ENCHANTMENT_CONVERSION_TABLE.get(), SideConfigBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BIG_CAPACITY_CHEST.get(), ChestRenderer::new);
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        BigCapacityChestItemRenderer renderer = new BigCapacityChestItemRenderer();
        event.registerItem(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }
        }, ModItems.BIG_CAPACITY_CHEST.get());
    }
}