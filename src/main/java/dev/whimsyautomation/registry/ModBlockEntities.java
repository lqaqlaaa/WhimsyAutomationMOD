package dev.whimsyautomation.registry;

import dev.whimsyautomation.WhimsyAutomation;
import dev.whimsyautomation.block.entity.DeconstructionTableBlockEntity;
import dev.whimsyautomation.block.entity.EnchantmentExtractorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WhimsyAutomation.MOD_ID);

    public static final net.neoforged.neoforge.registries.DeferredHolder<BlockEntityType<?>, BlockEntityType<EnchantmentExtractorBlockEntity>> ENCHANTMENT_EXTRACTOR =
            BLOCK_ENTITIES.register("enchantment_extractor", () ->
                    BlockEntityType.Builder.of(EnchantmentExtractorBlockEntity::new, ModBlocks.ENCHANTMENT_EXTRACTOR.get()).build(null));

    public static final net.neoforged.neoforge.registries.DeferredHolder<BlockEntityType<?>, BlockEntityType<DeconstructionTableBlockEntity>> DECONSTRUCTION_TABLE =
            BLOCK_ENTITIES.register("deconstruction_table", () ->
                    BlockEntityType.Builder.of(DeconstructionTableBlockEntity::new, ModBlocks.DECONSTRUCTION_TABLE.get()).build(null));

    private ModBlockEntities() {}

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
