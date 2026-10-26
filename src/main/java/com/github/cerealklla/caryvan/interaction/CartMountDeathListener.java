package com.github.cerealklla.caryvan.interaction;

import com.github.cerealklla.caryvan.registration.ModAttachments;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Real user report, 2026-10-25: "it did drop 'free' leads when the horse died which it should not
 * do." Traced via the real decompiled bytecode of {@link Leashable#tickLeash}: its very first real
 * branch, before any distance check at all, is "if either the leashed entity or its holder can no
 * longer interact with the level" (either one died/was removed) -> spawn a real Lead item via
 * {@code dropLeash()}. This mod's own synthetic team-to-lead leash (see {@code
 * entity.WagonEntity#syncTeamLeash}) was never placed by a player, so this is an unearned item every
 * time -- {@code entity.WagonEntity} can override its own {@code dropLeash()} directly (see that
 * class's own doc), but the TEAM mount itself is a plain, unmodified vanilla {@code Horse} -- not a
 * type this mod owns the class of, so there's no override point on it at all.
 *
 * <p>The fix instead races vanilla's own check: {@link LivingDeathEvent} fires synchronously inside
 * {@code LivingEntity#die()}, the same tick the dying mount actually dies, which is well before any
 * future {@code Leashable#tickLeash} call for whatever's leashed TO it would next run (that only
 * happens on THAT entity's own later tick). The moment a {@code CART_TEAM_MOUNT}-tagged entity dies,
 * this immediately calls {@code removeLeash()} (not {@code dropLeash()}) on everything currently
 * leashed to it ({@link Leashable#leashableLeashedTo}, the same real static helper vanilla's own
 * shears-cutting path uses) -- clearing the relationship outright before {@code tickLeash} ever gets
 * a chance to see "holder can't interact" and spawn an item for it.
 */
public final class CartMountDeathListener {

    @SubscribeEvent
    public void onDeath(final LivingDeathEvent event) {
        final LivingEntity dying = event.getEntity();
        if (dying.getExistingData(ModAttachments.CART_TEAM_MOUNT).isEmpty()) {
            return;
        }
        for (final Leashable leashedToDying : Leashable.leashableLeashedTo(dying)) {
            if (leashedToDying.isLeashed()) {
                leashedToDying.removeLeash();
            }
        }
        if (dying instanceof Leashable leashableDying && leashableDying.isLeashed()) {
            leashableDying.removeLeash();
        }
    }
}
