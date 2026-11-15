package dev.whimsyautomation.camouflage;

import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * Immutable model data shared by all camouflage-capable machine block entities.
 * The target state stays in BlockEntity NBT; this property only exposes it to
 * the client chunk-model pipeline.
 */
public final class CamouflageModelData {
    public static final ModelProperty<BlockState> CAMOUFLAGE_STATE = new ModelProperty<>();

    private CamouflageModelData() {}
}
