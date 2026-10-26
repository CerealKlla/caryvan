package com.github.cerealklla.caryvan.entity;

import com.github.cerealklla.caryvan.registration.ModItems;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 1 of the design document's build order -- player-pulled directly (no animal), real
 * 27-slot cargo storage (vanilla's own {@link ChestMenu#threeRows}, same vanilla-screen-reuse
 * precedent as Kyt/Yconomics' own menus elsewhere in this suite). Adapted from NiftyCarts'
 * {@code HandCartEntity} (MIT licensed, see this repo's own CLAUDE.md), which also uses a plain
 * 27-slot chest menu for this cart.
 *
 * <p><b>Hitch interaction reworked 2026-10-25, explicit user correction</b> -- "same as horses/
 * chairs: right click toggles, left shift auto dismounts," replacing an earlier dedicated keybind
 * (deleted along with {@code network.ActionKeyPayload}). Originally a plain right-click toggled
 * hitch/unhitch and sneak+right-click opened cargo (mirroring {@code AbstractChestBoat}'s own
 * board-vs-open-storage split) -- **flipped the same day per explicit follow-up request**: a plain
 * right-click now opens the cargo inventory (see {@link #createMenu}), sneak+right-click toggles
 * hitch/unhitch instead (see {@link AbstractDrawnEntity#tryToggleHitch}).
 */
public final class HandCartEntity extends AbstractDrawnInventoryEntity {

    private static final int CONTAINER_SIZE = 27;
    private static final double SPACING_BLOCKS = 1.0;

    public HandCartEntity(final EntityType<? extends HandCartEntity> entityType, final Level level) {
        super(entityType, level, CONTAINER_SIZE);
    }

    @Override
    protected double getSpacing() {
        return SPACING_BLOCKS;
    }

    @Override
    public Item getCartItem() {
        return ModItems.handCartFor(this.getWoodType());
    }

    @Override
    public Item getBrokenCartItem() {
        return ModItems.BROKEN_HAND_CART.get();
    }

    /** Only a player's own right-click can hitch it -- no animal AI exists to make anything else want to grab one. */
    @Override
    protected boolean canBePulledBy(final Entity entity) {
        return entity instanceof Player && super.canBePulledBy(entity);
    }

    /** The one deliberate exception to {@link AbstractDrawnEntity}'s own "never detach" default -- see that method's own doc. */
    @Override
    protected boolean canDetachFromPuller() {
        return true;
    }

    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand, final Vec3 location) {
        if (!player.isShiftKeyDown()) {
            return super.interact(player, hand, location);
        }
        final InteractionResult result = tryToggleHitch(player);
        return result != null ? result : super.interact(player, hand, location);
    }

    @Override
    public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
        this.unpackChestVehicleLootTable(player);
        return ChestMenu.threeRows(containerId, inventory, this);
    }
}
