package com.github.cerealklla.caryvan.entity;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * {@link AbstractDamageableDrawnEntity} + real cargo storage via {@link ContainerEntity} -- the
 * {@code LivingEntity}-based mirror of {@link AbstractDrawnInventoryEntity}, for Wagon/Carriage
 * only (see {@link AbstractDamageableDrawnEntity}'s own doc for why these two needed a separate
 * hierarchy). Identical body to {@link AbstractDrawnInventoryEntity} -- {@code ContainerEntity}'s
 * own default methods don't care what the implementing class ultimately extends.
 */
public abstract class AbstractDamageableDrawnInventoryEntity extends AbstractDamageableDrawnEntity implements ContainerEntity {

    private final NonNullList<ItemStack> itemStacks;
    private final int containerSize;
    private ResourceKey<LootTable> lootTable;
    private long lootTableSeed;

    protected AbstractDamageableDrawnInventoryEntity(final EntityType<? extends AbstractDamageableDrawnEntity> entityType, final Level level, final int containerSize) {
        super(entityType, level);
        this.containerSize = containerSize;
        this.itemStacks = NonNullList.withSize(containerSize, ItemStack.EMPTY);
    }

    @Override
    public int getContainerSize() {
        return this.containerSize;
    }

    @Override
    public boolean isEmpty() {
        return this.isChestVehicleEmpty();
    }

    @Override
    public ItemStack getItem(final int slot) {
        return this.getChestVehicleItem(slot);
    }

    @Override
    public ItemStack removeItem(final int slot, final int amount) {
        return this.removeChestVehicleItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(final int slot) {
        return this.removeChestVehicleItemNoUpdate(slot);
    }

    @Override
    public void setItem(final int slot, final ItemStack stack) {
        this.setChestVehicleItem(slot, stack);
    }

    @Override
    public SlotAccess getSlot(final int slot) {
        return this.getChestVehicleSlot(slot);
    }

    @Override
    public void clearContent() {
        this.clearChestVehicleContent();
    }

    @Override
    public void setChanged() {
        // Nothing to mark dirty -- saved via the normal addAdditionalSaveData path, same as AbstractDrawnInventoryEntity.
    }

    @Override
    public boolean stillValid(final Player player) {
        return this.isChestVehicleStillValid(player);
    }

    @Override
    public NonNullList<ItemStack> getItemStacks() {
        return this.itemStacks;
    }

    @Override
    public void clearItemStacks() {
        this.itemStacks.clear();
    }

    @Override
    public ResourceKey<LootTable> getContainerLootTable() {
        return this.lootTable;
    }

    @Override
    public void setContainerLootTable(final ResourceKey<LootTable> lootTable) {
        this.lootTable = lootTable;
    }

    @Override
    public long getContainerLootTableSeed() {
        return this.lootTableSeed;
    }

    @Override
    public void setContainerLootTableSeed(final long seed) {
        this.lootTableSeed = seed;
    }

    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand, final Vec3 location) {
        final InteractionResult result = this.interactWithContainerVehicle(player);
        if (result.consumesAction()) {
            this.gameEvent(GameEvent.CONTAINER_OPEN, player);
        }
        return result;
    }

    @Override
    public void remove(final Entity.RemovalReason removalReason) {
        if (!this.level().isClientSide() && removalReason.shouldDestroy()) {
            Containers.dropContents(this.level(), this, this);
        }
        super.remove(removalReason);
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        this.readChestVehicleSaveData(input);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        this.addChestVehicleSaveData(output);
    }

    /**
     * Real live-test gap, 2026-10-26: the GUI "Repair" button read {@code player.getMainHandItem()}
     * -- the same held-item requirement the sneak+right-click world gesture deliberately uses -- but
     * a player naturally stores spare Wheels IN the cart's own cargo for a repair run, not in their
     * hand. If they weren't literally holding one, the button silently did nothing regardless of how
     * many Wheels sat in cargo, which could easily read as "it needs ALL the wheels before it'll do
     * anything" even though {@link #repair(int)}'s own math has always been a correct partial repair
     * ({@code Math.min(wheelsAvailable, brokenWheelCount)}, confirmed by re-reading it directly, not
     * an all-or-nothing gate anywhere). This scans the cart's own cargo instead, consuming across
     * however many stacks it takes.
     *
     * <p>Real spec, 2026-10-26 (Carriage redesign Phase 3): "The existing Repair button must also
     * consume Door items from cargo when present, alongside its existing Wheel-consumption logic."
     * {@link #repairAdditionalParts(Player)} is a hook for exactly this -- a no-op here, overridden
     * by {@link CarriageEntity} to scan/consume its own Door items the same way this method already
     * scans Wheels, entirely independent of whether any wheels needed fixing this click (a cart with
     * broken doors but no broken wheels must still get its doors fixed).
     */
    public void handleRepairFromCargo(final Player player) {
        if (this.isRecentlyDamaged()) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                    "This cart was recently damaged and can't be repaired yet."));
            return;
        }
        boolean repairedAnything = false;
        if (this.getBrokenWheelCount() > 0) {
            final net.minecraft.world.item.Item wheelsItem = com.github.cerealklla.caryvan.registration.ModItems.wheelsFor(this.getWoodType());
            int available = 0;
            for (int i = 0; i < this.getContainerSize(); i++) {
                final ItemStack stack = this.getItem(i);
                if (stack.getItem() == wheelsItem) {
                    available += stack.getCount();
                }
            }
            if (available > 0) {
                final int fixed = this.repair(available);
                if (fixed > 0) {
                    repairedAnything = true;
                    int remaining = fixed;
                    for (int i = 0; i < this.getContainerSize() && remaining > 0; i++) {
                        final ItemStack stack = this.getItem(i);
                        if (stack.getItem() == wheelsItem) {
                            final int take = Math.min(remaining, stack.getCount());
                            stack.shrink(take);
                            remaining -= take;
                        }
                    }
                    player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                            "Repaired " + fixed + " wheel" + (fixed == 1 ? "" : "s") + "."));
                }
            }
        }
        if (repairAdditionalParts(player)) {
            repairedAnything = true;
        }
        if (!repairedAnything) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                    "Nothing to repair, or no matching parts found in this cart's cargo."));
        }
    }

    /**
     * Hook for a concrete subclass to repair its own additional, non-wheel parts from cargo as part
     * of the same Repair click -- no-op by default (returns false, meaning "nothing repaired").
     * See {@link CarriageEntity#repairAdditionalParts(Player)} for the real Door-item implementation.
     *
     * @return whether anything was actually repaired (so the caller knows whether to fall back to a "nothing to repair" message).
     */
    protected boolean repairAdditionalParts(final Player player) {
        return false;
    }

    /**
     * Spills real cargo at Pick Up -- through a real Yconomics Loot Bag
     * ({@code bridge.YconomicsLootBagBridge#depositOrScatter}, see its own doc) when Yconomics is
     * loaded, falling back to plain vanilla item drops otherwise (a Yconomics-less server has no
     * bag mechanic to route through at all, but cargo is never lost either way).
     */
    @Override
    protected void dumpCargoOnPickUp(final ServerLevel level) {
        if (com.github.cerealklla.caryvan.bridge.YconomicsLootBagBridge.isLoaded()) {
            final net.minecraft.world.phys.Vec3 pos = this.position();
            for (final ItemStack stack : this.getItemStacks()) {
                if (!stack.isEmpty()) {
                    com.github.cerealklla.caryvan.bridge.YconomicsLootBagBridge.depositOrScatter(level, pos, stack);
                }
            }
            this.clearItemStacks();
        } else {
            Containers.dropContents(level, this, this);
        }
    }
}
