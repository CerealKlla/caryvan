package com.github.cerealklla.caryvan.menu;

import com.github.cerealklla.caryvan.entity.AbstractDrawnInventoryEntity;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Animal Cart's combined menu -- explicit user spec, 2026-10-25, replacing the earlier plain-
 * right-click-to-insert mount slot with "a more standard container style inventory": one reserved
 * {@link MountSlot} on the left edge (index 0), a real 27-slot cargo grid (indices 1-27, a single
 * vanilla chest's worth, indices 9-15), both backed by the SAME underlying {@link Container}
 * (the {@code AnimalCartEntity} itself, via {@code ContainerEntity}) -- not two separate
 * containers, so save/load/loot-table unpacking all come free from
 * {@code AbstractDrawnInventoryEntity}'s existing proven plumbing. Shift-click between the two
 * groups and the player's own inventory mirrors vanilla {@code ChestMenu}'s exact shape (same
 * precedent as Settlemynts' {@code GuardhouseFoodMenu}); {@link #startOpen}/{@link #removed}'s
 * {@code Container#startOpen}/{@code #stopOpen} calls are copied from the real decompiled
 * {@code ChestMenu} constructor/{@code #removed}, confirmed via {@code javap} rather than assumed.
 */
public final class AnimalCartMenu extends AbstractContainerMenu {

    public static final int MOUNT_SLOT_COUNT = 1;
    public static final int CARGO_SLOT_COUNT = 27;
    public static final int CONTAINER_SIZE = MOUNT_SLOT_COUNT + CARGO_SLOT_COUNT;

    // Two genuinely separate panels with real visible spacing between them (explicit user spec +
    // reference mockup, 2026-10-25) -- see client.AnimalCartScreen's own doc for the full layout
    // derivation (a custom standalone mount-slot panel texture, and vanilla's own exact real
    // small-chest screen coordinates for the cargo+player-inventory side, confirmed via the real
    // decompiled ContainerScreen class rather than assumed).
    // Real user report, 2026-10-25: "the summon stone is 1 pixel too far left and 1 pixel too far
    // up" relative to the drawn slot square -- nudged both by 1 to match.
    private static final int MOUNT_X = 7;
    private static final int MOUNT_Y = 28;
    private static final int CARGO_X = 43;
    private static final int CARGO_Y = 18;
    // Real user report, 2026-10-25: "the inventory items don't line up with the slots." Root cause:
    // this used 71 (where the background's SECOND blit image starts) for the real slot widget
    // positions too, but vanilla's own real ChestMenu constructor (confirmed via javap) computes
    // player-inventory slot Y as `18 + containerRows*18 + 13` = 85 for 3 rows -- the 14px gap
    // between those two numbers is the "Inventory" text label's own margin baked into that second
    // background image before its real slot squares visually begin.
    private static final int PLAYER_INV_Y = 85;

    private final Container container;

    /** Server-side: a real {@code AnimalCartEntity} backs this menu. */
    public AnimalCartMenu(final MenuType<?> type, final int containerId, final Inventory inventory, final Container container) {
        super(type, containerId);
        this.container = container;
        container.startOpen(inventory.player);
        layoutSlots(inventory);
    }

    /** Client-side reconstruction (see {@code registration.ModMenus}) -- no real entity to read from; slot contents arrive via vanilla's normal sync. */
    public AnimalCartMenu(final MenuType<?> type, final int containerId, final Inventory inventory) {
        this(type, containerId, inventory, new SimpleContainer(CONTAINER_SIZE));
    }

    private void layoutSlots(final Inventory inventory) {
        addSlot(new MountSlot(this.container, 0, MOUNT_X, MOUNT_Y));
        for (int i = 0; i < CARGO_SLOT_COUNT; i++) {
            final int col = i % 9;
            final int row = i / 9;
            addSlot(new Slot(this.container, MOUNT_SLOT_COUNT + i, CARGO_X + col * 18, CARGO_Y + row * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, CARGO_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, CARGO_X + col * 18, PLAYER_INV_Y + 58));
        }
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int slotIndex) {
        ItemStack clicked = ItemStack.EMPTY;
        final Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            final ItemStack stack = slot.getItem();
            clicked = stack.copy();
            if (slotIndex < CONTAINER_SIZE) {
                // From the mount slot or cargo -- try the player's own inventory.
                if (!moveItemStackTo(stack, CONTAINER_SIZE, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From the player's inventory -- the mount slot first if it fits there, else cargo.
                if (!moveItemStackTo(stack, 0, MOUNT_SLOT_COUNT, false) && !moveItemStackTo(stack, MOUNT_SLOT_COUNT, CONTAINER_SIZE, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return clicked;
    }

    /** "Pick Up" button -- real user spec, 2026-10-29: no sneak+right-click pickup for any cart, a button is the only way. See {@code AbstractDrawnInventoryEntity#executePickUp}'s own doc. */
    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        if (id == AbstractDrawnInventoryEntity.PICK_UP_BUTTON_ID && this.container instanceof AbstractDrawnInventoryEntity cart) {
            cart.executePickUp(player);
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    @Override
    public boolean stillValid(final Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(final Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
