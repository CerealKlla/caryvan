package com.github.cerealklla.caryvan.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Render state for {@code CarriageModel} -- same shape as {@code WagonRenderState}. */
public final class CarriageRenderState extends EntityRenderState {
    public float yRot;
    /** Mirrors {@code WagonRenderState#wheelRotation}'s own doc exactly (same 4-wheel index order). */
    public final float[] wheelRotation = new float[4];
    public String woodType;
    /** Health system, 2026-10-26: mirrors {@code WagonRenderState#broken}. */
    public boolean broken;
    /** Health system, 2026-10-26: mirrors {@code WagonRenderState#wheelBroken}. */
    public final boolean[] wheelBroken = new boolean[4];
    /** Carriage redesign Phase 3, 2026-10-26: 0 = left door, 1 = right door -- entirely separate from {@link #wheelBroken}/{@link #broken} (doors don't affect the cart's own HP at all). */
    public final boolean[] doorBroken = new boolean[2];
}
