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
    // Door: Carriage redesign Phase 3, 2026-10-26 -- real user spec, "mirror exactly how Wheels
    // items/recipes/advancements were built... same pattern, different part." A Carriage-only repair
    // material (the Wagon has no doors) consumed by Repair to fix a damaged door, same shape as
    // Wheels fixing a damaged wheel; see AbstractDamageableDrawnInventoryEntity#handleRepairFromCargo's
    // own doc for the shared cargo-scanning mechanism both now go through.
    private static final Map<WoodType, DeferredItem<Item>> DOORS = new HashMap<>();
    // Wainwright Station wood-species mixing, 2026-10-28 -- real user spec: a new "Parts"/"Body"
    // crafting flow where a cart's body and wheels are crafted/chosen independently (Carriage
    // excluded from this feature for now, per explicit scope confirmation). Same plain-Item,
    // no-custom-class shape as Wheels/Doors above -- these are pure crafting intermediates, never
    // placed/used directly. Originally a single shared "Cart Body" fed both Hand Cart and Animal
    // Cart; split into two separate items the same day once the user confirmed (after a real
    // comparison of the two entities' geometry/mechanics) that Hand Cart and Animal Cart are
    // genuinely distinct, not redundant -- so their Body parts/costs should be independent too.
    private static final Map<WoodType, DeferredItem<Item>> HAND_CART_BODIES = new HashMap<>();
    private static final Map<WoodType, DeferredItem<Item>> ANIMAL_CART_BODIES = new HashMap<>();
    private static final Map<WoodType, DeferredItem<Item>> WAGON_BODIES = new HashMap<>();

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
            DOORS.put(woodType, ITEMS.register(
                    woodType.name() + "_door",
                    id -> new Item(new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, id)))));
            HAND_CART_BODIES.put(woodType, ITEMS.register(
                    woodType.name() + "_hand_cart_body",
                    id -> new Item(new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, id)))));
            ANIMAL_CART_BODIES.put(woodType, ITEMS.register(
                    woodType.name() + "_animal_cart_body",
                    id -> new Item(new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, id)))));
            WAGON_BODIES.put(woodType, ITEMS.register(
                    woodType.name() + "_wagon_body",
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

    /**
     * Reverse of {@link #wheelsFor} -- added 2026-10-28 for Wagon Repair accepting a different
     * species of Wheels than the wagon's own body (real user request: "replace the wheels if broken
     * with a different kind of wheel, and actually see the new style of wheel"). Empty if {@code
     * item} isn't any real Wheels item at all.
     */
    public static java.util.Optional<WoodType> woodTypeForWheelsItem(final Item item) {
        for (final var entry : WHEELS.entrySet()) {
            if (entry.getValue().get() == item) {
                return java.util.Optional.of(entry.getKey());
            }
        }
        return java.util.Optional.empty();
    }

    public static Item doorFor(final WoodType woodType) {
        return DOORS.get(woodType).get();
    }

    public static Item handCartBodyFor(final WoodType woodType) {
        return HAND_CART_BODIES.get(woodType).get();
    }

    public static Item animalCartBodyFor(final WoodType woodType) {
        return ANIMAL_CART_BODIES.get(woodType).get();
    }

    public static Item wagonBodyFor(final WoodType woodType) {
        return WAGON_BODIES.get(woodType).get();
    }
}
