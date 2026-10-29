package com.github.cerealklla.caryvan.interaction;

import com.github.cerealklla.caryvan.entity.WagonEntity;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Recalculates a Wagon's Max Speed whenever a currently-riding player's own inventory could have
 * changed -- explicit user correction, 2026-10-29: "Don't add ticks like that. There are events
 * for the inventory changing, only fire the recalculation when those events fire," replacing an
 * earlier tick-throttled poll. No single generic "inventory changed" event exists in this NeoForge
 * version (confirmed against the real decompiled source -- {@code Inventory#setChanged} is a bare
 * counter, posts nothing) -- the same gap this suite has already hit and solved the same way
 * elsewhere (see e.g. {@code merchant.MerchantListener}/{@code currency.CoinPurseListener}'s own
 * use of {@link PlayerContainerEvent.Close}): react to every real discrete event that could
 * plausibly change the riding player's main-storage slots instead of inventing a poll.
 *
 * <p><b>Known, honestly-flagged gap</b>: dragging/shift-clicking items purely within a player's own
 * already-open default inventory screen (no container actually opening/closing) fires none of
 * these -- the only real hook for that specific case would be attaching a {@code ContainerListener}
 * to the player's menu for the duration of the ride, which has no precedent anywhere in this suite
 * and wasn't built here. Not expected to matter much in practice (that screen isn't normally open
 * while actively riding/steering), but flagged rather than silently assumed covered.
 */
public final class WagonRiderInventoryListener {

    @SubscribeEvent
    public void onPickup(final ItemEntityPickupEvent.Post event) {
        recalculateIfRidingWagon(event.getPlayer());
    }

    @SubscribeEvent
    public void onContainerClose(final PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof Player player) {
            recalculateIfRidingWagon(player);
        }
    }

    @SubscribeEvent
    public void onItemCrafted(final PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof Player player) {
            recalculateIfRidingWagon(player);
        }
    }

    @SubscribeEvent
    public void onItemSmelted(final PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof Player player) {
            recalculateIfRidingWagon(player);
        }
    }

    @SubscribeEvent
    public void onUseItemFinish(final LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player player) {
            recalculateIfRidingWagon(player);
        }
    }

    @SubscribeEvent
    public void onEquipmentChange(final LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            recalculateIfRidingWagon(player);
        }
    }

    private static void recalculateIfRidingWagon(final Player player) {
        if (player.getVehicle() instanceof WagonEntity wagon) {
            wagon.recalculateMaxSpeed();
        }
    }
}
