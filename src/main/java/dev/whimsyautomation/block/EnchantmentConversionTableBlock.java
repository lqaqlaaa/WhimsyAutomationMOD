package dev.whimsyautomation.block;

import com.mojang.serialization.MapCodec;
import dev.whimsyautomation.block.entity.EnchantmentConversionTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class EnchantmentConversionTableBlock extends BaseEntityBlock {
    public static final MapCodec<EnchantmentConversionTableBlock> CODEC = MapCodec.unit(EnchantmentConversionTableBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public EnchantmentConversionTableBlock() {
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.LECTERN));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnchantmentConversionTableBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof EnchantmentConversionTableBlockEntity provider) {
            serverPlayer.openMenu(provider, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof EnchantmentConversionTableBlockEntity machine) {
            machine.dropAllContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Reuse the corresponding vanilla block state for all physical and culling
     * shapes. The machine keeps its own block entity and interaction logic,
     * but does not invent a second set of block geometry rules.
     */
    private BlockState vanillaShapeState(BlockState state) {
        return Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, state.getValue(FACING));
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return vanillaShapeState(state).getShape(level, pos, context);
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return vanillaShapeState(state).getCollisionShape(level, pos, context);
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getOcclusionShape(
            BlockState state, BlockGetter level, BlockPos pos) {
        return vanillaShapeState(state).getOcclusionShape(level, pos);
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getBlockSupportShape(
            BlockState state, BlockGetter level, BlockPos pos) {
        return vanillaShapeState(state).getBlockSupportShape(level, pos);
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getInteractionShape(
            BlockState state, BlockGetter level, BlockPos pos) {
        return vanillaShapeState(state).getInteractionShape(level, pos);
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getVisualShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return vanillaShapeState(state).getVisualShape(level, pos, context);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return vanillaShapeState(state).getLightBlock(level, pos);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return vanillaShapeState(state).useShapeForLightOcclusion();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return vanillaShapeState(state).propagatesSkylightDown(level, pos);
    }

}
