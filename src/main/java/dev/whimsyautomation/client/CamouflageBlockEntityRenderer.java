package dev.whimsyautomation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.whimsyautomation.camouflage.CamouflageableBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders the stored camouflage state as an inset block volume.
 *
 * The machine's baked model remains responsible for its frame, directional
 * markings and decorations. The inset leaves a one-pixel border on every side.
 */
public final class CamouflageBlockEntityRenderer<T extends BlockEntity & CamouflageableBlockEntity>
        implements BlockEntityRenderer<T> {
    private static final float INSET = 1.0F / 16.0F;
    private static final float SCALE = 1.0F - 2.0F * INSET;

    private final BlockRenderDispatcher blockRenderer;

    public CamouflageBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState camouflage = blockEntity.getCamouflageState();
        if (camouflage == null || camouflage.isAir() || camouflage.is(Blocks.AIR)) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(INSET, INSET, INSET);
        poseStack.scale(SCALE, SCALE, SCALE);
        blockRenderer.renderSingleBlock(camouflage, poseStack, buffers, packedLight, packedOverlay);
        poseStack.popPose();
    }
}