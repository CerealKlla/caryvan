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
    /** Mirrors {@code WagonRenderer}'s own doc exactly (including the real live-test reversal dropping the fully-grey texture tier). */
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
        for (int i = 0; i < state.doorBroken.length; i++) {
            state.doorBroken[i] = entity.isDoorBroken(i);
        }
        buildLeashRopes(entity, state, partialTicks);
    }

    /**
     * Real user reference diagram, 2026-10-26 (3 green squares = lead/team-left/team-right, orange
     * rounded rect = the Carriage): lead-to-team-left and lead-to-team-right already render for free
     * via vanilla's own generic per-{@code Leashable} rope rendering (both team horses really are
     * leashed to the lead, see {@code CarriageEntity#syncOneTeamLeash}) -- those two need no code
     * here at all. What the diagram adds on top, and what this method now actually builds: each team
     * horse individually tethered down to the carriage (not both of the carriage's own quad-offset
     * ropes converging on whichever single horse {@code CarriageEntity#syncWagonPullTarget}'s own
     * one-holder preference happened to pick as the real vanilla pull target), plus a genuinely new
     * direct team-left-to-team-right rope -- not a real vanilla leash relationship (both are actually
     * leashed to the lead, not each other), so nothing renders it automatically; built the exact same
     * custom-{@link EntityRenderState.LeashState} way the carriage's own ropes always have been.
     */
    private void buildLeashRopes(final CarriageEntity entity, final CarriageRenderState state, final float partialTicks) {
        final Entity holder = entity.getLeashHolder();
        if (holder == null) {
            state.leashStates = null;
            return;
        }
        final float bodyRotRad = (float) Math.toRadians(entity.getPreciseBodyRotation(partialTicks));
        final Vec3 entityPos = entity.getPosition(partialTicks);
        final int startBlockLight = getBlockLightLevel(entity, partialTicks);
        final int startSkyLight = getSkyLightLevel(entity, partialTicks);

        final List<EntityRenderState.LeashState> ropes = new ArrayList<>(3);
        final Vec3[] offsets = entity.getQuadLeashOffsets();
        final Vec3 leftCarriageOffset = entityPos.add(offsets[0].yRot(-bodyRotRad));
        final Vec3 rightCarriageOffset = entityPos.add(offsets[1].yRot(-bodyRotRad));

        final Entity teamLeft = entity.getTeamLeftMount();
        final Entity teamRight = entity.getTeamRightMount();
        final boolean teamLeftAlive = teamLeft != null && teamLeft.isAlive();
        final boolean teamRightAlive = teamRight != null && teamRight.isAlive();

        if (teamLeftAlive && teamRightAlive) {
            addRope(ropes, leftCarriageOffset, startBlockLight, startSkyLight, teamLeft, partialTicks);
            addRope(ropes, rightCarriageOffset, startBlockLight, startSkyLight, teamRight, partialTicks);
            addRope(ropes, teamLeft.getRopeHoldPosition(partialTicks), getBlockLightLevel(teamLeft, partialTicks),
                    getSkyLightLevel(teamLeft, partialTicks), teamRight, partialTicks);
        } else {
            // Fallback -- fewer than 2 live team horses (e.g. mid-respawn, or one was lost picking up
            // a Broken carriage): both carriage-side ropes converge on whichever single mount vanilla's
            // own one-holder model actually picked, same behavior this method always had.
            addRope(ropes, leftCarriageOffset, startBlockLight, startSkyLight, holder, partialTicks);
            addRope(ropes, rightCarriageOffset, startBlockLight, startSkyLight, holder, partialTicks);
        }
        state.leashStates = ropes;
    }

    private void addRope(final List<EntityRenderState.LeashState> ropes, final Vec3 start, final int startBlockLight,
            final int startSkyLight, final Entity endEntity, final float partialTicks) {
        final EntityRenderState.LeashState rope = new EntityRenderState.LeashState();
        rope.start = start;
        rope.end = endEntity.getRopeHoldPosition(partialTicks);
        rope.startBlockLight = startBlockLight;
        rope.endBlockLight = getBlockLightLevel(endEntity, partialTicks);
        rope.startSkyLight = startSkyLight;
        rope.endSkyLight = getSkyLightLevel(endEntity, partialTicks);
        ropes.add(rope);
    }

    // Not EntityRenderer#getBlockLightLevel -- that protected helper is typed to this renderer's own
    // T (CarriageEntity), not a general Entity, so it can't be called for an arbitrary team mount.
    // Queries the level's own light engine directly instead, same approach #getSkyLightLevel already
    // used (this method always had to hand-roll sky light for exactly this reason).
    private static int getBlockLightLevel(final Entity entity, final float partialTicks) {
        return entity.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(entity.getEyePosition(partialTicks)));
    }

    private static int getSkyLightLevel(final Entity entity, final float partialTicks) {
        return entity.level().getBrightness(LightLayer.SKY, BlockPos.containing(entity.getEyePosition(partialTicks)));
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
        final Map<String, Identifier> textureSet = anyWheelBroken(state) ? WHEELS_DAMAGED_TEXTURES : TEXTURES;
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
