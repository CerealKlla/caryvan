package com.github.cerealklla.caryvan.util;

import net.minecraft.world.entity.Entity;

/**
 * Shared contract between {@code entity.AbstractDrawnEntity} (Hand Cart/Animal Cart, {@code
 * VehicleEntity}-based) and {@code entity.AbstractDamageableDrawnEntity} (Wagon/Carriage, {@code
 * LivingEntity}-based, 2026-10-26 health system work) -- the two hierarchies share no common base
 * beyond plain {@code Entity}/{@code LivingEntity}, but {@link CaryvanWorld} only ever needs this
 * one tick-ordering contract (both classes already implement it identically), not either class's
 * wider surface.
 */
public interface PulledCart {

    boolean shouldStopPulledTick();

    void pulledTick();

    Entity getPulling();
}
