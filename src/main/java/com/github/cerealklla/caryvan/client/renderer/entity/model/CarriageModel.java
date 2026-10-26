package com.github.cerealklla.caryvan.client.renderer.entity.model;

import com.github.cerealklla.caryvan.client.renderer.entity.state.CarriageRenderState;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Carriage's geometry (design-document.md §8, built 2026-10-26) -- a direct adaptation of
 * {@link WagonModel}, not a fresh design: same real 8-spoke wheel ({@link #wheel}, copied verbatim,
 * same {@code texOffs}/box dims, so the existing wheel-rim-iron texture treatment transfers exactly
 * unchanged) and the same corner-post/side-rail/axle body language, scaled up and extended with a
 * second and third bench row for the interior seats.
 *
 * <p>Real user spec: "It will likely need to be a little longer and wider than the wagon to fit 4
 * inside comfortably." Unlike {@link WagonModel}, where {@code TRACK_SCALE} only widened the wheel
 * track while the body box itself stayed a fixed width (a pre-existing Wagon quirk, not touched
 * here), this model reuses {@link #TRACK_SCALE} for the body's own X-axis dimensions too, so a wider
 * wheel track actually corresponds to a visually wider body.
 */
public final class CarriageModel extends EntityModel<CarriageRenderState> {

    // Same real wheel size as Wagon/Animal Cart -- "appropriate wheels" (real user spec) means
    // correctly-textured and proportioned, not smaller/mismatched.
    private static final float WHEEL_RADIUS = 11.0F;

    // Bigger than WagonModel's own 1.5/1.1 -- real user spec, "a little longer and wider... to fit 4
    // inside comfortably." Untested placeholder, like every one of WagonModel's own scale constants
    // started out as before live-test correction.
    private static final float LENGTH_SCALE = 2.2F;
    private static final float TRACK_SCALE = 1.3F;
    // Real live-test feedback, 2026-10-26: "widen the wheel well again, moving them another 5%
    // apart or so" -- a wheel-only multiplier on top of TRACK_SCALE, since TRACK_SCALE itself is
    // deliberately shared with the body's own X-axis dimensions here (see this class's own doc) --
    // widening it directly would also widen the body, which wasn't asked for.
    private static final float WHEEL_TRACK_EXTRA_SCALE = 1.05F;

    private final ModelPart leftWheelFront;
    private final ModelPart rightWheelFront;
    private final ModelPart leftWheelBack;
    private final ModelPart rightWheelBack;

    public CarriageModel(final ModelPart root) {
        super(root, RenderTypes::entityCutout);
        final ModelPart parts = root.getChild("root");
        this.leftWheelFront = parts.getChild("leftWheelFront");
        this.rightWheelFront = parts.getChild("rightWheelFront");
        this.leftWheelBack = parts.getChild("leftWheelBack");
        this.rightWheelBack = parts.getChild("rightWheelBack");
    }

    @Override
    /** Mirrors {@code WagonModel#setupAnim}'s own doc exactly -- 4 independent wheels, same index order. */
    public void setupAnim(final CarriageRenderState state) {
        this.leftWheelFront.xRot = state.wheelRotation[0];
        this.rightWheelFront.xRot = state.wheelRotation[1];
        this.leftWheelBack.xRot = state.wheelRotation[2];
        this.rightWheelBack.xRot = state.wheelRotation[3];
        this.leftWheelFront.visible = !state.wheelBroken[0];
        this.rightWheelFront.visible = !state.wheelBroken[1];
        this.leftWheelBack.visible = !state.wheelBroken[2];
        this.rightWheelBack.visible = !state.wheelBroken[3];
    }

    public static LayerDefinition createLayer() {
        final EasyMeshBuilder root = new EasyMeshBuilder("root");

        final float xOffset = 14.0F * TRACK_SCALE * WHEEL_TRACK_EXTRA_SCALE;
        final float frontZ = -10.0F * LENGTH_SCALE;
        final float backZ = 10.0F * LENGTH_SCALE;

        root.addChild(wheel("leftWheelFront", xOffset, frontZ));
        root.addChild(wheel("rightWheelFront", -xOffset, frontZ));
        root.addChild(wheel("leftWheelBack", xOffset, backZ));
        root.addChild(wheel("rightWheelBack", -xOffset, backZ));

        final EasyMeshBuilder body = new EasyMeshBuilder("body");
        body.setRotationPoint(0.0F, -WHEEL_RADIUS, 0.0F);

        final EasyMeshBuilder base = new EasyMeshBuilder("base", 0, 0);
        base.addBox(-13.0F * TRACK_SCALE, -9.0F, -2.0F, 27.0F * TRACK_SCALE, 18.0F * LENGTH_SCALE, 1);
        base.xRot = -Mth.PI / 2.0F;
        base.yRot = -Mth.PI / 2.0F;

        final EasyMeshBuilder axleFront = new EasyMeshBuilder("axleFront", 0, 20);
        axleFront.addBox(-xOffset, -1.0F, -1.0F, xOffset * 2.0F, 2, 2);
        axleFront.setRotationPoint(0.0F, 0.0F, frontZ);

        final EasyMeshBuilder axleBack = new EasyMeshBuilder("axleBack", 0, 20);
        axleBack.addBox(-xOffset, -1.0F, -1.0F, xOffset * 2.0F, 2, 2);
        axleBack.setRotationPoint(0.0F, 0.0F, backZ);

        final float postHeight = 8.0F;
        final float postFrontZ = -15.0F * LENGTH_SCALE;
        final float postBackZ = 14.0F * LENGTH_SCALE;
        final float postX = 14.0F * TRACK_SCALE;
        final float postXInner = 13.0F * TRACK_SCALE;
        final EasyMeshBuilder postFrontLeft = cornerPost("postFrontLeft", -postX, -postHeight, postFrontZ);
        final EasyMeshBuilder postFrontRight = cornerPost("postFrontRight", postXInner, -postHeight, postFrontZ);
        final EasyMeshBuilder postBackLeft = cornerPost("postBackLeft", -postX, -postHeight, postBackZ);
        final EasyMeshBuilder postBackRight = cornerPost("postBackRight", postXInner, -postHeight, postBackZ);

        // A middle pair of posts, new for Carriage -- the longer body otherwise has an unsupported
        // span over the 4 interior seats; same cornerPost shape as the front/back pairs.
        final float postMidZ = 0.0F;
        final EasyMeshBuilder postMidLeft = cornerPost("postMidLeft", -postX, -postHeight, postMidZ);
        final EasyMeshBuilder postMidRight = cornerPost("postMidRight", postXInner, -postHeight, postMidZ);

        final float railX = 15.0F * TRACK_SCALE;
        final EasyMeshBuilder sideLeftTop = sideRail("sideLeftTop", -railX, -8.0F);
        final EasyMeshBuilder sideLeftBottom = sideRail("sideLeftBottom", -railX, -3.0F);
        final EasyMeshBuilder sideRightTop = sideRail("sideRightTop", railX, -8.0F);
        final EasyMeshBuilder sideRightBottom = sideRail("sideRightBottom", railX, -3.0F);

        final float sideFrontZ = -15.0F * LENGTH_SCALE;
        final float sideBackZ = 15.0F * LENGTH_SCALE;
        final float sideWidth = 28.0F * TRACK_SCALE;
        final float sideX = 14.0F * TRACK_SCALE;

        final EasyMeshBuilder sideFront = new EasyMeshBuilder("sideFront", 0, 31);
        sideFront.addBox(-sideX, -5.0F, -1.0F, sideWidth, 4, 1);
        sideFront.setRotationPoint(0.0F, -5.0F, sideFrontZ);

        final EasyMeshBuilder sideBack = new EasyMeshBuilder("sideBack", 0, 31);
        sideBack.addBox(-sideX, -5.0F, -1.0F, sideWidth, 4, 1);
        sideBack.setRotationPoint(0.0F, -5.0F, sideBackZ);

        final float benchWidth = 22.0F * TRACK_SCALE;
        final float benchXInner = 11.0F * TRACK_SCALE;

        // Front bench -- Driver/Passenger, same backrest+seat split WagonModel's own bench uses (see
        // that class's own doc for why the seat/backrest arrangement is what it is).
        final float frontBenchZ = -16.0F * LENGTH_SCALE;
        final EasyMeshBuilder frontBenchBack = new EasyMeshBuilder("frontBenchBack", 0, 36);
        frontBenchBack.addBox(-benchXInner, -7.0F, 0.0F, benchWidth, 7, 1);
        frontBenchBack.setRotationPoint(0.0F, -9.0F, frontBenchZ);

        final EasyMeshBuilder frontBenchSeat = new EasyMeshBuilder("frontBenchSeat", 0, 44);
        frontBenchSeat.addBox(-benchXInner, -1.0F, -8.0F, benchWidth, 1, 8);
        frontBenchSeat.setRotationPoint(0.0F, -9.0F, frontBenchZ);

        // Two interior bench rows, new for Carriage -- the 4 non-combatant seats, spaced back from
        // the front bench toward the rear. Plain flat seat slabs (no backrest -- these face the same
        // direction as the front bench, back-to-back with the row ahead, same as a real
        // forward-facing coach interior) at the same bench height/depth as the front seat slab.
        final float interiorBench1Z = 5.0F * LENGTH_SCALE;
        final float interiorBench2Z = 11.0F * LENGTH_SCALE; // leaves clearance before the back wall at 15*LENGTH_SCALE

        final EasyMeshBuilder interiorBench1 = new EasyMeshBuilder("interiorBench1", 0, 44);
        interiorBench1.addBox(-benchXInner, -1.0F, -4.0F, benchWidth, 1, 8);
        interiorBench1.setRotationPoint(0.0F, -9.0F, interiorBench1Z);

        final EasyMeshBuilder interiorBench2 = new EasyMeshBuilder("interiorBench2", 0, 44);
        interiorBench2.addBox(-benchXInner, -1.0F, -4.0F, benchWidth, 1, 8);
        interiorBench2.setRotationPoint(0.0F, -9.0F, interiorBench2Z);

        body.addChild(base);
        body.addChild(axleFront);
        body.addChild(axleBack);
        body.addChild(postFrontLeft);
        body.addChild(postFrontRight);
        body.addChild(postBackLeft);
        body.addChild(postBackRight);
        body.addChild(postMidLeft);
        body.addChild(postMidRight);
        body.addChild(sideLeftTop);
        body.addChild(sideLeftBottom);
        body.addChild(sideRightTop);
        body.addChild(sideRightBottom);
        body.addChild(sideFront);
        body.addChild(sideBack);
        body.addChild(frontBenchBack);
        body.addChild(frontBenchSeat);
        body.addChild(interiorBench1);
        body.addChild(interiorBench2);
        root.addChild(body);

        return EasyMeshBuilder.rootLayer(root, 64, 64);
    }

    private static EasyMeshBuilder cornerPost(final String name, final float x, final float y, final float z) {
        final EasyMeshBuilder post = new EasyMeshBuilder(name, 0, 40);
        post.addBox(-0.5F, 0.0F, -0.5F, 1, 9, 1);
        post.setRotationPoint(x, y, z);
        return post;
    }

    private static EasyMeshBuilder sideRail(final String name, final float x, final float y) {
        final EasyMeshBuilder rail = new EasyMeshBuilder(name, 0, 26);
        rail.addBox(-15.0F * LENGTH_SCALE, -1.0F, -0.5F, 30.0F * LENGTH_SCALE, 2, 1);
        rail.setRotationPoint(x, y, 0.0F);
        rail.yRot = x < 0.0F ? Mth.PI / 2.0F : -Mth.PI / 2.0F;
        return rail;
    }

    /** Same real 8-spoke hub/rim pattern as {@code AnimalCartModel}/{@code WagonModel}'s own wheel -- copied verbatim, same {@code texOffs}/box dims, so the existing wheel-rim-iron texture treatment transfers unchanged. */
    private static EasyMeshBuilder wheel(final String name, final float xOffset, final float z) {
        final EasyMeshBuilder wheel = new EasyMeshBuilder(name, 46, 60);
        wheel.setRotationPoint(xOffset, -WHEEL_RADIUS, z);
        wheel.addBox(-1.0F, -1.0F, -1.0F, 2, 2, 2);
        for (int i = 0; i < 8; i++) {
            final EasyMeshBuilder rim = new EasyMeshBuilder(name + "_rim_" + i, 58, 54);
            rim.addBox(-1.0F, -4.5F, 9.86F, 2, 9, 1);
            rim.xRot = i * Mth.PI / 4.0F;
            wheel.addChild(rim);

            final EasyMeshBuilder spoke = new EasyMeshBuilder(name + "_spoke_" + i, 54, 54);
            spoke.addBox(-0.5F, 1.0F, -0.5F, 1, 9, 1);
            spoke.xRot = i * Mth.PI / 4.0F;
            wheel.addChild(spoke);
        }
        return wheel;
    }
}
