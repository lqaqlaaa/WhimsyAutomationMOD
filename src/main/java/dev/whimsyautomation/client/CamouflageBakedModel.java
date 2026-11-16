package dev.whimsyautomation.client;

import dev.whimsyautomation.camouflage.CamouflageModelData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the target block through the ordinary chunk-model pipeline, then
 * retains the machine's original frame quads. This is deliberately not a BER:
 * target render layers, quad geometry and model data stay in chunk rendering.
 */
public final class CamouflageBakedModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel fallbackModel;

    public CamouflageBakedModel(BakedModel frameModel, BakedModel fallbackModel) {
        super(frameModel);
        this.fallbackModel = fallbackModel;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData tileData) {
        ModelData frameData = super.getModelData(level, pos, state, tileData);
        BlockState camouflage = frameData.get(CamouflageModelData.CAMOUFLAGE_STATE);
        if (camouflage == null || camouflage.isAir()) return frameData;

        try {
            BakedModel targetModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(camouflage);
            CamouflageLevelView view = new CamouflageLevelView(level, pos, camouflage);
            ModelData targetData = targetModel.getModelData(view, pos, camouflage, frameData);
            ModelData.Builder merged = frameData.derive();
            for (ModelProperty<?> property : targetData.getProperties()) {
                copyProperty(merged, targetData, property);
            }
            return merged.build();
        } catch (RuntimeException exception) {
            // A third-party model may assume a real block entity or world type.
            // Keep the machine renderable rather than crashing chunk rebuilds.
            return frameData;
        }
    }

    private static <T> void copyProperty(ModelData.Builder builder, ModelData source, ModelProperty<T> property) {
        if (source.has(property)) builder.with(property, source.get(property));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState machineState, @Nullable Direction side,
                                    RandomSource random, ModelData modelData, @Nullable RenderType renderType) {
        BlockState camouflage = modelData.get(CamouflageModelData.CAMOUFLAGE_STATE);
        List<BakedQuad> result = new ArrayList<>();
        if (camouflage == null || camouflage.isAir()) {
            result.addAll(fallbackModel.getQuads(machineState, side, random, modelData, renderType));
        } else {
            BakedModel target = Minecraft.getInstance().getBlockRenderer().getBlockModel(camouflage);

            /*
             * Do not let the machine's full-cube occlusion shape cull the
             * camouflage block's boundary faces. Directional quads are
             * normally treated as cullable by the chunk compiler; because
             * this model occupies a machine block position, those culling
             * decisions would incorrectly remove fence/bar/pane faces when
             * they touch the ground or another solid block.
             *
             * Promote every directional target quad into the unculled bucket.
             * The target geometry remains unchanged, while the machine frame
             * below keeps its original directional/culling behavior.
             */
            if (side == null) {
                result.addAll(target.getQuads(camouflage, null, random, modelData, renderType));
                for (Direction face : Direction.values()) {
                    result.addAll(target.getQuads(camouflage, face, random, modelData, renderType));
                }
            }
        }
        // Frame quads are appended after target quads; depth testing keeps the
        // physical rim in front while the camouflage fills the central opening.
        result.addAll(super.getQuads(machineState, side, random, modelData, renderType));
        return result;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(@Nullable BlockState machineState, RandomSource random, ModelData modelData) {
        ChunkRenderTypeSet frameTypes = super.getRenderTypes(machineState, random, modelData);
        BlockState camouflage = modelData.get(CamouflageModelData.CAMOUFLAGE_STATE);
        BakedModel body = camouflage == null || camouflage.isAir()
                ? fallbackModel : Minecraft.getInstance().getBlockRenderer().getBlockModel(camouflage);
        ChunkRenderTypeSet bodyTypes = camouflage == null || camouflage.isAir()
                ? body.getRenderTypes(machineState, random, modelData)
                : body.getRenderTypes(camouflage, random, modelData);
        return ChunkRenderTypeSet.union(bodyTypes, frameTypes);
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        BlockState camouflage = data.get(CamouflageModelData.CAMOUFLAGE_STATE);
        if (camouflage != null && !camouflage.isAir()) {
            return Minecraft.getInstance().getBlockRenderer().getBlockModel(camouflage)
                    .useAmbientOcclusion(camouflage, data, renderType);
        }
        return super.useAmbientOcclusion(state, data, renderType);
    }

    @Override
    public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState camouflage = data.get(CamouflageModelData.CAMOUFLAGE_STATE);
        if (camouflage != null && !camouflage.isAir()) {
            return Minecraft.getInstance().getBlockRenderer().getBlockModel(camouflage).getParticleIcon(data);
        }
        return fallbackModel.getParticleIcon(data);
    }
}
