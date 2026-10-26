package com.github.cerealklla.caryvan.menu;

import com.github.cerealklla.caryvan.bridge.LyfeSummoningBridge;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The reserved mount slot of an {@link com.github.cerealklla.caryvan.entity.AnimalCartEntity}'s
 * menu -- only accepts a bound Summoning Stone. {@link #mayPlace} is the real enforcement point,
 * same reasoning/precedent as Settlemynts' {@code GuardhouseFoodSlot} (vanilla's own
 * {@code Slot#mayPlace} defaults to {@code true} and never consults {@code Container#canPlaceItem}
 * on its own -- a real bug that precedent found and fixed, 2026-09-30).
 */
public final class MountSlot extends Slot {

    public MountSlot(final Container container, final int index, final int x, final int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(final ItemStack stack) {
        return LyfeSummoningBridge.isLoaded() && LyfeSummoningBridge.isMountSummoningStone(stack);
    }
}
