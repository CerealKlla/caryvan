package com.github.cerealklla.caryvan.menu;

import com.github.cerealklla.caryvan.bridge.LyfeSummoningBridge;
import com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A reserved Companion slot (design-document.md §8 point 6, real spec + diagram 2026-10-26) --
 * mirror-image of {@link MountSlot}, only accepts a bound Allay in a Bottle (never a Summoning
 * Stone, which belongs in a {@link MountSlot} instead). Same real enforcement point/precedent as
 * {@link MountSlot}'s own doc already explains, including {@link #onTake}'s own clean-despawn fix.
 *
 * <p><b>50%-destroy roll on a Broken cart, added 2026-10-26</b> -- real user spec: "Removing a
 * Companion... from a broken wagon has a 50% chance to destroy [it]... with a message saying that
 * [it was] too wounded to retrieve." Only applies when the owning cart ({@code container}) is a
 * {@link AbstractDamageableDrawnEntity} currently at 0 HP -- a healthy cart's Companion is always
 * recovered cleanly, same as before this change.
 */
public final class CompanionSlot extends Slot {

    public CompanionSlot(final Container container, final int index, final int x, final int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(final ItemStack stack) {
        return LyfeSummoningBridge.isLoaded() && LyfeSummoningBridge.isAllayBottleItem(stack);
    }

    @Override
    public void onTake(final Player player, final ItemStack stack) {
        if (LyfeSummoningBridge.isLoaded() && player.level() instanceof ServerLevel level) {
            if (this.container instanceof AbstractDamageableDrawnEntity cart && cart.isBroken()
                    && level.getRandom().nextFloat() < 0.5F) {
                player.sendSystemMessage(Component.literal(
                        "The Companion was too wounded to retrieve."));
            } else {
                LyfeSummoningBridge.despawnCompanionCleanly(level, stack);
            }
        }
        super.onTake(player, stack);
    }
}
