package dev.whimsyautomation.client;

import dev.whimsyautomation.WhimsyAutomation;
import dev.whimsyautomation.registry.ModBlockEntities;
import dev.whimsyautomation.registry.ModBlocks;
import dev.whimsyautomation.registry.ModMenus;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
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
        event.registerBlockEntityRenderer(ModBlockEntities.DECONSTRUCTION_TABLE.get(), CamouflageBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ENCHANTMENT_EXTRACTOR.get(), CamouflageBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ENCHANTMENT_CONVERSION_TABLE.get(), CamouflageBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerCamouflageModels(ModelEvent.RegisterAdditional event) {
        registerModel(event, "deconstruction_table_fallback");
        registerModel(event, "enchantment_extractor_fallback");
        registerModel(event, "enchantment_conversion_table_fallback");
        registerModel(event, "deconstruction_table_inset_fill");
        registerModel(event, "enchantment_extractor_inset_fill");
        registerModel(event, "enchantment_conversion_table_inset_fill");
    }

    private static void registerModel(ModelEvent.RegisterAdditional event, String path) {
        event.register(ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(WhimsyAutomation.MOD_ID, "block/" + path)));
    }

    @SubscribeEvent
    public static void modifyCamouflageModels(ModelEvent.ModifyBakingResult event) {
        wrapMachineModel(event, ModBlocks.DECONSTRUCTION_TABLE.get(), "deconstruction_table_fallback", "deconstruction_table_inset_fill");
        wrapMachineModel(event, ModBlocks.ENCHANTMENT_EXTRACTOR.get(), "enchantment_extractor_fallback", "enchantment_extractor_inset_fill");
        wrapMachineModel(event, ModBlocks.ENCHANTMENT_CONVERSION_TABLE.get(), "enchantment_conversion_table_fallback", "enchantment_conversion_table_inset_fill");
    }

    private static void wrapMachineModel(ModelEvent.ModifyBakingResult event, Block block,
                                         String fallbackPath, String insetFillPath) {
        ModelResourceLocation machineLocation = BlockModelShaper.stateToModelLocation(block.defaultBlockState());
        BakedModel fallback = event.getModels().get(ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(WhimsyAutomation.MOD_ID, "block/" + fallbackPath)));
        BakedModel insetFill = event.getModels().get(ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(WhimsyAutomation.MOD_ID, "block/" + insetFillPath)));
        if (fallback == null || insetFill == null) return;
        event.getModels().computeIfPresent(machineLocation,
                (location, frameModel) -> new CamouflageBakedModel(frameModel, fallback, insetFill));
    }
}
