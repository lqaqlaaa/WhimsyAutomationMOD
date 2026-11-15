package dev.whimsyautomation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.whimsyautomation.camouflage.CamouflageableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the camouflage state through Minecraft's normal block-model renderer.
 * The renderer is generic over BlockEntity so NeoForge can infer each concrete
 * registered block-entity type; camouflage capability is checked at runtime.
 */
public final class CamouflageBlockEntityRenderer<T extends BlockEntity>
        implements BlockEntityRenderer<T> {

    public CamouflageBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!(blockEntity instanceof CamouflageableBlockEntity camouflageable)) return;
        if (blockEntity.getLevel() == null) return;

        BlockState camouflage = camouflageable.getCamouflageState();
        if (camouflage == null || camouflage.isAir()) return;

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(camouflage);
        ModelData modelData = model.getModelData(blockEntity.getLevel(), blockEntity.getBlockPos(),
                camouflage, ModelData.EMPTY);
        RandomSource random = RandomSource.create();
        for (RenderType renderType : model.getRenderTypes(camouflage, random, modelData)) {
            dispatcher.renderBatched(camouflage, blockEntity.getBlockPos(), blockEntity.getLevel(),
                    poseStack, bufferSource.getBuffer(renderType), false, random, modelData, renderType);
        }
    }
}
