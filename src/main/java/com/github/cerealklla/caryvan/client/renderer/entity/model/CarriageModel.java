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
 * <p><b>Real structural redesign, 2026-10-26 -- enclosed cabin, Phase 1 of 4</b>: the user compared
 * the Carriage against a real reference photo and found it was just a stretched/widened Wagon.
 *
 * <p><b>Real second rebuild, 2026-10-26, against the actual reference photo</b> -- a process failure
 * upstream meant the Phase 1 pass above was built from a text paraphrase ("windows, roof, 2 doors")
 * of the user's own real reference image, never the image itself, and it showed: "just a wider and
 * longer wagon," per the user, once actually described back to them. This pass was authored directly
 * against that real photo (a period landau/berline carriage), studied feature by feature rather than
 * patched incrementally on top of the old box shape:
 * <ul>
 *   <li><b>Lower body is a curved hull, not a box</b> -- the photo's body panel sweeps upward at both
 *   the front and rear ends rather than running flat the full length. Minecraft's cuboid-only model
 *   system can't author a true curve, so this is approximated the same way good voxel builds always
 *   fake a curve: a short run of progressively shorter, progressively inset boxes stepping outward
 *   past each end of the enclosed cabin ({@link #hullCurl}) -- a real, visible stepped taper reading
 *   as a swept curve at normal camera distance, not a flat panel with rounded corners.</li>
 *   <li><b>Three window bays per side, not one long gap</b> -- the photo shows a door bay, a fixed
 *   center window bay, and a solid rear quarter panel, each a distinct division along the body
 *   (matching the mechanic spec's own "one door per side" -- only the FRONT bay is an actual door;
 *   the photo's visible 3-bay rhythm on its own near side is reused for proportion/shape even though
 *   this mod's own established interaction design keeps exactly 2 real doors, one per full side).</li>
 *   <li><b>Roof has real silhouette</b> -- a two-tier stepped crown (a narrower upper band on the
 *   wider lower eave) plus a perimeter rail with finial posts at the corners and mid-sides, matching
 *   the photo's visible brass luggage rail -- not a single flat slab.</li>
 *   <li><b>Driver's bench is a real separate elevated structure</b> -- raised well above the cabin
 *   roofline on its own riser posts, out in front of the enclosed cabin, matching the photo's raised
 *   coachman's box (the photo's own folding leather calash hood above that box is not built -- static
 *   geometry only, like the doors, see the Phase-3 door doc for why that's an established pattern in
 *   this model already).</li>
 *   <li><b>A real rear footman's platform</b> -- a low external step/cushion at the very back, outside
 *   the enclosed cabin, matching the photo's low rear jump seat (companions already ride here via the
 *   pre-existing roof/running-board Companion slots, see {@code CarriageEntity}'s own doc -- this is
 *   only the visual structure those slots were always meant to sit on/near).</li>
 *   <li><b>Front wheels smaller than back wheels</b> -- the photo's front wheels are visibly smaller
 *   in diameter than the rear pair (a real period-carriage proportion, letting the front wheels turn
 *   under the body). Both wheel pairs still touch the same ground line ({@link #FRONT_WHEEL_RADIUS}/
 *   {@link #BACK_WHEEL_RADIUS} each supply their own {@code wheelY = -radius}), and the body's own
 *   floor stays perfectly level, tied to the (unchanged) back-axle height -- only the decorative
 *   {@code axleFront} strut is repositioned down to actually meet the now-shorter front hub, not the
 *   whole chassis.</li>
 * </ul>
 * The overall length/width footprint ({@link #LENGTH_SCALE}/{@link #TRACK_SCALE}/{@link
 * #WHEEL_TRACK_X}/{@code sideFrontZ}/{@code sideBackZ}) is deliberately UNCHANGED from the first
 * pass -- {@code CarriageEntity}'s own door/interior-seat/hitch Z constants are independently
 * duplicated against this envelope (client-only {@code ModelPart} types can't be referenced from
 * that common-sourceset class, see the root project's own "client-only type in a common class" rule),
 * so reshaping the body's own silhouette within the same footprint avoids a much larger coordinated
 * re-tuning pass across files this rebuild wasn't asked to touch.
 *
 * <p>This class's own Y convention, named honestly this rebuild (the first pass's {@code
 * WALL_Y_BOTTOM}/{@code WALL_Y_TOP} names were backwards from physical up/down and are not reused):
 * {@link #FLOOR_Y}=0 is the body's own floor; Y becomes MORE NEGATIVE going UP (standard Minecraft
 * entity-model convention, confirmed the same way {@link WagonModel}'s own doc already derived it
 * against {@code com.mojang.math.Axis}'s real rotation convention). {@link #BELT_Y}, {@link
 * #WINDOW_TOP_Y}, and {@link #WALL_TOP_Y} climb upward from there in that order.
 */
public final class CarriageModel extends EntityModel<CarriageRenderState> {

    // Real reference-photo proportion, 2026-10-26: the front wheels are visibly smaller in diameter
    // than the back pair. Both still touch the same ground line -- see this class's own top-level doc.
    private static final float BACK_WHEEL_RADIUS = 11.0F;
    private static final float FRONT_WHEEL_RADIUS = 8.0F;
    private static final float BACK_WHEEL_Y = -BACK_WHEEL_RADIUS;
    private static final float FRONT_WHEEL_Y = -FRONT_WHEEL_RADIUS;

    /** Mirrors {@code CarriageRenderer}'s own identical constant -- see {@code WagonModel#BROKEN_TILT_DEGREES}'s own doc for why the single source of truth lives on the Model class. */
    public static final float BROKEN_TILT_DEGREES = 12.0F;

    // Real user spec: "a little longer and wider... to fit 4 inside comfortably." Deliberately
    // UNCHANGED by this rebuild -- see this class's own top-level doc for why the overall footprint
    // stays fixed while the body's own shape within it is rebuilt.
    private static final float LENGTH_SCALE = 2.2F;
    private static final float TRACK_SCALE = 1.3F;
    private static final float WHEEL_TRACK_EXTRA_SCALE = 1.21275F;

    private static final float WHEEL_TRACK_X = 14.0F * TRACK_SCALE * WHEEL_TRACK_EXTRA_SCALE;
    private static final float WHEEL_FRONT_Z = -10.0F * LENGTH_SCALE;
    private static final float WHEEL_BACK_Z = 10.0F * LENGTH_SCALE;

    // Real Y convention, rebuilt 2026-10-26 -- see this class's own top-level doc. FLOOR_Y=0 is
    // physically lowest; each constant below climbs higher (more negative) in turn.
    private static final float FLOOR_Y = 0.0F;
    private static final float SKIRT_HEIGHT = 5.0F;
    private static final float WINDOW_HEIGHT = 2.0F;
    private static final float HEADER_HEIGHT = 2.0F;
    private static final float BELT_Y = FLOOR_Y - SKIRT_HEIGHT;
    private static final float WINDOW_TOP_Y = BELT_Y - WINDOW_HEIGHT;
    private static final float WALL_TOP_Y = WINDOW_TOP_Y - HEADER_HEIGHT;
    private static final float WALL_HEIGHT = FLOOR_Y - WALL_TOP_Y;

    private static final float GROUNDED_COUNTER_ZROT = (float) -Math.toRadians(BROKEN_TILT_DEGREES);

    private static final float FALLEN_TARGET_X = WHEEL_TRACK_X + 6.0F;
    private static final float FALLEN_TARGET_Y = -2.0F;
    private static final float[] FALLEN_COMPENSATED = compensateForTilt(FALLEN_TARGET_X, FALLEN_TARGET_Y);
    private static final float FALLEN_WORLD_ZROT_DEGREES = 55.0F;
    private static final float FALLEN_LOCAL_ZROT = (float) (Math.toRadians(FALLEN_WORLD_ZROT_DEGREES) - Math.toRadians(BROKEN_TILT_DEGREES));

    // Carriage redesign Phase 3, 2026-10-26: "a damaged door should visually hang off its hinges."
    private static final float DOOR_SWING_DEGREES = 35.0F;
    private static final float DOOR_DROOP_DEGREES = 12.0F;

    // Real reference-photo hull taper, 2026-10-26 -- see #hullCurl's own doc.
    private static final int CURL_SEGMENTS = 3;
    private static final float CURL_SEGMENT_LENGTH = 2.5F;

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
        applyWheelPair(this.leftWheelFrontNormal, this.leftWheelFrontBroken, state.wheelRotation[0], state.wheelBroken[0], fullyBroken, false, WHEEL_TRACK_X, FRONT_WHEEL_Y);
        applyWheelPair(this.rightWheelFrontNormal, this.rightWheelFrontBroken, state.wheelRotation[1], state.wheelBroken[1], fullyBroken, true, -WHEEL_TRACK_X, FRONT_WHEEL_Y);
        applyWheelPair(this.leftWheelBackNormal, this.leftWheelBackBroken, state.wheelRotation[2], state.wheelBroken[2], fullyBroken, false, WHEEL_TRACK_X, BACK_WHEEL_Y);
        applyWheelPair(this.rightWheelBackNormal, this.rightWheelBackBroken, state.wheelRotation[3], state.wheelBroken[3], fullyBroken, true, -WHEEL_TRACK_X, BACK_WHEEL_Y);

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

    /**
     * Mirrors {@code WagonModel#applyWheelPair}'s own doc exactly, generalized 2026-10-26 to take the
     * wheel's own {@code wheelY} directly (previously a single shared module-wide constant) now that
     * front and back wheels use different radii/heights -- see this class's own top-level doc.
     */
    private static void applyWheelPair(final ModelPart normal, final ModelPart broken, final float rotation,
            final boolean wheelBroken, final boolean fullyBroken, final boolean grounded, final float bakedX, final float wheelY) {
        normal.xRot = rotation;
        broken.xRot = rotation;
        normal.x = bakedX;
        normal.y = wheelY;
        normal.zRot = 0.0F;
        if (fullyBroken && grounded) {
            final float[] compensated = compensateForTilt(bakedX, wheelY);
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
            broken.y = wheelY;
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

        root.addChild(wheel("leftWheelFrontNormal", WHEEL_TRACK_X, WHEEL_FRONT_Z, false, FRONT_WHEEL_RADIUS));
        root.addChild(wheel("leftWheelFrontBroken", WHEEL_TRACK_X, WHEEL_FRONT_Z, true, FRONT_WHEEL_RADIUS));
        root.addChild(wheel("rightWheelFrontNormal", -WHEEL_TRACK_X, WHEEL_FRONT_Z, false, FRONT_WHEEL_RADIUS));
        root.addChild(wheel("rightWheelFrontBroken", -WHEEL_TRACK_X, WHEEL_FRONT_Z, true, FRONT_WHEEL_RADIUS));
        root.addChild(wheel("leftWheelBackNormal", WHEEL_TRACK_X, WHEEL_BACK_Z, false, BACK_WHEEL_RADIUS));
        root.addChild(wheel("leftWheelBackBroken", WHEEL_TRACK_X, WHEEL_BACK_Z, true, BACK_WHEEL_RADIUS));
        root.addChild(wheel("rightWheelBackNormal", -WHEEL_TRACK_X, WHEEL_BACK_Z, false, BACK_WHEEL_RADIUS));
        root.addChild(wheel("rightWheelBackBroken", -WHEEL_TRACK_X, WHEEL_BACK_Z, true, BACK_WHEEL_RADIUS));

        // Mirrors WagonModel's own doc exactly -- fallen debris always built with the damaged-look
        // geometry; baked position is irrelevant, #setupAnim always overrides x/y/zRot dynamically.
        // Modeled at the back wheel's own radius -- debris doesn't need to match whichever specific
        // wheel broke, just read as "a broken wheel lying nearby."
        root.addChild(wheel("fallenWheelFront", FALLEN_TARGET_X, WHEEL_FRONT_Z, true, BACK_WHEEL_RADIUS));
        root.addChild(wheel("fallenWheelBack", FALLEN_TARGET_X, WHEEL_BACK_Z, true, BACK_WHEEL_RADIUS));

        final EasyMeshBuilder body = new EasyMeshBuilder("body");
        body.setRotationPoint(0.0F, -BACK_WHEEL_RADIUS, 0.0F);

        final EasyMeshBuilder base = new EasyMeshBuilder("base", 0, 0);
        base.addBox(-13.0F * TRACK_SCALE, -9.0F, -2.0F, 27.0F * TRACK_SCALE, 18.0F * LENGTH_SCALE, 1);
        base.xRot = -Mth.PI / 2.0F;
        base.yRot = -Mth.PI / 2.0F;

        // Real reference-photo fix, 2026-10-26: the front axle now has to reach DOWN from the body's
        // own floor (pegged to the back wheel's taller hub) to the shorter front hub -- the floor
        // itself stays level; only this decorative strut moves. frontHubWorldY - bodyOriginWorldY =
        // -FRONT_WHEEL_RADIUS - (-BACK_WHEEL_RADIUS) = BACK_WHEEL_RADIUS - FRONT_WHEEL_RADIUS.
        final float axleFrontY = BACK_WHEEL_RADIUS - FRONT_WHEEL_RADIUS;
        final EasyMeshBuilder axleFront = new EasyMeshBuilder("axleFront", 0, 20);
        axleFront.addBox(-WHEEL_TRACK_X, -1.0F, -1.0F, WHEEL_TRACK_X * 2.0F, 2, 2);
        axleFront.setRotationPoint(0.0F, axleFrontY, WHEEL_FRONT_Z);

        final EasyMeshBuilder axleBack = new EasyMeshBuilder("axleBack", 0, 20);
        axleBack.addBox(-WHEEL_TRACK_X, -1.0F, -1.0F, WHEEL_TRACK_X * 2.0F, 2, 2);
        axleBack.setRotationPoint(0.0F, 0.0F, WHEEL_BACK_Z);

        final float postFrontZ = -15.0F * LENGTH_SCALE;
        final float postBackZ = 14.0F * LENGTH_SCALE;
        final float postX = 14.0F * TRACK_SCALE;
        final float postXInner = 13.0F * TRACK_SCALE;
        final EasyMeshBuilder postFrontLeft = cornerPost("postFrontLeft", -postX, postFrontZ);
        final EasyMeshBuilder postFrontRight = cornerPost("postFrontRight", postXInner, postFrontZ);
        final EasyMeshBuilder postBackLeft = cornerPost("postBackLeft", -postX, postBackZ);
        final EasyMeshBuilder postBackRight = cornerPost("postBackRight", postXInner, postBackZ);

        // A middle pair of posts -- the longer body otherwise has an unsupported span over the 4
        // interior seats; same cornerPost shape as the front/back pairs.
        final float postMidZ = 0.0F;
        final EasyMeshBuilder postMidLeft = cornerPost("postMidLeft", -postX, postMidZ);
        final EasyMeshBuilder postMidRight = cornerPost("postMidRight", postXInner, postMidZ);

        final float sideFrontZ = -15.0F * LENGTH_SCALE;
        final float sideBackZ = 15.0F * LENGTH_SCALE;
        final float sideX = 14.0F * TRACK_SCALE;

        // Real reference-photo redesign, 2026-10-26: 3 real window bays per side instead of one long
        // undifferentiated gap -- a front DOOR bay, a fixed center WINDOW bay, and a solid rear
        // quarter panel, matching the photo's own visible division rhythm on its near side (this
        // mod's own established mechanic still keeps exactly 2 real doors, one per full side -- see
        // this class's own top-level doc).
        final float doorLength = 5.0F * LENGTH_SCALE;
        final float doorFrontZ = sideFrontZ;
        final float doorBackZ = doorFrontZ + doorLength;
        final EasyMeshBuilder wallDoorLeft = wallSegment("wallDoorLeft", -sideX, WALL_TOP_Y, WALL_HEIGHT, doorFrontZ, doorBackZ);
        final EasyMeshBuilder wallDoorRight = wallSegment("wallDoorRight", sideX, WALL_TOP_Y, WALL_HEIGHT, doorFrontZ, doorBackZ);

        // Real "hang off its hinges" damaged-door geometry, Carriage redesign Phase 3 -- the hinge is
        // the door's own BACK edge (doorBackZ, away from the driver's bench); the panel swings open
        // away from the body and sags, a genuine geometry change, not a recolor.
        final EasyMeshBuilder wallDoorLeftBroken = brokenDoorPanel("wallDoorLeftBroken", -sideX, WALL_TOP_Y, WALL_HEIGHT, doorBackZ, doorLength);
        final EasyMeshBuilder wallDoorRightBroken = brokenDoorPanel("wallDoorRightBroken", sideX, WALL_TOP_Y, WALL_HEIGHT, doorBackZ, doorLength);

        // Fixed center window bay -- same skirt/gap/header shape the door bay's own window portion
        // implies, just with no door panel at all (nothing ever opens/breaks here).
        final float fixedWinFrontZ = doorBackZ;
        final float fixedWinBackZ = fixedWinFrontZ + doorLength;
        final EasyMeshBuilder wallSkirtLeft = wallSegment("wallSkirtLeft", -sideX, BELT_Y, SKIRT_HEIGHT, fixedWinFrontZ, fixedWinBackZ);
        final EasyMeshBuilder wallSkirtRight = wallSegment("wallSkirtRight", sideX, BELT_Y, SKIRT_HEIGHT, fixedWinFrontZ, fixedWinBackZ);
        final EasyMeshBuilder wallHeaderLeft = wallSegment("wallHeaderLeft", -sideX, WALL_TOP_Y, HEADER_HEIGHT, fixedWinFrontZ, fixedWinBackZ);
        final EasyMeshBuilder wallHeaderRight = wallSegment("wallHeaderRight", sideX, WALL_TOP_Y, HEADER_HEIGHT, fixedWinFrontZ, fixedWinBackZ);

        // Solid rear quarter panel -- the photo's own body runs solid (no window) from the last bay
        // back to the curling rear end.
        final float rearPanelFrontZ = fixedWinBackZ;
        final float rearPanelBackZ = sideBackZ;
        final EasyMeshBuilder rearPanelLeft = wallSegment("rearPanelLeft", -sideX, WALL_TOP_Y, WALL_HEIGHT, rearPanelFrontZ, rearPanelBackZ);
        final EasyMeshBuilder rearPanelRight = wallSegment("rearPanelRight", sideX, WALL_TOP_Y, WALL_HEIGHT, rearPanelFrontZ, rearPanelBackZ);

        // Real reference-photo hull curl, 2026-10-26 -- see #hullCurl's own doc. Reads as the body
        // sweeping up and tapering to a point beyond both the front door and the rear quarter panel,
        // instead of the old flat box silhouette.
        final EasyMeshBuilder hullCurlFrontLeft = hullCurl("hullCurlFrontLeft", -sideX, sideFrontZ, -1.0F);
        final EasyMeshBuilder hullCurlFrontRight = hullCurl("hullCurlFrontRight", sideX, sideFrontZ, -1.0F);
        final EasyMeshBuilder hullCurlBackLeft = hullCurl("hullCurlBackLeft", -sideX, sideBackZ, 1.0F);
        final EasyMeshBuilder hullCurlBackRight = hullCurl("hullCurlBackRight", sideX, sideBackZ, 1.0F);

        // Real reference-photo roof, 2026-10-26 -- a two-tier stepped crown plus a perimeter brass
        // rail with finial posts, instead of a single flat slab. See #roofCrown/#roofRail/#roofFinials.
        final float roofOverhang = 1.0F;
        final float cabinLength = sideBackZ - sideFrontZ;
        final float cabinCenterZ = (sideFrontZ + sideBackZ) / 2.0F;
        final float roofBaseHeight = 1.0F;
        final float crownHeight = 1.0F;
        final float crownInset = 8.0F;
        final EasyMeshBuilder roofBase = new EasyMeshBuilder("roofBase", 0, 50);
        roofBase.addBox(-(sideX + roofOverhang), -roofBaseHeight, -(cabinLength / 2.0F + roofOverhang),
                (sideX + roofOverhang) * 2.0F, roofBaseHeight, cabinLength + roofOverhang * 2.0F);
        roofBase.setRotationPoint(0.0F, WALL_TOP_Y, cabinCenterZ);

        final float crownY = WALL_TOP_Y - roofBaseHeight;
        final EasyMeshBuilder roofCrown = new EasyMeshBuilder("roofCrown", 0, 52);
        roofCrown.addBox(-sideX * 0.6F, -crownHeight, -(cabinLength / 2.0F - crownInset),
                sideX * 1.2F, crownHeight, cabinLength - crownInset * 2.0F);
        roofCrown.setRotationPoint(0.0F, crownY, cabinCenterZ);

        final float railThickness = 0.5F;
        final float railY = crownY - crownHeight;
        final EasyMeshBuilder roofRail = roofRail("roofRail", sideX + roofOverhang, cabinLength / 2.0F + roofOverhang, railThickness, railY, cabinCenterZ);
        final EasyMeshBuilder roofFinials = roofFinials("roofFinials", sideX + roofOverhang, cabinLength / 2.0F + roofOverhang, railY - railThickness, cabinCenterZ);

        final float benchWidth = 22.0F * TRACK_SCALE;
        final float benchXInner = 11.0F * TRACK_SCALE;

        // Real reference-photo elevated driver's bench, 2026-10-26 -- out in front of the enclosed
        // cabin (past the front hull curl) and raised well above the roofline on its own riser posts,
        // same backrest+seat split WagonModel's own bench uses, just shifted onto a real structure
        // instead of sitting at the same height as the interior seats. The photo's own folding leather
        // calash hood above this seat is not built -- static geometry only, same established "not
        // every real-world detail needs its own animation state" pattern the doors already use.
        final float frontBenchZ = -40.0F;
        final float riserHeight = 4.0F;
        final float benchBaseY = WALL_TOP_Y - riserHeight;
        final EasyMeshBuilder benchRiserLeft = new EasyMeshBuilder("benchRiserLeft", 0, 40);
        benchRiserLeft.addBox(-0.5F, 0.0F, -0.5F, 1, FLOOR_Y - benchBaseY, 1);
        benchRiserLeft.setRotationPoint(-benchXInner, benchBaseY, frontBenchZ);
        final EasyMeshBuilder benchRiserRight = new EasyMeshBuilder("benchRiserRight", 0, 40);
        benchRiserRight.addBox(-0.5F, 0.0F, -0.5F, 1, FLOOR_Y - benchBaseY, 1);
        benchRiserRight.setRotationPoint(benchXInner, benchBaseY, frontBenchZ);

        final EasyMeshBuilder frontBenchBack = new EasyMeshBuilder("frontBenchBack", 0, 36);
        frontBenchBack.addBox(-benchXInner, -7.0F, 0.0F, benchWidth, 7, 1);
        frontBenchBack.setRotationPoint(0.0F, benchBaseY, frontBenchZ);

        final EasyMeshBuilder frontBenchSeat = new EasyMeshBuilder("frontBenchSeat", 0, 44);
        frontBenchSeat.addBox(-benchXInner, -1.0F, -8.0F, benchWidth, 1, 8);
        frontBenchSeat.setRotationPoint(0.0F, benchBaseY, frontBenchZ);

        // Real reference-photo rear footman's platform, 2026-10-26 -- a low external step/cushion
        // outside the enclosed cabin, beyond the rear hull curl, matching the photo's low rear jump
        // seat. Deliberately simple (one step slab + one cushion slab, no backrest) -- the photo's own
        // rear seat reads the same way, unlike the driver's own taller bench.
        final float rearPlatformZ = 44.0F;
        final float rearPlatformSeatY = -2.0F;
        final EasyMeshBuilder rearPlatformStep = new EasyMeshBuilder("rearPlatformStep", 0, 48);
        rearPlatformStep.addBox(-benchXInner, 0.0F, -3.0F, benchWidth, 1, 6);
        rearPlatformStep.setRotationPoint(0.0F, FLOOR_Y, rearPlatformZ);
        final EasyMeshBuilder rearPlatformCushion = new EasyMeshBuilder("rearPlatformCushion", 0, 48);
        rearPlatformCushion.addBox(-benchXInner, -1.0F, -3.0F, benchWidth, 1, 6);
        rearPlatformCushion.setRotationPoint(0.0F, rearPlatformSeatY, rearPlatformZ);

        // Two interior bench rows -- the 4 non-combatant seats, spaced back from the front bench
        // toward the rear. Plain flat seat slabs (no backrest -- face the same direction as the front
        // bench, back-to-back with the row ahead, same as a real forward-facing coach interior).
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
        body.addChild(wallDoorLeft);
        body.addChild(wallDoorRight);
        body.addChild(wallDoorLeftBroken);
        body.addChild(wallDoorRightBroken);
        body.addChild(wallSkirtLeft);
        body.addChild(wallSkirtRight);
        body.addChild(wallHeaderLeft);
        body.addChild(wallHeaderRight);
        body.addChild(rearPanelLeft);
        body.addChild(rearPanelRight);
        body.addChild(hullCurlFrontLeft);
        body.addChild(hullCurlFrontRight);
        body.addChild(hullCurlBackLeft);
        body.addChild(hullCurlBackRight);
        body.addChild(roofBase);
        body.addChild(roofCrown);
        body.addChild(roofRail);
        body.addChild(roofFinials);
        body.addChild(benchRiserLeft);
        body.addChild(benchRiserRight);
        body.addChild(frontBenchBack);
        body.addChild(frontBenchSeat);
        body.addChild(rearPlatformStep);
        body.addChild(rearPlatformCushion);
        body.addChild(interiorBench1);
        body.addChild(interiorBench2);
        root.addChild(body);

        return EasyMeshBuilder.rootLayer(root, 64, 64);
    }

    /** Corner/mid posts -- real fix, 2026-10-26: spans the full {@link #FLOOR_Y}-to-{@link #WALL_TOP_Y} height exactly (9 units); the first pass's {@code postHeight}=8 offset against a hardcoded box height of 9 left the post floating 1 unit above the true floor. */
    private static EasyMeshBuilder cornerPost(final String name, final float x, final float z) {
        final EasyMeshBuilder post = new EasyMeshBuilder(name, 0, 40);
        post.addBox(-0.5F, 0.0F, -0.5F, 1, WALL_HEIGHT, 1);
        post.setRotationPoint(x, WALL_TOP_Y, z);
        return post;
    }

    /**
     * A flat panel running along the body's Z axis (length), used for the cabin's walls/doors --
     * "build along local X, then yRot 90 degrees to align with Z." {@code yTopEdge} is the physically
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
     * The damaged-door variant -- same panel shape {@link #wallSegment} builds, but pivoted at one
     * real edge ({@code hingeZ}, the door's own back edge) instead of its center, with an additional
     * swing (yRot) and droop (zRot) layered on top of the same base alignment rotation -- reads as
     * the door hanging open off that edge. Carriage redesign Phase 3, 2026-10-26.
     */
    private static EasyMeshBuilder brokenDoorPanel(final String name, final float x, final float yTopEdge, final float height, final float hingeZ, final float length) {
        final EasyMeshBuilder door = new EasyMeshBuilder(name, 0, 31);
        // Spans from the hinge (local X=0, pre-alignment) back toward the door's own front edge.
        door.addBox(-length, 0.0F, -0.5F, length, height, 1);
        door.setRotationPoint(x, yTopEdge, hingeZ);
        final float baseYRot = x < 0.0F ? Mth.PI / 2.0F : -Mth.PI / 2.0F;
        final float swingRad = (float) Math.toRadians(DOOR_SWING_DEGREES);
        door.yRot = baseYRot + (x < 0.0F ? -swingRad : swingRad);
        door.zRot = (float) Math.toRadians(DOOR_DROOP_DEGREES);
        return door;
    }

    /**
     * Real reference-photo hull taper, 2026-10-26 -- Minecraft's cuboid-only model system can't
     * author the photo's true curved body panel, so this fakes it the way voxel builds always fake a
     * curve: {@link #CURL_SEGMENTS} boxes stepping outward from the cabin's own front/back edge
     * ({@code zEdge}, in the direction {@code zDirSign} points), each shorter (bottom edge climbing
     * toward {@link #BELT_Y}) and each pulled slightly further inward in X than the last -- reading as
     * the body sweeping up and tapering to a point past the enclosed cabin, same spirit as the broken-
     * wheel/broken-door geometry work already in this model (a real shape change, not a texture trick).
     */
    private static EasyMeshBuilder hullCurl(final String name, final float sideX, final float zEdge, final float zDirSign) {
        final EasyMeshBuilder curl = new EasyMeshBuilder(name, 0, 31);
        final float thickness = 1.0F;
        for (int i = 0; i < CURL_SEGMENTS; i++) {
            final float segHeight = SKIRT_HEIGHT * (CURL_SEGMENTS - i) / (CURL_SEGMENTS + 1.0F);
            final float xOutset = Math.abs(sideX) * (1.0F - 0.12F * (i + 1));
            final float localX = sideX < 0.0F ? -xOutset : xOutset - thickness;
            final float zStart = zDirSign < 0.0F ? zEdge - CURL_SEGMENT_LENGTH * (i + 1) : zEdge + CURL_SEGMENT_LENGTH * i;
            curl.addBox(localX, BELT_Y, zStart, thickness, segHeight, CURL_SEGMENT_LENGTH);
        }
        return curl;
    }

    /** One continuous part (4 edge bars) sitting on top of the roof crown, matching the reference photo's visible brass luggage rail. */
    private static EasyMeshBuilder roofRail(final String name, final float halfWidth, final float halfLength, final float thickness, final float railY, final float centerZ) {
        final EasyMeshBuilder rail = new EasyMeshBuilder(name, 0, 54);
        rail.setRotationPoint(0.0F, railY, centerZ);
        rail.addBox(halfWidth - thickness, -thickness, -halfLength, thickness, thickness, halfLength * 2.0F);
        rail.addBox(-halfWidth, -thickness, -halfLength, thickness, thickness, halfLength * 2.0F);
        rail.addBox(-halfWidth, -thickness, -halfLength, halfWidth * 2.0F, thickness, thickness);
        rail.addBox(-halfWidth, -thickness, halfLength - thickness, halfWidth * 2.0F, thickness, thickness);
        return rail;
    }

    /** 4 corner + 2 mid-side finial posts standing on {@link #roofRail}, matching the reference photo's roof-rail finials. */
    private static EasyMeshBuilder roofFinials(final String name, final float halfWidth, final float halfLength, final float topY, final float centerZ) {
        final EasyMeshBuilder finials = new EasyMeshBuilder(name, 0, 56);
        finials.setRotationPoint(0.0F, topY, centerZ);
        final float finialHeight = 2.0F;
        final float finialSize = 0.6F;
        final float[] xs = {-halfWidth + 0.5F, halfWidth - 0.5F};
        final float[] zs = {-halfLength + 0.5F, 0.0F, halfLength - 0.5F};
        for (final float x : xs) {
            for (final float z : zs) {
                finials.addBox(x - finialSize / 2.0F, -finialHeight, z - finialSize / 2.0F, finialSize, finialHeight, finialSize);
            }
        }
        return finials;
    }

    /** Mirrors {@code WagonModel#wheel}'s own doc exactly -- same real 8-spoke hub/rim pattern, same damaged-look variant, generalized 2026-10-26 to take its own {@code radius} (front/back wheels now differ -- see this class's own top-level doc). */
    private static EasyMeshBuilder wheel(final String name, final float xOffset, final float z, final boolean brokenLook, final float radius) {
        final EasyMeshBuilder wheel = new EasyMeshBuilder(name, 46, 60);
        wheel.setRotationPoint(xOffset, -radius, z);
        wheel.addBox(-1.0F, -1.0F, -1.0F, 2, 2, 2);
        final float spokeLength = radius - 2.0F;
        final float rimZ = radius - 1.14F;
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
