package com.github.cerealklla.caryvan.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Render state for {@code WagonModel} -- same shape as {@code AnimalCartRenderState}, including {@link #woodType} (per-wood-species texture, added 2026-10-26). */
public final class WagonRenderState extends EntityRenderState {
    public float yRot;
    public float wheelRotationLeft;
    public float wheelRotationRight;
    public String woodType;
    /** Health system, 2026-10-26: HP == 0 -- selects the full Broken-Wagon texture variant and tilts the model (see WagonRenderer#submit). */
    public boolean broken;
    /**
     * Real live-test feedback, 2026-10-26: a subtle texture recolor alone ("wheels damaged") read as
     * "nothing happened" -- these instead hide the corresponding wheel {@code ModelPart}s entirely
     * (see {@code WagonModel#setupAnim}), a much more legible "this wheel is gone" signal, for each
     * independently-tracked wheel ({@code CartWheel} index 0 = left, 1 = right -- see {@code
     * AbstractDamageableDrawnEntity#isWheelBroken(int)}).
     */
    public boolean wheelBrokenLeft;
    public boolean wheelBrokenRight;
}
