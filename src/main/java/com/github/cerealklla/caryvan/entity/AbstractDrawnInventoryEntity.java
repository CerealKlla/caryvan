package com.github.cerealklla.caryvan.entity;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
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
 * A {@link AbstractDrawnEntity} with real cargo storage, via vanilla's own {@link ContainerEntity}
 * interface (the same one Chest Boats implement) -- adapted from NiftyCarts'
 * {@code AbstractDrawnInventoryEntity} (MIT licensed, see this repo's own CLAUDE.md). Most of the
 * actual inventory plumbing (save/load, loot-table unpacking, the open-screen interaction) comes
 * free from {@code ContainerEntity}'s own default methods; this class is mostly the small set of
 * plain {@link net.minecraft.world.Container} methods that have to exist with Container's own exact
 * names, each a one-line delegate to the matching {@code ContainerEntity} default.
 */
public abstract class AbstractDrawnInventoryEntity extends AbstractDrawnEntity implements ContainerEntity {

    private final NonNullList<ItemStack> itemStacks;
    private final int containerSize;
    private ResourceKey<LootTable> lootTable;
    private long lootTableSeed;

    protected AbstractDrawnInventoryEntity(final EntityType<? extends AbstractDrawnEntity> entityType, final Level level, final int containerSize) {
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
        // Nothing to mark dirty -- a drawn entity isn't a block entity, it's saved via the normal
        // addAdditionalSaveData path every entity already goes through.
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
}
