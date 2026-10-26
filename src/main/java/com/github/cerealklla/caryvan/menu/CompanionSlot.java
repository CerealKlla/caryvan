package com.github.cerealklla.caryvan.menu;

import com.github.cerealklla.caryvan.bridge.LyfeSummoningBridge;

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
            LyfeSummoningBridge.despawnCompanionCleanly(level, stack);
        }
        super.onTake(player, stack);
    }
}
