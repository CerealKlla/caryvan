package com.github.cerealklla.caryvan.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Render state for {@code WagonModel} -- same shape as {@code AnimalCartRenderState}, including {@link #woodType} (per-wood-species texture, added 2026-10-26). */
public final class WagonRenderState extends EntityRenderState {
    public float yRot;
    public float wheelRotationLeft;
    public float wheelRotationRight;
    public String woodType;
    /** Health system, 2026-10-26: at least one wheel is broken (but HP > 0) -- selects the "wheels damaged" texture variant. */
    public boolean wheelsDamaged;
    /** Health system, 2026-10-26: HP == 0 -- selects the full Broken-Wagon texture variant (takes priority over {@link #wheelsDamaged}). */
    public boolean broken;
}
