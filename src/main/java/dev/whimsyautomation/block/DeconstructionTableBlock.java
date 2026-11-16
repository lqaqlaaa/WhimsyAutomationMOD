package dev.whimsyautomation.block;

import com.mojang.serialization.MapCodec;
import dev.whimsyautomation.block.entity.DeconstructionTableBlockEntity;
import dev.whimsyautomation.camouflage.CamouflageStateUpdater;
import dev.whimsyautomation.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;

public final class DeconstructionTableBlock extends BaseEntityBlock {
    public static final MapCodec<DeconstructionTableBlock> CODEC = MapCodec.unit(DeconstructionTableBlock::new);
    public DeconstructionTableBlock() { super(BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops()); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DeconstructionTableBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                              LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        CamouflageStateUpdater.updateNeighbor(level, pos, direction, neighborState, neighborPos);
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        CamouflageStateUpdater.updateAllNeighbors(level, pos);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof DeconstructionTableBlockEntity provider) serverPlayer.openMenu(provider, pos);
        return InteractionResult.SUCCESS;
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DeconstructionTableBlockEntity machine) {
            machine.dropCamouflage(); machine.dropAllContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.DECONSTRUCTION_TABLE.get(), DeconstructionTableBlockEntity::serverTick);
    }
}
