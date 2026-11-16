package dev.whimsyautomation.client;

import dev.whimsyautomation.WhimsyAutomation;
import dev.whimsyautomation.camouflage.CamouflageableBlockEntity;
import dev.whimsyautomation.registry.ModBlocks;
import dev.whimsyautomation.registry.ModMenus;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
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
    public static void registerCamouflageModels(ModelEvent.RegisterAdditional event) {
        registerModel(event, "deconstruction_table_fallback");
        registerModel(event, "enchantment_extractor_fallback");
        registerModel(event, "enchantment_conversion_table_fallback");
    }

    private static void registerModel(ModelEvent.RegisterAdditional event, String path) {
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
        BakedModel fallback = event.getModels().get(ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(WhimsyAutomation.MOD_ID, "block/" + fallbackPath)));
        if (fallback == null) return;
        event.getModels().computeIfPresent(machineLocation,
                (location, frameModel) -> new CamouflageBakedModel(frameModel, fallback));
    }

    @SubscribeEvent
    public static void registerCamouflageColors(RegisterColorHandlersEvent.Block event) {
        BlockColors colors = event.getBlockColors();
        event.register((machineState, level, pos, tintIndex) -> {
            if (level == null || pos == null || !(level.getBlockEntity(pos) instanceof CamouflageableBlockEntity machine)) {
                return -1;
            }
            BlockState camouflage = machine.getCamouflageState();
            if (camouflage == null || camouflage.isAir()) return -1;
            try {
                if (!(level instanceof net.minecraft.world.level.BlockAndTintGetter tintGetter)) return -1;
                CamouflageLevelView view = new CamouflageLevelView(tintGetter, pos, camouflage);
                return colors.getColor(camouflage, view, pos, tintIndex);
            } catch (RuntimeException exception) {
                return -1;
            }
        }, ModBlocks.DECONSTRUCTION_TABLE.get(), ModBlocks.ENCHANTMENT_EXTRACTOR.get(),
                ModBlocks.ENCHANTMENT_CONVERSION_TABLE.get());
    }
}
