package com.github.cerealklla.caryvan.client.renderer.entity;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.client.renderer.entity.model.HandCartModel;
import com.github.cerealklla.caryvan.client.renderer.entity.state.HandCartRenderState;
import com.github.cerealklla.caryvan.entity.HandCartEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * {@link HandCartEntity}'s real client renderer -- replaces the earlier placeholder floating-chest
 * icon (see this repo's own CLAUDE.md/decisions.md, 2026-10-25) with {@link HandCartModel}'s actual
 * geometry, adapted from NiftyCarts (MIT licensed, see this repo's own CLAUDE.md for the full
 * attribution writeup).
 */
public final class HandCartRenderer extends EntityRenderer<HandCartEntity, HandCartRenderState> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "textures/entity/hand_cart.png");

    private final HandCartModel model;

    public HandCartRenderer(final EntityRendererProvider.Context context) {
        super(context);
        this.model = new HandCartModel(context.bakeLayer(com.github.cerealklla.caryvan.client.ModModelLayers.HAND_CART));
        this.shadowRadius = 0.5F;
    }

    @Override
    public HandCartRenderState createRenderState() {
        return new HandCartRenderState();
    }

    @Override
    public void extractRenderState(final HandCartEntity entity, final HandCartRenderState state, final float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        state.wheelRotationRight = (float) entity.getWheelRotation(0);
        state.wheelRotationLeft = (float) entity.getWheelRotation(1);
        state.hurtTime = entity.getHurtTime() - partialTicks;
        state.damageTaken = entity.getDamage();
        state.forwardDirection = entity.getHurtDir();
    }

    @Override
    public void submit(final HandCartRenderState state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        this.model.setupAnim(state);
        submitNodeCollector.submitModel(this.model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null);
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
