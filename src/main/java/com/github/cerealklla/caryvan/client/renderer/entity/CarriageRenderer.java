package com.github.cerealklla.caryvan.client.renderer.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.client.ModModelLayers;
import com.github.cerealklla.caryvan.client.renderer.entity.model.CarriageModel;
import com.github.cerealklla.caryvan.client.renderer.entity.state.CarriageRenderState;
import com.github.cerealklla.caryvan.entity.CarriageEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.Vec3;

/**
 * {@link CarriageEntity}'s renderer -- a direct copy of {@code WagonRenderer}'s own shape (same
 * per-{@link WoodType} texture map, same manually-built two-rope leash rendering, see that class's
 * own doc for the full writeup of why both exist in this form), just pointed at {@link CarriageModel}
 * and the Carriage's own per-wood textures.
 */
public final class CarriageRenderer extends EntityRenderer<CarriageEntity, CarriageRenderState> {

    private static final Map<String, Identifier> TEXTURES = WoodType.values()
            .collect(Collectors.toMap(WoodType::name,
                    type -> Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "textures/entity/carriage_" + type.name() + ".png")));
    /** Health system, 2026-10-26 -- mirrors {@code WagonRenderer}'s own doc exactly. */
    private static final Map<String, Identifier> BROKEN_TEXTURES = WoodType.values()
            .collect(Collectors.toMap(WoodType::name,
                    type -> Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "textures/entity/carriage_" + type.name() + "_broken.png")));
    /** Mirrors {@code WagonRenderer}'s own doc exactly. */
    private static final Map<String, Identifier> WHEELS_DAMAGED_TEXTURES = WoodType.values()
            .collect(Collectors.toMap(WoodType::name,
                    type -> Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "textures/entity/carriage_" + type.name() + "_wheels_damaged.png")));

    private final CarriageModel model;

    public CarriageRenderer(final EntityRendererProvider.Context context) {
        super(context);
        this.model = new CarriageModel(context.bakeLayer(ModModelLayers.CARRIAGE));
        this.shadowRadius = 1.6F;
    }

    @Override
    public CarriageRenderState createRenderState() {
        return new CarriageRenderState();
    }

    @Override
    public void extractRenderState(final CarriageEntity entity, final CarriageRenderState state, final float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        entity.tickWheelsClient();
        for (int i = 0; i < state.wheelRotation.length; i++) {
            state.wheelRotation[i] = (float) entity.getWheelRotation(i);
            state.wheelBroken[i] = entity.isWheelBroken(i);
        }
        state.woodType = entity.getWoodType().name();
        state.broken = entity.isBroken();
        buildLeashRopes(entity, state, partialTicks);
    }

    private void buildLeashRopes(final CarriageEntity entity, final CarriageRenderState state, final float partialTicks) {
        final Entity holder = entity.getLeashHolder();
        if (holder == null) {
            state.leashStates = null;
            return;
        }
        final float bodyRotRad = (float) Math.toRadians(entity.getPreciseBodyRotation(partialTicks));
        final Vec3 entityPos = entity.getPosition(partialTicks);
        final Vec3 ropeHoldPos = holder.getRopeHoldPosition(partialTicks);
        final int startBlockLight = this.getBlockLightLevel(entity, BlockPos.containing(entity.getEyePosition(partialTicks)));
        final int endBlockLight = this.getBlockLightLevel(entity, BlockPos.containing(holder.getEyePosition(partialTicks)));
        final int startSkyLight = entity.level().getBrightness(LightLayer.SKY, BlockPos.containing(entity.getEyePosition(partialTicks)));
        final int endSkyLight = entity.level().getBrightness(LightLayer.SKY, BlockPos.containing(holder.getEyePosition(partialTicks)));

        final List<EntityRenderState.LeashState> ropes = new ArrayList<>(2);
        for (final Vec3 localOffset : entity.getQuadLeashOffsets()) {
            final EntityRenderState.LeashState rope = new EntityRenderState.LeashState();
            rope.offset = localOffset.yRot(-bodyRotRad);
            rope.start = entityPos.add(rope.offset);
            rope.end = ropeHoldPos;
            rope.startBlockLight = startBlockLight;
            rope.endBlockLight = endBlockLight;
            rope.startSkyLight = startSkyLight;
            rope.endSkyLight = endSkyLight;
            ropes.add(rope);
        }
        state.leashStates = ropes;
    }

    /** Mirrors {@code WagonRenderer}'s own doc exactly. */
    // Mirrors WagonRenderer's own doc exactly -- single source of truth lives on CarriageModel now.
    private static final float BROKEN_TILT_DEGREES = com.github.cerealklla.caryvan.client.renderer.entity.model.CarriageModel.BROKEN_TILT_DEGREES;

    @Override
    public void submit(final CarriageRenderState state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        if (state.broken) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(BROKEN_TILT_DEGREES));
        }
        this.model.setupAnim(state);
        final Map<String, Identifier> textureSet = state.broken ? BROKEN_TEXTURES : anyWheelBroken(state) ? WHEELS_DAMAGED_TEXTURES : TEXTURES;
        final Identifier texture = textureSet.getOrDefault(state.woodType, textureSet.get(WoodType.OAK.name()));
        final RenderType renderType = this.model.renderType(texture);
        submitNodeCollector.submitModel(this.model, state, poseStack, renderType, state.lightCoords,
                OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null);
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    private static boolean anyWheelBroken(final CarriageRenderState state) {
        for (final boolean broken : state.wheelBroken) {
            if (broken) {
                return true;
            }
        }
        return false;
    }
}
