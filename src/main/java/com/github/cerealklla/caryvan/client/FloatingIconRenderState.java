package com.github.cerealklla.caryvan.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/**
 * Carries the one extra piece a {@link FloatingIconRenderer} needs beyond the base state: the
 * resolved floating icon's own render state. Direct copy of Lyfe's own identical class (its own
 * `client.FloatingIconRenderState`) -- generic, no Lyfe-specific logic, copied rather than shared
 * across an optional cross-mod dependency, same "no shared code between independent renderers"
 * precedent this session already used for CaryvanImager relative to BluepryntImager.
 */
public class FloatingIconRenderState extends BlockEntityRenderState {
    public final ItemStackRenderState itemRenderState = new ItemStackRenderState();
    public double yRotDegrees;
}
