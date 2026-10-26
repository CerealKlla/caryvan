package com.github.cerealklla.caryvan.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Render state for {@code CarriageModel} -- same shape as {@code WagonRenderState}. */
public final class CarriageRenderState extends EntityRenderState {
    public float yRot;
    public float wheelRotationLeft;
    public float wheelRotationRight;
    public String woodType;
}
