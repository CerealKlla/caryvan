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

    /**
     * The first real cargo slot index -- {@code 0} by default, overridden by a subclass (e.g.
     * {@link WagonEntity}, {@link CarriageEntity}) whose own menu reserves a block of leading
     * slots for {@code menu.MountSlot}/{@code menu.CompanionSlot}. Real live-test bug, 2026-10-30:
     * {@link #onWheelBroken} and {@link #handleRepairFromCargo}'s own cargo scans both looped the
     * whole container from index 0, unaware a Wagon's/Carriage's real cargo doesn't start there --
     * a newly-dropped Broken Wheel landed directly in the first reserved Mount slot (the Wagon's own
     * "left slot"), and removing it from there then wrongly triggered {@code MountSlot}/{@code
     * CompanionSlot}'s Broken-cart 50%-destroy-on-take confirmation, a mechanic meant only for real
     * Summon Stones/Companions -- "you keep putting the broken wheels in the left slots... it
     * destroyed them when I tried to take them out." Every cargo-only scan in this class and in
     * {@link CarriageEntity#repairAdditionalParts} now starts from this offset instead of a bare 0.
     */
    protected int cargoSlotOffset() {
        return 0;
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
            // Real live-test bugs, 2026-10-28: this used to only accept Wheels matching the cart's
            // own BODY species (`ModItems.wheelsFor(this.getWoodType())`) -- never updated when the
            // same day's "Wagon Repair can use a different wheel species" change widened the
            // right-click world interaction (WagonEntity#use) to accept ANY real Wheels item via
            // ModItems#woodTypeForWheelsItem, leaving this GUI "Repair" button (the one players
            // actually use, since it reads cargo instead of the held item) still rejecting anything
            // not matching the body -- "it would not repair because I didn't have the needed
            // materials" despite real Wheels sitting in cargo. Separately, even a same-species repair
            // here always called the plain 1-arg #repair(int) overload, which passes {@code
            // newSpecies = null} -- so a wheel that had been re-colored to a DIFFERENT species by an
            // earlier right-click repair stayed visually that old species forever, even once
            // genuinely repaired with matching-body-species Wheels ("it repaired the wheel but the
            // wheel still looks like a birch wheel"). Fixed by scanning cargo for every real Wheels
            // species present (not just the body's own) and repairing one species-batch at a time,
            // passing that species into #repair(int, WoodType) so a freshly-fixed wheel always
            // re-colors to whatever Wheels item actually fixed it -- exactly matching the right-click
            // path's own behavior.
            final java.util.Map<net.minecraft.world.level.block.state.properties.WoodType, Integer> availableBySpecies =
                    new java.util.HashMap<>();
            for (int i = cargoSlotOffset(); i < this.getContainerSize(); i++) {
                final ItemStack stack = this.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                com.github.cerealklla.caryvan.registration.ModItems.woodTypeForWheelsItem(stack.getItem())
                        .ifPresent(species -> availableBySpecies.merge(species, stack.getCount(), Integer::sum));
            }
            int totalFixed = 0;
            for (final var entry : availableBySpecies.entrySet()) {
                if (this.getBrokenWheelCount() <= 0) {
                    break;
                }
                final net.minecraft.world.level.block.state.properties.WoodType species = entry.getKey();
                final int fixed = this.repair(entry.getValue(), species);
                if (fixed <= 0) {
                    continue;
                }
                totalFixed += fixed;
                final net.minecraft.world.item.Item wheelsItem = com.github.cerealklla.caryvan.registration.ModItems.wheelsFor(species);
                int remaining = fixed;
                for (int i = cargoSlotOffset(); i < this.getContainerSize() && remaining > 0; i++) {
                    final ItemStack stack = this.getItem(i);
                    if (stack.getItem() == wheelsItem) {
                        final int take = Math.min(remaining, stack.getCount());
                        stack.shrink(take);
                        remaining -= take;
                    }
                }
            }
            if (totalFixed > 0) {
                repairedAnything = true;
                player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                        "Repaired " + totalFixed + " wheel" + (totalFixed == 1 ? "" : "s") + "."));
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
     * Real user request, 2026-10-28: "when a wagon's wheel breaks a broken wheel item needs to be
     * added to the wagon's inventory." Deposits {@code count} {@code caryvan:broken_wheel} (a plain,
     * non-wood-specific junk item -- NOT the same item {@link #handleRepairFromCargo} consumes to fix
     * a wheel, which is the per-wood-species {@code Wheels} crafting item) into this cart's own
     * cargo, stacking into any existing matching stack first, then the first empty slot; whatever
     * doesn't fit scatters on the ground, same overflow convention every other cargo-deposit path in
     * this suite already uses (e.g. Yconomics' {@code LootBagListener}).
     */
    @Override
    protected void onWheelBroken(final int count) {
        final net.minecraft.world.item.Item brokenWheelItem = com.github.cerealklla.caryvan.registration.ModItems.BROKEN_WHEEL.get();
        int remaining = count;
        for (int i = cargoSlotOffset(); i < this.getContainerSize() && remaining > 0; i++) {
            final ItemStack stack = this.getItem(i);
            if (stack.getItem() == brokenWheelItem && stack.getCount() < stack.getMaxStackSize()) {
                final int room = stack.getMaxStackSize() - stack.getCount();
                final int add = Math.min(room, remaining);
                stack.grow(add);
                remaining -= add;
            }
        }
        for (int i = cargoSlotOffset(); i < this.getContainerSize() && remaining > 0; i++) {
            if (this.getItem(i).isEmpty()) {
                final int add = Math.min(new ItemStack(brokenWheelItem).getMaxStackSize(), remaining);
                this.setItem(i, new ItemStack(brokenWheelItem, add));
                remaining -= add;
            }
        }
        if (remaining > 0 && this.level() instanceof ServerLevel serverLevel) {
            net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                    serverLevel, this.getX(), this.getY(), this.getZ(), new ItemStack(brokenWheelItem, remaining));
            serverLevel.addFreshEntity(itemEntity);
        }
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
