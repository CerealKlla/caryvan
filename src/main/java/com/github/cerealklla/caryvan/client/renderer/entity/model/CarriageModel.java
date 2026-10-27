package com.github.cerealklla.caryvan.client.renderer.entity.model;

import com.github.cerealklla.caryvan.client.renderer.entity.state.CarriageRenderState;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Carriage's geometry (design-document.md §8, built 2026-10-26).
 *
 * <p><b>Real third rebuild, 2026-10-26 -- scrapped the curved-hull/stepped-taper body entirely</b>:
 * explicit user verdict after the previous (reference-photo-matched, correctly-sized, visible-door)
 * body was actually seen live -- "I don't see any real improvement over the previous iterations." Not
 * a bug fix; the user found and downloaded a new, deliberately simpler reference model and asked for
 * the body/roof/driver-platform to be rebuilt against it directly (doors/interior-seat mechanism, the
 * HP/damage system, and the 3-horse team are all unrelated to body shape and untouched by this pass).
 * Read directly via the Read tool (two real rendered images, not a paraphrase -- same lesson as the
 * earlier lost-image failure this session), the new reference is a faceted hexagonal-prism cabin:
 * chamfered (angle-cut) front/back-and-top/bottom corners instead of a square box, built entirely from
 * flat angled panels (no curves/stepped-taper approximation at all -- genuinely simpler in Minecraft's
 * own cuboid system than the previous body, not just simpler-looking), a plain tented roof following
 * that same chamfered footprint to a flat ridge strip (no two-tier crown/rail/finials), narrow vertical
 * window slits on the angled shoulder faces (not wide panes), a simple 2-step side platform with a
 * small seat and one diagonal strut for the driver (not a separate raised box on risers), and 4
 * same-size wheels (the old front/back size difference is gone).
 *
 * <p><b>How the chamfer panels are really built</b> -- each side of the hexagonal cross-section is 3
 * bands stacked in Y: a vertical {@code wallMid}/door band (full half-width, {@link #sideX}), a
 * {@code lowerChamfer} band tilting IN toward the floor, and an {@code upperChamfer} band tilting IN
 * toward the roof. The tilt is a REAL rotation, not another stepped approximation: confirmed via
 * {@code ModelPart#translateAndRotate}'s own real decompiled bytecode that {@code PartPose} composes
 * {@code new Quaternionf().rotationZYX(zRot, yRot, xRot)} -- i.e. {@code xRot} is applied first, in
 * the part's own original local frame, before the {@code yRot} that (as in every other wall/door panel
 * in this model) swings the part from "built along local X" to "running along world Z." Building a
 * chamfer band's own local box with local Y spanning its real slant length (hypotenuse of the desired
 * horizontal inset and vertical rise) and setting {@code xRot} to the matching slant angle tilts the
 * band's cross-section correctly BEFORE that alignment swing, so the two compose into a real diagonal
 * plane that still runs the cabin's full length -- see {@link #chamferPanel} for the worked derivation.
 * The front/back end caps use a simpler trick that doesn't need this: 3 stacked plain rectangles of
 * decreasing-then-increasing width (floor/mid/ridge) already read as the same hexagon silhouette from
 * the near-head-on angle those faces are actually seen at, with no tilted geometry needed there at all.
 *
 * <p>The overall length/width footprint ({@link #LENGTH_SCALE}/{@link #TRACK_SCALE}/{@code
 * sideFrontZ}/{@code sideBackZ}) is still deliberately unchanged from every earlier pass, for the same
 * reason as before -- {@code CarriageEntity}'s own door/interior-seat/hitch Z constants are
 * independently duplicated against this envelope.
 */
public final class CarriageModel extends EntityModel<CarriageRenderState> {

    // Real reference-model fix, 2026-10-26: "4 wire-spoke wheels, all one size" -- the previous
    // pass's smaller-front/bigger-back distinction is gone.
    private static final float WHEEL_RADIUS = 10.0F;
    private static final float WHEEL_Y = -WHEEL_RADIUS;

    /** Mirrors {@code CarriageRenderer}'s own identical constant -- see {@code WagonModel#BROKEN_TILT_DEGREES}'s own doc for why the single source of truth lives on the Model class. */
    public static final float BROKEN_TILT_DEGREES = 12.0F;

    // Real user spec: "a little longer and wider... to fit 4 inside comfortably." Deliberately
    // UNCHANGED across every rebuild -- see this class's own top-level doc.
    private static final float LENGTH_SCALE = 2.2F;
    private static final float TRACK_SCALE = 1.3F;
    private static final float WHEEL_TRACK_EXTRA_SCALE = 1.21275F;

    private static final float WHEEL_TRACK_X = 14.0F * TRACK_SCALE * WHEEL_TRACK_EXTRA_SCALE;
    private static final float WHEEL_FRONT_Z = -10.0F * LENGTH_SCALE;
    private static final float WHEEL_BACK_Z = 10.0F * LENGTH_SCALE;

    // Real hexagonal cross-section, 2026-10-26 -- see this class's own top-level doc. FLOOR_Y=0 is
    // physically lowest; each constant below climbs higher (more negative) in turn. sideX is the
    // cross-section's own widest point (the vertical mid-wall/door band); floorHalfWidth/
    // ridgeHalfWidth are narrower, chamfered inward from there.
    private static final float FLOOR_Y = 0.0F;
    private static final float sideX = 14.0F * TRACK_SCALE;
    private static final float BOTTOM_INSET = 4.0F;
    private static final float TOP_INSET = 6.0F;
    private static final float LOWER_CHAMFER_HEIGHT = 5.0F;
    private static final float MID_HEIGHT = 14.0F;
    private static final float UPPER_CHAMFER_HEIGHT = 7.0F;
    private static final float WAIST_Y = FLOOR_Y - LOWER_CHAMFER_HEIGHT;
    private static final float MID_TOP_Y = WAIST_Y - MID_HEIGHT;
    private static final float ROOF_Y = MID_TOP_Y - UPPER_CHAMFER_HEIGHT;
    private static final float FLOOR_HALF_WIDTH = sideX - BOTTOM_INSET;
    private static final float RIDGE_HALF_WIDTH = sideX - TOP_INSET;
    // Kept for cornerPost's own existing call shape -- posts still run the real full body height.
    private static final float WALL_HEIGHT = FLOOR_Y - ROOF_Y;
    private static final float WALL_TOP_Y = ROOF_Y;

    private static final float GROUNDED_COUNTER_ZROT = (float) -Math.toRadians(BROKEN_TILT_DEGREES);

    private static final float FALLEN_TARGET_X = WHEEL_TRACK_X + 6.0F;
    private static final float FALLEN_TARGET_Y = -2.0F;
    private static final float[] FALLEN_COMPENSATED = compensateForTilt(FALLEN_TARGET_X, FALLEN_TARGET_Y);
    private static final float FALLEN_WORLD_ZROT_DEGREES = 55.0F;
    private static final float FALLEN_LOCAL_ZROT = (float) (Math.toRadians(FALLEN_WORLD_ZROT_DEGREES) - Math.toRadians(BROKEN_TILT_DEGREES));

    // Carriage redesign Phase 3, 2026-10-26: "a damaged door should visually hang off its hinges."
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
        // one level deeper than the wheels above, which are direct children of "root" instead.
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
            final float[] compensated = compensateForTilt(bakedX, WHEEL_Y);
            normal.visible = false;
            broken.visible = true;
            broken.x = compensated[0];
            broken.y = compensated[1];
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

        // Real reference-model fix, 2026-10-26: all 4 wheels are now the same size, so both axles
        // sit at the same height again -- no more front-axle offset strut.
        final EasyMeshBuilder axleFront = new EasyMeshBuilder("axleFront", 0, 20);
        axleFront.addBox(-WHEEL_TRACK_X, -1.0F, -1.0F, WHEEL_TRACK_X * 2.0F, 2, 2);
        axleFront.setRotationPoint(0.0F, 0.0F, WHEEL_FRONT_Z);

        final EasyMeshBuilder axleBack = new EasyMeshBuilder("axleBack", 0, 20);
        axleBack.addBox(-WHEEL_TRACK_X, -1.0F, -1.0F, WHEEL_TRACK_X * 2.0F, 2, 2);
        axleBack.setRotationPoint(0.0F, 0.0F, WHEEL_BACK_Z);

        final float postFrontZ = -15.0F * LENGTH_SCALE;
        final float postBackZ = 14.0F * LENGTH_SCALE;
        final float postX = sideX;
        final float postXInner = sideX - 1.0F;
        final EasyMeshBuilder postFrontLeft = cornerPost("postFrontLeft", -postX, postFrontZ);
        final EasyMeshBuilder postFrontRight = cornerPost("postFrontRight", postXInner, postFrontZ);
        final EasyMeshBuilder postBackLeft = cornerPost("postBackLeft", -postX, postBackZ);
        final EasyMeshBuilder postBackRight = cornerPost("postBackRight", postXInner, postBackZ);
        final float postMidZ = 0.0F;
        final EasyMeshBuilder postMidLeft = cornerPost("postMidLeft", -postX, postMidZ);
        final EasyMeshBuilder postMidRight = cornerPost("postMidRight", postXInner, postMidZ);

        final float sideFrontZ = -15.0F * LENGTH_SCALE;
        final float sideBackZ = 15.0F * LENGTH_SCALE;

        // Real reference-model redesign, 2026-10-26: one plain door bay (front) + one plain wall bay
        // (the rest of the cabin's length) per side -- replacing the old 3-window-bay system, since
        // the new reference has no distinct window-pane bays on the vertical mid-wall at all (its own
        // window slits live on the upper chamfer band instead, see #upperChamferPair below).
        final float doorLength = 5.0F * LENGTH_SCALE;
        final float doorFrontZ = sideFrontZ;
        final float doorBackZ = doorFrontZ + doorLength;
        // Real bug fix, 2026-10-26 -- live screenshot, "no walls at all... hollow." WAIST_Y is the
        // BOTTOM of this band (where the lower chamfer tops out), not the top -- confirmed via a real
        // getExtentsForGui bake check that passing it as wallSegment's own "yTopEdge" put the whole
        // mid-wall mostly UNDERGROUND (baked y=[-5,+9] raw instead of the real [-19,-5] span). The
        // real top edge is MID_TOP_Y.
        final EasyMeshBuilder wallDoorLeft = wallSegment("wallDoorLeft", -sideX, MID_TOP_Y, MID_HEIGHT, doorFrontZ, doorBackZ);
        final EasyMeshBuilder wallDoorRight = wallSegment("wallDoorRight", sideX, MID_TOP_Y, MID_HEIGHT, doorFrontZ, doorBackZ);
        addDoorDetails(wallDoorLeft, doorLength, MID_HEIGHT);
        addDoorDetails(wallDoorRight, doorLength, MID_HEIGHT);

        final EasyMeshBuilder wallDoorLeftBroken = brokenDoorPanel("wallDoorLeftBroken", -sideX, MID_TOP_Y, MID_HEIGHT, doorBackZ, doorLength);
        final EasyMeshBuilder wallDoorRightBroken = brokenDoorPanel("wallDoorRightBroken", sideX, MID_TOP_Y, MID_HEIGHT, doorBackZ, doorLength);

        final EasyMeshBuilder wallMidLeft = wallSegment("wallMidLeft", -sideX, MID_TOP_Y, MID_HEIGHT, doorBackZ, sideBackZ);
        final EasyMeshBuilder wallMidRight = wallSegment("wallMidRight", sideX, MID_TOP_Y, MID_HEIGHT, doorBackZ, sideBackZ);

        // Real hexagonal chamfer bands, 2026-10-26 -- see this class's own top-level doc for the real
        // tilt derivation. Lower band tapers the body IN toward the floor; upper band tapers it IN
        // toward the roof. Each is split front/back of a centered window-slit gap on the upper band
        // only, matching the reference's own narrow vertical window slits on the angled shoulder face.
        final EasyMeshBuilder lowerChamferLeft = chamferPanel("lowerChamferLeft", -1.0F, FLOOR_Y, FLOOR_HALF_WIDTH, sideX,
                BOTTOM_INSET, LOWER_CHAMFER_HEIGHT, sideFrontZ, sideBackZ);
        final EasyMeshBuilder lowerChamferRight = chamferPanel("lowerChamferRight", 1.0F, FLOOR_Y, FLOOR_HALF_WIDTH, sideX,
                BOTTOM_INSET, LOWER_CHAMFER_HEIGHT, sideFrontZ, sideBackZ);

        final float windowGap = 4.0F;
        final float windowCenterZ = (sideFrontZ + sideBackZ) / 2.0F;
        final float windowStartZ = windowCenterZ - windowGap / 2.0F;
        final float windowEndZ = windowCenterZ + windowGap / 2.0F;
        final EasyMeshBuilder upperChamferLeftFront = chamferPanel("upperChamferLeftFront", -1.0F, MID_TOP_Y, sideX, RIDGE_HALF_WIDTH,
                TOP_INSET, UPPER_CHAMFER_HEIGHT, sideFrontZ, windowStartZ);
        final EasyMeshBuilder upperChamferLeftBack = chamferPanel("upperChamferLeftBack", -1.0F, MID_TOP_Y, sideX, RIDGE_HALF_WIDTH,
                TOP_INSET, UPPER_CHAMFER_HEIGHT, windowEndZ, sideBackZ);
        final EasyMeshBuilder upperChamferRightFront = chamferPanel("upperChamferRightFront", 1.0F, MID_TOP_Y, sideX, RIDGE_HALF_WIDTH,
                TOP_INSET, UPPER_CHAMFER_HEIGHT, sideFrontZ, windowStartZ);
        final EasyMeshBuilder upperChamferRightBack = chamferPanel("upperChamferRightBack", 1.0F, MID_TOP_Y, sideX, RIDGE_HALF_WIDTH,
                TOP_INSET, UPPER_CHAMFER_HEIGHT, windowEndZ, sideBackZ);

        // Real end caps, 2026-10-26 -- 3 stacked plain rectangles (floor/mid/ridge widths) per end.
        // No tilted geometry needed here at all -- see this class's own top-level doc for why a
        // stepped-width stack already reads as the same hexagon silhouette from the near-head-on
        // angle these faces are actually seen at.
        final EasyMeshBuilder endCapFront = endCap("endCapFront", sideFrontZ, -1.0F);
        final EasyMeshBuilder endCapBack = endCap("endCapBack", sideBackZ, 1.0F);

        // Real plain tented roof, 2026-10-26 -- continues the upper chamfer's own taper further up to
        // a flat ridge strip, with a small eave overhang on the length (not on the width -- the body's
        // own end caps already close the silhouette there) so no separate gable infill is needed at
        // either end.
        final float roofOverhang = 1.0F;
        final float roofLengthOverhang = 2.0F;
        final float roofHeight = 9.0F;
        final float roofRidgeHalfWidth = 2.0F;
        final EasyMeshBuilder roofLeft = chamferPanel("roofLeft", -1.0F, ROOF_Y, RIDGE_HALF_WIDTH + roofOverhang, roofRidgeHalfWidth,
                RIDGE_HALF_WIDTH + roofOverhang - roofRidgeHalfWidth, roofHeight, sideFrontZ - roofLengthOverhang, sideBackZ + roofLengthOverhang);
        final EasyMeshBuilder roofRight = chamferPanel("roofRight", 1.0F, ROOF_Y, RIDGE_HALF_WIDTH + roofOverhang, roofRidgeHalfWidth,
                RIDGE_HALF_WIDTH + roofOverhang - roofRidgeHalfWidth, roofHeight, sideFrontZ - roofLengthOverhang, sideBackZ + roofLengthOverhang);
        final float roofTopY = ROOF_Y - roofHeight;
        final EasyMeshBuilder roofRidge = new EasyMeshBuilder("roofRidge", 0, 50);
        roofRidge.addBox(-roofRidgeHalfWidth, -1.0F, -(sideBackZ - sideFrontZ) / 2.0F - roofLengthOverhang,
                roofRidgeHalfWidth * 2.0F, 1, (sideBackZ - sideFrontZ) + roofLengthOverhang * 2.0F);
        roofRidge.setRotationPoint(0.0F, roofTopY, (sideFrontZ + sideBackZ) / 2.0F);

        // Real simple driver's platform, 2026-10-26 -- a 2-step side platform with a small seat and
        // one diagonal strut, replacing the old separate raised coachman's box on risers entirely.
        // Built on BOTH sides (not just one, like the reference's own single platform) to keep the
        // existing left/right driver-and-passenger seat mechanic intact -- the reference is a style
        // guide for the platform's own shape, not a literal "only one seat" requirement this mod's
        // mechanic doesn't share.
        final float platformZ = (doorFrontZ + doorBackZ) / 2.0F;
        final EasyMeshBuilder platformLeft = driverPlatform("PlatformLeft", -1.0F, platformZ);
        final EasyMeshBuilder platformRight = driverPlatform("PlatformRight", 1.0F, platformZ);

        body.addChild(base);
        body.addChild(axleFront);
        body.addChild(axleBack);
        body.addChild(postFrontLeft);
        body.addChild(postFrontRight);
        body.addChild(postBackLeft);
        body.addChild(postBackRight);
        body.addChild(postMidLeft);
        body.addChild(postMidRight);
        body.addChild(wallDoorLeft);
        body.addChild(wallDoorRight);
        body.addChild(wallDoorLeftBroken);
        body.addChild(wallDoorRightBroken);
        body.addChild(wallMidLeft);
        body.addChild(wallMidRight);
        body.addChild(lowerChamferLeft);
        body.addChild(lowerChamferRight);
        body.addChild(upperChamferLeftFront);
        body.addChild(upperChamferLeftBack);
        body.addChild(upperChamferRightFront);
        body.addChild(upperChamferRightBack);
        body.addChild(endCapFront);
        body.addChild(endCapBack);
        body.addChild(roofLeft);
        body.addChild(roofRight);
        body.addChild(roofRidge);
        body.addChild(platformLeft);
        body.addChild(platformRight);

        // Two interior bench rows -- the 4 non-combatant seats, unaffected by the body-shape rebuild.
        final float benchWidth = 22.0F * TRACK_SCALE;
        final float benchXInner = 11.0F * TRACK_SCALE;
        final float interiorBench1Z = 5.0F * LENGTH_SCALE;
        final float interiorBench2Z = 11.0F * LENGTH_SCALE;

        final EasyMeshBuilder interiorBench1 = new EasyMeshBuilder("interiorBench1", 0, 44);
        interiorBench1.addBox(-benchXInner, -1.0F, -4.0F, benchWidth, 1, 8);
        interiorBench1.setRotationPoint(0.0F, -9.0F, interiorBench1Z);

        final EasyMeshBuilder interiorBench2 = new EasyMeshBuilder("interiorBench2", 0, 44);
        interiorBench2.addBox(-benchXInner, -1.0F, -4.0F, benchWidth, 1, 8);
        interiorBench2.setRotationPoint(0.0F, -9.0F, interiorBench2Z);

        body.addChild(interiorBench1);
        body.addChild(interiorBench2);
        root.addChild(body);

        return EasyMeshBuilder.rootLayer(root, 64, 64);
    }

    private static EasyMeshBuilder cornerPost(final String name, final float x, final float z) {
        final EasyMeshBuilder post = new EasyMeshBuilder(name, 0, 40);
        post.addBox(-0.5F, 0.0F, -0.5F, 1, WALL_HEIGHT, 1);
        post.setRotationPoint(x, WALL_TOP_Y, z);
        return post;
    }

    /**
     * A flat vertical panel running along the body's Z axis (length) at a FIXED half-width -- used
     * for the door/plain-wall mid bands, which don't taper. {@code yTopEdge} is the physically
     * HIGHER (more negative) edge the panel hangs down from by {@code height}.
     */
    private static EasyMeshBuilder wallSegment(final String name, final float x, final float yTopEdge, final float height, final float zStart, final float zEnd) {
        final float length = zEnd - zStart;
        final float zCenter = (zStart + zEnd) / 2.0F;
        final EasyMeshBuilder wall = new EasyMeshBuilder(name, 0, 31);
        wall.addBox(-length / 2.0F, 0.0F, -0.5F, length, height, 1);
        wall.setRotationPoint(x, yTopEdge, zCenter);
        wall.yRot = x < 0.0F ? Mth.PI / 2.0F : -Mth.PI / 2.0F;
        return wall;
    }

    /**
     * A real tilted (chamfered) panel running along the body's Z axis -- see this class's own
     * top-level doc for the full rotation-composition derivation. Spans from a BOTTOM edge at
     * {@code (bottomHalfWidth, bottomY)} to a TOP edge at {@code (topHalfWidth, bottomY -
     * verticalRise)}, tapering inward by {@code horizontalInset = |topHalfWidth - bottomHalfWidth|}
     * over that rise. {@code sideSign} is {@code -1} for the left side, {@code +1} for the right --
     * NOT just the sign of {@code x} (the panel's own pivot sits at the half-width, which is always
     * positive; the sign has to be passed explicitly to pick the correct mirrored tilt direction).
     */
    private static EasyMeshBuilder chamferPanel(final String name, final float sideSign, final float bottomY,
            final float bottomHalfWidth, final float topHalfWidth, final float horizontalInset, final float verticalRise,
            final float zStart, final float zEnd) {
        final float length = zEnd - zStart;
        final float zCenter = (zStart + zEnd) / 2.0F;
        final double slantLength = Math.sqrt((double) horizontalInset * horizontalInset + (double) verticalRise * verticalRise);
        final double theta = Math.atan2(horizontalInset, verticalRise);
        final EasyMeshBuilder panel = new EasyMeshBuilder(name, 0, 31);
        // Real bug fix, 2026-10-26 -- live screenshot, "no walls... hollow" and "a large detached
        // plank floating disconnected high above." Confirmed via a real getExtentsForGui bake check
        // that this box's old local Y span of [0, slantLength] made the panel extend DOWNWARD (toward
        // the floor) from its own pivot, not upward toward the roof as every call site here actually
        // needs -- cos(theta) is positive for any real chamfer angle, so the panel's own world Y only
        // ever grew MORE positive (down) as local Y grew, the opposite of the intended direction. The
        // box now spans [-slantLength, 0] instead, so its own local Y is negative at the "top" corner,
        // landing world Y at bottomY - verticalRise there, as intended. The X-tilt sign needed
        // re-deriving (and re-verifying against real baked output, not hand math alone) to match.
        panel.addBox(-length / 2.0F, -(float) slantLength, -0.5F, length, (float) slantLength, 1);
        panel.setRotationPoint(sideSign * bottomHalfWidth, bottomY, zCenter);
        panel.yRot = sideSign < 0.0F ? Mth.PI / 2.0F : -Mth.PI / 2.0F;
        panel.xRot = sideSign < 0.0F ? (float) -theta : (float) theta;
        return panel;
    }

    /**
     * The front/back faces closing the hexagonal prism -- 3 stacked plain rectangles (floor/mid/ridge
     * half-widths), no tilted geometry at all. See this class's own top-level doc for why that's a
     * reasonable simplification specifically for these 2 faces. {@code dirSign} is {@code -1} for the
     * front face (builds toward {@code -Z}) and {@code +1} for the back (builds toward {@code +Z}).
     */
    private static EasyMeshBuilder endCap(final String name, final float z, final float dirSign) {
        final EasyMeshBuilder cap = new EasyMeshBuilder(name, 0, 31);
        final float depth = 1.0F;
        // Always extends INWARD (toward the cabin's own center) from the pivot edge: for the front
        // face (dirSign<0, pivot = the most-negative Z boundary), inward is +Z, so local z starts at
        // 0; for the back face (pivot = the most-positive Z boundary), inward is -Z, so local z
        // starts at -depth.
        final float zNear = dirSign < 0.0F ? 0.0F : -depth;
        cap.addBox(-FLOOR_HALF_WIDTH, WAIST_Y, zNear, FLOOR_HALF_WIDTH * 2.0F, LOWER_CHAMFER_HEIGHT, depth);
        cap.addBox(-sideX, MID_TOP_Y, zNear, sideX * 2.0F, MID_HEIGHT, depth);
        cap.addBox(-RIDGE_HALF_WIDTH, ROOF_Y, zNear, RIDGE_HALF_WIDTH * 2.0F, UPPER_CHAMFER_HEIGHT, depth);
        cap.setRotationPoint(0.0F, 0.0F, z);
        return cap;
    }

    /**
     * The damaged-door variant -- same panel shape {@link #wallSegment} builds, but pivoted at one
     * real edge ({@code hingeZ}, the door's own back edge) instead of its center, with an additional
     * swing (yRot) and droop (zRot) layered on top of the same base alignment rotation -- reads as
     * the door hanging open off that edge. Carriage redesign Phase 3, 2026-10-26.
     */
    private static EasyMeshBuilder brokenDoorPanel(final String name, final float x, final float yTopEdge, final float height, final float hingeZ, final float length) {
        final EasyMeshBuilder door = new EasyMeshBuilder(name, 0, 31);
        door.addBox(-length, 0.0F, -0.5F, length, height, 1);
        door.setRotationPoint(x, yTopEdge, hingeZ);
        final float baseYRot = x < 0.0F ? Mth.PI / 2.0F : -Mth.PI / 2.0F;
        final float swingRad = (float) Math.toRadians(DOOR_SWING_DEGREES);
        door.yRot = baseYRot + (x < 0.0F ? -swingRad : swingRad);
        door.zRot = (float) Math.toRadians(DOOR_DROOP_DEGREES);
        return door;
    }

    /** See this door's own call site doc (unchanged from the previous pass's fix -- the frame/handle geometry itself isn't affected by the body-shape rebuild). */
    private static void addDoorDetails(final EasyMeshBuilder door, final float length, final float height) {
        final float frameThickness = 1.5F;
        final float frameDepth = 3.0F;
        final float frameZ = -frameDepth / 2.0F;
        door.addBox(-length / 2.0F, 0.0F, frameZ, length, frameThickness, frameDepth);
        door.addBox(-length / 2.0F, height - frameThickness, frameZ, length, frameThickness, frameDepth);
        door.addBox(-length / 2.0F, 0.0F, frameZ, frameThickness, height, frameDepth);
        door.addBox(length / 2.0F - frameThickness, 0.0F, frameZ, frameThickness, height, frameDepth);

        final float handleSize = 1.2F;
        final float handleX = -length / 2.0F + frameThickness + 1.0F;
        door.addBox(handleX, height * 0.4F, -2.5F, handleSize, handleSize, handleSize);
    }

    /**
     * The real simple 2-step driver's platform, 2026-10-26 -- a low outer step, a higher inner step,
     * a small flat seat cushion, and one diagonal support strut, all projecting outward in X from the
     * body's own side at {@code platformZ}. {@code sideSign} is {@code -1} for the left platform,
     * {@code +1} for the right.
     */
    private static EasyMeshBuilder driverPlatform(final String name, final float sideSign, final float platformZ) {
        final EasyMeshBuilder platform = new EasyMeshBuilder(name, 0, 40);
        final float lowerStepY = 0.0F;
        final float lowerStepOuter = sideX + 8.0F;
        final float lowerStepInner = sideX + 1.0F;
        final float upperStepY = -5.0F;
        final float upperStepOuter = sideX + 5.0F;
        final float upperStepInner = sideX + 0.5F;
        final float seatY = -7.0F;
        final float seatOuter = sideX + 4.0F;
        final float seatInner = sideX + 0.5F;
        final float stepZLen = 10.0F;
        final float stepZStart = -stepZLen / 2.0F;

        // Box X-span must be given smallest-first regardless of side -- resolve explicitly per side.
        final float lowerX0 = sideSign < 0.0F ? -lowerStepOuter : lowerStepInner;
        final float lowerWidth = lowerStepOuter - lowerStepInner;
        platform.addBox(lowerX0, lowerStepY - 1.0F, stepZStart, lowerWidth, 1.0F, stepZLen);

        final float upperX0 = sideSign < 0.0F ? -upperStepOuter : upperStepInner;
        final float upperWidth = upperStepOuter - upperStepInner;
        platform.addBox(upperX0, upperStepY - 1.0F, stepZStart, upperWidth, 1.0F, stepZLen);

        final float seatX0 = sideSign < 0.0F ? -seatOuter : seatInner;
        final float seatWidth = seatOuter - seatInner;
        platform.addBox(seatX0, seatY - 1.0F, stepZStart, seatWidth, 1.0F, stepZLen);

        // Diagonal strut -- a single tilted box from the ground near the lower step up to the upper
        // step's own outer corner, built along local Y (height) then zRot-tilted to lean outward.
        final float strutRun = (lowerStepOuter - 1.0F) - (upperStepOuter - 1.0F);
        final float strutRise = lowerStepY - upperStepY;
        final double strutSlant = Math.sqrt((double) strutRun * strutRun + (double) strutRise * strutRise);
        final double strutTheta = Math.atan2(strutRun, strutRise);
        final EasyMeshBuilder strut = new EasyMeshBuilder(name + "Strut", 0, 20);
        strut.addBox(-0.5F, -(float) strutSlant, -0.5F, 1, (float) strutSlant, 1);
        strut.setRotationPoint(sideSign * (lowerStepOuter - 1.0F), lowerStepY, platformZ);
        // Leans the strut's own top end INWARD (toward the body) from its ground pivot, to meet the
        // upper step's own closer-in outer edge -- same real tilt derivation #chamferPanel uses, just
        // around Z (this element needs no yRot length-alignment at all) instead of X.
        strut.zRot = sideSign < 0.0F ? (float) strutTheta : (float) -strutTheta;
        platform.addChild(strut);

        platform.setRotationPoint(0.0F, 0.0F, platformZ);
        return platform;
    }

    /** Mirrors {@code WagonModel#wheel}'s own doc exactly -- same real 8-spoke hub/rim pattern, same damaged-look variant. */
    private static EasyMeshBuilder wheel(final String name, final float xOffset, final float z, final boolean brokenLook) {
        final EasyMeshBuilder wheel = new EasyMeshBuilder(name, 46, 60);
        wheel.setRotationPoint(xOffset, WHEEL_Y, z);
        wheel.addBox(-1.0F, -1.0F, -1.0F, 2, 2, 2);
        final float spokeLength = WHEEL_RADIUS - 2.0F;
        final float rimZ = WHEEL_RADIUS - 1.14F;
        for (int i = 0; i < 8; i++) {
            if (!(brokenLook && i == 0)) {
                final EasyMeshBuilder rim = new EasyMeshBuilder(name + "_rim_" + i, 58, 54);
                rim.addBox(-1.0F, -4.5F, rimZ, 2, spokeLength, 1);
                rim.xRot = i * Mth.PI / 4.0F;
                wheel.addChild(rim);
            }
            if (brokenLook && i == 0) {
                continue;
            }
            final boolean snappedSpoke = brokenLook && (i == 1 || i == 2);
            final EasyMeshBuilder spoke = new EasyMeshBuilder(name + "_spoke_" + i, 54, 54);
            spoke.addBox(-0.5F, 1.0F, -0.5F, 1, snappedSpoke ? 4 : spokeLength, 1);
            spoke.xRot = i * Mth.PI / 4.0F;
            wheel.addChild(spoke);
        }
        return wheel;
    }
}
