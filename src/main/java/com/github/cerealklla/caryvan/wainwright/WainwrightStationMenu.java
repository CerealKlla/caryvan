package com.github.cerealklla.caryvan.wainwright;

import java.util.List;

import com.github.cerealklla.caryvan.registration.ModDataComponents;
import com.github.cerealklla.caryvan.registration.ModItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.WoodType;

/**
 * Wainwright Station's menu -- Parts tab (craft a Wheel/Hand Cart Body/Animal Cart Body/Wagon Body
 * of a chosen species)
 * and Body tab (assemble a Hand Cart/Animal Cart/Wagon from a Body + matching Wheels, choosing the
 * body and wheel species independently). Mirrors Lyfe's {@code CraftingStructureMenu} in shape --
 * inventory-only slots, no grid/output slot, button-id-encoded actions, same 27-main-inventory-slot
 * scan/consume range (hotbar and armor/offhand deliberately excluded, same reasoning as that class's
 * own doc) -- but a parallel copy, not a shared base class (none exists anywhere in this suite).
 *
 * <p>The species picklists themselves are NOT computed here -- {@code client.WainwrightStationScreen}
 * derives them directly from this menu's own synced inventory {@link Slot}s each frame, since it's
 * the viewing player's own inventory and needs no server round trip to know what it's carrying.
 */
public class WainwrightStationMenu extends AbstractContainerMenu {

    // Part indices (Parts tab), fixed order. A single shared PART_CART_BODY was split into
    // PART_HAND_CART_BODY/PART_ANIMAL_CART_BODY, 2026-10-28, once Hand Cart and Animal Cart were
    // confirmed genuinely distinct entities (not redundant) -- see ModItems' own doc.
    public static final int PART_WHEEL = 0;
    public static final int PART_HAND_CART_BODY = 1;
    public static final int PART_ANIMAL_CART_BODY = 2;
    public static final int PART_WAGON_BODY = 3;

    // Assembly type indices (Body tab), fixed order.
    public static final int TYPE_HAND_CART = 0;
    public static final int TYPE_ANIMAL_CART = 1;
    public static final int TYPE_WAGON = 2;

    private static final int SPECIES_COUNT = 12; // WoodType.values().count() -- real vanilla wood species.
    private static final int CRAFT_PART_BASE = 100;
    private static final int ASSEMBLE_BASE = 200;

    private static final int MAIN_INVENTORY_START = 9;
    private static final int MAIN_INVENTORY_END = 36;

    // Raised 140 -> 185 -> 200 -> 250 -> 240 -> 185, 2026-10-28, chasing a real live-test "stretching"/
    // off-screen report through several rounds (see decisions.md's full account) before the actual
    // root cause was found: a FIXED panel height can never be safe on its own, because vanilla's Auto
    // GUI Scale only *guarantees* a logical canvas of >=320x240 -- the chosen integer scale can leave
    // the real available height anywhere >=240, not >=330 or even >=185. Any constant here can still
    // overflow some player's actual window. Lyfe's `craft.CraftingStructureMenu` hit this exact bug
    // class once before (2026-10-03) and fixed it with real "shrink to fit" math instead of a bigger
    // guess -- ported that proven pattern here rather than re-shrinking a fixed number a third time.
    // BASE_INVENTORY_Y is now just the server-side/no-client-window fallback (used when this Menu is
    // constructed with no real window to measure, e.g. server-side); the client always computes its
    // own inventoryY from its real window height via computeInventoryY, see registration.ModMenus.
    public static final int BASE_INVENTORY_Y = 185;

    // Mirrors CraftingStructureMenu's own PREFERRED_IMAGE_HEIGHT/INVENTORY_BLOCK_HEIGHT/margins
    // exactly -- see that class's own doc for the full reasoning. PREFERRED_IMAGE_HEIGHT is this
    // panel's own designed-for height (client.WainwrightStationScreen's IMAGE_HEIGHT, 275); kept in
    // sync manually, same as Lyfe's own pair of classes already are.
    public static final int PREFERRED_IMAGE_HEIGHT = 275;
    public static final int INVENTORY_BLOCK_HEIGHT = 76;
    private static final int SCREEN_EDGE_MARGIN = 20;
    private static final int BOTTOM_MARGIN = 14;
    // The smallest content area that still shows the type-button row, both species grids (1 row
    // minimum each), and the preview/cost block above the inventory slots without collision.
    private static final int MIN_CONTENT_HEIGHT = 95;

    public static int computeImageHeight(final int windowHeight) {
        final int minImageHeight = MIN_CONTENT_HEIGHT + INVENTORY_BLOCK_HEIGHT + BOTTOM_MARGIN;
        return Math.max(minImageHeight, Math.min(PREFERRED_IMAGE_HEIGHT, windowHeight - SCREEN_EDGE_MARGIN));
    }

    public static int computeInventoryY(final int windowHeight) {
        return computeImageHeight(windowHeight) - INVENTORY_BLOCK_HEIGHT - BOTTOM_MARGIN;
    }

    /**
     * The inverse of {@link #computeInventoryY} -- used by {@code client.WainwrightStationScreen} to
     * derive its own {@code imageHeight} from this menu's already-fixed {@code inventoryY} (set once
     * at construction, since {@code Slot.y} is final in this MC version) rather than recomputing
     * independently from the window's current size. Keeps the two always self-consistent even across
     * a live window resize, at the cost of the panel simply not resizing live -- same accepted,
     * documented limitation as Lyfe's identical pair of methods.
     */
    public static int imageHeightForInventoryY(final int inventoryY) {
        return inventoryY + INVENTORY_BLOCK_HEIGHT + BOTTOM_MARGIN;
    }

    private final WainwrightStationBlockEntity structure;
    private final int inventoryY;

    public WainwrightStationMenu(final MenuType<?> type, final int containerId, final Inventory inventory, final WainwrightStationBlockEntity structure) {
        super(type, containerId);
        this.structure = structure;
        // Cosmetic only (Slot x/y never affects server-authoritative click handling) -- the real
        // per-client value is computed client-side, see the other constructor.
        this.inventoryY = BASE_INVENTORY_Y;
        layoutSlots(inventory);
    }

    /**
     * Client-side reconstruction (see {@code registration.ModMenus}) -- {@code inventoryY} is
     * computed by the client factory from the player's real window size (same "shrink to fit" fix
     * Lyfe's {@code CraftingStructureMenu} already uses) since {@code Slot.y} is final once
     * constructed.
     */
    public WainwrightStationMenu(final MenuType<?> type, final int containerId, final Inventory inventory, final int inventoryY) {
        super(type, containerId);
        this.structure = null;
        this.inventoryY = inventoryY;
        layoutSlots(inventory);
    }

    public int inventoryY() {
        return inventoryY;
    }

    private void layoutSlots(final Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, inventoryY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, inventoryY + 58));
        }
    }

    public static int craftPartButtonId(final int partIndex, final int speciesIndex) {
        return CRAFT_PART_BASE + partIndex * SPECIES_COUNT + speciesIndex;
    }

    public static int assembleButtonId(final int typeIndex, final int bodySpeciesIndex, final int wheelSpeciesIndex) {
        return ASSEMBLE_BASE + typeIndex * SPECIES_COUNT * SPECIES_COUNT + bodySpeciesIndex * SPECIES_COUNT + wheelSpeciesIndex;
    }

    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id >= CRAFT_PART_BASE && id < ASSEMBLE_BASE) {
            final int offset = id - CRAFT_PART_BASE;
            return craftPart(serverPlayer, offset / SPECIES_COUNT, offset % SPECIES_COUNT);
        }
        if (id >= ASSEMBLE_BASE) {
            final int offset = id - ASSEMBLE_BASE;
            final int typeIndex = offset / (SPECIES_COUNT * SPECIES_COUNT);
            final int remainder = offset % (SPECIES_COUNT * SPECIES_COUNT);
            return assemble(serverPlayer, typeIndex, remainder / SPECIES_COUNT, remainder % SPECIES_COUNT);
        }
        return false;
    }

    private static WoodType speciesAt(final int index) {
        final List<WoodType> all = WoodType.values().toList();
        return index >= 0 && index < all.size() ? all.get(index) : WoodType.OAK;
    }

    private static Item resultForPart(final int partIndex, final WoodType species) {
        return switch (partIndex) {
            case PART_WHEEL -> ModItems.wheelsFor(species);
            case PART_HAND_CART_BODY -> ModItems.handCartBodyFor(species);
            case PART_ANIMAL_CART_BODY -> ModItems.animalCartBodyFor(species);
            case PART_WAGON_BODY -> ModItems.wagonBodyFor(species);
            default -> null;
        };
    }

    /**
     * Wheel mirrors the equivalent existing shaped recipe's own total ingredient count (4 planks, 4
     * sticks, 1 iron ingot). Hand Cart Body/Animal Cart Body/Wagon Body costs set directly by the
     * user, 2026-10-28 -- Hand/Animal split into two independent parts/costs the same day, once the
     * user confirmed (after a real geometry/mechanics comparison) that Hand Cart and Animal Cart are
     * genuinely distinct entities, not redundant -- see {@code ModItems}' own doc. Wagon Body's cost
     * also gained 4 iron ingots on top of its already-set figure the same message. Hand Cart Body/
     * Animal Cart Body each gained their own 1 Chest, 2026-10-28 (real live-test report: "neither
     * cart requires a chest, which both are supposed to require 1") -- the original combined Cart
     * Body recipe (before the Hand/Animal split, same day) genuinely did include 1 Chest, but it got
     * silently dropped when the user's follow-up split-cost spec didn't re-state it. Extracted into
     * its own static method (not just inlined in {@link #craftPart}) so {@code
     * client.WainwrightStationScreen} can show the real cost/what's-missing instead of a blind
     * "Craft" button -- real user report, 2026-10-28, after the UI overlap fix shipped: "it needs to
     * show the actual recipe costs so the user knows what they have and what they are lacking."
     */
    public static java.util.Map<Item, Integer> costForPart(final int partIndex, final WoodType species) {
        final Item planks = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(species.name() + "_planks"));
        final Item fence = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(species.name() + "_fence"));
        final Item slab = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(species.name() + "_slab"));

        final java.util.Map<Item, Integer> cost = new java.util.LinkedHashMap<>();
        switch (partIndex) {
            case PART_WHEEL -> {
                cost.put(planks, 4);
                cost.put(net.minecraft.world.item.Items.STICK, 4);
                cost.put(net.minecraft.world.item.Items.IRON_INGOT, 1);
            }
            case PART_HAND_CART_BODY -> {
                cost.put(net.minecraft.world.item.Items.STICK, 4);
                cost.put(planks, 6);
                cost.put(net.minecraft.world.item.Items.CHEST, 1);
            }
            case PART_ANIMAL_CART_BODY -> {
                cost.put(net.minecraft.world.item.Items.CHEST, 1);
                cost.put(net.minecraft.world.item.Items.STICK, 2);
                cost.put(net.minecraft.world.item.Items.IRON_INGOT, 2);
                cost.put(slab, 9);
                cost.put(fence, 4);
            }
            case PART_WAGON_BODY -> {
                cost.put(fence, 6);
                cost.put(slab, 5);
                cost.put(planks, 4);
                cost.put(net.minecraft.world.item.Items.CHEST, 2);
                cost.put(net.minecraft.world.item.Items.IRON_INGOT, 4);
            }
            default -> {
            }
        }
        return cost;
    }

    /** 2 for Hand Cart/Animal Cart, 4 for Wagon -- shared between {@link #assemble} and the client's own cost display. */
    public static int wheelCountFor(final int typeIndex) {
        return typeIndex == TYPE_WAGON ? 4 : 2;
    }

    private boolean craftPart(final ServerPlayer player, final int partIndex, final int speciesIndex) {
        final WoodType species = speciesAt(speciesIndex);
        final Item result = resultForPart(partIndex, species);
        if (result == null) {
            return false;
        }
        final java.util.Map<Item, Integer> cost = costForPart(partIndex, species);

        if (!hasAndConsume(player, cost)) {
            player.sendSystemMessage(Component.literal("You don't have the components for this."));
            return false;
        }
        final ItemStack output = new ItemStack(result);
        if (!player.getInventory().add(output)) {
            player.drop(output, false);
        }
        return true;
    }

    /**
     * Consumes 1 matching Body item (Hand Cart Body/Animal Cart Body/Wagon Body, per {@code
     * typeIndex}) of {@code bodySpeciesIndex} plus the right Wheels count (2 for Hand Cart/Animal
     * Cart, 4 for Wagon) of {@code wheelSpeciesIndex}, then grants the finished cart
     * item tagged with both species -- body baked into the item itself (per-wood {@code Item}
     * instance, unchanged from before this feature), wheel species carried via {@link
     * ModDataComponents#WHEEL_WOOD_TYPE}, read back by {@code HandCartItem}/{@code AnimalCartItem}/
     * {@code WagonItem#use} on placement.
     */
    private boolean assemble(final ServerPlayer player, final int typeIndex, final int bodySpeciesIndex, final int wheelSpeciesIndex) {
        final WoodType bodySpecies = speciesAt(bodySpeciesIndex);
        final WoodType wheelSpecies = speciesAt(wheelSpeciesIndex);

        final Item bodyItem;
        final Item resultItem;
        switch (typeIndex) {
            case TYPE_HAND_CART -> {
                bodyItem = ModItems.handCartBodyFor(bodySpecies);
                resultItem = ModItems.handCartFor(bodySpecies);
            }
            case TYPE_ANIMAL_CART -> {
                bodyItem = ModItems.animalCartBodyFor(bodySpecies);
                resultItem = ModItems.animalCartFor(bodySpecies);
            }
            case TYPE_WAGON -> {
                bodyItem = ModItems.wagonBodyFor(bodySpecies);
                resultItem = ModItems.wagonFor(bodySpecies);
            }
            default -> {
                return false;
            }
        }
        final int wheelCount = wheelCountFor(typeIndex);
        final Item wheelsItem = ModItems.wheelsFor(wheelSpecies);

        final java.util.Map<Item, Integer> cost = new java.util.LinkedHashMap<>();
        cost.put(bodyItem, 1);
        cost.put(wheelsItem, wheelCount);
        if (!hasAndConsume(player, cost)) {
            player.sendSystemMessage(Component.literal("You don't have the Body/Wheels for this."));
            return false;
        }

        final ItemStack output = new ItemStack(resultItem);
        if (wheelSpecies != bodySpecies) {
            output.set(ModDataComponents.WHEEL_WOOD_TYPE.get(), wheelSpecies.name());
        }
        if (!player.getInventory().add(output)) {
            player.drop(output, false);
        }
        return true;
    }

    private boolean hasAndConsume(final ServerPlayer player, final java.util.Map<Item, Integer> cost) {
        final var inventory = player.getInventory();
        for (final var entry : cost.entrySet()) {
            int have = 0;
            for (int i = MAIN_INVENTORY_START; i < MAIN_INVENTORY_END; i++) {
                final ItemStack stack = inventory.getItem(i);
                if (stack.getItem() == entry.getKey()) {
                    have += stack.getCount();
                }
            }
            if (have < entry.getValue()) {
                return false;
            }
        }
        for (final var entry : cost.entrySet()) {
            int remaining = entry.getValue();
            for (int i = MAIN_INVENTORY_START; i < MAIN_INVENTORY_END && remaining > 0; i++) {
                final ItemStack stack = inventory.getItem(i);
                if (stack.getItem() == entry.getKey()) {
                    final int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int slotIndex) {
        final Slot slot = slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack clicked = stack.copy();
        final int mainInvSize = 27;
        if (slotIndex < mainInvSize) {
            if (!moveItemStackTo(stack, mainInvSize, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, mainInvSize, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return clicked;
    }

    @Override
    public boolean stillValid(final Player player) {
        if (structure == null) {
            return true;
        }
        final var level = structure.getLevel();
        final var pos = structure.getBlockPos();
        if (level == null || level.getBlockEntity(pos) != structure) {
            return false;
        }
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
