package com.github.cerealklla.caryvan.util;

import java.util.HashMap;
import java.util.Map;

import com.github.cerealklla.caryvan.entity.AbstractDrawnEntity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Per-level registry of "which drawn entity is currently being pulled by which puller," and the
 * single place that calls {@link AbstractDrawnEntity#pulledTick()} each server tick -- adapted from
 * NiftyCarts' {@code NiftyWorld} (MIT licensed, see this repo's own CLAUDE.md), simplified: this
 * version is deliberately NOT persisted {@code SavedData}. The real authoritative "who am I being
 * pulled by" state already lives on each cart entity itself ({@code AbstractDrawnEntity}'s own
 * persisted {@code pullingUUID} field, re-resolved via {@code attemptReattach()} on load) -- this
 * registry only exists to get {@link AbstractDrawnEntity#pulledTick()} called in the right order
 * (puller moves first, then whatever it's pulling chases it), which is a purely transient, in-memory
 * concern with nothing meaningful to persist across a restart on its own.
 */
public final class CaryvanWorld {

    private static final Map<ResourceKey<Level>, CaryvanWorld> INSTANCES = new HashMap<>();

    private final Map<java.util.UUID, AbstractDrawnEntity> pulling = new HashMap<>();

    private CaryvanWorld() {
    }

    public static CaryvanWorld get(final ServerLevel level) {
        return INSTANCES.computeIfAbsent(level.dimension(), key -> new CaryvanWorld());
    }

    /** The cart currently registered as being pulled by {@code pullerId}, if any -- the authoritative O(1) lookup {@code ActionKeyPayload} uses instead of a world entity-radius scan. */
    public java.util.Optional<AbstractDrawnEntity> getDrawnBy(final java.util.UUID pullerId) {
        return java.util.Optional.ofNullable(this.pulling.get(pullerId));
    }

    /** {@code puller == null} unregisters {@code cart} entirely. */
    public void setPulling(final AbstractDrawnEntity cart, final Entity puller) {
        this.pulling.values().remove(cart);
        if (puller != null) {
            this.pulling.put(puller.getUUID(), cart);
        }
    }

    /**
     * Called once per server tick (see {@code CaryvanMod}'s tick listener) -- advances every
     * currently-pulled drawn entity, dropping any whose puller is gone.
     *
     * <p>Real crash, 2026-10-25 ({@code ConcurrentModificationException} at the {@code removeIf}
     * this replaced): {@link AbstractDrawnEntity#pulledTick()}/{@code #shouldStopPulledTick()} can
     * themselves call {@link #setPulling} (shift-dismount, exceeding {@code MAX_SLACK_BLOCKS}, a
     * wall between puller and cart), which directly mutates this same {@link #pulling} map -- doing
     * that from inside a live {@code removeIf} iteration over that same map is exactly what
     * {@code ConcurrentModificationException} exists to catch. Iterating a snapshot instead means
     * any such nested mutation lands on the real map safely, outside the iteration entirely.
     */
    public void tick() {
        for (final AbstractDrawnEntity cart : new java.util.ArrayList<>(this.pulling.values())) {
            if (cart.shouldStopPulledTick()) {
                // shouldStopPulledTick()'s "puller is dead/gone" branch clears the cart's own
                // #pulling field directly (not via #setPulling), so it never mutates this map on
                // its own -- do that cleanup here instead. A no-op if setPulling() already removed
                // it (e.g. the "wall between puller and cart" branch, which does call setPulling()).
                this.pulling.values().remove(cart);
                continue;
            }
            if (!(cart.getPulling() instanceof AbstractDrawnEntity)) {
                cart.pulledTick();
            }
        }
    }
}
