package dev.whimsyautomation.block;

import com.mojang.serialization.MapCodec;
import dev.whimsyautomation.block.entity.BigCapacityChestBlockEntity;
import dev.whimsyautomation.registry.ModBlockEntities;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import dev.whimsyautomation.menu.BigCapacityChestMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A vanilla chest block with the vanilla double-chest state machine and menu behavior.
 * The inventory implementation lives in BigCapacityChestBlockEntity.
 */
public final class BigCapacityChestBlock extends ChestBlock {
    public static final MapCodec<BigCapacityChestBlock> CODEC = MapCodec.unit(BigCapacityChestBlock::new);

    public BigCapacityChestBlock() {
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST), ModBlockEntities.BIG_CAPACITY_CHEST::get);
    }

    @Override
    public MapCodec<? extends ChestBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BigCapacityChestBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (ChestBlock.isChestBlockedAt(level, pos)
                || (state.getValue(TYPE) != ChestType.SINGLE
                && ChestBlock.isChestBlockedAt(level, pos.relative(ChestBlock.getConnectedDirection(state))))) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Container container = ChestBlock.getContainer(this, state, level, pos, false);
        if (container == null) {
            return InteractionResult.PASS;
        }
        int rows = Math.max(3, Math.min(6, container.getContainerSize() / 9));
        MenuProvider provider = new SimpleMenuProvider(
                (id, inventory, viewer) -> new BigCapacityChestMenu(id, inventory, container, rows),
                Component.translatable("container.chest"));
        player.openMenu(provider, buffer -> buffer.writeBlockPos(pos));
        player.awardStat(Stats.CUSTOM.get(Stats.OPEN_CHEST));
        PiglinAi.angerNearbyPiglins(player, true);
        return InteractionResult.CONSUME;
    }
}
