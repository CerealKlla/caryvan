package com.github.cerealklla.caryvan.client;

import com.github.cerealklla.caryvan.CaryvanMod;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

/**
 * Client-only -- {@link ModelLayerLocation} is a client-only vanilla type, and referencing it from
 * a class touched on both sides (like the common {@code registration} package) risks crashing a
 * dedicated server even behind a runtime guard (see the root project's own CLAUDE.md, "client-only
 * type referenced from a common class"). Deliberately its own small class rather than folded into
 * {@code CaryvanModClient} directly, so a future Phase 2+ cart's own layer constant has an obvious
 * place to live.
 */
public final class ModModelLayers {

    private ModModelLayers() {
    }

    public static final ModelLayerLocation HAND_CART =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "hand_cart"), "main");

    public static final ModelLayerLocation ANIMAL_CART =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "animal_cart"), "main");

    public static final ModelLayerLocation WAGON =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "wagon"), "main");
}
