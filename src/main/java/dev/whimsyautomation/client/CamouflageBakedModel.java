package dev.whimsyautomation.client;

import dev.whimsyautomation.camouflage.CamouflageModelData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
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
 * Re-routes the machine's body quads to the stored camouflage block model.
 *
 * The machine's own model is deliberately only the frame/directional trim.
 * With no camouflage, a separate baked fallback model supplies the normal
 * machine body. With camouflage, the target's baked quads are emitted first
 * and the machine frame last, so the machine's directional trim remains visible.
 */
public final class CamouflageBakedModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel fallbackModel;

    public CamouflageBakedModel(BakedModel frameModel, BakedModel fallbackModel) {
        super(frameModel);
        this.fallbackModel = fallbackModel;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState machineState,
                                    @Nullable net.minecraft.core.Direction side,
                                    RandomSource random,
                                    ModelData modelData,
                                    @Nullable RenderType renderType) {
        BlockState camouflage = modelData.get(CamouflageModelData.CAMOUFLAGE_STATE);
        List<BakedQuad> result = new ArrayList<>();

        if (camouflage == null || camouflage.isAir()) {
            result.addAll(fallbackModel.getQuads(machineState, side, random, ModelData.EMPTY, renderType));
        } else {
            BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
            BakedModel targetModel = dispatcher.getBlockModel(camouflage);
            result.addAll(targetModel.getQuads(camouflage, side, random, ModelData.EMPTY, renderType));
        }

        // Keep the machine's edge frame and six-direction markings as the top layer.
        result.addAll(super.getQuads(machineState, side, random, modelData, renderType));
        return result;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(@Nullable BlockState machineState,
                                             RandomSource random,
                                             ModelData modelData) {
        ChunkRenderTypeSet frameTypes = super.getRenderTypes(machineState, random, modelData);
        BlockState camouflage = modelData.get(CamouflageModelData.CAMOUFLAGE_STATE);
        BakedModel bodyModel = camouflage == null || camouflage.isAir()
                ? fallbackModel
                : Minecraft.getInstance().getBlockRenderer().getBlockModel(camouflage);
        BlockState bodyState = camouflage == null || camouflage.isAir() ? machineState : camouflage;
        ChunkRenderTypeSet bodyTypes = bodyModel.getRenderTypes(bodyState, random, ModelData.EMPTY);
        return ChunkRenderTypeSet.union(bodyTypes, frameTypes);
    }
}
