package dev.whimsyautomation.client;

import dev.whimsyautomation.camouflage.CamouflageModelData;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the machine frame in the chunk model. The camouflage block itself is
 * rendered by CamouflageBlockEntityRenderer so its own BlockState, ModelData,
 * render layers and biome tint handlers are evaluated by Minecraft's renderer.
 */
public final class CamouflageBakedModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel fallbackModel;
    private final BakedModel insetFillModel;

    public CamouflageBakedModel(BakedModel frameModel, BakedModel fallbackModel, BakedModel insetFillModel) {
        super(frameModel);
        this.fallbackModel = fallbackModel;
        this.insetFillModel = insetFillModel;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState machineState,
                                    @Nullable net.minecraft.core.Direction side,
                                    RandomSource random,
                                    ModelData modelData,
                                    @Nullable RenderType renderType) {
        BlockState camouflage = modelData.get(CamouflageModelData.CAMOUFLAGE_STATE);
        List<BakedQuad> result = new ArrayList<>();
        BakedModel body = camouflage == null || camouflage.isAir() ? fallbackModel : insetFillModel;
        result.addAll(body.getQuads(machineState, side, random, ModelData.EMPTY, renderType));
        result.addAll(super.getQuads(machineState, side, random, modelData, renderType));
        return result;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(@Nullable BlockState machineState,
                                             RandomSource random,
                                             ModelData modelData) {
        ChunkRenderTypeSet frameTypes = super.getRenderTypes(machineState, random, modelData);
        BlockState camouflage = modelData.get(CamouflageModelData.CAMOUFLAGE_STATE);
        BakedModel body = camouflage == null || camouflage.isAir() ? fallbackModel : insetFillModel;
        ChunkRenderTypeSet bodyTypes = body.getRenderTypes(machineState, random, ModelData.EMPTY);
        return ChunkRenderTypeSet.union(bodyTypes, frameTypes);
    }
}
