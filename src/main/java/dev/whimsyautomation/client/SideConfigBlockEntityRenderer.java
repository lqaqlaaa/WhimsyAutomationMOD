package dev.whimsyautomation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Temporary diagnostic renderer for the six-direction configuration overlay.
 *
 * While the overlay is active, this deliberately draws one unmistakable red
 * top-face frame. It isolates BER invocation, pose coordinates and line-strip
 * submission from the six-face color/mode logic.
 */
public final class SideConfigBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<BlockPos> LOGGED_POSITIONS = new HashSet<>();
    private static final AABB RENDER_BOUNDS = new AABB(-0.08, -0.08, -0.08, 1.08, 1.08, 1.08);
    private static final int TEST_RED = 0xFFFF2020;

    public SideConfigBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return SideConfigOverlay.isActiveAt(blockEntity.getBlockPos());
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(T blockEntity) {
        return RENDER_BOUNDS.move(blockEntity.getBlockPos());
    }

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockPos pos = blockEntity.getBlockPos();
        if (LOGGED_POSITIONS.add(pos.immutable())) {
            LOGGER.info("SideConfig BER render called for {} ({})", pos, blockEntity.getType());
        }
        if (!SideConfigOverlay.isActiveAt(pos)) return;

        // Diagnostic only: a 3px red closed line strip on the top face.
        // If this does not appear, the failure is outside six-face color logic.
        VertexConsumer line = buffers.getBuffer(RenderType.debugLineStrip(3.0D));
        PoseStack.Pose pose = poseStack.last();
        float y = 1.035F;
        vertex(line, pose, 0.02F, y, 0.02F);
        vertex(line, pose, 0.98F, y, 0.02F);
        vertex(line, pose, 0.98F, y, 0.98F);
        vertex(line, pose, 0.02F, y, 0.98F);
        vertex(line, pose, 0.02F, y, 0.02F);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z) {
        out.addVertex(pose, x, y, z)
                .setColor(TEST_RED)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
