package dev.whimsyautomation.camouflage;

import com.mojang.serialization.MapCodec;
import dev.whimsyautomation.block.CamouflageMachineBlock;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * State-level occlusion bridge for camouflage machines.
 *
 * Position-dependent face shapes and destroy speed are delegated to the
 * current camouflage state. The occlusion cache must not retain pair results
 * because the effective face shape is stored in block-entity model data.
 */
public final class CamouflageBlockState extends BlockState {
    private static final ThreadLocal<List<Block.BlockStatePairKey>> KEYS_TO_REMOVE =
            ThreadLocal.withInitial(ArrayList::new);

    public CamouflageBlockState(Block block,
                                Reference2ObjectArrayMap<Property<?>, Comparable<?>> values,
                                MapCodec<BlockState> codec) {
        super(block, values, codec);
    }

    @Override
    public boolean canOcclude() {
        removeAllKeys();
        return getValue(CamouflageMachineBlock.CAMOUFLAGE_SOLID);
    }

    @Override
    public boolean skipRendering(BlockState otherState, Direction direction) {
        removeAllKeys();
        return super.skipRendering(otherState, direction);
    }

    @Override
    public VoxelShape getFaceOcclusionShape(BlockGetter level, BlockPos pos, Direction direction) {
        addOcclusionCacheKeysForRemoval(level, pos, direction);
        BlockState camouflage = CamouflageOcclusion.getCamouflageState(level, pos);
        if (camouflage != null && !camouflage.isAir()
                && !(camouflage.getBlock() instanceof CamouflageMachineBlock)) {
            return camouflage.getFaceOcclusionShape(CamouflageOcclusion.createView(level, pos, camouflage), pos, direction);
        }
        return super.getFaceOcclusionShape(level, pos, direction);
    }

    @Override
    public float getDestroySpeed(BlockGetter level, BlockPos pos) {
        BlockState camouflage = CamouflageOcclusion.getCamouflageState(level, pos);
        if (camouflage == null || camouflage.isAir()
                || camouflage.getBlock() instanceof CamouflageMachineBlock) {
            return super.getDestroySpeed(level, pos);
        }
        float speed = camouflage.getDestroySpeed(CamouflageOcclusion.createView(level, pos, camouflage), pos);
        // Keep normally unbreakable disguises from making the machine unbreakable.
        return Math.max(0.5F, Math.min(5.0F, speed));
    }

    @Override
    public boolean emissiveRendering(BlockGetter level, BlockPos pos) {
        BlockState camouflage = CamouflageOcclusion.getCamouflageState(level, pos);
        if (camouflage == null || camouflage.isAir()
                || camouflage.getBlock() instanceof CamouflageMachineBlock) {
            return super.emissiveRendering(level, pos);
        }
        return camouflage.emissiveRendering(CamouflageOcclusion.createView(level, pos, camouflage), pos);
    }

    @Override
    public boolean useShapeForLightOcclusion() {
        return true;
    }

    private void addOcclusionCacheKeysForRemoval(BlockGetter level, BlockPos pos, Direction direction) {
        BlockState other = level.getBlockState(pos.relative(direction));
        KEYS_TO_REMOVE.get().add(new Block.BlockStatePairKey(other, this.asState(), direction.getOpposite()));
        KEYS_TO_REMOVE.get().add(new Block.BlockStatePairKey(this.asState(), other, direction));
    }

    private static void removeAllKeys() {
        List<Block.BlockStatePairKey> keys = KEYS_TO_REMOVE.get();
        Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey> cache = Block.OCCLUSION_CACHE.get();
        for (Block.BlockStatePairKey key : keys) cache.removeByte(key);
        keys.clear();
    }
}
