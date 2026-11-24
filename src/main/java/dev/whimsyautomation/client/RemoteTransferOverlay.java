package dev.whimsyautomation.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Renders selected network members and thick, direction-colored connection beams. */
@EventBusSubscriber(modid = "whimsy_automation", value = Dist.CLIENT)
public final class RemoteTransferOverlay {
    private static CompoundTag snapshot = new CompoundTag();
    private static UUID selectedNetwork;
    private static boolean connectionsVisible = true;

    private static final int INPUT_COLOR = 0xFF35C8FF;
    private static final int OUTPUT_COLOR = 0xFFFFA33B;
    // Same order of magnitude as the 0.035-wide side-config face frames.
    private static final float BEAM_RADIUS = 0.035F;

    private static final RenderType CONNECTION_BEAM = RenderType.create(
            "whimsy_automation_remote_connection_beam",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .setLayeringState(new RenderStateShard.LayeringStateShard(
                            "whimsy_remote_beam_polygon_offset",
                            () -> {
                                RenderSystem.enablePolygonOffset();
                                RenderSystem.polygonOffset(-1.0F, -1.0F);
                            },
                            RenderSystem::disablePolygonOffset))
                    .setDepthTestState(new RenderStateShard.DepthTestStateShard("whimsy_remote_beam_depth", 515))
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, true))
                    .createCompositeState(false));

    private RemoteTransferOverlay() {}

    public static void applySnapshot(CompoundTag data) {
        snapshot = data == null ? new CompoundTag() : data.copy();
        if (selectedNetwork != null && findSelectedNetwork() == null) selectedNetwork = null;
    }

    public static void selectNetwork(UUID id) { selectedNetwork = id; }
    public static void setConnectionsVisible(boolean visible) { connectionsVisible = visible; }
    public static boolean connectionsVisible() { return connectionsVisible; }
    public static void toggleConnectionsVisible() { connectionsVisible = !connectionsVisible; }

    private static CompoundTag findSelectedNetwork() {
        ListTag list = snapshot.getList("Networks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag network = list.getCompound(i);
            if (network.hasUUID("Id") && network.getUUID("Id").equals(selectedNetwork)) return network;
        }
        return null;
    }

    @SubscribeEvent
    public static void renderEndpoints(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || selectedNetwork == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;
        CompoundTag network = findSelectedNetwork();
        if (network == null) return;

        ListTag members = network.getList("Members", Tag.TAG_COMPOUND);
        if (members.isEmpty()) return;

        Map<UUID, BlockPos> positions = new HashMap<>();
        for (int i = 0; i < members.size(); i++) {
            CompoundTag member = members.getCompound(i);
            if (!level.dimension().location().toString().equals(member.getString("Dimension"))) continue;
            BlockPos pos = BlockPos.of(member.getLong("Pos"));
            if (level.hasChunkAt(pos) && member.hasUUID("Id")) positions.put(member.getUUID("Id"), pos);
        }

        PoseStack pose = event.getPoseStack();
        var cameraPos = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        VertexConsumer boxLines = buffers.getBuffer(RenderType.lines());
        int[] memberColors = {0xFFFF5555, 0xFFFFAA33, 0xFFFFFF55, 0xFF55FF77,
                0xFF55DDFF, 0xFF6688FF, 0xFFCC66FF, 0xFFFF66CC};
        for (int i = 0; i < members.size(); i++) {
            CompoundTag member = members.getCompound(i);
            if (!level.dimension().location().toString().equals(member.getString("Dimension"))) continue;
            BlockPos pos = BlockPos.of(member.getLong("Pos"));
            if (!level.hasChunkAt(pos)) continue;
            int color = memberColors[i % memberColors.length];
            float red = ((color >> 16) & 255) / 255.0F;
            float green = ((color >> 8) & 255) / 255.0F;
            float blue = (color & 255) / 255.0F;
            LevelRenderer.renderLineBox(pose, boxLines, new AABB(pos).inflate(0.003D), red, green, blue, 1.0F);
        }
        buffers.endBatch(RenderType.lines());

        if (connectionsVisible) {
            VertexConsumer beams = buffers.getBuffer(CONNECTION_BEAM);
            ListTag connections = network.getList("Connections", Tag.TAG_COMPOUND);
            Matrix4f matrix = pose.last().pose();
            for (int i = 0; i < connections.size(); i++) {
                CompoundTag connection = connections.getCompound(i);
                if (!connection.getBoolean("Enabled")) continue;
                BlockPos source = positions.get(connection.getUUID("Source"));
                BlockPos target = positions.get(connection.getUUID("Target"));
                if (source == null || target == null) continue;
                int color = "INPUT".equals(connection.getString("Direction")) ? INPUT_COLOR : OUTPUT_COLOR;
                drawBeam(beams, matrix,
                        source.getX() + 0.5F, source.getY() + 0.5F, source.getZ() + 0.5F,
                        target.getX() + 0.5F, target.getY() + 0.5F, target.getZ() + 0.5F,
                        color);
            }
            buffers.endBatch(CONNECTION_BEAM);
        }
        pose.popPose();
    }

    /** A solid rectangular beam, not a debug line: its visible width is stable on all clients. */
    private static void drawBeam(VertexConsumer out, Matrix4f matrix,
                                 float ax, float ay, float az, float bx, float by, float bz, int color) {
        Vector3f direction = new Vector3f(bx - ax, by - ay, bz - az);
        if (direction.lengthSquared() < 1.0E-5F) return;
        direction.normalize();
        Vector3f reference = Math.abs(direction.y) < 0.9F
                ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f u = new Vector3f(direction).cross(reference).normalize().mul(BEAM_RADIUS);
        Vector3f v = new Vector3f(direction).cross(u).normalize().mul(BEAM_RADIUS);

        Vector3f a = new Vector3f(ax, ay, az);
        Vector3f b = new Vector3f(bx, by, bz);
        Vector3f[] start = corners(a, u, v);
        Vector3f[] end = corners(b, u, v);
        float r = ((color >>> 16) & 255) / 255.0F;
        float g = ((color >>> 8) & 255) / 255.0F;
        float bl = (color & 255) / 255.0F;

        quad(out, matrix, start[0], start[1], start[2], start[3], r, g, bl);
        quad(out, matrix, end[3], end[2], end[1], end[0], r, g, bl);
        for (int i = 0; i < 4; i++) quad(out, matrix, start[i], start[(i + 1) & 3],
                end[(i + 1) & 3], end[i], r, g, bl);
    }

    private static Vector3f[] corners(Vector3f center, Vector3f u, Vector3f v) {
        return new Vector3f[] {
                new Vector3f(center).add(u).add(v),
                new Vector3f(center).sub(u).add(v),
                new Vector3f(center).sub(u).sub(v),
                new Vector3f(center).add(u).sub(v)
        };
    }

    private static void quad(VertexConsumer out, Matrix4f matrix,
                             Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                             float r, float g, float blue) {
        vertex(out, matrix, a, r, g, blue);
        vertex(out, matrix, b, r, g, blue);
        vertex(out, matrix, c, r, g, blue);
        vertex(out, matrix, d, r, g, blue);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Vector3f p,
                               float r, float g, float b) {
        out.addVertex(matrix, p.x, p.y, p.z).setColor(r, g, b, 1.0F);
    }
}
