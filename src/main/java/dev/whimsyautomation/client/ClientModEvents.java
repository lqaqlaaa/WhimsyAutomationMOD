package dev.whimsyautomation.client;

import dev.whimsyautomation.WhimsyAutomation;
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

    /**
     * Ensure the original machine body is baked as a fallback, independently
     * from the open-center frame model used by the dynamic camouflage model.
     */
    @SubscribeEvent
    public static void registerCamouflageFallbackModels(ModelEvent.RegisterAdditional event) {
        registerFallback(event, "deconstruction_table_fallback");
        registerFallback(event, "enchantment_extractor_fallback");
        registerFallback(event, "enchantment_conversion_table_fallback");
    }

    private static void registerFallback(ModelEvent.RegisterAdditional event, String path) {
        event.register(ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(WhimsyAutomation.MOD_ID, "block/" + path)));
    }

    @SubscribeEvent
    public static void modifyCamouflageModels(ModelEvent.ModifyBakingResult event) {
        wrapMachineModel(event, ModBlocks.DECONSTRUCTION_TABLE.get(), "deconstruction_table_fallback");
        wrapMachineModel(event, ModBlocks.ENCHANTMENT_EXTRACTOR.get(), "enchantment_extractor_fallback");
        wrapMachineModel(event, ModBlocks.ENCHANTMENT_CONVERSION_TABLE.get(), "enchantment_conversion_table_fallback");
    }

    private static void wrapMachineModel(ModelEvent.ModifyBakingResult event, Block block, String fallbackPath) {
        ModelResourceLocation machineLocation = BlockModelShaper.stateToModelLocation(block.defaultBlockState());
        ModelResourceLocation fallbackLocation = ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(WhimsyAutomation.MOD_ID, "block/" + fallbackPath));
        BakedModel fallback = event.getModels().get(fallbackLocation);
        if (fallback == null) {
            return;
        }
        event.getModels().computeIfPresent(machineLocation,
                (location, frameModel) -> new CamouflageBakedModel(frameModel, fallback));
    }
}
