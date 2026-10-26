package com.github.cerealklla.caryvan.client.renderer.entity.model;

import com.github.cerealklla.caryvan.client.renderer.entity.state.WagonRenderState;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Wagon's real geometry -- explicit user request, 2026-10-25 ("Let's get the wagon's model upgraded
 * now to the real version"), replacing the placeholder box+4-flat-wheel model (see
 * design-document.md/decisions.md's own 2026-10-25 entries) with real 8-spoke wheels and a real
 * open wagon-bed structure (corner posts, rail-built side walls, real axles), same two-step
 * "mechanic first, real model later" precedent Hand Cart and Animal Cart both already used.
 *
 * <p>Wheel geometry is the same proven 8-spoke hub/rim pattern {@link AnimalCartModel} already
 * established this session (itself adapted from AstikorCarts, MIT licensed -- see this repo's own
 * {@code THIRD_PARTY_NOTICES.md}), at the SAME literal size as Animal Cart's own wheels (real user
 * report, 2026-10-25: an initial scaled-down pass made these visibly smaller -- {@link
 * #WHEEL_RADIUS} now matches Animal Cart's own real wheel rotation point exactly, which also
 * raises the whole bed off the ground to match since the body's own rotation point is tied
 * directly to this same constant). The bed/wall structure is loosely informed by NiftyCarts' own
 * real {@code WagonModel}/{@code
 * CartModel} (also MIT, github.com/jmb05/nifty-carts -- pulled directly from its repo to check
 * before writing any of this) but substantially simplified: that reference's own Wagon is a full
 * covered wagon with a 3-state furl-able canvas roof and three separately-lidded internal chests,
 * neither of which this mod's {@code WagonEntity} has any mechanic for (one unified 54-slot cargo
 * inventory, no roof-state concept at all) -- porting either would mean inventing new interaction
 * mechanics this mod doesn't want yet, not just upgrading a model, so only the real open-bed/wall/
 * axle shape was adapted; the driving bench (this mod's own addition, with no NiftyCarts
 * equivalent at all) is kept exactly as before.
 *
 * <p>Wood-species texture variants (the same per-wood-type treatment Hand Cart/Animal Cart both
 * already have) are a deliberate, separate follow-up -- not built this pass. The current single
 * tiled-oak-planks texture has no hand-painted per-region detail at all (confirmed by direct pixel
 * sampling before writing this -- only 7 distinct colors across 500 random samples), so the new
 * geometry's exact UV placement carries none of the "misplaced accent" risk the wheel-rim iron
 * accent did for Hand Cart/Animal Cart's own real-model passes.
 *
 * <p>Real user report, 2026-10-25, after first seeing this geometry live: the {@link #WHEEL_RADIUS}
 * increase above (to match Animal Cart's real size) made the front/back wheels' real circular
 * extents overlap, since the axles were still only 20 units apart -- see {@link #LENGTH_SCALE}'s
 * own doc for the fix. The same report also asked for a real seat, not just the backrest the
 * original bench box actually was -- see {@code #createLayer()}'s own `benchBack`/`benchSeat` split
 * for that fix, and {@code entity.WagonEntity#getPassengerAttachmentPoint}'s own doc for a related
 * real bug this surfaced (the seat was never actually rotated with the wagon at all).
 */
public final class WagonModel extends EntityModel<WagonRenderState> {

    // Real user report, 2026-10-25: "The wheels on the Wagon are considerably smaller than the
    // wheels on the Animal Cart... update the Wagon to be higher off the ground and use the same
    // size wheels as the Animal Cart." Raised 5.0 -> 11.0 to match AnimalCartModel's own real wheel
    // rotation point (-11.0, confirmed by re-reading that class directly) -- #createLayer's body
    // rotation point is tied directly to this same constant (same pattern NiftyCarts' own
    // CartModel#createBody uses), so raising it also raises the whole bed off the ground for free,
    // satisfying both halves of the request with the one change.
    private static final float WHEEL_RADIUS = 11.0F;

    private final ModelPart leftWheelFront;
    private final ModelPart rightWheelFront;
    private final ModelPart leftWheelBack;
    private final ModelPart rightWheelBack;
    private final ModelPart fallenWheelFront;
    private final ModelPart fallenWheelBack;

    public WagonModel(final ModelPart root) {
        super(root, RenderTypes::entityCutout);
        // EasyMeshBuilder#rootLayer nests the builder passed to it as a "root" child, same gotcha
        // HandCartModel/AnimalCartModel both already document -- one level deeper than naive.
        final ModelPart parts = root.getChild("root");
        this.leftWheelFront = parts.getChild("leftWheelFront");
        this.rightWheelFront = parts.getChild("rightWheelFront");
        this.leftWheelBack = parts.getChild("leftWheelBack");
        this.rightWheelBack = parts.getChild("rightWheelBack");
        this.fallenWheelFront = parts.getChild("fallenWheelFront");
        this.fallenWheelBack = parts.getChild("fallenWheelBack");
    }

    /**
     * Real bug fix, 2026-10-26: Wagon genuinely has 4 independent wheels (see {@code
     * WagonRenderState#wheelRotation}'s own doc) -- each of the 4 real {@code ModelPart}s here now
     * gets its own independent rotation from the matching array index (0 = leftFront, 1 =
     * rightFront, 2 = leftBack, 3 = rightBack, matching this class's own child declaration order
     * exactly), replacing the old left/right-shared-across-front-and-back pairing.
     *
     * <p>Design reversal, same day, real live-test feedback: a broken wheel no longer hides its own
     * {@code ModelPart} (the earlier pass's approach) -- "I would like to see a visibly broken wheel
     * still attached... it could still 'limp along' on the broken wheel until they all are broken,
     * then it'd crash onto the ground." A wheel stays geometrically present and keeps rotating
     * regardless of its own broken state; the damaged look now comes entirely from
     * {@code WagonRenderer}'s own whole-body "wheels damaged" texture tier instead (see that class's
     * own doc), and the dramatic full-wreck presentation (tilt, broken texture) is reserved for the
     * fully-Broken (0 HP, all 4 wheels broken) state alone.
     *
     * <p>Real live-test polish request, same day: the Broken model "looks cool" but the tilt itself
     * has "no visible cause" -- {@link #fallenWheelFront}/{@link #fallenWheelBack} (built in
     * {@link #createLayer()}, lying flat, offset outboard of the real wheels) are now shown only
     * while {@code state.broken}, to visually explain why the wagon is listing to one side.
     */
    @Override
    public void setupAnim(final WagonRenderState state) {
        this.leftWheelFront.xRot = state.wheelRotation[0];
        this.rightWheelFront.xRot = state.wheelRotation[1];
        this.leftWheelBack.xRot = state.wheelRotation[2];
        this.rightWheelBack.xRot = state.wheelRotation[3];
        this.fallenWheelFront.visible = state.broken;
        this.fallenWheelBack.visible = state.broken;
    }

    // Real user report, 2026-10-25: "lengthen the wagon by about 50%; the wheels are overlaping
    // from front and back right now." Root cause: WHEEL_RADIUS=11 (just raised to match Animal
    // Cart's own size, see that constant's own doc) makes each wheel's real circular extent span
    // 22 units -- with axles only 20 units apart (frontZ=-10/backZ=10), the front and back wheels'
    // circles genuinely overlapped in the middle. Every Z-axis (length) dimension below is scaled
    // by this factor; X-axis (width) dimensions are deliberately untouched, since only length was
    // reported as a problem.
    private static final float LENGTH_SCALE = 1.5F;

    // Real user report, 2026-10-25: "The wheels need to be wider bit a little bit, maybe 10%" --
    // the wheel TRACK (how far apart the left/right wheels sit), not the wheels' own thickness.
    // Widened again 2026-10-26, real live-test feedback: "widen the wheel well again, moving them
    // another 5% apart or so" -- 1.1 -> 1.155. Widened a third time same day: "widen another 10%
    // across the width" -- 1.155 * 1.10 = 1.2705.
    private static final float TRACK_SCALE = 1.2705F;

    public static LayerDefinition createLayer() {
        final EasyMeshBuilder root = new EasyMeshBuilder("root");

        final float xOffset = 14.0F * TRACK_SCALE;
        final float frontZ = -10.0F * LENGTH_SCALE;
        final float backZ = 10.0F * LENGTH_SCALE;

        root.addChild(wheel("leftWheelFront", xOffset, frontZ));
        root.addChild(wheel("rightWheelFront", -xOffset, frontZ));
        root.addChild(wheel("leftWheelBack", xOffset, backZ));
        root.addChild(wheel("rightWheelBack", -xOffset, backZ));

        // Real live-test polish request, 2026-10-26: "add 2 broken wheels visibly lying on the ground
        // next to the wagon" when fully Broken, to visually explain the tilt (currently no visible
        // cause). Lightest implementation -- no new art: reuses the same real 8-spoke #wheel geometry,
        // just laid flat (zRot) and placed outboard of the real wheel track, near ground level. Only
        // shown while broken (see #setupAnim).
        final float fallenWheelX = xOffset + 6.0F;
        final float fallenWheelY = -2.0F;
        final EasyMeshBuilder fallenWheelFront = wheel("fallenWheelFront", fallenWheelX, frontZ);
        fallenWheelFront.setRotationPoint(fallenWheelX, fallenWheelY, frontZ);
        fallenWheelFront.zRot = Mth.PI / 2.0F;
        root.addChild(fallenWheelFront);

        final EasyMeshBuilder fallenWheelBack = wheel("fallenWheelBack", fallenWheelX, backZ);
        fallenWheelBack.setRotationPoint(fallenWheelX, fallenWheelY, backZ);
        fallenWheelBack.zRot = Mth.PI / 2.0F;
        root.addChild(fallenWheelBack);

        final EasyMeshBuilder body = new EasyMeshBuilder("body");
        body.setRotationPoint(0.0F, -WHEEL_RADIUS, 0.0F);

        final EasyMeshBuilder base = new EasyMeshBuilder("base", 0, 0);
        base.addBox(-13.0F, -9.0F, -2.0F, 27, 18.0F * LENGTH_SCALE, 1);
        base.xRot = -Mth.PI / 2.0F;
        base.yRot = -Mth.PI / 2.0F;

        // Real axles connecting each pair of wheels -- structural dressing new this pass, matching
        // NiftyCarts' own reference (which also models real axles, not just free-floating wheels).
        final EasyMeshBuilder axleFront = new EasyMeshBuilder("axleFront", 0, 20);
        axleFront.addBox(-xOffset, -1.0F, -1.0F, xOffset * 2.0F, 2, 2);
        axleFront.setRotationPoint(0.0F, 0.0F, frontZ);

        final EasyMeshBuilder axleBack = new EasyMeshBuilder("axleBack", 0, 20);
        axleBack.addBox(-xOffset, -1.0F, -1.0F, xOffset * 2.0F, 2, 2);
        axleBack.setRotationPoint(0.0F, 0.0F, backZ);

        // Corner posts + top/bottom side rails replace the old single flat side panel -- reads as a
        // real built wagon side (NiftyCarts' own wagon uses the same "beams + rails" shape for its
        // side walls, see this class's own doc) instead of one solid plank.
        final float postHeight = 8.0F;
        final float postFrontZ = -15.0F * LENGTH_SCALE;
        final float postBackZ = 14.0F * LENGTH_SCALE;
        final EasyMeshBuilder postFrontLeft = cornerPost("postFrontLeft", -14.0F, -postHeight, postFrontZ);
        final EasyMeshBuilder postFrontRight = cornerPost("postFrontRight", 13.0F, -postHeight, postFrontZ);
        final EasyMeshBuilder postBackLeft = cornerPost("postBackLeft", -14.0F, -postHeight, postBackZ);
        final EasyMeshBuilder postBackRight = cornerPost("postBackRight", 13.0F, -postHeight, postBackZ);

        final EasyMeshBuilder sideLeftTop = sideRail("sideLeftTop", -15.0F, -8.0F);
        final EasyMeshBuilder sideLeftBottom = sideRail("sideLeftBottom", -15.0F, -3.0F);
        final EasyMeshBuilder sideRightTop = sideRail("sideRightTop", 15.0F, -8.0F);
        final EasyMeshBuilder sideRightBottom = sideRail("sideRightBottom", 15.0F, -3.0F);

        final float sideFrontZ = -15.0F * LENGTH_SCALE;
        final float sideBackZ = 15.0F * LENGTH_SCALE;

        final EasyMeshBuilder sideFront = new EasyMeshBuilder("sideFront", 0, 31);
        sideFront.addBox(-14.0F, -5.0F, -1.0F, 28, 4, 1);
        sideFront.setRotationPoint(0.0F, -5.0F, sideFrontZ);

        final EasyMeshBuilder sideBack = new EasyMeshBuilder("sideBack", 0, 31);
        sideBack.addBox(-14.0F, -5.0F, -1.0F, 28, 4, 1);
        sideBack.setRotationPoint(0.0F, -5.0F, sideBackZ);

        // The driving bench -- real user report, 2026-10-25: "add a visual bench to the front,
        // right now there is a vertical piece (like a back rest) but no actual seat." The old single
        // box WAS only ever the backrest half of a bench; a real flat seat slab is added here,
        // attached at the backrest's own base and extending back into the bed -- the two together
        // now actually read as a bench, not a lone vertical plank.
        //
        // Real user follow-up, same day: "The bench is facing toward the back, not toward the
        // front" / "the player is still sitting in the very back of the wagon, no matter which way
        // the wagon rotates." First attempt flipped benchZ's own sign (moving the whole bench to the
        // opposite end of the wagon, near sideBack instead of sideFront) on a theory -- cross-checked
        // against AbstractDrawnEntity#mountSpawnPosition's forward-vector math -- that this entity's
        // real "forward" is +Z, not -Z. **That was wrong, confirmed live**: "Now the wagon is facing
        // entirely backwards" -- moving the whole bench assembly to the other end of the wagon only
        // made it worse, proving -Z (next to sideFront, where it always was) really is the correct
        // end after all. The ACTUAL bug was conflating two separate things: WHERE along the wagon's
        // length the bench sits (anchor position -- was already correct) vs. WHICH WAY a seated
        // person faces (the backrest/seat's relative arrangement -- was NOT correct). benchZ is back
        // to its original -16 (scaled), unchanged from the first bench pass; instead benchBack and
        // benchSeat's own relative positions are swapped (backrest now on the inner/bed side, seat
        // extending toward the true front) so a seated driver faces the right way without relocating
        // the bench at all.
        final float benchZ = -16.0F * LENGTH_SCALE;
        final EasyMeshBuilder benchBack = new EasyMeshBuilder("benchBack", 0, 36);
        benchBack.addBox(-11.0F, -7.0F, 0.0F, 22, 7, 1);
        benchBack.setRotationPoint(0.0F, -9.0F, benchZ);

        final EasyMeshBuilder benchSeat = new EasyMeshBuilder("benchSeat", 0, 44);
        benchSeat.addBox(-11.0F, -1.0F, -8.0F, 22, 1, 8);
        benchSeat.setRotationPoint(0.0F, -9.0F, benchZ);

        body.addChild(base);
        body.addChild(axleFront);
        body.addChild(axleBack);
        body.addChild(postFrontLeft);
        body.addChild(postFrontRight);
        body.addChild(postBackLeft);
        body.addChild(postBackRight);
        body.addChild(sideLeftTop);
        body.addChild(sideLeftBottom);
        body.addChild(sideRightTop);
        body.addChild(sideRightBottom);
        body.addChild(sideFront);
        body.addChild(sideBack);
        body.addChild(benchBack);
        body.addChild(benchSeat);
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
        // Rail length is itself a Z-axis (length) dimension -- scaled along with everything else
        // this pass, see #LENGTH_SCALE's own doc.
        rail.addBox(-15.0F * LENGTH_SCALE, -1.0F, -0.5F, 30.0F * LENGTH_SCALE, 2, 1);
        rail.setRotationPoint(x, y, 0.0F);
        rail.yRot = x < 0.0F ? Mth.PI / 2.0F : -Mth.PI / 2.0F;
        return rail;
    }

    /**
     * Same real 8-spoke hub/rim pattern {@link AnimalCartModel}'s own wheel builders use, now
     * copied at the SAME literal box dimensions (hub, rim, spoke) rather than a scaled-down
     * approximation -- real user report, 2026-10-25, that the first pass's proportional scale-down
     * made the Wagon's wheels visibly smaller than Animal Cart's. Kept centered/symmetric around
     * x=0 (unlike Animal Cart's own left/right-specific offset boxes) since this one function is
     * reused for all 4 wheels via the {@code xOffset} sign -- already proven safe for this model's
     * own simpler wheel shape, see the previous geometry pass.
     */
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
