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
 * Wagon's combined menu -- same base shape as {@link AnimalCartMenu}, with two reserved
 * {@link MountSlot}s (lead index 0, team index 1, stacked vertically) plus, as of 2026-10-26, two
 * reserved {@link CompanionSlot}s (index 2/3, side by side below the mount slots) -- the real
 * layout from design-document.md §8's own diagram ("S / S / [C C]"). See
 * {@code menu.client.WagonScreen}'s own doc for the wider/taller mount panel this was laid out
 * against.
 */
public final class WagonMenu extends AbstractContainerMenu {

    public static final int MOUNT_SLOT_COUNT = 2;
    public static final int COMPANION_SLOT_COUNT = 2;
    private static final int RESERVED_SLOT_COUNT = MOUNT_SLOT_COUNT + COMPANION_SLOT_COUNT;
    // Real user request, 2026-10-25: "we need to double the storage capacity though, to match a
    // double chest" -- 54 slots (6 rows), not 27.
    public static final int CARGO_SLOT_COUNT = 54;
    public static final int CARGO_ROWS = CARGO_SLOT_COUNT / 9;
    public static final int CONTAINER_SIZE = RESERVED_SLOT_COUNT + CARGO_SLOT_COUNT;

    // Derived from wagon_mount_panel.png's own generation script (build_wagon_mount_panel_v2.py,
    // slot-local positions printed at generation time: lead (20,6), team (20,31), companions
    // (6,56)/(35,56)), each converted to real menu-screen coordinates via panel-local + 1 (the same
    // nudge AnimalCartMenu's own single slot needed, real user feedback 2026-10-25: "the summon
    // stone is 1 pixel too far left and 1 pixel too far up") for X, and panel-local +
    // WagonScreen.MOUNT_PANEL_Y_OFFSET(21) + 1 for Y (the panel itself is blitted 21px down from the
    // screen's own top, same offset the original single-column panel already used). The two Summon
    // Stone slots land at the exact same screen position as before this pass, since the new, wider
    // panel keeps that part of its layout unchanged -- only the companion row is new.
    private static final int MOUNT_X = 21;
    private static final int MOUNT_LEAD_Y = 28;
    private static final int MOUNT_TEAM_Y = 53;
    private static final int COMPANION_LEFT_X = 7;
    private static final int COMPANION_RIGHT_X = 36;
    private static final int COMPANION_Y = 78;
    // The mount panel grew from 29px to 58px wide (two Companion slots side by side, see above) --
    // the cargo panel's own screen X shifts by that same +29px difference to stay clear of it,
    // matching WagonScreen's own CARGO_PANEL_X recalculation.
    private static final int CARGO_X = 43 + 29;
    private static final int CARGO_Y = 18;
    // Vanilla's own exact real ChestMenu constructor formula (confirmed via javap earlier this
    // session, same source AnimalCartMenu's own 3-row PLAYER_INV_Y came from): 18 + rows*18 + 13,
    // now for 6 rows instead of 3.
    private static final int PLAYER_INV_Y = 18 + CARGO_ROWS * 18 + 13;

    private final Container container;

    /** Server-side: a real {@code WagonEntity} backs this menu. */
    public WagonMenu(final MenuType<?> type, final int containerId, final Inventory inventory, final Container container) {
        super(type, containerId);
        this.container = container;
        container.startOpen(inventory.player);
        layoutSlots(inventory);
    }

    /** Client-side reconstruction (see {@code registration.ModMenus}) -- no real entity to read from; slot contents arrive via vanilla's normal sync. */
    public WagonMenu(final MenuType<?> type, final int containerId, final Inventory inventory) {
        this(type, containerId, inventory, new SimpleContainer(CONTAINER_SIZE));
    }

    private void layoutSlots(final Inventory inventory) {
        addSlot(new MountSlot(this.container, 0, MOUNT_X, MOUNT_LEAD_Y));
        addSlot(new MountSlot(this.container, 1, MOUNT_X, MOUNT_TEAM_Y));
        addSlot(new CompanionSlot(this.container, 2, COMPANION_LEFT_X, COMPANION_Y));
        addSlot(new CompanionSlot(this.container, 3, COMPANION_RIGHT_X, COMPANION_Y));
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

    /**
     * Real live-test bug, 2026-10-26: "I was able to both click+drag and shift+click summon stones
     * out of the wagon without A) getting a warning, or B) any ever being destroyed... only works
     * with the pick up button." Confirmed against the real decompiled {@code
     * AbstractContainerMenu#doClick}: a shift-click ({@code ContainerInput.QUICK_MOVE}) is dispatched
     * from {@link #clicked}/{@code doClick} straight into this very method -- so {@link #clicked}'s
     * own guard (below) running first SHOULD already cover it the same way it covers a plain click,
     * but this is checked here too, independently, as the second of three real interception layers
     * (the third being {@code CompanionSlot}/{@code MountSlot#onTake}'s own roll) so a shift-click
     * removal is genuinely blocked at its OWN real execution point regardless of anything upstream,
     * not just assumed covered by one single check.
     */
    private boolean requiresRemovalConfirm(final int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.slots.size()
                && (this.slots.get(slotIndex) instanceof CompanionSlot || this.slots.get(slotIndex) instanceof MountSlot)
                && this.slots.get(slotIndex).hasItem()
                && this.container instanceof com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity cart
                && cart.isBroken();
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int slotIndex) {
        if (requiresRemovalConfirm(slotIndex)) {
            ((com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity) this.container)
                    .requestCompanionSlotRemovalConfirm(player, slotIndex);
            return ItemStack.EMPTY;
        }
        ItemStack clicked = ItemStack.EMPTY;
        final Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            final ItemStack stack = slot.getItem();
            clicked = stack.copy();
            if (slotIndex < CONTAINER_SIZE) {
                // From a mount slot or cargo -- try the player's own inventory.
                if (!moveItemStackTo(stack, CONTAINER_SIZE, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From the player's inventory -- a reserved slot (mount or companion) first if it
                // fits there, else cargo.
                if (!moveItemStackTo(stack, 0, RESERVED_SLOT_COUNT, false) && !moveItemStackTo(stack, RESERVED_SLOT_COUNT, CONTAINER_SIZE, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            // Real user report, 2026-10-26: shift-clicking a Summon Stone/Companion item out never
            // triggered MountSlot/CompanionSlot's own #onTake clean-despawn fix at all -- this
            // hand-written quickMoveStack (unlike vanilla's own generic chest-menu implementations)
            // never called it. Harmless no-op for a plain cargo Slot.
            slot.onTake(player, clicked);
        }
        return clicked;
    }

    /**
     * Real GUI buttons, 2026-10-26 (health system work) -- {@code menu.client.WagonScreen}'s own
     * "Pick Up"/"Repair" buttons route through here via vanilla's own real
     * {@code ServerboundContainerButtonClickPacket} mechanism (confirmed against the real decompiled
     * {@code MultiPlayerGameMode#handleInventoryButtonClick}/{@code AbstractContainerMenu
     * #clickMenuButton}), which only ever runs server-side -- {@link #container} here is guaranteed
     * the real live entity (only the CLIENT's own reconstructed menu uses a dummy
     * {@code SimpleContainer}, and this method never executes there).
     */
    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        if (this.container instanceof com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity cart) {
            if (id == com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity.PICK_UP_BUTTON_ID) {
                // Sends the real confirm dialog (see #requestPickUpConfirm's own doc) -- does NOT
                // execute Pick Up directly here. The menu is still genuinely open/valid at this
                // exact instant (no screen transition has happened yet), but by the time the player
                // actually clicks "Yes" on the dialog it won't be -- the follow-up confirm payload
                // carries the entity id directly instead of depending on this menu still existing.
                cart.requestPickUpConfirm(player);
                return true;
            }
            if (id == com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity.REPAIR_BUTTON_ID
                    && cart instanceof com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnInventoryEntity inventoryCart) {
                // Real live-test gap, 2026-10-26: reads from the cart's own cargo now, not the
                // player's held item -- see AbstractDamageableDrawnInventoryEntity#handleRepairFromCargo's
                // own doc for why the held-item version was misleading here specifically.
                inventoryCart.handleRepairFromCargo(player);
                return true;
            }
        }
        return super.clickMenuButton(player, id);
    }

    /**
     * Real live-test gap, 2026-10-26: removing a Companion/Summon Stone via a plain slot click
     * rolled the 50%-destroy chance immediately while Broken, with no "Are you sure?" warning --
     * only the GUI "Pick Up" button had one. {@code AbstractContainerMenu#clicked} is the single
     * real entry point for EVERY click type (confirmed against the real decompiled {@code
     * doClick} -- {@code PICKUP}, {@code QUICK_MOVE}, and {@code QUICK_CRAFT} all dispatch from
     * here) -- cancel the click outright and send the real confirm dialog instead whenever it would
     * remove a non-empty {@link CompanionSlot}/{@link MountSlot}'s item while Broken.
     *
     * <p>Real live-test bug, same day: this originally only matched {@link CompanionSlot}, never
     * {@link MountSlot} -- see {@link #requiresRemovalConfirm}'s own doc for the full fix (now
     * shared with {@link #quickMoveStack} as an independent second check on that exact path).
     */
    @Override
    public void clicked(final int slotIndex, final int buttonNum, final net.minecraft.world.inventory.ContainerInput containerInput, final Player player) {
        if (requiresRemovalConfirm(slotIndex)) {
            ((com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity) this.container)
                    .requestCompanionSlotRemovalConfirm(player, slotIndex);
            return;
        }
        super.clicked(slotIndex, buttonNum, containerInput, player);
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
