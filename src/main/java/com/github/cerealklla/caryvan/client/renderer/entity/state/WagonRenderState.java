package com.github.cerealklla.caryvan.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Render state for {@code WagonModel} -- same shape as {@code AnimalCartRenderState}, including {@link #woodType} (per-wood-species texture, added 2026-10-26). */
public final class WagonRenderState extends EntityRenderState {
    public float yRot;
    /**
     * Real bug, 2026-10-26: Wagon/Carriage actually have 4 independent wheels (confirmed against
     * {@code WagonModel}'s own 4 real wheel {@code ModelPart}s and the Wheels-repair recipe's own
     * 4-wheel requirement) -- the old 2-value left/right pair (still correct for Hand Cart/Animal
     * Cart, which really do have 2) silently under-modeled Wagon/Carriage's own wheel-break math.
     * Index order matches {@code WagonModel}'s own real child declaration order exactly: 0 =
     * leftFront, 1 = rightFront, 2 = leftBack, 3 = rightBack.
     */
    public final float[] wheelRotation = new float[4];
    public String woodType;
    /** Health system, 2026-10-26: HP == 0 -- selects the full Broken-Wagon texture variant and tilts the model (see WagonRenderer#submit). */
    public boolean broken;
    /**
     * Real live-test feedback, 2026-10-26: a subtle texture recolor alone ("wheels damaged") read as
     * "nothing happened" -- these instead hide the corresponding wheel {@code ModelPart}s entirely
     * (see {@code WagonModel#setupAnim}), a much more legible "this wheel is gone" signal. Same
     * index order as {@link #wheelRotation}.
     */
    public final boolean[] wheelBroken = new boolean[4];
}
