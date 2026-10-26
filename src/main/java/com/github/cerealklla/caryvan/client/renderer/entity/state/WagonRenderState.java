package com.github.cerealklla.caryvan.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Render state for {@code WagonModel} -- same shape as {@code AnimalCartRenderState}, including {@link #woodType} (per-wood-species texture, added 2026-10-26). */
public final class WagonRenderState extends EntityRenderState {
    public float yRot;
    public float wheelRotationLeft;
    public float wheelRotationRight;
    public String woodType;
}
