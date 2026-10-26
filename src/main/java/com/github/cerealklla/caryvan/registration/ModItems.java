package com.github.cerealklla.caryvan.registration;

import java.util.HashMap;
import java.util.Map;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.item.AnimalCartItem;
import com.github.cerealklla.caryvan.item.CarriageItem;
import com.github.cerealklla.caryvan.item.HandCartItem;
import com.github.cerealklla.caryvan.item.WagonItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One {@link HandCartItem}/{@link AnimalCartItem}/{@link WagonItem} per real vanilla {@link
 * WoodType} (12 each, 36 total) -- explicit user request, 2026-10-25: "is it possible to add
 * variants to respect the wood species used when crafting?" -> "All vanilla plank types." Wagon
 * joined the other two 2026-10-26 ("make sure all the wood variants exist for the wagon"), having
 * shipped as a single plain item up to that point. Registered in a loop rather than 36 hand-written
 * fields to avoid the transcription-error risk of that much repetition; {@link #handCartFor}/
 * {@link #animalCartFor}/{@link #wagonFor} are the lookup side every caller (entities'
 * {@code getCartItem()}, the Dev Kyt contribution) should use instead of reaching into the maps
 * directly.
 */
public final class ModItems {

    private ModItems() {
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CaryvanMod.MODID);

    // WoodType is a record, not a real Java enum (confirmed via javap), so EnumMap isn't an option here.
    private static final Map<WoodType, DeferredItem<HandCartItem>> HAND_CARTS = new HashMap<>();
    private static final Map<WoodType, DeferredItem<AnimalCartItem>> ANIMAL_CARTS = new HashMap<>();
    // Added 2026-10-26, same shape as the two above -- explicit user request, "make sure all the
    // wood variants exist for the wagon," closing the one remaining single-plain-item gap.
    private static final Map<WoodType, DeferredItem<WagonItem>> WAGONS = new HashMap<>();
    // Added 2026-10-26 alongside the new Carriage entity -- same shape as the three above.
    private static final Map<WoodType, DeferredItem<CarriageItem>> CARRIAGES = new HashMap<>();
    // Wheels: a new wood-specific crafting intermediate (real user spec, 2026-10-26, matching a video
    // reference of the mod this one was adapted from: "you actually crafted the wheels separately,
    // and the wheels in conjunction with other stuff crafted the actual carts/wagons"). Plain Item,
    // no custom class needed -- it's a pure crafting material, never placed/used directly.
    private static final Map<WoodType, DeferredItem<Item>> WHEELS = new HashMap<>();

    static {
        WoodType.values().forEach(woodType -> {
            HAND_CARTS.put(woodType, ITEMS.register(
                    woodType.name() + "_hand_cart",
                    id -> new HandCartItem(new Item.Properties()
                            .stacksTo(1)
                            .setId(ResourceKey.create(Registries.ITEM, id)), woodType)));
            ANIMAL_CARTS.put(woodType, ITEMS.register(
                    woodType.name() + "_animal_cart",
                    id -> new AnimalCartItem(new Item.Properties()
                            .stacksTo(1)
                            .setId(ResourceKey.create(Registries.ITEM, id)), woodType)));
            WAGONS.put(woodType, ITEMS.register(
                    woodType.name() + "_wagon",
                    id -> new WagonItem(new Item.Properties()
                            .stacksTo(1)
                            .setId(ResourceKey.create(Registries.ITEM, id)), woodType)));
            CARRIAGES.put(woodType, ITEMS.register(
                    woodType.name() + "_carriage",
                    id -> new CarriageItem(new Item.Properties()
                            .stacksTo(1)
                            .setId(ResourceKey.create(Registries.ITEM, id)), woodType)));
            WHEELS.put(woodType, ITEMS.register(
                    woodType.name() + "_wheels",
                    id -> new Item(new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, id)))));
        });
    }

    // "Broken" cart items -- real user spec, 2026-10-26: a destroyed cart drops one of these (plus
    // Broken Wheels) instead of the intact cart item, repaired back with fresh matching Wheels.
    // Deliberately NOT one-per-wood -- a wrecked cart no longer carries its original wood identity
    // (the repair recipe restores it, picked by whichever wood's Wheels are used to fix it).
    public static final DeferredItem<Item> BROKEN_HAND_CART = ITEMS.register(
            "broken_hand_cart", id -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
    public static final DeferredItem<Item> BROKEN_ANIMAL_CART = ITEMS.register(
            "broken_animal_cart", id -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
    public static final DeferredItem<Item> BROKEN_WAGON = ITEMS.register(
            "broken_wagon", id -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
    public static final DeferredItem<Item> BROKEN_CARRIAGE = ITEMS.register(
            "broken_carriage", id -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
    // A single generic item (not per-wood, not per-cart-type) -- a broken wheel is just scrap either
    // way, whichever cart it came from.
    public static final DeferredItem<Item> BROKEN_WHEEL = ITEMS.register(
            "broken_wheel", id -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));

    public static Item handCartFor(final WoodType woodType) {
        return HAND_CARTS.get(woodType).get();
    }

    public static Item animalCartFor(final WoodType woodType) {
        return ANIMAL_CARTS.get(woodType).get();
    }

    public static Item wagonFor(final WoodType woodType) {
        return WAGONS.get(woodType).get();
    }

    public static Item carriageFor(final WoodType woodType) {
        return CARRIAGES.get(woodType).get();
    }

    public static Item wheelsFor(final WoodType woodType) {
        return WHEELS.get(woodType).get();
    }
}
