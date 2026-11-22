package dev.whimsyautomation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.whimsyautomation.block.entity.BigCapacityChestBlockEntity;
import dev.whimsyautomation.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the item with Minecraft's own chest block-entity renderer.
 * The temporary block entity MUST use this mod's registered chest block state:
 * its BlockEntityType is only valid for BIG_CAPACITY_CHEST, not minecraft:chest.
 */
public final class BigCapacityChestItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher dispatcher;
    private final BigCapacityChestBlockEntity chest;

    public BigCapacityChestItemRenderer() {
        this(Minecraft.getInstance());
    }

    private BigCapacityChestItemRenderer(Minecraft minecraft) {
        super(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
        this.dispatcher = minecraft.getBlockEntityRenderDispatcher();
        this.chest = new BigCapacityChestBlockEntity(
                BlockPos.ZERO,
                ModBlocks.BIG_CAPACITY_CHEST.get().defaultBlockState()
        );
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        dispatcher.renderItem(chest, poseStack, bufferSource, packedLight, packedOverlay);
    }
}