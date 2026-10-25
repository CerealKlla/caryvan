package com.github.cerealklla.caryvan.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/** Render state for {@link com.github.cerealklla.caryvan.entity.HandCartEntity} -- see {@code HandCartRenderer}. */
public class HandCartRenderState extends EntityRenderState {
    public final ItemStackRenderState icon = new ItemStackRenderState();
}
