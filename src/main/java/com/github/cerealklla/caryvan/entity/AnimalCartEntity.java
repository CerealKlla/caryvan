package com.github.cerealklla.caryvan.entity;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.caryvan.bridge.LyfeSummoningBridge;
import com.github.cerealklla.caryvan.menu.AnimalCartMenu;
import com.github.cerealklla.caryvan.registration.ModItems;
import com.github.cerealklla.caryvan.registration.ModMenus;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Phase 2's first real vertical slice (design doc §3/§4, scoped down to a single mount per explicit
 * user request 2026-10-25 -- the multi-mount "aggregate target" extension flagged in the design doc
 * is deliberately not built yet). No passenger seat yet either (design doc: "carries another
 * LivingEntity as cargo" -- {@link AbstractDrawnEntity}'s own doc already flags passenger/dismount
 * support as a real follow-up once Phase 2 needs it; this slice proves the mount mechanic and its
 * inventory first, same incremental discipline Hand Cart's own Phase 1 used).
 *
 * <p><b>Reworked into a real container menu, same day</b> -- "a more standard container style
 * inventory," replacing the original plain-right-click-to-insert mount slot. {@link #CONTAINER_SIZE}
 * is {@link AnimalCartMenu#CONTAINER_SIZE} (slot 0 = the reserved mount slot, slots 1-27 = a real
 * 27-slot cargo hold, a single vanilla chest's worth) -- now extends {@link
 * AbstractDrawnInventoryEntity} directly (gains real {@code Container}/save/loot-table plumbing for
 * free, exactly like Hand Cart already does) instead of tracking the mount Stone as its own ad hoc
 * field. {@link #tick()} upkeeps whatever's in slot 0 every server tick via {@link
 * LyfeSummoningBridge#tickCompanion} (summon once, re-summon after cooldown if it dies -- the exact
 * same mechanism Settlemynts' own Guards already use for their companions) and, once a live
 * summoned mount exists, sets it as this cart's {@link #getPulling() puller} -- the mount is the one
 * actually walking/ridden (completely unmodified vanilla riding), the cart just chases it with the
 * exact same physics {@link AbstractDrawnEntity#pulledTick()} already uses for Hand Cart. Never
 * voluntarily detaches from a still-alive mount (see {@link AbstractDrawnEntity#canDetachFromPuller}'s
 * own doc -- explicit user spec, "for all but hand cart... should never detach").
 */
public final class AnimalCartEntity extends AbstractDrawnInventoryEntity {

    private static final double SPACING_BLOCKS = 2.0;
    private static final int MOUNT_SLOT = 0;

    public AnimalCartEntity(final EntityType<? extends AnimalCartEntity> entityType, final Level level) {
        super(entityType, level, AnimalCartMenu.CONTAINER_SIZE);
    }

    @Override
    protected double getSpacing() {
        return SPACING_BLOCKS;
    }

    @Override
    public Item getCartItem() {
        return ModItems.animalCartFor(this.getWoodType());
    }

    @Override
    public Item getBrokenCartItem() {
        return ModItems.BROKEN_ANIMAL_CART.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        this.tickMountSlot(serverLevel);
    }

    private void tickMountSlot(final ServerLevel level) {
        // Real user request, 2026-10-25: "Removing the summoning stone needs to despawn the
        // animal." Whenever the current puller no longer matches whatever's actually in the mount
        // slot right now (the stone was removed, or swapped for a different one), discard the now-
        // orphaned mount instead of leaving it alive and ownerless in the world -- mirrors
        // MountBindListener's own silent mount.discard() when a mount is re-bound. #canBePulledBy
        // already encodes exactly this check (does the slot's current stone claim this entity as
        // its own summoned mount), so this reuses it rather than duplicating the logic.
        final Entity currentPulling = this.getPulling();
        if (currentPulling != null && !this.canBePulledBy(currentPulling)) {
            currentPulling.discard();
            this.setPulling(null);
        }

        final ItemStack stone = this.getItem(MOUNT_SLOT);
        if (stone.isEmpty() || !LyfeSummoningBridge.isLoaded()) {
            return;
        }
        final net.minecraft.world.phys.Vec3 spawnPos = this.mountSpawnPosition();
        LyfeSummoningBridge.tickCompanion(level, stone, this.getUUID(), spawnPos.x, spawnPos.y, spawnPos.z, this.getYRot());
        final Optional<UUID> mountId = LyfeSummoningBridge.getSummonedMountId(stone);
        if (mountId.isEmpty()) {
            return;
        }
        final Entity mount = level.getEntity(mountId.get());
        if (mount != null && mount.isAlive() && this.getPulling() != mount) {
            // Real user request, 2026-10-25: "disable the wandering AI on Animal Cart, like you did
            // for the Wagon." Same real fix as WagonEntity#prepareCartMount -- strip idle AI goals
            // so wandering stops fighting the chase physics, via Mob#removeAllGoals specifically
            // (not Mob#setNoAi, which was already proven this session to also silently break
            // PathNavigation#tick() entirely, confirmed via decompiled bytecode -- see WagonEntity's
            // own doc for the full writeup). Unlike the Wagon's team mounts, Animal Cart's own mount
            // is still meant to be ridden directly by a player (this class's own top-level doc:
            // "completely unmodified vanilla riding") -- only the wandering-goal strip applies here,
            // not the Wagon's additional CART_TEAM_MOUNT ride-block tag.
            if (mount instanceof Mob mob) {
                mob.removeAllGoals(goal -> true);
            }
            this.setPulling(mount);
        }
    }

    /** Only this cart's own summoned mount may ever pull it -- {@link #setPulling} for this class is only ever called from {@link #tickMountSlot}, never a player interaction. */
    @Override
    protected boolean canBePulledBy(final Entity entity) {
        final ItemStack stone = this.getItem(MOUNT_SLOT);
        if (stone.isEmpty() || !LyfeSummoningBridge.isLoaded()) {
            return false;
        }
        return LyfeSummoningBridge.getSummonedMountId(stone)
                .map(id -> id.equals(entity.getUUID()))
                .orElse(false);
    }

    @Override
    public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
        this.unpackChestVehicleLootTable(player);
        return new AnimalCartMenu(ModMenus.ANIMAL_CART.get(), containerId, inventory, this);
    }
}
