package com.github.cerealklla.caryvan.client.renderer.entity.model;

import com.github.cerealklla.caryvan.client.renderer.entity.state.CarriageRenderState;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Carriage's geometry (design-document.md §8, built 2026-10-26) -- originally a direct adaptation of
 * {@link WagonModel}, including its wheel ({@link #wheel}, still copied verbatim, same
 * {@code texOffs}/box dims) and corner-post/axle body language.
 *
 * <p>Real user spec: "It will likely need to be a little longer and wider than the wagon to fit 4
 * inside comfortably." Unlike {@link WagonModel}, where {@code TRACK_SCALE} only widened the wheel
 * track while the body box itself stayed a fixed width (a pre-existing Wagon quirk, not touched
 * here), this model reuses {@link #TRACK_SCALE} for the body's own X-axis dimensions too, so a wider
 * wheel track actually corresponds to a visually wider body.
 *
 * <p><b>Real wheel-break geometry + positional tilt fix, 2026-10-26</b> -- mirrors
 * {@link WagonModel}'s own doc exactly (same live-test reports applied to both cart types at once):
 * each wheel position now bakes a damaged-look variant (missing rim wedge/spoke, two snapped-stub
 * spokes) toggled by {@code state.wheelBroken[i]}, and the grounded-side wheels/fallen-debris pieces
 * are dynamically position/orientation-compensated in {@link #setupAnim} so they stay visually
 * planted on the ground instead of floating with the shared body tilt. See {@link WagonModel}'s own
 * doc for the full real derivation (confirmed against {@code com.mojang.math.Axis}'s own source) --
 * identical reasoning, just applied to this model's own geometry/constants.
 *
 * <p><b>Real structural redesign, 2026-10-26 -- enclosed cabin, Phase 1 of 4</b>: the user compared
 * the Carriage against a real reference photo and found it was just a stretched/widened Wagon, not
 * the enclosed passenger cabin they actually wanted. The open side rails behind the driver's bench
 * ({@code sideLeftTop}/{@code Bottom}, {@code sideRightTop}/{@code Bottom}, now removed) are replaced
 * by real solid walls with a genuine window-sized gap (two stacked bands, {@link #wallSkirt}/
 * {@link #wallHeader}, with nothing built in between -- the same "an opening is modeled as missing
 * geometry, not a texture trick" language this session's broken-wheel work already established) and
 * two real door panels ({@link #wallDoor}), one per side, flush against the existing front wall. The
 * existing {@code sideFront}/{@code sideBack} divider walls (previously a short 4-unit "dressing"
 * rail) are now full wall height, and a flat {@link #roof} slab sits on top of the existing corner
 * posts (which already happened to be exactly the right height -- {@code postHeight}=8, giving a
 * world Y range of {@code [-8, 1]}, identical to the new {@link #WALL_HEIGHT}=9 span, confirmed by
 * re-reading the existing constants before assuming anything needed to move). The driver's own open
 * bench area in front of the existing front wall is completely untouched -- real period carriages
 * seat the coachman exposed up front and the passengers enclosed behind, which this already matched
 * by coincidence once the front wall was made solid. Doors are static/decorative this pass (no open/
 * close animation, explicitly not ruled out later) -- interior seating, the door-triggered seat/
 * eject interaction, and each door's own independent health/repair state are deliberately separate,
 * later phases of this same dispatch, not built here.
 */
public final class CarriageModel extends EntityModel<CarriageRenderState> {

    // Same real wheel size as Wagon/Animal Cart -- "appropriate wheels" (real user spec) means
    // correctly-textured and proportioned, not smaller/mismatched.
    private static final float WHEEL_RADIUS = 11.0F;
    private static final float WHEEL_Y = -WHEEL_RADIUS;

    /** Mirrors {@code CarriageRenderer}'s own identical constant -- see {@code WagonModel#BROKEN_TILT_DEGREES}'s own doc for why the single source of truth lives on the Model class. */
    public static final float BROKEN_TILT_DEGREES = 12.0F;

    // Bigger than WagonModel's own 1.5/1.1 -- real user spec, "a little longer and wider... to fit 4
    // inside comfortably." Untested placeholder, like every one of WagonModel's own scale constants
    // started out as before live-test correction.
    private static final float LENGTH_SCALE = 2.2F;
    private static final float TRACK_SCALE = 1.3F;
    // Real live-test feedback, 2026-10-26: "widen the wheel well again, moving them another 5%
    // apart or so" -- a wheel-only multiplier on top of TRACK_SCALE, since TRACK_SCALE itself is
    // deliberately shared with the body's own X-axis dimensions here (see this class's own doc) --
    // widening it directly would also widen the body, which wasn't asked for. Widened a second time
    // same day: "widen another 10% across the width" -- 1.05 * 1.10 = 1.155. Widened a third and
    // final time same day -- "let's make it 5% more, and that should be it" -- 1.155 * 1.05 =
    // 1.21275.
    private static final float WHEEL_TRACK_EXTRA_SCALE = 1.21275F;

    private static final float WHEEL_TRACK_X = 14.0F * TRACK_SCALE * WHEEL_TRACK_EXTRA_SCALE;
    private static final float WHEEL_FRONT_Z = -10.0F * LENGTH_SCALE;
    private static final float WHEEL_BACK_Z = 10.0F * LENGTH_SCALE;

    // Real cabin wall height, 2026-10-26 -- matches the existing corner posts exactly (postHeight=8
    // below gives a world Y range of [-8, 1], confirmed by re-reading that constant before picking
    // a new one, rather than introducing a second, possibly-mismatched height).
    private static final float WALL_Y_BOTTOM = -8.0F;
    private static final float WALL_HEIGHT = 9.0F;
    private static final float WALL_Y_TOP = WALL_Y_BOTTOM + WALL_HEIGHT;

    private static final float[] GROUNDED_COMPENSATED = compensateForTilt(-WHEEL_TRACK_X, WHEEL_Y);
    private static final float GROUNDED_COUNTER_ZROT = (float) -Math.toRadians(BROKEN_TILT_DEGREES);

    private static final float FALLEN_TARGET_X = WHEEL_TRACK_X + 6.0F;
    private static final float FALLEN_TARGET_Y = -2.0F;
    private static final float[] FALLEN_COMPENSATED = compensateForTilt(FALLEN_TARGET_X, FALLEN_TARGET_Y);
    private static final float FALLEN_WORLD_ZROT_DEGREES = 55.0F;
    private static final float FALLEN_LOCAL_ZROT = (float) (Math.toRadians(FALLEN_WORLD_ZROT_DEGREES) - Math.toRadians(BROKEN_TILT_DEGREES));

    // Carriage redesign Phase 3, 2026-10-26: "a damaged door should visually hang off its hinges,
    // real geometry change, same spirit as the broken-wheel geometry work -- don't just recolor."
    // Swung open (yRot) and sagging (zRot) around the door's own BACK edge (treated as the hinge) --
    // untested placeholder angles, like every other geometry constant in this file.
    private static final float DOOR_SWING_DEGREES = 35.0F;
    private static final float DOOR_DROOP_DEGREES = 12.0F;

    private final ModelPart leftWheelFrontNormal;
    private final ModelPart leftWheelFrontBroken;
    private final ModelPart rightWheelFrontNormal;
    private final ModelPart rightWheelFrontBroken;
    private final ModelPart leftWheelBackNormal;
    private final ModelPart leftWheelBackBroken;
    private final ModelPart rightWheelBackNormal;
    private final ModelPart rightWheelBackBroken;
    private final ModelPart fallenWheelFront;
    private final ModelPart fallenWheelBack;
    private final ModelPart wallDoorLeft;
    private final ModelPart wallDoorLeftBroken;
    private final ModelPart wallDoorRight;
    private final ModelPart wallDoorRightBroken;

    public CarriageModel(final ModelPart root) {
        super(root, RenderTypes::entityCutout);
        final ModelPart parts = root.getChild("root");
        this.leftWheelFrontNormal = parts.getChild("leftWheelFrontNormal");
        this.leftWheelFrontBroken = parts.getChild("leftWheelFrontBroken");
        this.rightWheelFrontNormal = parts.getChild("rightWheelFrontNormal");
        this.rightWheelFrontBroken = parts.getChild("rightWheelFrontBroken");
        this.leftWheelBackNormal = parts.getChild("leftWheelBackNormal");
        this.leftWheelBackBroken = parts.getChild("leftWheelBackBroken");
        this.rightWheelBackNormal = parts.getChild("rightWheelBackNormal");
        this.rightWheelBackBroken = parts.getChild("rightWheelBackBroken");
        this.fallenWheelFront = parts.getChild("fallenWheelFront");
        this.fallenWheelBack = parts.getChild("fallenWheelBack");
        // The door parts are added to "body" (see #createLayer), which is itself a child of "root" --
        // one level deeper than the wheels above, which are direct children of "root" instead. Real
        // crash fixed 2026-10-26: NoSuchElementException from calling parts.getChild("wallDoorLeft")
        // directly, one level too shallow for where this part actually lives.
        final ModelPart body = parts.getChild("body");
        this.wallDoorLeft = body.getChild("wallDoorLeft");
        this.wallDoorLeftBroken = body.getChild("wallDoorLeftBroken");
        this.wallDoorRight = body.getChild("wallDoorRight");
        this.wallDoorRightBroken = body.getChild("wallDoorRightBroken");
    }

    /** Mirrors {@code WagonModel#setupAnim}'s own doc exactly. */
    @Override
    public void setupAnim(final CarriageRenderState state) {
        final boolean fullyBroken = state.broken;
        applyWheelPair(this.leftWheelFrontNormal, this.leftWheelFrontBroken, state.wheelRotation[0], state.wheelBroken[0], fullyBroken, false, WHEEL_TRACK_X);
        applyWheelPair(this.rightWheelFrontNormal, this.rightWheelFrontBroken, state.wheelRotation[1], state.wheelBroken[1], fullyBroken, true, -WHEEL_TRACK_X);
        applyWheelPair(this.leftWheelBackNormal, this.leftWheelBackBroken, state.wheelRotation[2], state.wheelBroken[2], fullyBroken, false, WHEEL_TRACK_X);
        applyWheelPair(this.rightWheelBackNormal, this.rightWheelBackBroken, state.wheelRotation[3], state.wheelBroken[3], fullyBroken, true, -WHEEL_TRACK_X);

        // Carriage redesign Phase 3 -- entirely independent of the wheel/fully-broken state above;
        // a door can be hanging off its hinges on a perfectly drivable carriage.
        this.wallDoorLeft.visible = !state.doorBroken[0];
        this.wallDoorLeftBroken.visible = state.doorBroken[0];
        this.wallDoorRight.visible = !state.doorBroken[1];
        this.wallDoorRightBroken.visible = state.doorBroken[1];

        this.fallenWheelFront.visible = fullyBroken;
        this.fallenWheelBack.visible = fullyBroken;
        if (fullyBroken) {
            this.fallenWheelFront.x = FALLEN_COMPENSATED[0];
            this.fallenWheelFront.y = FALLEN_COMPENSATED[1];
            this.fallenWheelFront.zRot = FALLEN_LOCAL_ZROT;
            this.fallenWheelBack.x = FALLEN_COMPENSATED[0];
            this.fallenWheelBack.y = FALLEN_COMPENSATED[1];
            this.fallenWheelBack.zRot = FALLEN_LOCAL_ZROT;
        }
    }

    /** Mirrors {@code WagonModel#applyWheelPair}'s own doc exactly. */
    private static void applyWheelPair(final ModelPart normal, final ModelPart broken, final float rotation,
            final boolean wheelBroken, final boolean fullyBroken, final boolean grounded, final float bakedX) {
        normal.xRot = rotation;
        broken.xRot = rotation;
        normal.x = bakedX;
        normal.y = WHEEL_Y;
        normal.zRot = 0.0F;
        if (fullyBroken && grounded) {
            normal.visible = false;
            broken.visible = true;
            broken.x = GROUNDED_COMPENSATED[0];
            broken.y = GROUNDED_COMPENSATED[1];
            broken.zRot = GROUNDED_COUNTER_ZROT;
        } else if (fullyBroken) {
            normal.visible = false;
            broken.visible = false;
        } else {
            broken.x = bakedX;
            broken.y = WHEEL_Y;
            broken.zRot = 0.0F;
            normal.visible = !wheelBroken;
            broken.visible = wheelBroken;
        }
    }

    /** Mirrors {@code WagonModel#compensateForTilt}'s own doc exactly. */
    private static float[] compensateForTilt(final float targetX, final float targetY) {
        final double theta = Math.toRadians(BROKEN_TILT_DEGREES);
        final double cos = Math.cos(theta);
        final double sin = Math.sin(theta);
        return new float[] {(float) (targetX * cos + targetY * sin), (float) (-targetX * sin + targetY * cos)};
    }

    public static LayerDefinition createLayer() {
        final EasyMeshBuilder root = new EasyMeshBuilder("root");

        root.addChild(wheel("leftWheelFrontNormal", WHEEL_TRACK_X, WHEEL_FRONT_Z, false));
        root.addChild(wheel("leftWheelFrontBroken", WHEEL_TRACK_X, WHEEL_FRONT_Z, true));
        root.addChild(wheel("rightWheelFrontNormal", -WHEEL_TRACK_X, WHEEL_FRONT_Z, false));
        root.addChild(wheel("rightWheelFrontBroken", -WHEEL_TRACK_X, WHEEL_FRONT_Z, true));
        root.addChild(wheel("leftWheelBackNormal", WHEEL_TRACK_X, WHEEL_BACK_Z, false));
        root.addChild(wheel("leftWheelBackBroken", WHEEL_TRACK_X, WHEEL_BACK_Z, true));
        root.addChild(wheel("rightWheelBackNormal", -WHEEL_TRACK_X, WHEEL_BACK_Z, false));
        root.addChild(wheel("rightWheelBackBroken", -WHEEL_TRACK_X, WHEEL_BACK_Z, true));

        // Mirrors WagonModel's own doc exactly -- fallen debris always built with the damaged-look
        // geometry; baked position is irrelevant, #setupAnim always overrides x/y/zRot dynamically.
        root.addChild(wheel("fallenWheelFront", FALLEN_TARGET_X, WHEEL_FRONT_Z, true));
        root.addChild(wheel("fallenWheelBack", FALLEN_TARGET_X, WHEEL_BACK_Z, true));

        final EasyMeshBuilder body = new EasyMeshBuilder("body");
        body.setRotationPoint(0.0F, -WHEEL_RADIUS, 0.0F);

        final EasyMeshBuilder base = new EasyMeshBuilder("base", 0, 0);
        base.addBox(-13.0F * TRACK_SCALE, -9.0F, -2.0F, 27.0F * TRACK_SCALE, 18.0F * LENGTH_SCALE, 1);
        base.xRot = -Mth.PI / 2.0F;
        base.yRot = -Mth.PI / 2.0F;

        final EasyMeshBuilder axleFront = new EasyMeshBuilder("axleFront", 0, 20);
        axleFront.addBox(-WHEEL_TRACK_X, -1.0F, -1.0F, WHEEL_TRACK_X * 2.0F, 2, 2);
        axleFront.setRotationPoint(0.0F, 0.0F, WHEEL_FRONT_Z);

        final EasyMeshBuilder axleBack = new EasyMeshBuilder("axleBack", 0, 20);
        axleBack.addBox(-WHEEL_TRACK_X, -1.0F, -1.0F, WHEEL_TRACK_X * 2.0F, 2, 2);
        axleBack.setRotationPoint(0.0F, 0.0F, WHEEL_BACK_Z);

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

        final float sideFrontZ = -15.0F * LENGTH_SCALE;
        final float sideBackZ = 15.0F * LENGTH_SCALE;
        final float sideWidth = 28.0F * TRACK_SCALE;
        final float sideX = 14.0F * TRACK_SCALE;

        // Real cabin walls, 2026-10-26 -- see this class's own top-level doc for the full writeup.
        // Front/back walls raised from a 4-unit dressing rail to the real full WALL_HEIGHT, flush
        // with the existing corner posts (already exactly this tall).
        final EasyMeshBuilder sideFront = new EasyMeshBuilder("sideFront", 0, 31);
        sideFront.addBox(-sideX, 0.0F, -1.0F, sideWidth, WALL_HEIGHT, 1);
        sideFront.setRotationPoint(0.0F, WALL_Y_BOTTOM, sideFrontZ);

        final EasyMeshBuilder sideBack = new EasyMeshBuilder("sideBack", 0, 31);
        sideBack.addBox(-sideX, 0.0F, -1.0F, sideWidth, WALL_HEIGHT, 1);
        sideBack.setRotationPoint(0.0F, WALL_Y_BOTTOM, sideBackZ);

        // Each side: a full-height door flush against the front wall (nearest the driver, easiest to
        // step up into), then a solid skirt band, a genuine window-sized GAP (nothing built there at
        // all -- see this class's own doc), then a header band above it, covering the rest of the
        // cabin's length back to the rear wall.
        final float doorLength = 5.0F * LENGTH_SCALE;
        final float doorFrontZ = sideFrontZ;
        final float doorBackZ = doorFrontZ + doorLength;
        final EasyMeshBuilder wallDoorLeft = wallSegment("wallDoorLeft", -sideX, WALL_Y_BOTTOM, WALL_HEIGHT, doorFrontZ, doorBackZ);
        final EasyMeshBuilder wallDoorRight = wallSegment("wallDoorRight", sideX, WALL_Y_BOTTOM, WALL_HEIGHT, doorFrontZ, doorBackZ);

        // Real "hang off its hinges" damaged-door geometry, 2026-10-26 (Carriage redesign Phase 3) --
        // the hinge is the door's own BACK edge (doorBackZ, away from the driver's bench); the panel
        // swings open away from the body and sags, a genuine geometry change, not a recolor. Only
        // one of a door's two baked variants is ever shown at a time, same per-part toggle pattern
        // the broken-wheel work already established.
        final EasyMeshBuilder wallDoorLeftBroken = brokenDoorPanel("wallDoorLeftBroken", -sideX, WALL_Y_BOTTOM, WALL_HEIGHT, doorBackZ, doorLength);
        final EasyMeshBuilder wallDoorRightBroken = brokenDoorPanel("wallDoorRightBroken", sideX, WALL_Y_BOTTOM, WALL_HEIGHT, doorBackZ, doorLength);

        final float skirtHeight = 5.0F;
        final float windowHeight = 2.0F;
        final float headerHeight = WALL_HEIGHT - skirtHeight - windowHeight;
        final EasyMeshBuilder wallSkirtLeft = wallSegment("wallSkirtLeft", -sideX, WALL_Y_BOTTOM, skirtHeight, doorBackZ, sideBackZ);
        final EasyMeshBuilder wallSkirtRight = wallSegment("wallSkirtRight", sideX, WALL_Y_BOTTOM, skirtHeight, doorBackZ, sideBackZ);
        final EasyMeshBuilder wallHeaderLeft = wallSegment("wallHeaderLeft", -sideX, WALL_Y_BOTTOM + skirtHeight + windowHeight, headerHeight, doorBackZ, sideBackZ);
        final EasyMeshBuilder wallHeaderRight = wallSegment("wallHeaderRight", sideX, WALL_Y_BOTTOM + skirtHeight + windowHeight, headerHeight, doorBackZ, sideBackZ);

        // A flat roof slab over the cabin, resting on the existing corner/mid posts -- a small
        // overhang on every edge for a real-looking eave, not flush with the walls.
        final float roofOverhang = 1.0F;
        final float cabinLength = sideBackZ - sideFrontZ;
        final float cabinCenterZ = (sideFrontZ + sideBackZ) / 2.0F;
        final EasyMeshBuilder roof = new EasyMeshBuilder("roof", 0, 50);
        roof.addBox(-(sideX + roofOverhang), -1.0F, -(cabinLength / 2.0F + roofOverhang),
                (sideX + roofOverhang) * 2.0F, 1, cabinLength + roofOverhang * 2.0F);
        roof.setRotationPoint(0.0F, WALL_Y_TOP, cabinCenterZ);

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
        body.addChild(sideFront);
        body.addChild(sideBack);
        body.addChild(wallDoorLeft);
        body.addChild(wallDoorRight);
        body.addChild(wallDoorLeftBroken);
        body.addChild(wallDoorRightBroken);
        body.addChild(wallSkirtLeft);
        body.addChild(wallSkirtRight);
        body.addChild(wallHeaderLeft);
        body.addChild(wallHeaderRight);
        body.addChild(roof);
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

    /**
     * A flat panel running along the body's Z axis (length), used for both the cabin's side walls
     * and its doors -- same "build along local X, then yRot 90 degrees to align with Z" technique
     * the old {@code sideRail} helper this replaces already used, just with a real configurable
     * height/Y-position/Z-span instead of a single fixed thin rail shape.
     */
    private static EasyMeshBuilder wallSegment(final String name, final float x, final float yBottom, final float height, final float zStart, final float zEnd) {
        final float length = zEnd - zStart;
        final float zCenter = (zStart + zEnd) / 2.0F;
        final EasyMeshBuilder wall = new EasyMeshBuilder(name, 0, 31);
        wall.addBox(-length / 2.0F, 0.0F, -0.5F, length, height, 1);
        wall.setRotationPoint(x, yBottom, zCenter);
        wall.yRot = x < 0.0F ? Mth.PI / 2.0F : -Mth.PI / 2.0F;
        return wall;
    }

    /**
     * The damaged-door variant -- same panel shape {@link #wallSegment} builds, but pivoted at one
     * real edge ({@code hingeZ}, the door's own back edge) instead of its center, with an additional
     * swing (yRot) and droop (zRot) layered on top of the same base alignment rotation -- reads as
     * the door hanging open off that edge. Carriage redesign Phase 3, 2026-10-26.
     */
    private static EasyMeshBuilder brokenDoorPanel(final String name, final float x, final float yBottom, final float height, final float hingeZ, final float length) {
        final EasyMeshBuilder door = new EasyMeshBuilder(name, 0, 31);
        // Spans from the hinge (local X=0, pre-alignment) back toward the door's own front edge.
        door.addBox(-length, 0.0F, -0.5F, length, height, 1);
        door.setRotationPoint(x, yBottom, hingeZ);
        final float baseYRot = x < 0.0F ? Mth.PI / 2.0F : -Mth.PI / 2.0F;
        final float swingRad = (float) Math.toRadians(DOOR_SWING_DEGREES);
        door.yRot = baseYRot + (x < 0.0F ? -swingRad : swingRad);
        door.zRot = (float) Math.toRadians(DOOR_DROOP_DEGREES);
        return door;
    }

    /** Mirrors {@code WagonModel#wheel}'s own doc exactly -- same real 8-spoke hub/rim pattern, same damaged-look variant. */
    private static EasyMeshBuilder wheel(final String name, final float xOffset, final float z, final boolean brokenLook) {
        final EasyMeshBuilder wheel = new EasyMeshBuilder(name, 46, 60);
        wheel.setRotationPoint(xOffset, WHEEL_Y, z);
        wheel.addBox(-1.0F, -1.0F, -1.0F, 2, 2, 2);
        for (int i = 0; i < 8; i++) {
            if (!(brokenLook && i == 0)) {
                final EasyMeshBuilder rim = new EasyMeshBuilder(name + "_rim_" + i, 58, 54);
                rim.addBox(-1.0F, -4.5F, 9.86F, 2, 9, 1);
                rim.xRot = i * Mth.PI / 4.0F;
                wheel.addChild(rim);
            }
            if (brokenLook && i == 0) {
                continue;
            }
            final boolean snappedSpoke = brokenLook && (i == 1 || i == 2);
            final EasyMeshBuilder spoke = new EasyMeshBuilder(name + "_spoke_" + i, 54, 54);
            spoke.addBox(-0.5F, 1.0F, -0.5F, 1, snappedSpoke ? 4 : 9, 1);
            spoke.xRot = i * Mth.PI / 4.0F;
            wheel.addChild(spoke);
        }
        return wheel;
    }
}
