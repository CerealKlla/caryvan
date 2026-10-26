package com.github.cerealklla.caryvan.client.renderer.entity.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

/**
 * Catches the exact class of bug that let a {@code NoSuchElementException} ("Can't find part
 * wallDoorLeft") through {@code ./gradlew build}/{@code compileJava} straight to a live client,
 * crashing entity renderer registration and black-screening the whole resource-manager reload
 * (2026-10-26). {@code createLayer()}/{@code bakeRoot()} and each model's constructor (every
 * {@code ModelPart#getChild(String)} lookup) run here exactly as {@code EntityRenderersEvent
 * .RegisterLayerDefinitions} + the renderer's own real construction would, but without needing a
 * full client launch -- {@code neoForge.unitTest} (see build.gradle) wires Minecraft/NeoForge
 * client classes onto this test's own classpath for exactly this kind of pure-data check. A naming
 * mismatch between where a part is defined in {@code createLayer()}'s {@code LayerDefinition} and
 * where the constructor looks it up throws here immediately, long before a live boot-test would.
 */
final class ModelGeometryTest {

    @Test
    void wagonModelBakesAndConstructs() {
        assertDoesNotThrow(() -> {
            final ModelPart root = WagonModel.createLayer().bakeRoot();
            new WagonModel(root);
        });
    }

    @Test
    void carriageModelBakesAndConstructs() {
        assertDoesNotThrow(() -> {
            final ModelPart root = CarriageModel.createLayer().bakeRoot();
            new CarriageModel(root);
        });
    }

    @Test
    void animalCartModelBakesAndConstructs() {
        assertDoesNotThrow(() -> {
            final ModelPart root = AnimalCartModel.createLayer().bakeRoot();
            new AnimalCartModel(root);
        });
    }

    @Test
    void handCartModelBakesAndConstructs() {
        assertDoesNotThrow(() -> {
            final ModelPart root = HandCartModel.createLayer().bakeRoot();
            new HandCartModel(root);
        });
    }

    /**
     * Real gap this closes, 2026-10-26: a live user report that the Carriage renders as a flat open
     * bed with none of the cabin (walls/roof/doors/bench/platform) visible, despite {@link
     * #carriageModelBakesAndConstructs()} passing clean -- that test only proves parts resolve by
     * name, it says nothing about whether they're degenerate (zero/near-zero size) or displaced far
     * from where the rest of the body actually is. This asserts the real transformed world-space
     * extents of "body" and of each of the new cabin parts specifically, via {@link
     * ModelPart#getExtentsForGui} (confirmed via its real decompiled bytecode to walk every cube
     * regardless of {@code visible}/{@code skipDraw} -- i.e. it reports what's genuinely DEFINED).
     * Thresholds are deliberately loose (well under this model's own real authored size) -- this
     * exists to catch an actual copy-paste/degenerate-dimension regression, not to pin exact numbers.
     */
    @Test
    void carriageCabinPartsAreNonDegenerate() {
        // Real third rebuild, 2026-10-26: the whole curved-hull/stepped-taper body was scrapped for a
        // simpler faceted hexagonal-prism reference -- part names changed completely (hullCurl*/
        // roofCrown/roofRail/roofFinials/benchRiser*/rearPlatform* are all gone), so this asserts
        // against the new real part set instead.
        final ModelPart body = CarriageModel.createLayer().bakeRoot().getChild("root").getChild("body");
        assertSpanAtLeast(body, "body", 0.5F, 0.5F, 2.0F);
        assertSpanAtLeast(body.getChild("endCapFront"), "endCapFront", 1.0F, 1.0F, 0.01F);
        assertSpanAtLeast(body.getChild("lowerChamferLeft"), "lowerChamferLeft", 0.1F, 0.1F, 3.0F);
        assertSpanAtLeast(body.getChild("upperChamferLeftFront"), "upperChamferLeftFront", 0.1F, 0.1F, 1.0F);
        assertSpanAtLeast(body.getChild("roofLeft"), "roofLeft", 0.3F, 0.3F, 3.0F);
        assertSpanAtLeast(body.getChild("roofRidge"), "roofRidge", 0.1F, 0.01F, 3.0F);
        // Real fix, 2026-10-26 (carried over unchanged from the previous body): the x-span threshold
        // here is the one that actually matters for the door-frame/handle geometry specifically --
        // wallSegment's own yRot alignment maps the panel's local Z (thickness/protrusion axis) onto
        // world X. The plain, undecorated panel alone is only 1 raw unit (0.0625 blocks) thick; a real
        // frame-visibility regression would still pass a loose 0.01 threshold, so this is set well
        // above the bare-panel baseline specifically to catch that.
        assertSpanAtLeast(body.getChild("wallDoorLeft"), "wallDoorLeft", 0.15F, 0.2F, 0.3F);
        assertSpanAtLeast(body.getChild("PlatformLeft"), "PlatformLeft", 0.1F, 0.1F, 0.3F);
    }

    private static void assertSpanAtLeast(final ModelPart part, final String label, final float minXSpan, final float minYSpan, final float minZSpan) {
        final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        final float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        part.getExtentsForGui(new PoseStack(), v -> {
            min[0] = Math.min(min[0], v.x());
            min[1] = Math.min(min[1], v.y());
            min[2] = Math.min(min[2], v.z());
            max[0] = Math.max(max[0], v.x());
            max[1] = Math.max(max[1], v.y());
            max[2] = Math.max(max[2], v.z());
        });
        final float xSpan = max[0] - min[0];
        final float ySpan = max[1] - min[1];
        final float zSpan = max[2] - min[2];
        assertTrue(xSpan >= minXSpan, label + " x-span too small: " + xSpan + " (wanted >= " + minXSpan + ")");
        assertTrue(ySpan >= minYSpan, label + " y-span too small: " + ySpan + " (wanted >= " + minYSpan + ")");
        assertTrue(zSpan >= minZSpan, label + " z-span too small: " + zSpan + " (wanted >= " + minZSpan + ")");
    }
}
