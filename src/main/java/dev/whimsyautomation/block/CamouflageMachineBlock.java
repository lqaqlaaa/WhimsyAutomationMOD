package dev.whimsyautomation.block;

import dev.whimsyautomation.camouflage.CamouflageBlockState;
import dev.whimsyautomation.camouflage.CamouflageOcclusion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Shared behavior for camouflage-capable machines.
 *
 * The machine remains the real world block, but all position-dependent shape
 * and light queries are evaluated against a read-only view that exposes the
 * disguised state at the machine position. This mirrors SecretRooms'
 * SecretBaseBlock delegation instead of trying to repair each model separately.
 */
public abstract class CamouflageMachineBlock extends BaseEntityBlock {
    public static final BooleanProperty CAMOUFLAGE_SOLID = BooleanProperty.create("camouflage_solid");

    private final StateDefinition<Block, BlockState> camouflageStateDefinition;

    protected CamouflageMachineBlock(Properties properties) {
        super(properties);
        StateDefinition.Builder<Block, BlockState> builder = new StateDefinition.Builder<>(this);
        this.createBlockStateDefinition(builder);
        this.camouflageStateDefinition = builder.create(Block::defaultBlockState, CamouflageBlockState::new);
        this.registerDefaultState(this.camouflageStateDefinition.any().setValue(CAMOUFLAGE_SOLID, false));
    }

    @Override
    public StateDefinition<Block, BlockState> getStateDefinition() {
        return camouflageStateDefinition;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CAMOUFLAGE_SOLID);
    }

    private static BlockState getTarget(BlockGetter level, BlockPos pos) {
        BlockState target = CamouflageOcclusion.getCamouflageState(level, pos);
        if (target == null || target.isAir() || target.getBlock() instanceof CamouflageMachineBlock) return null;
        return target;
    }

    private static <T> T delegate(BlockGetter level, BlockPos pos,
                                  BiFunction<BlockState, BlockGetter, T> operation,
                                  Supplier<T> fallback) {
        BlockState target = getTarget(level, pos);
        if (target == null) return fallback.get();
        BlockGetter view = CamouflageOcclusion.createView(level, pos, target);
        return operation.apply(target, view);
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return delegate(level, pos, (target, view) -> target.getShadeBrightness(view, pos),
                () -> super.getShadeBrightness(state, level, pos));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return delegate(level, pos, (target, view) -> target.getShape(view, pos, context),
                () -> super.getShape(state, level, pos, context));
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return delegate(level, pos, (target, view) -> target.getBlockSupportShape(view, pos),
                () -> super.getBlockSupportShape(state, level, pos));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return delegate(level, pos, (target, view) -> target.getCollisionShape(view, pos, context),
                () -> super.getCollisionShape(state, level, pos, context));
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return delegate(level, pos, (target, view) -> target.getVisualShape(view, pos, context),
                () -> super.getVisualShape(state, level, pos, context));
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return delegate(level, pos, (target, view) -> target.getInteractionShape(view, pos),
                () -> super.getInteractionShape(state, level, pos));
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return CamouflageOcclusion.getShape(level, pos);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return delegate(level, pos, (target, view) -> target.getLightEmission(view, pos),
                () -> super.getLightEmission(state, level, pos));
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return delegate(level, pos, (target, view) -> target.getLightBlock(view, pos),
                () -> super.getLightBlock(state, level, pos));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return delegate(level, pos, (target, view) -> target.propagatesSkylightDown(view, pos),
                () -> super.propagatesSkylightDown(state, level, pos));
    }

    @Override
    public float getFriction(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        BlockState target = getTarget(level, pos);
        return target == null ? super.getFriction(state, level, pos, entity)
                : target.getFriction(level, pos, entity);
    }
}
