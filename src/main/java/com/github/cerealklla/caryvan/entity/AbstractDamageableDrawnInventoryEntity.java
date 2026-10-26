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
