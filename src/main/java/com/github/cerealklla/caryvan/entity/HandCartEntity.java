package com.github.cerealklla.caryvan.entity;

import com.github.cerealklla.caryvan.registration.ModItems;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Phase 1 of the design document's build order -- player-pulled directly (no animal), real
 * 27-slot cargo storage (vanilla's own {@link ChestMenu#threeRows}, same vanilla-screen-reuse
 * precedent as Kyt/Yconomics' own menus elsewhere in this suite). Adapted from NiftyCarts'
 * {@code HandCartEntity} (MIT licensed, see this repo's own CLAUDE.md), which also uses a plain
 * 27-slot chest menu for this cart.
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
        return ModItems.HAND_CART.get();
    }

    /** Only the player actually pulling it (via the action key) -- no animal AI exists to make anything else want to grab one. */
    @Override
    protected boolean canBePulledBy(final Entity entity) {
        return entity instanceof Player && super.canBePulledBy(entity);
    }

    @Override
    public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
        this.unpackChestVehicleLootTable(player);
        return ChestMenu.threeRows(containerId, inventory, this);
    }
}
