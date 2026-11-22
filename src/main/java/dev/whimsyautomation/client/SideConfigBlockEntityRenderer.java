package dev.whimsyautomation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Draws the six-direction configuration frames around the edited machine.
 * The expanded render bounds are essential: the frames sit just outside the
 * block's unit AABB, so the default block-entity frustum test can cull them.
 */
public final class SideConfigBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private static final AABB RENDER_BOUNDS = new AABB(-0.08, -0.08, -0.08, 1.08, 1.08, 1.08);

    public SideConfigBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        // The preview is a temporary editor overlay, not part of the machine's
        // normal render bounds. Keep it alive while its editor is open.
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
        if (!SideConfigOverlay.isActiveAt(blockEntity.getBlockPos())) return;

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();
        for (Direction direction : Direction.values()) {
            int index = direction.ordinal();
            if (!SideConfigOverlay.shouldShowFace(index)) continue;
            drawFace(lines, pose, direction, SideConfigOverlay.color(index));
        }
    }

    private static void drawFace(VertexConsumer out, PoseStack.Pose pose,
                                 Direction direction, int color) {
        // Keep the border slightly outside the block to avoid z-fighting.
        float low = 0.025F, high = 0.975F, edge = 0.035F;
        float x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4;
        float nx = direction.getStepX(), ny = direction.getStepY(), nz = direction.getStepZ();
        switch (direction) {
            case DOWN -> { x1=low; y1=-edge; z1=low; x2=high; y2=-edge; z2=low; x3=high; y3=-edge; z3=high; x4=low; y4=-edge; z4=high; }
            case UP -> { x1=low; y1=1+edge; z1=low; x2=high; y2=1+edge; z2=low; x3=high; y3=1+edge; z3=high; x4=low; y4=1+edge; z4=high; }
            case NORTH -> { x1=low; y1=low; z1=-edge; x2=high; y2=low; z2=-edge; x3=high; y3=high; z3=-edge; x4=low; y4=high; z4=-edge; }
            case SOUTH -> { x1=low; y1=low; z1=1+edge; x2=high; y2=low; z2=1+edge; x3=high; y3=high; z3=1+edge; x4=low; y4=high; z4=1+edge; }
            case WEST -> { x1=-edge; y1=low; z1=low; x2=-edge; y2=low; z2=high; x3=-edge; y3=high; z3=high; x4=-edge; y4=high; z4=low; }
            case EAST -> { x1=1+edge; y1=low; z1=low; x2=1+edge; y2=low; z2=high; x3=1+edge; y3=high; z3=high; x4=1+edge; y4=high; z4=low; }
            default -> { return; }
        }
        line(out, pose, color, nx, ny, nz, x1,y1,z1, x2,y2,z2);
        line(out, pose, color, nx, ny, nz, x2,y2,z2, x3,y3,z3);
        line(out, pose, color, nx, ny, nz, x3,y3,z3, x4,y4,z4);
        line(out, pose, color, nx, ny, nz, x4,y4,z4, x1,y1,z1);
    }

    private static void line(VertexConsumer out, PoseStack.Pose pose, int color,
                             float nx, float ny, float nz,
                             float x1, float y1, float z1, float x2, float y2, float z2) {
        out.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz);
        out.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, nx, ny, nz);
    }
}
