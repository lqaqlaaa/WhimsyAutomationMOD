package dev.whimsyautomation.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Draws a visible, slightly-outset rectangular frame on each enabled machine face.
 * The frame is submitted as POSITION_COLOR quads instead of debug line primitives,
 * so its width is stable and it does not depend on the debug-line render path.
 */
public final class SideConfigBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private static final float EDGE = 0.035F;
    private static final float LOW = 0.02F;
    private static final float HIGH = 0.98F;
    private static final float OUTSET = 0.012F;
    private static final AABB RENDER_BOUNDS = new AABB(-0.06, -0.06, -0.06, 1.06, 1.06, 1.06);

    private static final RenderType SIDE_FRAME = RenderType.create(
            "whimsy_automation_side_frame",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    // The frame is an opaque overlay. Blending without depth writes
                    // lets overlapping faces accumulate and appear to flicker.
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    // Apply a small depth bias to the overlay so coplanar frame
                    // quads do not compete with the block surface in the depth buffer.
                    .setLayeringState(new RenderStateShard.LayeringStateShard(
                            "whimsy_side_frame_polygon_offset",
                            () -> {
                                RenderSystem.enablePolygonOffset();
                                RenderSystem.polygonOffset(-1.0F, -1.0F);
                            },
                            RenderSystem::disablePolygonOffset))
                    .setDepthTestState(new RenderStateShard.DepthTestStateShard("whimsy_side_frame_depth", 515))
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, true))
                    .createCompositeState(false));

    public SideConfigBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return SideConfigOverlay.isActiveAt(blockEntity.getLevel(), blockEntity.getBlockPos());
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
        if (!SideConfigOverlay.isActiveAt(blockEntity.getLevel(), pos)) return;

        VertexConsumer out = buffers.getBuffer(SIDE_FRAME);
        Matrix4f matrix = poseStack.last().pose();

        for (int side = 0; side < 6; side++) {
            if (!SideConfigOverlay.shouldShowFace(blockEntity.getLevel(), pos, side)) continue;
            int argb = SideConfigOverlay.color(side);
            float r = ((argb >>> 16) & 0xFF) / 255.0F;
            float g = ((argb >>> 8) & 0xFF) / 255.0F;
            float b = (argb & 0xFF) / 255.0F;
            drawFace(out, matrix, side, r, g, b, 1.0F);
        }
    }

    // Direction ordinal order: DOWN, UP, NORTH, SOUTH, WEST, EAST.
    private static void drawFace(VertexConsumer out, Matrix4f m, int side,
                                 float r, float g, float b, float a) {
        switch (side) {
            case 0 -> { // DOWN: X/Z plane
                float y = -OUTSET;
                quad(out, m, LOW, y, LOW, HIGH, y, LOW, HIGH, y, LOW + EDGE, LOW, y, LOW + EDGE, r,g,b,a);
                quad(out, m, LOW, y, HIGH - EDGE, HIGH, y, HIGH - EDGE, HIGH, y, HIGH, LOW, y, HIGH, r,g,b,a);
                quad(out, m, LOW, y, LOW + EDGE, LOW + EDGE, y, LOW + EDGE, LOW + EDGE, y, HIGH - EDGE, LOW, y, HIGH - EDGE, r,g,b,a);
                quad(out, m, HIGH - EDGE, y, LOW + EDGE, HIGH, y, LOW + EDGE, HIGH, y, HIGH - EDGE, HIGH - EDGE, y, HIGH - EDGE, r,g,b,a);
            }
            case 1 -> { // UP
                float y = 1.0F + OUTSET;
                quad(out, m, LOW, y, LOW, HIGH, y, LOW, HIGH, y, LOW + EDGE, LOW, y, LOW + EDGE, r,g,b,a);
                quad(out, m, LOW, y, HIGH - EDGE, HIGH, y, HIGH - EDGE, HIGH, y, HIGH, LOW, y, HIGH, r,g,b,a);
                quad(out, m, LOW, y, LOW + EDGE, LOW + EDGE, y, LOW + EDGE, LOW + EDGE, y, HIGH - EDGE, LOW, y, HIGH - EDGE, r,g,b,a);
                quad(out, m, HIGH - EDGE, y, LOW + EDGE, HIGH, y, LOW + EDGE, HIGH, y, HIGH - EDGE, HIGH - EDGE, y, HIGH - EDGE, r,g,b,a);
            }
            case 2 -> { // NORTH: X/Y plane
                float z = -OUTSET;
                quad(out, m, LOW, LOW, z, HIGH, LOW, z, HIGH, LOW + EDGE, z, LOW, LOW + EDGE, z, r,g,b,a);
                quad(out, m, LOW, HIGH - EDGE, z, HIGH, HIGH - EDGE, z, HIGH, HIGH, z, LOW, HIGH, z, r,g,b,a);
                quad(out, m, LOW, LOW + EDGE, z, LOW + EDGE, LOW + EDGE, z, LOW + EDGE, HIGH - EDGE, z, LOW, HIGH - EDGE, z, r,g,b,a);
                quad(out, m, HIGH - EDGE, LOW + EDGE, z, HIGH, LOW + EDGE, z, HIGH, HIGH - EDGE, z, HIGH - EDGE, HIGH - EDGE, z, r,g,b,a);
            }
            case 3 -> { // SOUTH
                float z = 1.0F + OUTSET;
                quad(out, m, LOW, LOW, z, HIGH, LOW, z, HIGH, LOW + EDGE, z, LOW, LOW + EDGE, z, r,g,b,a);
                quad(out, m, LOW, HIGH - EDGE, z, HIGH, HIGH - EDGE, z, HIGH, HIGH, z, LOW, HIGH, z, r,g,b,a);
                quad(out, m, LOW, LOW + EDGE, z, LOW + EDGE, LOW + EDGE, z, LOW + EDGE, HIGH - EDGE, z, LOW, HIGH - EDGE, z, r,g,b,a);
                quad(out, m, HIGH - EDGE, LOW + EDGE, z, HIGH, LOW + EDGE, z, HIGH, HIGH - EDGE, z, HIGH - EDGE, HIGH - EDGE, z, r,g,b,a);
            }
            case 4 -> { // WEST: Y/Z plane
                float x = -OUTSET;
                quad(out, m, x, LOW, LOW, x, LOW, HIGH, x, LOW + EDGE, HIGH, x, LOW + EDGE, LOW, r,g,b,a);
                quad(out, m, x, HIGH - EDGE, LOW, x, HIGH - EDGE, HIGH, x, HIGH, HIGH, x, HIGH, LOW, r,g,b,a);
                quad(out, m, x, LOW + EDGE, LOW, x, LOW + EDGE, LOW + EDGE, x, HIGH - EDGE, LOW + EDGE, x, HIGH - EDGE, LOW, r,g,b,a);
                quad(out, m, x, LOW + EDGE, HIGH - EDGE, x, LOW + EDGE, HIGH, x, HIGH - EDGE, HIGH, x, HIGH - EDGE, HIGH - EDGE, r,g,b,a);
            }
            case 5 -> { // EAST
                float x = 1.0F + OUTSET;
                quad(out, m, x, LOW, LOW, x, LOW, HIGH, x, LOW + EDGE, HIGH, x, LOW + EDGE, LOW, r,g,b,a);
                quad(out, m, x, HIGH - EDGE, LOW, x, HIGH - EDGE, HIGH, x, HIGH, HIGH, x, HIGH, LOW, r,g,b,a);
                quad(out, m, x, LOW + EDGE, LOW, x, LOW + EDGE, LOW + EDGE, x, HIGH - EDGE, LOW + EDGE, x, HIGH - EDGE, LOW, r,g,b,a);
                quad(out, m, x, LOW + EDGE, HIGH - EDGE, x, LOW + EDGE, HIGH, x, HIGH - EDGE, HIGH, x, HIGH - EDGE, HIGH - EDGE, r,g,b,a);
            }
            default -> { }
        }
    }

    private static void quad(VertexConsumer out, Matrix4f m,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        vertex(out, m, x0, y0, z0, r, g, b, a);
        vertex(out, m, x1, y1, z1, r, g, b, a);
        vertex(out, m, x2, y2, z2, r, g, b, a);
        vertex(out, m, x3, y3, z3, r, g, b, a);
    }

    private static void vertex(VertexConsumer out, Matrix4f m,
                               float x, float y, float z,
                               float r, float g, float b, float a) {
        out.addVertex(m, x, y, z).setColor(r, g, b, a);
    }
}
