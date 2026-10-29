package com.github.cerealklla.caryvan.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Real user question, 2026-10-28: "I'm not seeing the ability to break down the broken wheels in
 * any crafting tables, where do I do it at?" -- not a bug, a discoverability gap: {@code
 * caryvan:broken_wheel_scrap} is a plain {@code minecraft:smelting} recipe (1 Iron Ingot per Broken
 * Wheel), not a crafting-table one, so there was nothing in-game pointing a player at a Furnace.
 */
public final class BrokenWheelItem extends Item {

    public BrokenWheelItem(final Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context, final TooltipDisplay display,
            final Consumer<Component> builder, final TooltipFlag tooltipFlag) {
        builder.accept(Component.literal("Smelt in furnace to recover iron").withStyle(ChatFormatting.GRAY));
    }
}
