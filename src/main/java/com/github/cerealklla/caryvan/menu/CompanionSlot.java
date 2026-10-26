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
 *
 * <p><b>Real live-test bug fixed, 2026-10-26</b>: this originally only ever sent the "too wounded"
 * message on a failed roll -- it never actually confiscated anything, so the player walked away
 * with the item regardless (confirmed exploitable via shift-click/click-drag, 10/10 attempts, zero
 * destructions). By the time {@link #onTake} runs, vanilla's own generic removal mechanics have
 * already handed the real stack to the player; {@link AbstractDamageableDrawnEntity
 * #rollDestroyOnBrokenRemoval} is called here and, on a failed roll, this method now actually
 * empties the {@code stack} parameter -- for the plain-click/pickup path this is the SAME live
 * object already stored as the player's carried stack (mutating it here genuinely confiscates it,
 * confirmed against the real decompiled {@code AbstractContainerMenu#doClick}'s pickup branch,
 * which stores then calls {@code onTake} with the identical reference). The shift-click path has
 * its own, separate, earlier interception now too -- see {@code WagonMenu}/{@code
 * CarriageMenu#quickMoveStack}'s own doc -- so this roll should rarely if ever actually need to fire
 * for that path in practice; it stays here purely as defense-in-depth.
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
        if (this.container instanceof AbstractDamageableDrawnEntity cart && player.level() instanceof ServerLevel level) {
            if (cart.rollDestroyOnBrokenRemoval(level, this.index)) {
                stack.setCount(0);
                player.sendSystemMessage(Component.literal("Too wounded to retrieve."));
                super.onTake(player, stack);
                return;
            }
            if (LyfeSummoningBridge.isLoaded()) {
                LyfeSummoningBridge.despawnCompanionCleanly(level, stack);
            }
        }
        super.onTake(player, stack);
    }
}
