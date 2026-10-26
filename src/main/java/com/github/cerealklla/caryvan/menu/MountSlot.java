package com.github.cerealklla.caryvan.menu;

import com.github.cerealklla.caryvan.bridge.LyfeSummoningBridge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The reserved mount slot of an {@link com.github.cerealklla.caryvan.entity.AnimalCartEntity}'s
 * menu -- only accepts a bound Summoning Stone. {@link #mayPlace} is the real enforcement point,
 * same reasoning/precedent as Settlemynts' {@code GuardhouseFoodSlot} (vanilla's own
 * {@code Slot#mayPlace} defaults to {@code true} and never consults {@code Container#canPlaceItem}
 * on its own -- a real bug that precedent found and fixed, 2026-09-30).
 *
 * <p><b>{@link #onTake} properly despawns a still-summoned mount, 2026-10-26</b> -- real user
 * report: "removing a summon stone from the wagon is killing it and putting it on cooldown."
 * {@code WagonEntity#tickLeadSlot}/{@code #tickTeamSlot} only ever notice a stale claim a tick
 * *after* the item has already left (too late to update it -- the stack Caryvan can still see at
 * that point isn't the real one the player now holds), and {@code
 * summoning.GuardCompanionSupport#tick}'s own "entity gone -> must have died" logic has no other
 * branch, so anything that later notices the dangling reference always imposes the cooldown
 * regardless of why the entity is actually gone. {@code Slot#onTake} fires at the one moment
 * Caryvan still holds the real, live stack reference about to leave -- the correct place to call a
 * proper clean despawn (recapture + clear, no cooldown) instead.
 */
public final class MountSlot extends Slot {

    public MountSlot(final Container container, final int index, final int x, final int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(final ItemStack stack) {
        return LyfeSummoningBridge.isLoaded() && LyfeSummoningBridge.isMountSummoningStone(stack);
    }

    @Override
    public void onTake(final Player player, final ItemStack stack) {
        if (LyfeSummoningBridge.isLoaded() && player.level() instanceof ServerLevel level) {
            LyfeSummoningBridge.despawnMountCleanly(level, stack);
        }
        super.onTake(player, stack);
    }
}
