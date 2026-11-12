package dev.whimsyautomation.registry;

import dev.whimsyautomation.WhimsyAutomation;
import dev.whimsyautomation.block.DeconstructionTableBlock;
import dev.whimsyautomation.block.EnchantmentExtractorBlock;
import dev.whimsyautomation.block.EnchantmentConversionTableBlock;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WhimsyAutomation.MOD_ID);

    public static final DeferredBlock<Block> ENCHANTMENT_EXTRACTOR =
            BLOCKS.register("enchantment_extractor", EnchantmentExtractorBlock::new);
    public static final DeferredBlock<Block> ENCHANTMENT_CONVERSION_TABLE =
            BLOCKS.register("enchantment_conversion_table", EnchantmentConversionTableBlock::new);
    public static final DeferredBlock<Block> DECONSTRUCTION_TABLE =
            BLOCKS.register("deconstruction_table", DeconstructionTableBlock::new);

    private ModBlocks() {}

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        BLOCKS.register(bus);
    }
}
