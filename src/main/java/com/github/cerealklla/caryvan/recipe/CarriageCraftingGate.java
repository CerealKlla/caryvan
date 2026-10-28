package com.github.cerealklla.caryvan.recipe;

import com.github.cerealklla.caryvan.CaryvanMod;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;

/**
 * The Carriage is mid-redesign (see context/decisions.md) -- not ready for players to craft while
 * its body/doors/wheels are still being iterated on live. Removes all 12 wood-species
 * {@code <wood>_carriage} crafting recipes at every datapack reload, same real removal mechanism
 * (and same reasoning for why it's the only one that exists) as Lyfe's own
 * {@code craft.VanillaRecipeStripper#onModifyRecipeJsons}: {@code RecipeManager} has no runtime
 * mutator, so this has to happen here, before each reload's own deserialization, not as a one-time
 * poke. The recipe JSON files themselves, the Carriage entity/item/renderer code, and the
 * {@code repair_*_carriage} recipes (wheel/door repair, a separate mechanic) are all untouched --
 * re-enabling crafting later is a one-line deletion of this class's registration, not a rebuild of
 * anything removed.
 *
 * <p>The item/entity stay fully reachable for ongoing dev testing via Kyt's "Dev" Kyt
 * contribution ({@code CaryvanMod}'s constructor) and {@code /kyt getDev} -- this only closes the
 * normal crafting-table path.
 */
public final class CarriageCraftingGate {

    @SubscribeEvent
    public void onModifyRecipeJsons(ModifyRecipeJsonsEvent event) {
        WoodType.values().forEach(woodType ->
                event.getRecipeJsons().remove(Identifier.fromNamespaceAndPath(CaryvanMod.MODID, woodType.name() + "_carriage")));
    }
}
