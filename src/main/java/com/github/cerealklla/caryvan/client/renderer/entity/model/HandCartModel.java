package com.github.cerealklla.caryvan.client.renderer.entity.model;

import com.github.cerealklla.caryvan.client.renderer.entity.state.HandCartRenderState;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Hand Cart's real geometry -- adapted from NiftyCarts' {@code CartModel}/{@code HandCartModel}
 * pair (MIT licensed, see this repo's own CLAUDE.md), collapsed into one class since this mod has
 * only one cart type with a model so far (the shared wheel-building logic can be re-extracted into
 * a common base once Phase 2+ actually needs it -- not designed ahead of that need). Dimensions/
 * box placements are copied as-is from the reference, since they describe real physical geometry
 * (a cart bed, side/rear boards, an axis bar, a pulling shaft, spoked wheels), not anything
 * API-version-specific; the flower-basket cargo decoration was dropped (cosmetic extra, not needed
 * for a functional model).
 *
 * <p>Built on vanilla's own {@link net.minecraft.client.model.geom.builders.MeshDefinition}/
 * {@link ModelPart} system (unchanged from the reference's target version, confirmed by reading
 * this environment's real decompiled classes directly) -- the only real adaptation needed was
 * {@link EntityModel} now being generic over a render state type ({@link HandCartRenderState})
 * instead of the live entity, so {@link #setupAnim} reads pre-extracted state fields instead of
 * calling back into a live {@code HandCartEntity}.
 */
public final class HandCartModel extends EntityModel<HandCartRenderState> {

    private static final float RIM_LENGTH = 7.0F;
    private static final float AXLE_LENGTH = 20.0F;

    private final ModelPart leftWheel;
    private final ModelPart rightWheel;

    public HandCartModel(final ModelPart root) {
        super(root, RenderTypes::entityCutout);
        // EasyMeshBuilder#rootLayer attaches the builder passed to it as a child part named "root"
        // rather than merging its children directly into the baked mesh's own top-level part, so
        // leftWheel/rightWheel sit one level deeper than the baked root passed in here.
        final ModelPart wheels = root.getChild("root");
        this.leftWheel = wheels.getChild("leftWheel");
        this.rightWheel = wheels.getChild("rightWheel");
    }

    @Override
    public void setupAnim(final HandCartRenderState state) {
        this.rightWheel.xRot = state.wheelRotationRight;
        this.leftWheel.xRot = state.wheelRotationLeft;

        final float time = state.hurtTime;
        final float rot;
        if (time > 0.0F) {
            final float damage = Math.max(state.damageTaken, 0.0F) * 0.5F;
            rot = Mth.sin(time) * time * damage / 40.0F * -state.forwardDirection;
        } else {
            rot = 0.0F;
        }
        this.rightWheel.zRot = rot;
        this.leftWheel.zRot = rot;
    }

    public static LayerDefinition createLayer() {
        final float angle = Mth.PI / 8.0F;
        final float wheelRadius = (RIM_LENGTH / 2.0F) / (Mth.sin(angle) / Mth.cos(angle));
        final float xOffset = AXLE_LENGTH / 2.0F + 2.0F;

        final EasyMeshBuilder root = new EasyMeshBuilder("root");

        root.addChild(wheel("leftWheel", xOffset, wheelRadius));
        root.addChild(wheel("rightWheel", -xOffset, wheelRadius));

        final EasyMeshBuilder body = new EasyMeshBuilder("body");
        body.setRotationPoint(0.0F, -wheelRadius, 1.0F);

        final EasyMeshBuilder axis = new EasyMeshBuilder("axis", 0, 20);
        axis.addBox(-10F, -1, -1.0F, 20, 2, 2);

        final EasyMeshBuilder cartBase = new EasyMeshBuilder("cartBase", 0, 0);
        cartBase.addBox(-10.0F, -9.0F, -2.0F, 19, 18, 1);
        cartBase.xRot = -Mth.PI / 2.0F;
        cartBase.yRot = -Mth.PI / 2.0F;

        final EasyMeshBuilder boardFront = new EasyMeshBuilder("boardFront", 0, 30);
        boardFront.addBox(-10.0F, -10.0F, -11F, 20, 9, 1);

        final EasyMeshBuilder boardsSide0 = new EasyMeshBuilder("boardsSide0", 0, 26);
        boardsSide0.addBox(-9F, -5.0F, -2.0F, 19, 3, 1);
        boardsSide0.setRotationPoint(-11.0F, -5.0F, -1.0F);
        boardsSide0.yRot = -Mth.PI / 2.0F;

        final EasyMeshBuilder boardsSide1 = new EasyMeshBuilder("boardsSide1", 0, 26);
        boardsSide1.addBox(-10F, -5.0F, -2.0F, 19, 3, 1);
        boardsSide1.setRotationPoint(11.0F, -5.0F, -1.0F);
        boardsSide1.yRot = Mth.PI / 2.0F;

        final EasyMeshBuilder boardsSide2 = new EasyMeshBuilder("boardsSide2", 0, 26);
        boardsSide2.addBox(-9F, -1.0F, -2.0F, 19, 3, 1);
        boardsSide2.setRotationPoint(-11.0F, -5.0F, -1.0F);
        boardsSide2.yRot = -Mth.PI / 2.0F;

        final EasyMeshBuilder boardsSide3 = new EasyMeshBuilder("boardsSide3", 0, 26);
        boardsSide3.addBox(-10F, -1.0F, -2.0F, 19, 3, 1);
        boardsSide3.setRotationPoint(11.0F, -5.0F, -1.0F);
        boardsSide3.yRot = Mth.PI / 2.0F;

        final EasyMeshBuilder boardsRear0 = new EasyMeshBuilder("boardsRear0", 42, 2);
        boardsRear0.addBox(8.0F, -10.0F, 9F, 2, 9, 1);

        final EasyMeshBuilder boardsRear1 = new EasyMeshBuilder("boardsRear1", 42, 2);
        boardsRear1.addBox(-10.0F, -10.0F, 9F, 2, 9, 1);

        final EasyMeshBuilder boardsRear2 = new EasyMeshBuilder("boardsRear2", 0, 26);
        boardsRear2.addBox(-8F, -10.0F, 9F, 16, 3, 1);

        final EasyMeshBuilder boardsRear3 = new EasyMeshBuilder("boardsRear3", 0, 26);
        boardsRear3.addBox(-8F, -6, 9F, 16, 3, 1);

        final EasyMeshBuilder shaft = new EasyMeshBuilder("shaft", 0, 31);
        shaft.setRotationPoint(0.0F, -5.0F, -15.0F);
        shaft.yRot = Mth.PI / 2.0F;
        shaft.addBox(-4F, 0F, -7.0F, 20, 2, 1);
        shaft.addBox(-4F, 0F, 6.0F, 20, 2, 1);

        body.addChild(axis);
        body.addChild(cartBase);
        body.addChild(boardFront);
        body.addChild(boardsRear0);
        body.addChild(boardsRear1);
        body.addChild(boardsRear2);
        body.addChild(boardsRear3);
        body.addChild(boardsSide0);
        body.addChild(boardsSide1);
        body.addChild(boardsSide2);
        body.addChild(boardsSide3);
        body.addChild(shaft);
        root.addChild(body);

        return EasyMeshBuilder.rootLayer(root, 64, 64);
    }

    private static EasyMeshBuilder wheel(final String name, final float xOffset, final float wheelRadius) {
        final EasyMeshBuilder wheel = new EasyMeshBuilder(name, 46, 60);
        final float boxOffset = name.startsWith("left") ? -2 : 0;
        wheel.setRotationPoint(xOffset, -wheelRadius, 1.0F);
        wheel.addBox(boxOffset, -1, -1, 2, 2, 2);
        for (int i = 0; i < 8; i++) {
            final EasyMeshBuilder rim = new EasyMeshBuilder(name + "_rim_" + i, 58, 64 - ((int) RIM_LENGTH + 1));
            rim.addBox(boxOffset, -RIM_LENGTH / 2.0F, wheelRadius - 1, 2, RIM_LENGTH, 1);
            rim.xRot = i * Mth.PI / 4.0F;
            wheel.addChild(rim);

            final EasyMeshBuilder spoke = new EasyMeshBuilder(name + "_spoke_" + i, 54, 64 - Mth.ceil(wheelRadius - 1));
            spoke.addBox(0.5F + boxOffset, 1.0F, -0.5F, 1, wheelRadius - 2, 1);
            spoke.xRot = i * Mth.PI / 4.0F;
            wheel.addChild(spoke);
        }
        return wheel;
    }
}
