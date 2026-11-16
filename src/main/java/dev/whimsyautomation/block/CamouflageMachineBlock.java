package dev.whimsyautomation.block;

import dev.whimsyautomation.camouflage.CamouflageBlockState;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Shared BlockState definition for camouflage-capable machines.
 * BlockState.canOcclude() has no world/position parameter, so its effective
 * value must be represented by a persisted property on the machine state.
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
}
