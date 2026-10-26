package com.github.cerealklla.caryvan.client.renderer.entity.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
}
