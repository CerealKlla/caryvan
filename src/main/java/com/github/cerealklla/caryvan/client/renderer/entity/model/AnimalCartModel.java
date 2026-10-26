package com.github.cerealklla.caryvan.client.renderer.entity.model;

import com.github.cerealklla.caryvan.client.renderer.entity.state.AnimalCartRenderState;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Animal Cart's real geometry -- the earlier placeholder box+4-flat-wheel model ({@code
 * decisions.md}, 2026-10-25) replaced with real spoked-wheel/wagon-bed geometry adapted from
 * AstikorCarts' own {@code AnimalCartModel}/{@code CartModel} pair (MIT licensed, github.com/
 * MennoMax/astikor-carts -- see this repo's own {@code THIRD_PARTY_NOTICES.md}; this is a second,
 * separate attribution alongside NiftyCarts, since AstikorCarts is the one that actually kept the
 * original "Animal Cart" concept NiftyCarts itself dropped in favor of its own different cart
 * types). Box dimensions/UV offsets are copied as-is from the reference -- they describe real
 * physical geometry (a wagon bed, side/front/back boards, an axle, a pulling shaft, spoked wheels),
 * not anything API-version-specific, same reasoning {@code HandCartModel}'s own doc already used.
 *
 * <p>Built on the same proven {@link EasyMeshBuilder}/{@link ModelPart} pattern {@code
 * HandCartModel} already established this session -- same {@link #setupAnim} shape (reads
 * pre-extracted render-state fields only), same {@code root.getChild("root").getChild(...)}
 * lookup (see {@code EasyMeshBuilder.md}'s own doc for why). The reference mod's own shared
 * abstract {@code CartModel<T>} base (for its own Supply Cart/Plow siblings, neither of which this
 * mod has yet) is deliberately not mirrored here -- same "one cart type with a model so far, worth
 * re-extracting once a second one genuinely needs the shared shape" judgment call
 * {@code HandCartModel}'s own doc already made, now actually reached (Animal Cart having its own
 * distinct per-part dimensions from Hand Cart) but still deferred since neither model's own
 * wheel-building code is large enough yet to justify the abstraction.
 */
public final class AnimalCartModel extends EntityModel<AnimalCartRenderState> {

    private final ModelPart leftWheel;
    private final ModelPart rightWheel;

    public AnimalCartModel(final ModelPart root) {
        super(root, RenderTypes::entityCutout);
        final ModelPart parts = root.getChild("root");
        this.leftWheel = parts.getChild("leftWheel");
        this.rightWheel = parts.getChild("rightWheel");
    }

    @Override
    public void setupAnim(final AnimalCartRenderState state) {
        this.rightWheel.xRot = state.wheelRotationRight;
        this.leftWheel.xRot = state.wheelRotationLeft;
    }

    public static LayerDefinition createLayer() {
        final EasyMeshBuilder root = new EasyMeshBuilder("root");

        root.addChild(leftWheel());
        root.addChild(rightWheel());

        final EasyMeshBuilder body = new EasyMeshBuilder("body");
        body.setRotationPoint(0.0F, -11.0F, 1.0F);

        final EasyMeshBuilder axis = new EasyMeshBuilder("axis", 0, 21);
        axis.addBox(-12.5F, -1.0F, -1.0F, 25, 2, 2);

        final EasyMeshBuilder cartBase = new EasyMeshBuilder("cartBase", 0, 0);
        cartBase.addBox(-15.5F, -10.0F, -2.0F, 29, 20, 1);
        cartBase.xRot = -Mth.PI / 2.0F;
        cartBase.yRot = -Mth.PI / 2.0F;

        final EasyMeshBuilder shaft = new EasyMeshBuilder("shaft", 0, 25);
        shaft.setRotationPoint(0.0F, -5.0F, -15.0F);
        shaft.yRot = Mth.PI / 2.0F;
        shaft.addBox(0.0F, -0.5F, -8.0F, 20, 2, 1);
        shaft.addBox(0.0F, -0.5F, 7.0F, 20, 2, 1);

        final EasyMeshBuilder boardLeft = new EasyMeshBuilder("boardLeft", 0, 28);
        boardLeft.addBox(-10.0F, -14.5F, 9.0F, 8, 31, 2);
        boardLeft.xRot = -Mth.PI / 2.0F;
        boardLeft.zRot = Mth.PI / 2.0F;

        final EasyMeshBuilder boardRight = new EasyMeshBuilder("boardRight", 0, 28);
        boardRight.addBox(-10.0F, -14.5F, -11.0F, 8, 31, 2);
        boardRight.xRot = -Mth.PI / 2.0F;
        boardRight.zRot = Mth.PI / 2.0F;

        final EasyMeshBuilder boardBack = new EasyMeshBuilder("boardBack", 20, 28);
        boardBack.addBox(-9.0F, -10.0F, 12.5F, 18, 8, 2);

        final EasyMeshBuilder boardFront = new EasyMeshBuilder("boardFront", 20, 28);
        boardFront.addBox(-9.0F, -10.0F, -16.5F, 18, 8, 2);

        body.addChild(axis);
        body.addChild(cartBase);
        body.addChild(shaft);
        body.addChild(boardLeft);
        body.addChild(boardRight);
        body.addChild(boardBack);
        body.addChild(boardFront);
        root.addChild(body);

        return EasyMeshBuilder.rootLayer(root, 64, 64);
    }

    // Left/right wheels are kept as two explicit, separately-written builders (not one shared
    // helper parameterized by offset sign) -- a first attempt at unifying them into one method via
    // a sign-based conditional had a real bug in the rim offset's own sign logic, caught before
    // this ever compiled against a live client; copying the reference's own two explicit blocks
    // verbatim is simpler to verify correct than a clever-but-error-prone unification.
    private static EasyMeshBuilder leftWheel() {
        final EasyMeshBuilder wheel = new EasyMeshBuilder("leftWheel", 46, 60);
        wheel.setRotationPoint(14.5F, -11.0F, 1.0F);
        wheel.addBox(-2.0F, -1.0F, -1.0F, 2, 2, 2);
        for (int i = 0; i < 8; i++) {
            final EasyMeshBuilder rim = new EasyMeshBuilder("leftWheel_rim_" + i, 58, 54);
            rim.addBox(-2.0F, -4.5F, 9.86F, 2, 9, 1);
            rim.xRot = i * Mth.PI / 4.0F;
            wheel.addChild(rim);

            final EasyMeshBuilder spoke = new EasyMeshBuilder("leftWheel_spoke_" + i, 54, 54);
            spoke.addBox(-1.5F, 1.0F, -0.5F, 1, 9, 1);
            spoke.xRot = i * Mth.PI / 4.0F;
            wheel.addChild(spoke);
        }
        return wheel;
    }

    private static EasyMeshBuilder rightWheel() {
        final EasyMeshBuilder wheel = new EasyMeshBuilder("rightWheel", 46, 60);
        wheel.setRotationPoint(-14.5F, -11.0F, 1.0F);
        wheel.addBox(0.0F, -1.0F, -1.0F, 2, 2, 2);
        for (int i = 0; i < 8; i++) {
            final EasyMeshBuilder rim = new EasyMeshBuilder("rightWheel_rim_" + i, 58, 54);
            rim.addBox(0.0F, -4.5F, 9.86F, 2, 9, 1);
            rim.xRot = i * Mth.PI / 4.0F;
            wheel.addChild(rim);

            final EasyMeshBuilder spoke = new EasyMeshBuilder("rightWheel_spoke_" + i, 54, 54);
            spoke.addBox(0.5F, 1.0F, -0.5F, 1, 9, 1);
            spoke.xRot = i * Mth.PI / 4.0F;
            wheel.addChild(spoke);
        }
        return wheel;
    }
}
