package dev.whimsyautomation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.UUID;

/** Highlights every loaded endpoint belonging to the currently selected network. */
@EventBusSubscriber(modid = "whimsy_automation", value = Dist.CLIENT)
public final class RemoteTransferOverlay {
    private static CompoundTag snapshot = new CompoundTag();
    private static UUID selectedNetwork;

    private static final int[] COLORS = {
            0xFFFF5555, 0xFFFFAA33, 0xFFFFFF55, 0xFF55FF77,
            0xFF55DDFF, 0xFF6688FF, 0xFFCC66FF, 0xFFFF66CC
    };

    private RemoteTransferOverlay() {}

    public static void applySnapshot(CompoundTag data) {
        snapshot = data == null ? new CompoundTag() : data.copy();
        if (selectedNetwork != null && findSelectedNetwork() == null) selectedNetwork = null;
    }

    public static void selectNetwork(UUID id) {
        selectedNetwork = id;
    }

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

        var pose = event.getPoseStack();
        var camera = event.getCamera();
        var cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        int colorIndex = 0;
        for (int i = 0; i < members.size(); i++) {
            CompoundTag member = members.getCompound(i);
            if (!level.dimension().location().toString().equals(member.getString("Dimension"))) continue;
            BlockPos pos = BlockPos.of(member.getLong("Pos"));
            if (!level.hasChunkAt(pos)) continue;
            int color = COLORS[colorIndex++ % COLORS.length];
            float red = ((color >> 16) & 255) / 255.0F;
            float green = ((color >> 8) & 255) / 255.0F;
            float blue = (color & 255) / 255.0F;
            AABB box = new AABB(pos).inflate(0.003D);
            LevelRenderer.renderLineBox(pose, lines, box, red, green, blue, 1.0F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
