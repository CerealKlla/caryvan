package com.github.cerealklla.caryvan.client.renderer.entity;

import com.github.cerealklla.caryvan.client.renderer.entity.state.HandCartRenderState;
import com.github.cerealklla.caryvan.entity.HandCartEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * {@link HandCartEntity}'s client renderer -- a floating {@link Items#CHEST} icon, same
 * "no dedicated model/texture yet, render a real vanilla item so the entity is at least visible and
 * findable" placeholder {@code bag.client.LootBagRenderer} (Yconomics) already established in this
 * suite. The design document flags a real mesh/texture model as the single piece of this mod most
 * likely to need genuine new engineering (see this repo's CLAUDE.md/design-document.md §3) -- this
 * is deliberately not that, just enough to see where a Hand Cart actually is and confirm the physics
 * underneath it work. Also what keeps the entity from crashing the client the instant one spawns in
 * view at all -- every custom {@code Entity} needs *some* registered renderer (confirmed the hard
 * way in this exact codebase; see {@code LootBagRenderer}'s own doc).
 */
public final class HandCartRenderer extends EntityRenderer<HandCartEntity, HandCartRenderState> {

    private final ItemModelResolver itemModelResolver;

    public HandCartRenderer(final EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public HandCartRenderState createRenderState() {
        return new HandCartRenderState();
    }

    @Override
    public void extractRenderState(final HandCartEntity entity, final HandCartRenderState state, final float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        // Built fresh each call, not cached -- a static ItemStack field crashed resource-pack
        // loading once already in this exact suite (components not bound yet at class-load time,
        // see LootBagRenderer's own doc) -- cheap enough not to matter.
        this.itemModelResolver.updateForNonLiving(state.icon, new ItemStack(Items.CHEST), ItemDisplayContext.GROUND, entity);
    }

    @Override
    public void submit(final HandCartRenderState state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.3, 0.0);
        state.icon.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, EntityRenderState.NO_OUTLINE);
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
