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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/** Renders selected network members and thick, direction-colored connection beams. */
@EventBusSubscriber(modid = "whimsy_automation", value = Dist.CLIENT)
public final class RemoteTransferOverlay {
    private static CompoundTag snapshot = new CompoundTag();
    private static UUID selectedNetwork;
    private static boolean connectionsVisible = true;

    private static final int INPUT_COLOR = 0xFF35C8FF;
    private static final int OUTPUT_COLOR = 0xFFFFA33B;
    // Stable palette: every workstation slot keeps one color across its bound container and beam.
    private static final int[] SLOT_COLORS = {
            0xFF35C8FF, 0xFFFFA33B, 0xFF72D572, 0xFFD17CFF,
            0xFFFFD34E, 0xFFFF6F91, 0xFF63D6C5, 0xFF9BA8FF,
            0xFFFF9B55, 0xFFB8D66B
    };
    private static final int MEMBER_COLOR = 0xFF7890AA;
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
                    .setLayeringState(RenderStateShard.NO_LAYERING)
                    // X-ray beam: ignore terrain depth so the complete connection remains visible.
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, true))
                    .createCompositeState(false));

    private RemoteTransferOverlay() {}

    public static void applySnapshot(CompoundTag data) {
        snapshot = data == null ? new CompoundTag() : data.copy();
        if (selectedNetwork != null && findSelectedNetwork() == null) selectedNetwork = null;
    }

    public static void selectNetwork(UUID id) { selectedNetwork = id; }
    public static UUID selectedNetwork() { return selectedNetwork; }
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

        ListTag slotBindings = network.getList("SlotBindings", net.minecraft.nbt.Tag.TAG_COMPOUND);
        Map<UUID, Integer> boundTargetColors = new HashMap<>();
        for (int i = 0; i < slotBindings.size(); i++) {
            CompoundTag binding = slotBindings.getCompound(i);
            if (!binding.hasUUID("Target") || !binding.hasUUID("Workstation")) continue;
            UUID targetId = binding.getUUID("Target");
            int slotIndex = Math.max(0, binding.getInt("WorkstationSlot"));
            boundTargetColors.putIfAbsent(targetId, SLOT_COLORS[slotIndex % SLOT_COLORS.length]);
        }

        if (!network.contains("MemberBordersVisible") || network.getBoolean("MemberBordersVisible")) {
        VertexConsumer boxLines = buffers.getBuffer(RenderType.lines());
        for (int i = 0; i < members.size(); i++) {
            CompoundTag member = members.getCompound(i);
            if (!level.dimension().location().toString().equals(member.getString("Dimension"))) continue;
            BlockPos pos = BlockPos.of(member.getLong("Pos"));
            if (!level.hasChunkAt(pos)) continue;
            Integer boundColor = member.hasUUID("Id")
                    ? boundTargetColors.get(member.getUUID("Id")) : null;
            // One physical container always has exactly one outline. Multiple
            // slot bindings are distinguished by their separate colored beams.
            renderMemberBox(pose, boxLines, pos,
                    boundColor == null ? MEMBER_COLOR : boundColor, 0.003D);
        }
        buffers.endBatch(RenderType.lines());
        }

        if (connectionsVisible) {
            VertexConsumer beams = buffers.getBuffer(CONNECTION_BEAM);
            ListTag connections = network.getList("Connections", Tag.TAG_COMPOUND);
            Matrix4f matrix = pose.last().pose();
            for (int i = 0; i < connections.size(); i++) {
                CompoundTag connection = connections.getCompound(i);
                if (!connection.getBoolean("ShowLine")) continue;
                BlockPos source = positions.get(connection.getUUID("Source"));
                BlockPos target = positions.get(connection.getUUID("Target"));
                if (source == null || target == null) continue;
                int color = "INPUT".equals(connection.getString("Direction")) ? INPUT_COLOR : OUTPUT_COLOR;
                drawBetweenBlockFaces(beams, matrix, source, target, color, 0.0F);
            }

            // Slot bindings remain independent for transfer logic, but bindings
            // created as one workstation-to-container connection share one visual beam.
            // This avoids drawing a bundle of parallel lines for multi-slot auto-binding.
            Set<String> renderedBindingPairs = new HashSet<>();
            for (int i = 0; i < slotBindings.size(); i++) {
                CompoundTag binding = slotBindings.getCompound(i);
                if (!binding.getBoolean("Enabled")
                        || !binding.hasUUID("Workstation") || !binding.hasUUID("Target")) continue;
                BlockPos workstation = positions.get(binding.getUUID("Workstation"));
                BlockPos target = positions.get(binding.getUUID("Target"));
                if (workstation == null || target == null) continue;
                String pairKey = binding.getUUID("Workstation") + ":" + binding.getUUID("Target");
                if (!renderedBindingPairs.add(pairKey)) continue;
                int slotIndex = Math.max(0, binding.getInt("WorkstationSlot"));
                int color = SLOT_COLORS[slotIndex % SLOT_COLORS.length];
                drawBetweenBlockFaces(beams, matrix, workstation, target, color, 0.0F);
            }
            buffers.endBatch(CONNECTION_BEAM);
        }
        pose.popPose();
    }

    /** Draws between the nearest block faces, not through block centers. */
    private static void drawBetweenBlockFaces(VertexConsumer out, Matrix4f matrix,
                                              BlockPos from, BlockPos to, int color, float lane) {
        float ax = from.getX() + 0.5F, ay = from.getY() + 0.5F, az = from.getZ() + 0.5F;
        float bx = to.getX() + 0.5F, by = to.getY() + 0.5F, bz = to.getZ() + 0.5F;
        Vector3f direction = new Vector3f(bx - ax, by - ay, bz - az);
        if (direction.lengthSquared() < 1.0E-5F) return;
        direction.normalize();
        float faceOffset = 0.5F / Math.max(Math.max(Math.abs(direction.x), Math.abs(direction.y)), Math.abs(direction.z));
        float sx = ax + direction.x * faceOffset, sy = ay + direction.y * faceOffset, sz = az + direction.z * faceOffset;
        float ex = bx - direction.x * faceOffset, ey = by - direction.y * faceOffset, ez = bz - direction.z * faceOffset;
        Vector3f reference = Math.abs(direction.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f lateral = new Vector3f(direction).cross(reference).normalize().mul(lane);
        drawBeam(out, matrix, sx + lateral.x, sy + lateral.y, sz + lateral.z,
                ex + lateral.x, ey + lateral.y, ez + lateral.z, color);
    }

    private static void drawSlotBeam(VertexConsumer out, Matrix4f matrix,
                                     float ax, float ay, float az, float bx, float by, float bz,
                                     int color, float lane) {
        Vector3f direction = new Vector3f(bx - ax, by - ay, bz - az);
        if (direction.lengthSquared() < 1.0E-5F) return;
        direction.normalize();
        Vector3f reference = Math.abs(direction.y) < 0.9F
                ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f lateral = new Vector3f(direction).cross(reference).normalize().mul(lane);
        drawBeam(out, matrix,
                ax + lateral.x, ay + lateral.y, az + lateral.z,
                bx + lateral.x, by + lateral.y, bz + lateral.z, color);
    }

    private static void renderMemberBox(PoseStack pose, VertexConsumer lines, BlockPos pos,
                                        int color, double inflate) {
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        LevelRenderer.renderLineBox(pose, lines, new AABB(pos).inflate(inflate),
                red, green, blue, 1.0F);
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
