package com.github.cerealklla.caryvan.client.renderer.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.client.ModModelLayers;
import com.github.cerealklla.caryvan.client.renderer.entity.model.WagonModel;
import com.github.cerealklla.caryvan.client.renderer.entity.state.WagonRenderState;
import com.github.cerealklla.caryvan.entity.WagonEntity;
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
 * {@link WagonEntity}'s renderer -- real 8-spoke-wheel/open-bed geometry ({@link WagonModel}'s own
 * doc), one baked texture per {@link WoodType} (added 2026-10-26, same shape
 * {@code AnimalCartRenderer} already established -- explicit user request, "make sure all the wood
 * variants exist for the wagon," since this was the one cart type left on a single fixed texture).
 * Same proven submission shape every other renderer in this mod already uses.
 *
 * <p><b>Two manually-built leash ropes, not vanilla's generic one</b> -- real user request,
 * 2026-10-25: "attach two ropes just like leads to the front left and right corner of the wagon."
 * Confirmed via the real decompiled bytecode that vanilla's own generic leash rendering (which
 * {@link EntityRenderer}'s base {@code extractRenderState} already runs for any {@link
 * net.minecraft.world.entity.Leashable}) only ever draws **one** rope unless *both* sides of the
 * connection opt into quad-leash (and even then, always exactly four, never two) -- a plain
 * {@code Horse} never does, so {@code WagonEntity#supportQuadLeash} alone was never enough (see
 * decisions.md's own writeup for the full research trail). {@link #buildLeashRopes} replaces
 * whatever the generic pass already populated with two manually-built {@code LeashState}s instead,
 * reusing the exact same math vanilla's own single-rope path uses (confirmed via its own decompiled
 * bytecode) just applied to {@link WagonEntity#getQuadLeashOffsets()}'s two corner points instead of
 * the single default {@code getLeashOffset()}, both converging on the holder's own real {@link
 * Entity#getRopeHoldPosition}. {@code EntityRenderer#submit()}'s own generic code then draws
 * whatever ends up in {@code state.leashStates} with zero further changes needed here.
 */
public final class WagonRenderer extends EntityRenderer<WagonEntity, WagonRenderState> {

    // One baked texture per WoodType, 2026-10-26 (real user request: "make sure all the wood
    // variants exist for the wagon") -- same shape AnimalCartRenderer already established.
    private static final Map<String, Identifier> TEXTURES = WoodType.values()
            .collect(Collectors.toMap(WoodType::name,
                    type -> Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "textures/entity/wagon_" + type.name() + ".png")));

    // Health system, 2026-10-26: a fully desaturated/darkened variant for the complete Broken-Wagon
    // body swap at 0 HP, same "desaturate = damaged" convention as the Broken Wagon item icon.
    // (The earlier "wheels damaged" texture-only variant was dropped, 2026-10-26, after real
    // live-test feedback that a subtle recolor read as "nothing visibly changed" -- a broken wheel
    // now hides that wheel's own ModelPart entirely instead, see WagonModel#setupAnim.)
    private static final Map<String, Identifier> BROKEN_TEXTURES = WoodType.values()
            .collect(Collectors.toMap(WoodType::name,
                    type -> Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "textures/entity/wagon_" + type.name() + "_broken.png")));

    private final WagonModel model;

    public WagonRenderer(final EntityRendererProvider.Context context) {
        super(context);
        this.model = new WagonModel(context.bakeLayer(ModModelLayers.WAGON));
        this.shadowRadius = 1.3F;
    }

    @Override
    public WagonRenderState createRenderState() {
        return new WagonRenderState();
    }

    @Override
    public void extractRenderState(final WagonEntity entity, final WagonRenderState state, final float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        entity.tickWheelsClient();
        state.wheelRotationRight = (float) entity.getWheelRotation(0);
        state.wheelRotationLeft = (float) entity.getWheelRotation(1);
        state.woodType = entity.getWoodType().name();
        state.broken = entity.isBroken();
        // Index mapping matches the wheelRotation assignment just above (0 -> right, 1 -> left).
        state.wheelBrokenRight = entity.isWheelBroken(0);
        state.wheelBrokenLeft = entity.isWheelBroken(1);
        buildLeashRopes(entity, state, partialTicks);
    }

    /** See this class's own doc for why this exists instead of trusting vanilla's generic single-rope pass. */
    private void buildLeashRopes(final WagonEntity entity, final WagonRenderState state, final float partialTicks) {
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

    // Real live-test feedback, 2026-10-26: at 0 HP "the wagon stays upright" and reads as
    // underwhelming -- a visible list/tilt to one side, on top of the full grey texture and the
    // now-missing wheel geometry, reads much more clearly as "broken down," not just "repainted."
    private static final float BROKEN_TILT_DEGREES = 12.0F;

    @Override
    public void submit(final WagonRenderState state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        if (state.broken) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(BROKEN_TILT_DEGREES));
        }
        this.model.setupAnim(state);
        final Map<String, Identifier> textureSet = state.broken ? BROKEN_TEXTURES : TEXTURES;
        final Identifier texture = textureSet.getOrDefault(state.woodType, textureSet.get(WoodType.OAK.name()));
        final RenderType renderType = this.model.renderType(texture);
        submitNodeCollector.submitModel(this.model, state, poseStack, renderType, state.lightCoords,
                OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null);
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
