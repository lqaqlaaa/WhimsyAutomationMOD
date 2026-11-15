package dev.whimsyautomation.camouflage;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Implemented by every machine that can display a camouflage block.
 * The stored state is visual-only; it never replaces the machine's real BlockState.
 */
public interface CamouflageableBlockEntity {
    BlockState getCamouflageState();
    void setCamouflageState(BlockState state);

    default boolean hasCamouflage() {
        BlockState state = getCamouflageState();
        return state != null && !state.isAir();
    }
}
