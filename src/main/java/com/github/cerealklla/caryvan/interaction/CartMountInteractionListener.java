package com.github.cerealklla.caryvan.interaction;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.registration.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Refuses a player trying to ride a {@code WagonEntity} team mount directly -- real user request,
 * 2026-10-25, after a live repro: manually riding the team horse away from the lead, then
 * dismounting, left it stranded beyond vanilla's own real leash-snap distance
 * ({@code Leashable#leashSnapDistance()}, 12.0), which kept dropping and re-establishing the
 * team-to-lead leash every tick (each drop spawning a real Lead item) until the horse eventually
 * walked back -- see {@code entity.WagonEntity}'s own doc for the full writeup. Removing the
 * ability to ride a team mount at all removes the realistic way a player could cause that
 * separation in the first place. Same real {@code PlayerInteractEvent.EntityInteract}-cancellation
 * shape Lyfe's own {@code summoning.AllayInteractionListener} already uses for an analogous
 * "this summoned entity isn't meant for direct player interaction" case.
 */
public final class CartMountInteractionListener {

    @SubscribeEvent
    public void onEntityInteract(final PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        if (event.getTarget().getExistingData(ModAttachments.CART_TEAM_MOUNT).isPresent()) {
            // Temporary diagnostic, 2026-10-26 -- real user report: "right clicking the right half
            // does not seem to be doing anything" (truly nothing -- no menu, no mount, no message)
            // for WagonEntity's new right-seat click detection, which is EXACTLY what this listener
            // silently cancelling an interaction (because the click actually landed on a nearby team
            // mount instead of the wagon itself) would look like from the player's side. Delete once
            // confirmed either way.
            CaryvanMod.LOGGER.info(
                    "[cart-mount-debug] Cancelled interact on {} (uuid={}) by {} -- this entity was tagged CART_TEAM_MOUNT, so the click never reached the wagon's own interact()",
                    event.getTarget().getType(), event.getTarget().getUUID(), event.getEntity().getName().getString());
            event.setCanceled(true);
        }
    }
}
