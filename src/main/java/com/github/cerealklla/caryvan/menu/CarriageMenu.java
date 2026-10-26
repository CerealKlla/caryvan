package com.github.cerealklla.caryvan.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Carriage's combined menu -- same shape as {@link WagonMenu}, extended from 2 Companion slots to 6
 * (real user spec, 2026-10-26: "Driver and Passenger up front, and 4 internal seats for
 * non-combatants"): 2 reserved {@link MountSlot}s (lead/team, same as Wagon), then 6 reserved
 * {@link CompanionSlot}s -- front-left (Driver)/front-right (Passenger), then two more rows of 2
 * (the 4 interior seats). See {@code menu.client.CarriageScreen}'s own doc for the taller mount
 * panel this is laid out against (same width as Wagon's, just 3 more slot rows added).
 */
public final class CarriageMenu extends AbstractContainerMenu {

    public static final int MOUNT_SLOT_COUNT = 2;
    public static final int COMPANION_SLOT_COUNT = 6;
    private static final int RESERVED_SLOT_COUNT = MOUNT_SLOT_COUNT + COMPANION_SLOT_COUNT;
    // Same 54-slot (6-row) cargo hold as the Wagon -- not asked to change, so left unchanged.
    public static final int CARGO_SLOT_COUNT = 54;
    public static final int CARGO_ROWS = CARGO_SLOT_COUNT / 9;
    public static final int CONTAINER_SIZE = RESERVED_SLOT_COUNT + CARGO_SLOT_COUNT;

    // Derived from carriage_mount_panel.png's own generation script
    // (build_carriage_mount_panel.py, panel-local positions printed at generation time), each
    // converted to real menu-screen coordinates the exact same way WagonMenu's own constants were:
    // panel-local + 1 for X, panel-local + CarriageScreen.MOUNT_PANEL_Y_OFFSET(21) + 1 for Y. The
    // mount panel is the SAME WIDTH as the Wagon's own (58px -- unchanged, only taller), so the X
    // constants below are identical to WagonMenu's; only the 3 new seat-row Y values differ.
    private static final int MOUNT_X = 21;
    private static final int MOUNT_LEAD_Y = 28;
    private static final int MOUNT_TEAM_Y = 53;
    private static final int SEAT_LEFT_X = 7;
    private static final int SEAT_RIGHT_X = 36;
    private static final int FRONT_SEAT_Y = 78;
    private static final int INTERIOR_SEAT_1_Y = 103;
    private static final int INTERIOR_SEAT_2_Y = 128;
    // Panel width unchanged from Wagon's (58px) -- same real CARGO_X formula (panel width + GAP(6) +
    // vanilla's own 8px chest-grid margin), so this is numerically identical to WagonMenu's own.
    private static final int CARGO_X = 43 + 29;
    private static final int CARGO_Y = 18;
    private static final int PLAYER_INV_Y = 18 + CARGO_ROWS * 18 + 13;

    private final Container container;

    /** Server-side: a real {@code CarriageEntity} backs this menu. */
    public CarriageMenu(final MenuType<?> type, final int containerId, final Inventory inventory, final Container container) {
        super(type, containerId);
        this.container = container;
        container.startOpen(inventory.player);
        layoutSlots(inventory);
    }

    /** Client-side reconstruction (see {@code registration.ModMenus}) -- no real entity to read from; slot contents arrive via vanilla's normal sync. */
    public CarriageMenu(final MenuType<?> type, final int containerId, final Inventory inventory) {
        this(type, containerId, inventory, new SimpleContainer(CONTAINER_SIZE));
    }

    private void layoutSlots(final Inventory inventory) {
        addSlot(new MountSlot(this.container, 0, MOUNT_X, MOUNT_LEAD_Y));
        addSlot(new MountSlot(this.container, 1, MOUNT_X, MOUNT_TEAM_Y));
        addSlot(new CompanionSlot(this.container, 2, SEAT_LEFT_X, FRONT_SEAT_Y));
        addSlot(new CompanionSlot(this.container, 3, SEAT_RIGHT_X, FRONT_SEAT_Y));
        addSlot(new CompanionSlot(this.container, 4, SEAT_LEFT_X, INTERIOR_SEAT_1_Y));
        addSlot(new CompanionSlot(this.container, 5, SEAT_RIGHT_X, INTERIOR_SEAT_1_Y));
        addSlot(new CompanionSlot(this.container, 6, SEAT_LEFT_X, INTERIOR_SEAT_2_Y));
        addSlot(new CompanionSlot(this.container, 7, SEAT_RIGHT_X, INTERIOR_SEAT_2_Y));
        for (int i = 0; i < CARGO_SLOT_COUNT; i++) {
            final int col = i % 9;
            final int row = i / 9;
            addSlot(new Slot(this.container, RESERVED_SLOT_COUNT + i, CARGO_X + col * 18, CARGO_Y + row * 18));
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
                if (!moveItemStackTo(stack, CONTAINER_SIZE, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!moveItemStackTo(stack, 0, RESERVED_SLOT_COUNT, false) && !moveItemStackTo(stack, RESERVED_SLOT_COUNT, CONTAINER_SIZE, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            // Same fix as WagonMenu's own quickMoveStack -- see that class's own doc.
            slot.onTake(player, clicked);
        }
        return clicked;
    }

    /** Real GUI buttons, 2026-10-26 -- mirrors {@code WagonMenu#clickMenuButton}'s own doc exactly. */
    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        if (this.container instanceof com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity cart) {
            if (id == com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity.PICK_UP_BUTTON_ID) {
                // See WagonMenu#clickMenuButton's own doc -- sends the confirm dialog, doesn't execute directly.
                cart.requestPickUpConfirm(player);
                return true;
            }
            if (id == com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity.REPAIR_BUTTON_ID) {
                cart.handleRepairAttempt(player, player.getMainHandItem());
                return true;
            }
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
