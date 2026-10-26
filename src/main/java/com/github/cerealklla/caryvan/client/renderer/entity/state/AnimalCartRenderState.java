package com.github.cerealklla.caryvan.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Render state for {@code AnimalCartModel} -- same shape as {@code HandCartRenderState} minus the
 * hit/damage shake wobble (not wired up for this placeholder model yet).
 */
public final class AnimalCartRenderState extends EntityRenderState {
    public float yRot;
    public float wheelRotationLeft;
    public float wheelRotationRight;
    public String woodType = "oak";
}
