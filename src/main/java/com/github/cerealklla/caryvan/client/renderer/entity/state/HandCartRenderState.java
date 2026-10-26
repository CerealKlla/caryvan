package com.github.cerealklla.caryvan.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Render state for the real {@code HandCartModel} (replaces the earlier placeholder floating-icon
 * state once used here) -- {@link net.minecraft.client.model.EntityModel} is generic over a render
 * state type in this environment (not the live entity), so everything {@code setupAnim} needs
 * (wheel spin, the hit/damage shake wobble) has to be snapshotted here during
 * {@code HandCartRenderer#extractRenderState} rather than read from the entity directly.
 */
public final class HandCartRenderState extends EntityRenderState {
    public float yRot;
    public float wheelRotationLeft;
    public float wheelRotationRight;
    public float hurtTime;
    public float damageTaken;
    public int forwardDirection = 1;
    public String woodType = "oak";
}
