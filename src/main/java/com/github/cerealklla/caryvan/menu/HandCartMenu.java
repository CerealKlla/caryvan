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
 * Hand Cart's menu -- replaces the plain vanilla {@code ChestMenu.threeRows(...)} it used to
 * return directly (same real 27-slot layout, same vanilla small-chest screen coordinates), with
 * one addition: a "Pick Up" button. Real user spec, 2026-10-29: "no cart/wagon should have a
 * sneak + right click to pick up. We have a button in the inventory, that's the only way it
 * should be done." Mirrors {@code AnimalCartMenu}'s shape minus its mount slot (Hand Cart has
 * none -- player-pulled only, no team mount).
 */
public final class HandCartMenu extends AbstractContainerMenu {

    public static final int CARGO_SLOT_COUNT = 27;

    private static final int CARGO_X = 8;
    private static final int CARGO_Y = 18;
    private static final int PLAYER_INV_Y = 85;

    private final Container container;

    /** Server-side: a real {@code HandCartEntity} backs this menu. */
    public HandCartMenu(final MenuType<?> type, final int containerId, final Inventory inventory, final Container container) {
        super(type, containerId);
        this.container = container;
        container.startOpen(inventory.player);
        layoutSlots(inventory);
    }

    /** Client-side reconstruction (see {@code registration.ModMenus}) -- no real entity to read from; slot contents arrive via vanilla's normal sync. */
    public HandCartMenu(final MenuType<?> type, final int containerId, final Inventory inventory) {
        this(type, containerId, inventory, new SimpleContainer(CARGO_SLOT_COUNT));
    }

    private void layoutSlots(final Inventory inventory) {
        for (int i = 0; i < CARGO_SLOT_COUNT; i++) {
            final int col = i % 9;
            final int row = i / 9;
            addSlot(new Slot(this.container, i, CARGO_X + col * 18, CARGO_Y + row * 18));
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
            if (slotIndex < CARGO_SLOT_COUNT) {
                if (!moveItemStackTo(stack, CARGO_SLOT_COUNT, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!moveItemStackTo(stack, 0, CARGO_SLOT_COUNT, false)) {
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
