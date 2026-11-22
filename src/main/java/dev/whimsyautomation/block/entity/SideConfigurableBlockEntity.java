package dev.whimsyautomation.block.entity;

import net.minecraft.core.Direction;

/** Exposes the six persisted automation-side modes to client rendering. */
public interface SideConfigurableBlockEntity {
    int getSideMode(Direction side);
}
