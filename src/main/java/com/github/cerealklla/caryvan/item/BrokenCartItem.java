package com.github.cerealklla.caryvan.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Same real discoverability gap {@link BrokenWheelItem} already fixed, 2026-10-29: a Broken Hand
 * Cart/Animal Cart's repair recipe (itself + 2 matching Wheels -> a working cart of that wood) is a
 * plain crafting-table recipe with nothing in-game pointing a player at it -- no entity, no GUI,
 * since Hand Cart/Animal Cart deliberately don't use Wagon/Carriage's HP/repair-GUI system.
 */
public final class BrokenCartItem extends Item {

    public BrokenCartItem(final Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context, final TooltipDisplay display,
            final Consumer<Component> builder, final TooltipFlag tooltipFlag) {
        builder.accept(Component.literal("Craft with 2 Wheels of the same wood species to repair").withStyle(ChatFormatting.GRAY));
    }
}
