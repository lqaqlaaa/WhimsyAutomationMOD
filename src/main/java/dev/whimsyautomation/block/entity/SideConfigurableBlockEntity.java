package dev.whimsyautomation.block.entity;

import net.minecraft.core.Direction;

/** Client rendering preference and side-mode access owned by each machine entity. */
public interface SideConfigurableBlockEntity {
    int getSideMode(Direction side);
    boolean isFaceColorDisplayEnabled();
    void setFaceColorDisplayEnabled(boolean enabled);
    void setSideModeForDisplay(Direction side, int mode);

    default boolean shouldShowFaceColor(Direction side) {
        if (!isFaceColorDisplayEnabled()) return false;
        boolean anyConfigured = false;
        for (Direction direction : Direction.values()) {
            if (getSideMode(direction) != 0) { anyConfigured = true; break; }
        }
        return !anyConfigured || getSideMode(side) != 0;
    }
}
