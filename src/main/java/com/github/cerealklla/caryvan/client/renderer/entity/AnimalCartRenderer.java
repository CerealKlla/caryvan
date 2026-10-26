package com.github.cerealklla.caryvan.client.renderer.entity;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.client.ModModelLayers;
import com.github.cerealklla.caryvan.client.renderer.entity.model.AnimalCartModel;
import com.github.cerealklla.caryvan.client.renderer.entity.state.AnimalCartRenderState;
import com.github.cerealklla.caryvan.entity.AnimalCartEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.WoodType;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link AnimalCartEntity}'s renderer -- same submission shape {@code HandCartRenderer} already
 * proved out (real {@code InterpolationHandler}/{@code getChild("root")} gotchas already solved
 * there, nothing new needed here). {@code shadowRadius} raised to match AstikorCarts' own real
 * {@code AnimalCartRenderer} (1.0F) now that the model is the real, larger wagon-bed geometry
 * instead of the earlier placeholder box. One baked texture per {@link WoodType} (2026-10-25),
 * same shape as {@code HandCartRenderer}.
 */
public final class AnimalCartRenderer extends EntityRenderer<AnimalCartEntity, AnimalCartRenderState> {

    private static final Map<String, Identifier> TEXTURES = WoodType.values()
            .collect(Collectors.toMap(WoodType::name,
                    type -> Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "textures/entity/animal_cart_" + type.name() + ".png")));

    private final AnimalCartModel model;

    public AnimalCartRenderer(final EntityRendererProvider.Context context) {
        super(context);
        this.model = new AnimalCartModel(context.bakeLayer(ModModelLayers.ANIMAL_CART));
        this.shadowRadius = 1.0F;
    }

    @Override
    public AnimalCartRenderState createRenderState() {
        return new AnimalCartRenderState();
    }

    @Override
    public void extractRenderState(final AnimalCartEntity entity, final AnimalCartRenderState state, final float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        entity.tickWheelsClient();
        state.wheelRotationRight = (float) entity.getWheelRotation(0);
        state.wheelRotationLeft = (float) entity.getWheelRotation(1);
        state.woodType = entity.getWoodType().name();
    }

    @Override
    public void submit(final AnimalCartRenderState state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        this.model.setupAnim(state);
        final Identifier texture = TEXTURES.getOrDefault(state.woodType, TEXTURES.get(WoodType.OAK.name()));
        final RenderType renderType = this.model.renderType(texture);
        submitNodeCollector.submitModel(this.model, state, poseStack, renderType, state.lightCoords,
                OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null);
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
