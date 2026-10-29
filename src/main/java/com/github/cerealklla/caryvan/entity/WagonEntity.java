package com.github.cerealklla.caryvan.entity;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.bridge.LyfeSummoningBridge;
import com.github.cerealklla.caryvan.menu.WagonMenu;
import com.github.cerealklla.caryvan.registration.ModAttachments;
import com.github.cerealklla.caryvan.registration.ModItems;
import com.github.cerealklla.caryvan.registration.ModMenus;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 2's 2-horse team slice (design-document.md §7/§8), scoped down 2026-10-25 per explicit
 * user clarification during planning:
 *
 * <ul>
 *   <li>Two independent Summoning Stone slots, daisy-chained -- real user correction, 2026-10-25:
 *   "I think we daisy chain the horse positions. Lead to team 1, team 1 to... the wagon. 'Furthest'
 *   horse is the one that leads in the event of deaths, always ensure the wagon is connected to the
 *   rearmost horse." {@link #TEAM_SLOT}'s own mount is leashed directly to {@link #LEAD_SLOT}'s (see
 *   {@link #syncTeamLeash()}) via vanilla's own real {@link Leashable} interface -- "we can just use
 *   the existing leads to connect them to the one in front of them," replacing an earlier, needlessly
 *   custom rigid-position-lock implementation. Vanilla's own generic {@code Entity#tick()} already
 *   drives {@link Leashable#tickLeash} for any {@link Leashable} mob automatically (confirmed via the
 *   real decompiled bytecode) -- no per-tick driving code needed here at all, just the one
 *   {@code setLeashedTo} call. This cart's own real {@link AbstractDrawnEntity#getPulling() puller}
 *   is then the *rearmost alive* mount -- the team horse if it's alive, falling back to the lead if
 *   the team has died -- kept correct every tick by {@link #syncWagonPullTarget()}, so a mid-chain
 *   death never leaves the wagon chasing a dead/missing link. Still not the real "aggregate target"/
 *   formation system design-document.md §4/§8 eventually wants (a leash produces natural
 *   follow-when-too-far slack, not a precise formation offset), but far closer to it than a rigid
 *   teleport ever was, for free. Both mounts get {@link #prepareCartMount} on summon -- idle AI
 *   goals stripped ({@link Mob#removeAllGoals}, see that method's own doc for why not
 *   {@code setNoAi}), and riding disabled (see its own doc for the real repro that led to that).</li>
 *   <li>A real driving bench exists (plain right-click, high enough on the model) and now actually
 *   steers the team -- "remote control" (the user's own framing, 2026-10-25), rebuilt the same day
 *   after a first attempt (a hidden phantom-horse proxy the driver actually rode) produced a real,
 *   funny-but-wrong bug: the player visibly left their seat to ride a separate horse, and a yaw
 *   feedback loop between that phantom and the wagon's own chase-rotation made the whole rig spin
 *   faster and faster ("the cart starts orbiting me... faster and faster"). The real fix, and the
 *   actually-correct design the whole time: the driver stays a normal passenger of THIS entity (the
 *   seat never moves), and {@link #tickSteering()} reads their raw input directly off
 *   {@code ServerPlayer#getLastClientInput()} -- confirmed via the real decompiled bytecode of
 *   {@code ServerGamePacketListenerImpl#handlePlayerInput} that the client sends this packet (WASD/
 *   jump/sneak/sprint booleans) unconditionally every time it changes, with no gate on what the
 *   player is riding at all, so no phantom mount is needed to receive it. {@link #leadMount}'s yaw is
 *   set directly to the driver's own look yaw every tick (true remote-control feel: the team always
 *   faces wherever the driver is looking), and forward/strafe move it in that frame -- the team then
 *   follows via its own existing leash, and the wagon via its own existing chase physics, with zero
 *   changes to either.</li>
 *   <li>Placeholder box/flat-wheel geometry and a single fixed texture (no per-wood variants) --
 *   explicit user scoping: real geometry/wood-species textures are a follow-up, same two-step
 *   precedent Hand Cart and Animal Cart both used for their own first passes.</li>
 *   <li>Companions, the Fount equipment slot, the weight-based speed formula, and the hireable NPC
 *   driver (design-document.md §8 points 4/6/7/8) are all explicitly out of scope this pass.</li>
 * </ul>
 */
public final class WagonEntity extends AbstractDamageableDrawnInventoryEntity implements Leashable, com.github.cerealklla.nuclyus.statbar.StatBarOwner {

    /** Placeholder, flagged for live-test tuning -- see the approved plan's §2. */
    private static final float BASE_MAX_HEALTH = 100.0F;

    private static final int LEAD_SLOT = 0;
    private static final int TEAM_SLOT = 1;
    // Real user spec, 2026-10-26 (design-document.md §8's own diagram): the Wagon's real inventory
    // layout is 2 Summon Stones + 2 Companion slots, side by side below them. Indices follow
    // WagonMenu's own real slot ordering (0/1 mount, 2/3 companion).
    private static final int COMPANION_LEFT_SLOT = 2;
    private static final int COMPANION_RIGHT_SLOT = 3;
    // Real user report, 2026-10-25: "when the lead horse moves the wagon follows closely behind,
    // but too close... the team horse is roughly right next to the wagon instead of conceptually
    // pulling it." The wagon's own placeholder hitbox/model is bigger than Animal Cart's, so its
    // front edge eats further into the nominal center-to-center spacing than Animal Cart's own 2.0
    // does -- raised accordingly. Since the team horse is leashed to the LEAD (not the wagon, see
    // #syncTeamLeash), giving the lead more real breathing room from the wagon also pulls the team
    // horse further out by extension, addressing both complaints with one change. A placeholder
    // tuning value, likely to need another pass once the real model exists.
    // Tightened 0.5 blocks (4.0 -> 3.5), real user request 2026-10-25 ("shorten the distance
    // between horses and wagon by .5 each") once steering was confirmed working live.
    private static final double SPACING_BLOCKS = 3.5;

    private Entity leadMount;
    private Entity teamMount;
    private Entity companionLeft;
    private Entity companionRight;
    private Leashable.LeashData leashData;

    // Real user follow-up, 2026-10-25, right after the left/right bench split: "confirmed i'm on
    // the left seat, is there away to sit in the right if i'm alone?" Previously there was no way --
    // whoever mounted first always became passenger index 0 (the left/driver seat), regardless of
    // which half of the bench they actually clicked. These track which real passenger currently
    // owns each side so #interact can actually honor which half was clicked, instead of only ever
    // filling seats in mount order -- revalidated every tick (see #validateSeatOccupants) the same
    // way #leadMount/#teamMount already self-clean each tick, rather than relying on a dismount
    // event hook.
    private Entity leftSeatOccupant;
    private Entity rightSeatOccupant;

    // Real steering, rebuilt 2026-10-25 as "remote control" (the user's own framing) after the
    // first phantom-horse-proxy attempt visibly misbehaved -- see this class's own top-level doc and
    // #tickSteering()'s doc for the full writeup. Fallback per-tick move speed for #tickSteering()
    // when #leadMount isn't a LivingEntity (shouldn't happen in practice -- summoned mounts always
    // are -- kept only so a bad cast can never NPE this).
    private static final double STEER_SPEED_FALLBACK = 0.2D;

    // Real user report, 2026-10-25: "the distance between lead and team is slightly too long
    // visually" and "sometimes the team horse ends up facing backwards and kind of gets dragged
    // around like that... have it always face the horse in front of it." Root cause: the team's
    // actual real movement between lead and team was left entirely to vanilla's own Leashable#
    // tickLeash elastic tug (a plain Entity#addDeltaMovement nudge, confirmed via the real
    // decompiled bytecode -- it NEVER touches rotation at all, and its own elastic threshold is a
    // fixed vanilla constant derived from both entities' real bounding-box widths, not tunable on a
    // plain, unmodified vanilla Horse without a Mixin). #syncTeamFollow replaces reliance on that
    // tug with the same authoritative-chase shape AbstractDrawnEntity#pulledTick() already uses for
    // the wagon itself -- a tight, explicit resting distance, and the team's yaw set directly toward
    // the lead every tick. {@link #syncTeamLeash()}'s real vanilla leash relationship is kept
    // unchanged purely for the rope's own visual rendering -- real distance now always stays well
    // under vanilla's own elastic/snap thresholds (the same reason {@link #checkElasticInteractions}/
    // {@link #leashSnapDistance()} never needed overriding for this cart's OWN leash to its puller),
    // so vanilla's tug simply never activates.
    // Raised 2.0 -> 2.5, real user request 2026-10-25: "now that you overwrote the lead mechanics
    // they are too close (since they don't stretch apart briefly anymore)" -- the rigid resting
    // distance here reads as noticeably tighter than before now that there's no more occasional
    // vanilla-leash slack briefly stretching the gap back open.
    // Raised again 2.5 -> 3.0, real user request 2026-10-25: "spread the horses .5 further apart
    // please."
    private static final double TEAM_FOLLOW_SPACING = 3.0D;
    private static final double TEAM_CATCH_UP_RATE = 0.6D;

    /**
     * Real user spec, 2026-10-29: "I want the wagon's max speed to be set to the average of all the
     * pulling horses, not just the fastest" -- followed up the same conversation with the full
     * formula below. {@code BaseMaxSpeed} is 90% of the average of every alive, real-{@code
     * LivingEntity} mount's own {@code Attributes.MOVEMENT_SPEED}; the actual current Max Speed then
     * loses up to {@link #LOAD_SPEED_PENALTY} (40%) as the cart fills up, scaled by how full it is.
     *
     * <p><b>Corrected the same day</b> -- the original version treated a player sitting in a bench
     * seat as a flat +1 toward load, regardless of what they're carrying. Real spec: "add the
     * player's inventory to the wagon and then set the max speed based on the % of 'used' item
     * slots... every player's weight is adding to the weight in the wagon" -- each seat currently
     * occupied by a real {@link Player} (a Companion occupying a seat has no inventory, contributes
     * nothing) pools its own 27 main-storage inventory slots (hotbar and armor/offhand excluded,
     * same {@code MAIN_INVENTORY_START=9}/{@code MAIN_INVENTORY_END=36} range Wainwright Station's
     * own crafting scan already established as this mod's convention) into both the "used slots" and
     * "total capacity" side of the load fraction, alongside the cart's own cargo slots ("full" still
     * ignores stack size, per the original spec -- a slot with 1 item counts the same as a full
     * stack). Since a rider's own inventory contents can change through many different real actions
     * with no single generic "inventory changed" event behind any of them (confirmed against the
     * real decompiled source -- {@code Inventory#setChanged} is a bare counter, posts nothing),
     * explicit user correction, 2026-10-29: "Don't add ticks like that. There are events for the
     * inventory changing, only fire the recalculation when those events fire." See {@code
     * interaction.WagonRiderInventoryListener} for the real discrete events (pickup, container
     * close, crafting, smelting, item consumption, equipment change) that each call {@link
     * #recalculateMaxSpeed()} directly when the player involved is riding this Wagon -- no tick
     * polling at all. Synced via {@link #DATA_MAX_SPEED} so {@code menu.client.WagonScreen} can
     * display the real current value without needing a live reference to this entity to query it
     * directly.
     */
    private static final double BASE_SPEED_FRACTION = 0.9D;
    private static final double LOAD_SPEED_PENALTY = 0.4D;
    private static final int MOUNT_SLOT_COUNT_OFFSET = WagonMenu.MOUNT_SLOT_COUNT + WagonMenu.COMPANION_SLOT_COUNT;
    private static final int RIDER_MAIN_INVENTORY_START = 9;
    private static final int RIDER_MAIN_INVENTORY_END = 36;
    private static final int RIDER_INVENTORY_SLOT_COUNT = RIDER_MAIN_INVENTORY_END - RIDER_MAIN_INVENTORY_START;
    private static final EntityDataAccessor<Float> DATA_MAX_SPEED =
            SynchedEntityData.defineId(WagonEntity.class, EntityDataSerializers.FLOAT);

    public WagonEntity(final EntityType<? extends WagonEntity> entityType, final Level level) {
        super(entityType, level, WagonMenu.CONTAINER_SIZE);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MAX_SPEED, 0.0F);
    }

    /** Whatever {@link #recalculateMaxSpeed()} most recently computed -- for {@code menu.client.WagonScreen}'s own display, and real client/server consistency (both sides read this exact synced value, no duplicate computation). */
    public double getMaxSpeed() {
        return this.entityData.get(DATA_MAX_SPEED);
    }

    /**
     * Recomputes and re-syncs {@link #DATA_MAX_SPEED} -- called on seat mount ({@link #interact}),
     * seat dismount ({@link #validateSeatOccupants}), cargo change ({@link #setChanged}), and (from
     * outside this class, hence {@code public}) every real event {@code
     * interaction.WagonRiderInventoryListener} covers for a riding player's own inventory.
     */
    public void recalculateMaxSpeed() {
        double speedTotal = 0.0D;
        int mountCount = 0;
        if (this.leadMount instanceof LivingEntity livingLead) {
            speedTotal += livingLead.getAttributeValue(Attributes.MOVEMENT_SPEED);
            mountCount++;
        }
        if (this.teamMount != null && this.teamMount.isAlive() && this.teamMount instanceof LivingEntity livingTeam) {
            speedTotal += livingTeam.getAttributeValue(Attributes.MOVEMENT_SPEED);
            mountCount++;
        }
        final double baseMaxSpeed = mountCount == 0 ? 0.0D : (speedTotal / mountCount) * BASE_SPEED_FRACTION;

        int filledCargoSlots = 0;
        for (int i = MOUNT_SLOT_COUNT_OFFSET; i < this.getContainerSize(); i++) {
            if (!this.getItem(i).isEmpty()) {
                filledCargoSlots++;
            }
        }
        final int cargoSlotCount = this.getContainerSize() - MOUNT_SLOT_COUNT_OFFSET;

        int riderUsedSlots = 0;
        int riderCapacitySlots = 0;
        for (final Entity seatOccupant : new Entity[]{this.leftSeatOccupant, this.rightSeatOccupant}) {
            if (seatOccupant instanceof Player riderPlayer) {
                riderCapacitySlots += RIDER_INVENTORY_SLOT_COUNT;
                riderUsedSlots += countUsedRiderSlots(riderPlayer);
            }
        }

        final int totalCapacity = cargoSlotCount + riderCapacitySlots;
        final double loadFraction = totalCapacity <= 0 ? 0.0D
                : (double) (filledCargoSlots + riderUsedSlots) / totalCapacity;

        final double currentMaxSpeed = baseMaxSpeed * (1.0D - LOAD_SPEED_PENALTY * loadFraction);
        this.entityData.set(DATA_MAX_SPEED, (float) currentMaxSpeed);
    }

    /** A rider's own 27 main-storage slots (hotbar/armor/offhand excluded) -- see {@link #recalculateMaxSpeed()}'s own doc. */
    private static int countUsedRiderSlots(final Player rider) {
        final Inventory inventory = rider.getInventory();
        int used = 0;
        for (int i = RIDER_MAIN_INVENTORY_START; i < RIDER_MAIN_INVENTORY_END; i++) {
            if (!inventory.getItem(i).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /** The real "cargo changed" trigger point -- see {@link #recalculateMaxSpeed()}'s own doc for why this (not a per-tick scan) is the right hook. */
    @Override
    public void setChanged() {
        super.setChanged();
        this.recalculateMaxSpeed();
    }

    /** Registered in {@code CaryvanMod#registerAttributes} -- mirrors {@code GuardKitStandEntity}'s own real {@code ArmorStand.createAttributes()} precedent for a {@code LivingEntity} subclass. */
    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return AbstractDamageableDrawnEntity.createAttributes(BASE_MAX_HEALTH);
    }

    @Override
    protected float getBaseMaxHealth() {
        return BASE_MAX_HEALTH;
    }

    @Override
    protected double getSpacing() {
        return SPACING_BLOCKS;
    }

    @Override
    public Item getCartItem() {
        return ModItems.wagonFor(this.getWoodType());
    }

    @Override
    public Item getBrokenCartItem() {
        return ModItems.BROKEN_WAGON.get();
    }

    /**
     * De-summons both bound mount Summoning Stones the instant this wagon's HP hits 0 -- real user
     * spec: "de-summon any Summon Stones. Any active Companions will still defend the wagon."
     * Reuses {@link LyfeSummoningBridge#despawnMountCleanly} (the exact clean-recapture facade built
     * earlier this session for "recall, don't let it look like it died") rather than discarding the
     * live mount entities directly -- the stack itself recaptures whatever the mount currently is.
     */
    @Override
    protected void onEnterBrokenState(final ServerLevel level) {
        if (!LyfeSummoningBridge.isLoaded()) {
            return;
        }
        final ItemStack leadStone = this.getItem(LEAD_SLOT);
        if (!leadStone.isEmpty()) {
            LyfeSummoningBridge.despawnMountCleanly(level, leadStone);
        }
        final ItemStack teamStone = this.getItem(TEAM_SLOT);
        if (!teamStone.isEmpty()) {
            LyfeSummoningBridge.despawnMountCleanly(level, teamStone);
        }
    }

    /**
     * Real user spec: Pick Up on a Broken wagon rolls 50%-destroy per Companion (mount Summoning
     * Stones are already cleanly de-summoned by {@link #onEnterBrokenState}, nothing left to roll
     * against). Picking up a healthy wagon just cleanly recalls everything, same as always.
     */
    @Override
    protected void onPickUp(final ServerLevel level, final boolean broken) {
        if (!LyfeSummoningBridge.isLoaded()) {
            return;
        }
        final ItemStack leadStone = this.getItem(LEAD_SLOT);
        if (!leadStone.isEmpty()) {
            LyfeSummoningBridge.despawnMountCleanly(level, leadStone);
        }
        final ItemStack teamStone = this.getItem(TEAM_SLOT);
        if (!teamStone.isEmpty()) {
            LyfeSummoningBridge.despawnMountCleanly(level, teamStone);
        }
        rollOrDespawnCompanion(level, COMPANION_LEFT_SLOT, this.companionLeft, broken);
        rollOrDespawnCompanion(level, COMPANION_RIGHT_SLOT, this.companionRight, broken);
    }

    private void rollOrDespawnCompanion(final ServerLevel level, final int slot, final Entity liveCompanion, final boolean broken) {
        final ItemStack stack = this.getItem(slot);
        if (stack.isEmpty()) {
            return;
        }
        if (broken && level.getRandom().nextFloat() < 0.5F) {
            if (liveCompanion != null) {
                liveCompanion.discard();
            }
            this.setItem(slot, ItemStack.EMPTY);
        } else {
            LyfeSummoningBridge.despawnCompanionCleanly(level, stack);
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Real user report, 2026-10-26: "my player model is always sitting in the left seat
        // visually. The first time I try to sit on the right I physically appear on the right seat,
        // and every time after that it's the left." Root cause: #interact() runs on BOTH client and
        // server (standard vanilla client-prediction behavior for responsive mounting), but
        // #validateSeatOccupants() used to only run inside this method's own ServerLevel-gated
        // block below -- so the CLIENT's own copy of #leftSeatOccupant/#rightSeatOccupant never got
        // cleared on dismount. Once either field was set once (client-side), it stayed set forever,
        // biasing every later client-side #interact() call toward whichever field was STILL null --
        // the server (correctly validated every tick) stayed authoritative for actual driving, which
        // is why steering already worked correctly even while the visual seat was wrong. Moved out
        // of the server-only block so the client's own copy self-cleans too.
        this.validateSeatOccupants();
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // Real live-test bug, 2026-10-26: mounts were discarded once by #onEnterBrokenState's own
        // despawnMountCleanly call, then immediately RE-SUMMONED the very next tick anyway, since
        // that despawn is deliberately cooldown-free (see LyfeSummoningBridge#despawnMountCleanly's
        // own doc) and this method never checked isBroken() at all -- "horses never actually
        // despawned" was really "they despawned and instantly came back." Gating both slots here
        // also directly satisfies the user's own explicit follow-up spec: "they should not respawn
        // if the wagon is broken either" -- no ticking at all means no summon attempt at all, for
        // either slot, for as long as the wagon stays Broken.
        if (!this.isBroken()) {
            tickLeadSlot(serverLevel);
            tickTeamSlot(serverLevel);
        }
        tickCompanionSlots(serverLevel);
        syncTeamLeash();
        syncWagonPullTarget();
        syncWagonLeashVisual();
        tickSteering();
        syncTeamFollow();
    }

    /** Clears a seat's tracked occupant once they're no longer actually riding -- same per-tick self-cleaning idiom {@link #tickLeadSlot}/{@link #tickTeamSlot} already use, rather than a dismount event hook. Runs on both client and server, see {@link #tick()}'s own doc for why. */
    private void validateSeatOccupants() {
        if (this.leftSeatOccupant != null && !this.hasPassenger(this.leftSeatOccupant)) {
            this.leftSeatOccupant = null;
            recalculateMaxSpeed();
        }
        if (this.rightSeatOccupant != null && !this.hasPassenger(this.rightSeatOccupant)) {
            this.rightSeatOccupant = null;
            recalculateMaxSpeed();
        }
    }

    private boolean stoneClaims(final int slot, final Entity entity) {
        final ItemStack stone = this.getItem(slot);
        if (stone.isEmpty() || !LyfeSummoningBridge.isLoaded()) {
            return false;
        }
        return LyfeSummoningBridge.getSummonedMountId(stone).map(id -> id.equals(entity.getUUID())).orElse(false);
    }

    /** Same upkeep shape as {@code AnimalCartEntity#tickMountSlot}, keyed to {@link #leadMount} -- no longer calls {@link #setPulling} directly, see {@link #syncWagonPullTarget()}. */
    private void tickLeadSlot(final ServerLevel level) {
        if (this.leadMount != null && !stoneClaims(LEAD_SLOT, this.leadMount)) {
            this.leadMount.discard();
            this.leadMount = null;
            recalculateMaxSpeed();
        }
        final ItemStack stone = this.getItem(LEAD_SLOT);
        if (stone.isEmpty() || !LyfeSummoningBridge.isLoaded()) {
            return;
        }
        final Vec3 leadSpawnPos = this.mountSpawnPosition();
        LyfeSummoningBridge.tickCompanion(level, stone, this.getUUID(), leadSpawnPos.x, leadSpawnPos.y, leadSpawnPos.z, this.getYRot());
        final Optional<UUID> mountId = LyfeSummoningBridge.getSummonedMountId(stone);
        if (mountId.isEmpty()) {
            return;
        }
        final Entity mount = level.getEntity(mountId.get());
        if (mount != null && mount.isAlive()) {
            // Reasserted every tick, not just on first resolution -- see #prepareCartMount's own
            // doc; this guarantees it even if something sets it back to true later during play, not
            // only at summon time. Temporary debug log, 2026-10-25 -- see CartMountDebugListener's
            // own doc; this specifically catches the "found it true before we got to reset it"
            // case, which the attack-event logging alone can't distinguish from "never became true
            // at all."
            if (mount.isInvulnerable()) {
                com.github.cerealklla.caryvan.CaryvanMod.LOGGER.warn(
                        "[cart-mount-debug] leadMount {} (uuid={}) was invulnerable=true before this tick's reset",
                        mount.getType(), mount.getUUID());
            }
            mount.setInvulnerable(false);
            if (this.leadMount != mount) {
                prepareCartMount(mount);
                this.leadMount = mount;
                recalculateMaxSpeed();
            }
        }
    }

    /** Same shape as {@link #tickLeadSlot}, tracking {@link #teamMount} instead. */
    private void tickTeamSlot(final ServerLevel level) {
        if (this.teamMount != null && !stoneClaims(TEAM_SLOT, this.teamMount)) {
            this.teamMount.discard();
            this.teamMount = null;
            recalculateMaxSpeed();
        }
        final ItemStack stone = this.getItem(TEAM_SLOT);
        if (stone.isEmpty() || !LyfeSummoningBridge.isLoaded()) {
            return;
        }
        final Vec3 teamSpawnPos = this.mountSpawnPosition();
        LyfeSummoningBridge.tickCompanion(level, stone, this.getUUID(), teamSpawnPos.x, teamSpawnPos.y, teamSpawnPos.z, this.getYRot());
        final Optional<UUID> mountId = LyfeSummoningBridge.getSummonedMountId(stone);
        if (mountId.isEmpty()) {
            return;
        }
        final Entity mount = level.getEntity(mountId.get());
        if (mount != null && mount.isAlive()) {
            if (mount.isInvulnerable()) {
                com.github.cerealklla.caryvan.CaryvanMod.LOGGER.warn(
                        "[cart-mount-debug] teamMount {} (uuid={}) was invulnerable=true before this tick's reset",
                        mount.getType(), mount.getUUID());
            }
            mount.setInvulnerable(false);
            if (this.teamMount != mount) {
                prepareCartMount(mount);
                this.teamMount = mount;
                recalculateMaxSpeed();
            }
        }
    }

    /**
     * Real user spec, 2026-10-26 (design-document.md §8's own diagram): 2 real Companion slots,
     * same summon-once/re-summon-after-cooldown upkeep {@link #tickLeadSlot}/{@link #tickTeamSlot}
     * already use for Summon Stones, now for a bound Allay in a Bottle instead -- the exact same
     * real {@code summoning.GuardCompanionSupport#tick} already dispatches on item type internally
     * (confirmed via its own doc), so {@link LyfeSummoningBridge#tickCompanion} needed no changes at
     * all to support this. A companion never pulls the wagon and is never tagged
     * {@link ModAttachments#CART_TEAM_MOUNT} (that tag specifically blocks riding, which doesn't
     * apply to a non-rideable Allay).
     *
     * <p><b>Real live-test bug, found and fixed same day</b>: "I was able to set an allay in the
     * right seat and it summoned, but then immediately flew away and wasn't following the wagon."
     * Root cause -- unlike a mount (see {@link #prepareCartMount}), a freshly-summoned companion kept
     * its own default vanilla AI goals, and a plain {@code Allay} with nothing else to do runs
     * {@code WaterAvoidingRandomFlyingGoal} -- free-roam wandering, not a small-radius stroll, so it
     * drifted away from the wagon with nothing anchoring it back.
     *
     * <p>The real fix is **not** a hand-rolled per-tick {@code snapTo} (a first draft did this, and
     * was correctly called out before shipping: "if you mean literally each tick then you are doing
     * the wrong thing" -- recomputing a moving position every tick is fine in principle (vanilla's
     * own passenger system does exactly that internally), but reimplementing it by hand duplicates
     * machinery this class already has and gets worse results: no client-side interpolation, so the
     * companion would visibly teleport/jitter every tick instead of moving smoothly). The companion
     * instead becomes a **real passenger** of this wagon -- {@link #prepareCartCompanion} (the same
     * {@code Mob#removeAllGoals} idiom {@link #prepareCartMount} already uses, see its own doc for
     * why not {@code setNoAi}) plus {@code Entity#startRiding}, reusing the exact same
     * {@link #getPassengerAttachmentPoint}/{@link #canAddPassenger} machinery already proven out for
     * the player bench seats -- which is also a literal, direct implementation of the user's own
     * stated plan that a Companion slot's occupant is **seated/standing on the wagon itself**, at the
     * same left/right bench position a player would otherwise occupy (design-document.md §8's new
     * diagram capture). Ranged-only combat while mounted, aggressive always-target-enemies behavior,
     * and the Left-slot-companion-as-NPC-driver concept (design-document.md §8) are real follow-up
     * work, not built this pass.
     */
    private void tickCompanionSlots(final ServerLevel level) {
        this.companionLeft = tickCompanionSlot(level, COMPANION_LEFT_SLOT, this.companionLeft, true);
        this.companionRight = tickCompanionSlot(level, COMPANION_RIGHT_SLOT, this.companionRight, false);
    }

    private Entity tickCompanionSlot(final ServerLevel level, final int slot, final Entity current, final boolean left) {
        Entity resolved = current;
        if (resolved != null && !companionClaims(slot, resolved)) {
            resolved.discard();
            resolved = null;
        }
        final ItemStack stack = this.getItem(slot);
        if (stack.isEmpty() || !LyfeSummoningBridge.isLoaded()) {
            return resolved;
        }
        final Vec3 spawnPos = this.companionSpawnPosition(left);
        LyfeSummoningBridge.tickCompanion(level, stack, this.getUUID(), spawnPos.x, spawnPos.y, spawnPos.z, this.getYRot());
        final Optional<UUID> id = LyfeSummoningBridge.getSummonedAllayId(stack);
        if (id.isEmpty()) {
            return resolved;
        }
        final Entity entity = level.getEntity(id.get());
        if (entity != null && entity.isAlive()) {
            entity.setInvulnerable(false);
            if (resolved != entity) {
                prepareCartCompanion(entity);
                entity.startRiding(this, true, false);
            }
            return entity;
        }
        return resolved;
    }

    /** Companion-slot mirror of {@link #prepareCartMount}'s goal-strip -- no {@link ModAttachments#CART_TEAM_MOUNT} tag, since that's specifically about blocking direct riding of a mount, which doesn't apply to a non-rideable Allay. See {@link #tickCompanionSlots}'s own doc for the real bug this fixes. */
    private static void prepareCartCompanion(final Entity entity) {
        if (entity instanceof Mob mob) {
            mob.removeAllGoals(goal -> true);
        }
        entity.setInvulnerable(false);
    }

    /** Mirror-image of {@link #stoneClaims}, for a Companion slot's bound Allay instead of a Summoning Stone's bound mount. */
    private boolean companionClaims(final int slot, final Entity entity) {
        final ItemStack stack = this.getItem(slot);
        if (stack.isEmpty() || !LyfeSummoningBridge.isLoaded()) {
            return false;
        }
        return LyfeSummoningBridge.getSummonedAllayId(stack).map(id -> id.equals(entity.getUUID())).orElse(false);
    }

    /** A simple fixed spot beside the wagon's own bed -- not the real "stand in formation" positioning design-document.md §8's own diagram describes, deliberately deferred (see {@link #tickCompanionSlots}'s own doc). */
    private Vec3 companionSpawnPosition(final boolean left) {
        final double xOffset = left ? 1.0D : -1.0D;
        final Vec3 local = new Vec3(xOffset, 1.0D, 0.0D);
        final float yawRad = (float) Math.toRadians(this.getYRot());
        return this.position().add(local.yRot(-yawRad));
    }

    /**
     * Called once, right when a freshly-summoned mount is first resolved. Two things, both
     * explicit user requests, 2026-10-25:
     * <ul>
     *   <li>Strips idle AI goals -- vanilla's default idle-wandering AI visibly fought the
     *   leash/chase physics. **Not** {@code Mob#setNoAi} -- a first attempt used that and caused a
     *   real regression ("team isn't moving when lead moves"): confirmed via the real decompiled
     *   bytecode that {@code isNoAi()} gates {@code LivingEntity#isEffectiveAi()}, which gates
     *   {@code Mob#serverAiStep()} ENTIRELY -- and {@code PathNavigation#tick()} (the thing that
     *   actually executes a leash-pull's navigation command toward the holder) only runs from
     *   inside that same gated method, so disabling AI silently stalls leash-following too, not
     *   just wandering. The public {@link Mob#removeAllGoals} is the correct, narrower tool -- it
     *   clears the goal list (so nothing competes for independent movement) without touching
     *   {@code isEffectiveAi()}, so {@code serverAiStep()}/navigation keep ticking and the leash's
     *   own direct navigation commands still execute normally.</li>
     *   <li>Tags the mount with {@link ModAttachments#CART_TEAM_MOUNT} so
     *   {@code interaction.CartMountInteractionListener} refuses a player trying to ride it
     *   directly -- "I think once you disable being able to ride the horses individually in a
     *   team-style wagon we won't have that possibility of a problem anymore," in response to a
     *   real repro where riding the team horse away from the lead (then dismounting) stranded it
     *   beyond vanilla's own real leash-snap distance, causing a drop/re-leash spray of real Lead
     *   items every tick while it slowly walked back (see {@link #syncTeamLeash()}'s own doc).</li>
     * </ul>
     */
    private static void prepareCartMount(final Entity entity) {
        if (entity instanceof Mob mob) {
            mob.removeAllGoals(goal -> true);
        }
        // Real user report, 2026-10-25: "the horses were immortal for some reason... I punched it
        // many times and it did not flash/make a sound/take damage in any visible way." That exact
        // symptom (zero sound/flash/damage) matches vanilla's real Entity#invulnerable flag
        // precisely (confirmed via the real decompiled bytecode of Entity#isInvulnerableToBase --
        // when true, the entire damage pipeline is a no-op this early, before any sound/flash/health
        // change happens at all). No code in this mod or Lyfe's summoning bridge was found setting
        // it anywhere -- this forces it false unconditionally regardless of root cause (e.g. stray
        // NBT on whatever was captured into the bound Summoning Stone), since a cart's own summoned
        // mount should never be invulnerable by design.
        entity.setInvulnerable(false);
        entity.setData(ModAttachments.CART_TEAM_MOUNT, Boolean.TRUE);
    }

    /**
     * Leashes the team horse directly to the lead -- real user correction, 2026-10-25: "we can just
     * use the existing leads to connect them to the one in front of them," replacing an earlier
     * rigid {@code snapTo}-every-tick implementation. {@link Leashable#setLeashedTo} is a one-time
     * state change; vanilla's own generic {@code Entity#tick()} drives the actual pull-when-too-far
     * leash physics every tick from then on with no further code here (confirmed via the real
     * decompiled bytecode, not assumed). Guarded by {@code getLeashHolder() != lead} so this doesn't
     * redundantly re-leash an already-correctly-leashed mount every tick.
     */
    private void syncTeamLeash() {
        if (this.teamMount == null || !this.teamMount.isAlive() || this.leadMount == null || !this.leadMount.isAlive()) {
            return;
        }
        if (this.teamMount instanceof Leashable leashable && leashable.getLeashHolder() != this.leadMount) {
            leashable.setLeashedTo(this.leadMount, true);
        }
    }

    /**
     * The real authoritative team-to-lead following physics -- see {@link #TEAM_FOLLOW_SPACING}'s
     * own doc for why this exists instead of trusting vanilla's leash tug alone. Called after {@link
     * #tickSteering()} so it reads the lead's own already-this-tick-updated position, minimizing lag.
     * Sets the team's yaw toward the lead unconditionally (even standing still, per the explicit
     * request to always face forward), then smoothly closes any gap away from the resting distance --
     * a clamped step rather than a hard snap, same smooth-pursuit shape {@code AbstractDrawnEntity#
     * pulledTick()} already uses for the wagon's own chase.
     */
    private void syncTeamFollow() {
        if (this.teamMount == null || !this.teamMount.isAlive() || this.leadMount == null || !this.leadMount.isAlive()) {
            return;
        }
        final Vec3 toLead = this.leadMount.position().subtract(this.teamMount.position());
        final Vec3 horizontal = new Vec3(toLead.x, 0.0D, toLead.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            return;
        }
        final Vec3 direction = horizontal.normalize();
        final float heading = (float) Math.toDegrees(-Mth.atan2(direction.x, direction.z));
        this.teamMount.setYRot(heading);
        this.teamMount.setYHeadRot(heading);
        final double diff = horizontal.length() - TEAM_FOLLOW_SPACING;
        if (Math.abs(diff) < 1.0E-3D) {
            return;
        }
        final double step = Math.min(Math.abs(diff), TEAM_CATCH_UP_RATE) * Math.signum(diff);
        this.teamMount.move(MoverType.SELF, direction.scale(step));
    }

    /**
     * Keeps this cart's real {@link #getPulling() puller} pointed at the *rearmost alive* mount in
     * the chain -- real user correction, 2026-10-25: "'furthest' horse is the one that leads [pulls
     * the wagon]... always ensure the wagon is connected to the rearmost horse," specifically so a
     * mid-chain death (the team horse dying while the lead survives) doesn't leave the wagon chasing
     * a dead/missing link -- it just falls back to the lead directly.
     */
    private void syncWagonPullTarget() {
        final Entity rearmost;
        if (this.teamMount != null && this.teamMount.isAlive()) {
            rearmost = this.teamMount;
        } else if (this.leadMount != null && this.leadMount.isAlive()) {
            rearmost = this.leadMount;
        } else {
            rearmost = null;
        }
        if (this.getPulling() != rearmost) {
            this.setPulling(rearmost);
        }
    }

    /**
     * Keeps this cart itself leashed to whatever it's currently pulling -- real user request,
     * 2026-10-25, after seeing vanilla's own real leash rope render between the two horses:
     * "can you attach two ropes just like leads to the front left and right corner of the wagon from
     * the next closest horse?" This is purely visual -- confirmed via the real decompiled bytecode
     * that {@code EntityRenderer}'s own generic {@code extractRenderState} already checks
     * {@code instanceof Leashable} and builds real rope {@code LeashState}s for ANY leashed entity
     * automatically (the exact same mechanism that already draws the team-to-lead rope), so
     * implementing {@link Leashable} here needs zero custom rendering code at all -- see
     * {@link #supportQuadLeash()}/{@link #getQuadLeashOffsets()} for the two-rope-from-the-front
     * part specifically (the same real quad-leash feature {@code AbstractBoat} already uses for its
     * own four corners, just overridden here for two instead of the usual four). {@link
     * #checkElasticInteractions} is overridden to a no-op so vanilla's own elastic leash-tension
     * physics never actually pushes this cart around -- {@link AbstractDrawnEntity#pulledTick()}
     * stays the sole real movement authority, exactly as already resolved for the driving-bench
     * question earlier this session.
     */
    private void syncWagonLeashVisual() {
        final Entity rearmost = this.getPulling();
        if (rearmost == null) {
            if (this.isLeashed()) {
                this.removeLeash();
            }
            return;
        }
        if (this.getLeashHolder() != rearmost) {
            this.setLeashedTo(rearmost, true);
        }
    }

    @Override
    public Leashable.LeashData getLeashData() {
        return this.leashData;
    }

    @Override
    public void setLeashData(final Leashable.LeashData leashData) {
        this.leashData = leashData;
    }

    @Override
    public boolean supportQuadLeash() {
        return true;
    }

    /** Front-left and front-right corners specifically -- real user request, not the usual four-corner quad leash. */
    @Override
    public Vec3[] getQuadLeashOffsets() {
        final double halfWidth = this.getBbWidth() * 0.5D;
        final double frontZ = -this.getBbWidth() * 0.5D;
        final double height = this.getBbHeight() * 0.4D;
        return new Vec3[] {
                new Vec3(-halfWidth, height, frontZ),
                new Vec3(halfWidth, height, frontZ),
        };
    }

    /**
     * No-op, deliberately -- {@link Leashable#checkElasticInteractions} is the one real place
     * vanilla's own leash physics calls {@code Entity#addDeltaMovement} (confirmed via the real
     * decompiled bytecode), i.e. the only hook that could actually push this cart around. This cart
     * already has its own real movement authority ({@link AbstractDrawnEntity#pulledTick()}); the
     * leash here exists purely for the rope's own visual (see {@link #syncWagonLeashVisual()}'s own
     * doc), so this must never apply a second, competing force.
     */
    @Override
    public boolean checkElasticInteractions(final Entity entity, final Leashable.LeashData leashData) {
        return false;
    }

    /**
     * Real user report, 2026-10-25: "the rope is snapping 2-3 times a second and dropping loose
     * ropes." Traced via the real decompiled bytecode of {@code Leashable#tickLeash}: once
     * {@code leashDistanceTo(holder) > leashSnapDistance()} (default 12.0, confirmed from the real
     * constant -- a different, smaller number than {@code MAXIMUM_ALLOWED_LEASHED_DIST}'s 16.0, easy
     * to confuse), it unconditionally plays a snap sound and calls {@link Leashable#leashTooFarBehaviour}
     * (default: {@code dropLeash()}) -- entirely independent of {@link #checkElasticInteractions}, so
     * that earlier no-op didn't prevent this. {@link #syncWagonLeashVisual()} already re-derives this
     * cart's leash relationship authoritatively from {@link #getPulling()} every tick regardless of
     * distance, so vanilla's own independent distance-based snap is redundant at best and actively
     * fights it at worst -- a large return here makes it effectively never trigger, leaving our own
     * sync method as the sole authority on whether the rope exists.
     */
    @Override
    public double leashSnapDistance() {
        return 1000.0D;
    }

    /**
     * Real user report, 2026-10-25: "it did drop 'free' leads when the horse died which it should
     * not do." Traced via the real decompiled bytecode of {@link Leashable#tickLeash}: its very
     * first real branch, BEFORE any distance check at all (so {@link #leashSnapDistance()}'s huge
     * override never even gets a chance to matter here), is "if either the leashed entity or its
     * holder can no longer interact with the level" (i.e. either one died/was removed) -> spawn a
     * real Lead item via {@code dropLeash()} (gated only by the vanilla {@code entityDrops} game
     * rule, which defaults on). This cart's own leash to its puller (see {@link
     * #syncWagonLeashVisual()}) is purely a synthetic visual relationship -- a player never placed a
     * real Lead here -- so the moment the puller dies, vanilla's own cleanup would otherwise spawn
     * an unearned item. {@link Leashable#dropLeash()} is a default interface method, so (unlike a
     * plain vanilla {@code Horse}, see {@code interaction.CartMountDeathListener} for the other half
     * of this same fix) this cart can override it directly: redirect to {@link #removeLeash()}
     * (clears the relationship with no item spawn) every time vanilla would otherwise have dropped
     * one.
     */
    @Override
    public void dropLeash() {
        this.removeLeash();
    }

    /** Either slot's own bound stone may claim this cart's real puller target -- see {@link #syncWagonPullTarget()}. */
    @Override
    protected boolean canBePulledBy(final Entity entity) {
        return stoneClaims(LEAD_SLOT, entity) || stoneClaims(TEAM_SLOT, entity);
    }

    /**
     * Sneak+right-click sits on the driving bench (no real steering yet, see this class's own doc);
     * a plain right-click falls through to {@link AbstractDrawnInventoryEntity}'s own inherited
     * cargo-menu interaction, same as {@code AnimalCartEntity}.
     */
    // How high above this cart's own Y origin a right-click has to land to count as "the bench"
    // rather than "the cargo area" -- see #interact's own doc for why this can't be a sneak-based
    // trigger at all. A fraction of the real hitbox height rather than a hardcoded number, so it
    // stays sane if the hitbox is ever retuned.
    private static final double BENCH_CLICK_HEIGHT_FRACTION = 0.6;

    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand, final Vec3 location) {
        // Pick Up / Repair, 2026-10-26 (health system work) -- owner-only, sneak+right-click is an
        // alternate trigger for the same real GUI-button actions (see WagonScreen's own doc), not a
        // substitute confirm mechanism -- the actual "Are you sure?" dialog is a real client-side
        // ConfirmActionScreen, opened via #requestPickUpConfirm's own clientbound payload (never
        // referenced directly here -- this method runs on both sides, and a client-only type
        // reference here would crash a dedicated server on class load, per the root project's own
        // CLAUDE.md -- #requestPickUpConfirm itself self-guards on `player instanceof ServerPlayer`,
        // so calling it from this method's client-side invocation is already a harmless no-op with
        // no extra `isClientSide()` check needed here). Holding a Wheels item matching this wagon's
        // own wood species -> Repair (no confirm needed, matches the GUI button's own behavior) --
        // this one DOES need the `isClientSide()` guard, since it mutates state directly rather than
        // going through a self-guarding network call. Checked first, before the existing
        // sneak-falls-through-to-cargo behavior below -- a non-owner's sneak+right-click is
        // unaffected (still falls through to cargo, unchanged).
        if (player.isShiftKeyDown() && this.isOwner(player)) {
            final ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty()) {
                this.requestPickUpConfirm(player);
                return InteractionResult.SUCCESS;
            }
            // Real user request, 2026-10-28: "I want to be able to take a wagon that was crafted
            // with 1 kind wheel, and be able to replace the wheels if broken with a different kind
            // of wheel, and actually see the new style of wheel" -- widened from only accepting
            // Wheels matching this wagon's own body species to ANY real Wheels item, since per-wheel
            // species is already tracked (CartWheel#woodType/DATA_WHEEL_WOOD_TYPE[], see Wainwright
            // Station's own doc) and the renderer now actually reads it (WagonRenderer#submit).
            final java.util.Optional<net.minecraft.world.level.block.state.properties.WoodType> heldWheelSpecies =
                    ModItems.woodTypeForWheelsItem(held.getItem());
            if (heldWheelSpecies.isPresent()) {
                if (!this.level().isClientSide()) {
                    this.handleRepairAttempt(player, held, heldWheelSpecies.get());
                }
                return InteractionResult.SUCCESS;
            }
        }
        // Real bug, 2026-10-25: sneak+right-click can never actually mount anything, full stop --
        // vanilla's own generic Player#rideTick() checks "is shift currently held" EVERY tick while
        // riding and immediately calls stopRiding() the instant it's true (confirmed via the real
        // decompiled bytecode), so a sneak-triggered mount undoes itself on the very next tick --
        // the exact stale-held-shift-key bug class Hand Cart's own hitch toggle already hit once
        // this session. Sneaking can only ever mean "let go" in vanilla, never "get on." Switched to
        // a position-based trigger instead: a plain right-click that lands high enough on the model
        // (near where the placeholder bench sits) mounts; anywhere else falls through to cargo, same
        // as Animal Cart's own plain-right-click convention.
        //
        // Real user request, 2026-10-25: a second seat -- changed from "only if nobody's riding yet"
        // to "only if there's still room" (see #canAddPassenger, now allowing up to 2).
        //
        // Real follow-up, same day: "confirmed i'm on the left seat, is there away to sit in the
        // right if i'm alone?" Originally whoever mounted FIRST always landed at the left/driver
        // seat regardless of where they clicked. Now the click's own X position (relative to this
        // cart's current facing) picks a side directly -- if that side is already taken but the
        // other one isn't, falls back to the free one instead of refusing outright.
        // Real user correction, 2026-10-26: a first attempt at the mount-gate fix below let a player
        // mount a seat even while that seat's own Companion already occupied it, miscounting
        // companions as never competing for a player seat at all -- explicit "False, the purpose of
        // those slots is for a companion to occupy those seats. I never asked you to let me bypass
        // that and sit there anyway if a companion is already there." Reverted to the plain,
        // symmetric rule: exactly 2 seats total, a companion or a player, first to claim a given side
        // keeps it -- see #leftFree/#rightFree below and #canAddPassenger's own doc for the other
        // half of this.
        //
        // Real root cause, 2026-10-26, found via the diagnostic logging added right above this fix:
        // "clickHeightFraction=-46.7 (needs > 0.6)" -- not a small miscalibration, a ~70-block-off
        // garbage value. Confirmed against the real decompiled source
        // ({@code MultiPlayerGameMode#interact}): the CLIENT already subtracts the entity's own
        // position before ever sending this packet --
        // {@code Vec3 location = hitResult.getLocation().subtract(entity.getX(), entity.getY(),
        // entity.getZ());} -- so by the time {@code interact(Player, InteractionHand, Vec3 location)}
        // runs on the server, {@code location} is already LOCAL (relative to this entity's own
        // origin, typically a small number), not a world-absolute coordinate. Subtracting
        // {@code this.getY()} from it a second time was always wrong -- it just happened to go
        // unnoticed through this session's own dev-world testing (apparently a low enough Y there for
        // the garbage result to occasionally still clear the threshold by chance) until tested here
        // on Production's real terrain (surface Y ~70+), where it could never pass at all. Fixed by
        // using {@code location.y} directly -- it's already the height above this entity's own base.
        final boolean shiftHeld = player.isShiftKeyDown();
        final boolean hasRoom = this.getPassengers().size() < 2;
        CaryvanMod.LOGGER.info(
                "[cart-mount-debug] interact() gate check by {} -- shiftHeld={}, passengers={}, hasRoom={}, locationY={} (needs > {}), bbHeight={}",
                player.getName().getString(), shiftHeld, this.getPassengers().size(), hasRoom, location.y,
                this.getBbHeight() * BENCH_CLICK_HEIGHT_FRACTION, this.getBbHeight());
        // Real live-test bug, 2026-10-26: a Broken (0 HP) wagon could still be mounted/driven --
        // "broken means broken, you shouldn't be able to drive it no matter what's hitched to it."
        // Independent of whether a mount happens to be attached (see #tick's own fix for that side).
        if (!this.isBroken() && !shiftHeld && hasRoom
                && location.y > this.getBbHeight() * BENCH_CLICK_HEIGHT_FRACTION) {
            final boolean alreadyRiding = this.hasPassenger(player);
            final boolean wantsLeft = this.isLeftSideClick(player);
            final boolean leftFree = this.leftSeatOccupant == null && this.companionLeft == null;
            final boolean rightFree = this.rightSeatOccupant == null && this.companionRight == null;
            CaryvanMod.LOGGER.info(
                    "[cart-mount-debug] interact() by {} -- alreadyRiding={}, wantsLeft={}, leftFree={}, rightFree={}, leftSeatOccupant={}, rightSeatOccupant={}, companionLeft={}, companionRight={}",
                    player.getName().getString(), alreadyRiding, wantsLeft, leftFree, rightFree,
                    this.leftSeatOccupant == null ? "null" : this.leftSeatOccupant.getName().getString(),
                    this.rightSeatOccupant == null ? "null" : this.rightSeatOccupant.getName().getString(),
                    this.companionLeft == null ? "null" : this.companionLeft.getType(),
                    this.companionRight == null ? "null" : this.companionRight.getType());
            if (alreadyRiding) {
                // Don't let re-clicking the bench while already seated reassign/duplicate seat
                // tracking -- a real, separate bug found while investigating the report above.
                return InteractionResult.SUCCESS;
            }
            if (wantsLeft && leftFree) {
                this.leftSeatOccupant = player;
            } else if (!wantsLeft && rightFree) {
                this.rightSeatOccupant = player;
            } else if (leftFree) {
                this.leftSeatOccupant = player;
            } else if (rightFree) {
                this.rightSeatOccupant = player;
            } else {
                return super.interact(player, hand, location);
            }
            CaryvanMod.LOGGER.info("[cart-mount-debug] assigned {} to {} seat", player.getName().getString(),
                    player == this.leftSeatOccupant ? "LEFT" : "RIGHT");
            player.startRiding(this, true, true);
            recalculateMaxSpeed();
            return InteractionResult.SUCCESS;
        }
        return super.interact(player, hand, location);
    }

    /**
     * Which side of the wagon {@code player} should be seated on -- real user report, 2026-10-26,
     * with a precise repro: "Click Left Seat -> Seats me in Right seat... Click Either seat -> Seats
     * me in Left seat... Log out and log back in, first mount is right seat, every [one] after is
     * left." The result never actually tracked which HALF of the bench was clicked at all -- it was
     * driven almost entirely by this wagon's own current {@code yRot} at the exact instant of the
     * click, since the real click hit point used before this fix sits only ~0.3 blocks off either
     * side of center (the bench's own real width), a margin easily swamped by ordinary tick-to-tick
     * yaw jitter from {@code AbstractDrawnEntity#pulledTick()}'s own continuous chase-physics
     * correction (worse right after a world load, while the wagon is still settling toward its
     * puller) -- not a real sign bug, a real precision problem. Switched from the click's exact 3D
     * hit point to {@code player}'s own real standing position instead -- a player can stand several
     * real blocks to either side of the wagon, a margin yaw jitter can't meaningfully compete with,
     * so "which side are you standing on when you click" is a far more robust signal than "which
     * literal pixel did the ray hit" ever was. Same real "left = +X at yaw 0" convention {@link
     * #getPassengerAttachmentPoint}/{@link #tickSteering()} already use.
     */
    private boolean isLeftSideClick(final Player player) {
        final double dx = player.getX() - this.getX();
        final double dz = player.getZ() - this.getZ();
        final double yawRad = Math.toRadians(this.getYRot());
        final double leftDot = dx * Math.cos(yawRad) + dz * Math.sin(yawRad);
        return leftDot > 0.0D;
    }

    /**
     * Real user report, 2026-10-25: "When I right click the wagon I'm actually being sat in the
     * back of the wagon, not on the bench up front." Root cause, confirmed via the real decompiled
     * bytecode of {@code Entity#getPassengerRidingPosition}: it computes
     * {@code this.position().add(getPassengerAttachmentPoint(...))} directly -- the returned vector
     * is added as a RAW WORLD-SPACE offset, never rotated by the entity's own {@code yRot} at all.
     * The old implementation returned a fixed {@code (0, h, -z)} offset as if it would be rotated
     * into the wagon's current facing automatically (the same wrong assumption this session's own
     * earlier phantom-horse-proxy work briefly made about a different method, before being
     * corrected) -- so the seat only ever actually landed at the bench whenever the wagon happened
     * to be facing its original placement direction, and anywhere else once it had been towed
     * around to face a different way. Fixed by explicitly rotating the local offset into world
     * space first, via {@link Vec3#yRot(float)} with the wagon's own current yaw -- the same real,
     * proven transform this class's own rope-rendering code and the old (now-removed) drive-proxy
     * bench-anchor code both already used for exactly this kind of local-to-world conversion.
     *
     * <p><b>The Z sign is +, not -, and does NOT follow {@code WagonModel}'s own bench-anchor
     * sign</b> -- a real mistake made and corrected the same day. These two systems use genuinely
     * different coordinate conventions: this method's return value is added directly to
     * {@code this.position()} in plain world-relative space (confirmed via the real decompiled
     * bytecode of {@code Entity#getPassengerRidingPosition}), the same plain convention {@code
     * AbstractDrawnEntity#mountSpawnPosition} uses (where +Z at yaw 0 is confirmed to be "toward the
     * horses"). {@code WagonModel}'s own mesh, by contrast, renders through vanilla's standard
     * model-space transform ({@code WagonRenderer#submit}'s {@code Axis.YP.rotationDegrees(180 -
     * yRot)} plus an X/Y mirror scale -- the same quirk every vanilla entity model uses), a
     * completely different space where mesh-local -Z happens to end up visually "front." These two
     * -Z-means-different-things conventions are NOT interchangeable, and flipping this value to
     * match a bench-anchor sign change (as a same-day attempt briefly did) was the actual bug --
     * confirmed directly by the user's own two live reports: at +1.75 (paired with a temporarily
     * mis-anchored bench) they confirmed "the player is now in the correct position," and only after
     * this was flipped to -1.75 to "match" the bench fix did they report "the player is still
     * sitting furthest away from the horses." Reverted to +1.75, independent of wherever {@code
     * WagonModel}'s own bench anchor sits.
     *
     * <p>Real user follow-up, same day, once the single centered seat was confirmed correct: "i'm
     * in the center, not on the left or right, can you add the offset and second seat?" -- matches
     * design-document.md §8 point 2's own "1-2 seats, left=driver" spec. {@link #SEAT_X_OFFSET}
     * shifts whoever's tracked in {@link #leftSeatOccupant} to the LEFT half of the bench and
     * {@link #rightSeatOccupant} to the right. "Left"/"right" here means the driver's own left/right
     * while actually facing forward (+Z, per this method's own doc above) -- confirmed against
     * {@link #tickSteering()}'s own real strafe-direction math (a positive `right` input moves -X at
     * yaw 0), so left = +X.
     *
     * <p>Originally derived purely from passenger list index (0 = left); real follow-up the same
     * day -- "is there away to sit in the right if i'm alone?" -- there was no way to deliberately
     * choose, since whoever mounted first always became index 0. Now reads the real per-side
     * tracking {@link #interact} populates instead, so a lone player can land on either side
     * depending on which half of the bench they actually clicked.
     */
    private static final double SEAT_X_OFFSET = 0.34375D; // 5.5 units (half of a half-bench) in blocks.

    /**
     * A Companion-slot occupant (see {@link #tickCompanionSlots}'s own doc) is a real passenger too,
     * at the exact same left/right bench spot a player would otherwise sit -- added 2026-10-26, the
     * same "left = +X" side convention this method already established, just also checked against
     * {@link #companionLeft}/{@link #companionRight} alongside {@link #leftSeatOccupant}.
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(final Entity entity, final EntityDimensions dimensions, final float scale) {
        final boolean leftSide = entity == this.leftSeatOccupant || entity == this.companionLeft;
        final double xOffset = leftSide ? SEAT_X_OFFSET : -SEAT_X_OFFSET;
        final Vec3 local = new Vec3(xOffset, this.getBbHeight() * 0.85D, 1.75D).scale(scale);
        final float yawRad = (float) Math.toRadians(this.getYRot());
        return local.yRot(-yawRad);
    }

    /**
     * Up to 2 riders total, player or Companion, not both at the same side -- real user correction,
     * 2026-10-26: "the purpose of those slots is for a companion to occupy those seats... what would
     * [bypassing that] do to the companion? do they die? does the companion go back to the wagon
     * owner? there are unnecessary complications we'd have to overcome for a feature I never asked
     * for." A companion's own side is refused once a player already holds it (mirrors {@link
     * #interact}'s own {@code leftFree}/{@code rightFree} check for the reverse direction) -- a
     * player's own click-time check already guarantees a free side exists before {@code startRiding}
     * is even attempted, so the plain total-count cap is enough for that branch.
     */
    @Override
    protected boolean canAddPassenger(final Entity passenger) {
        if (passenger == this.companionLeft) {
            return this.leftSeatOccupant == null;
        }
        if (passenger == this.companionRight) {
            return this.rightSeatOccupant == null;
        }
        return this.getPassengers().size() < 2;
    }

    /**
     * Real steering, rebuilt 2026-10-25 as "remote control" -- see this class's own top-level doc
     * for why the first (phantom-horse-proxy) attempt was scrapped. The driver never leaves their
     * seat on this entity; their real input is read directly off {@code ServerPlayer}'s own
     * {@code lastClientInput} field (confirmed via the real decompiled bytecode of
     * {@code ServerGamePacketListenerImpl#handlePlayerInput} that this is set unconditionally from
     * every {@code ServerboundPlayerInputPacket}, regardless of what the player is riding -- the
     * client itself sends that packet on every input change independent of vehicle type too,
     * confirmed via {@code LocalPlayer#tick()}'s own bytecode). {@link #leadMount}'s yaw is set to
     * the driver's own look yaw every tick -- not accumulated/diffed against anything -- which is
     * exactly what closed off the previous version's runaway feedback loop (that one re-applied a
     * roughly-constant angular offset every tick instead of reading an absolute, self-contained
     * value). Movement is then forward/strafe transformed into that same frame, at a fixed fraction
     * of the lead mount's own real movement-speed attribute.
     */
    private void tickSteering() {
        if (this.leadMount == null || !this.leadMount.isAlive()) {
            return;
        }
        // Reads #leftSeatOccupant specifically (the real "left = driver" seat, see
        // #getPassengerAttachmentPoint's own doc), not just whoever mounted first -- a lone player
        // can now deliberately choose the right seat instead, which must NOT steer.
        if (!(this.leftSeatOccupant instanceof ServerPlayer driver)) {
            return;
        }
        final float driverYaw = driver.getYRot();
        this.leadMount.setYRot(driverYaw);
        this.leadMount.setYHeadRot(driverYaw);
        final Input input = driver.getLastClientInput();
        final float forward = (input.forward() ? 1.0F : 0.0F) - (input.backward() ? 1.0F : 0.0F);
        final float strafe = (input.right() ? 1.0F : 0.0F) - (input.left() ? 1.0F : 0.0F);
        if (forward == 0.0F && strafe == 0.0F) {
            return;
        }
        final float yawRad = (float) Math.toRadians(driverYaw);
        final float sin = Mth.sin(yawRad);
        final float cos = Mth.cos(yawRad);
        final Vec3 direction = new Vec3(-sin * forward - cos * strafe, 0.0D, cos * forward - sin * strafe).normalize();
        final double speed = this.getMaxSpeed();
        this.leadMount.move(MoverType.SELF, direction.scale(speed > 0.0D ? speed : STEER_SPEED_FALLBACK));
    }

    /**
     * Real user spec (access-gate correction, 2026-10-26): the owner always gets in; a non-owner
     * only while NOT ({@code isBroken()} and a Companion is still genuinely alive -- see {@link
     * #hasLiveDefendingCompanion}/{@link #canNonOwnerAccessMenu}'s own doc for why this is the real
     * "dead" signal, not {@code Entity#isAlive()}, and why that closes off the earlier deadlock.
     */
    @Override
    public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
        if (!this.isOwner(player) && !this.canNonOwnerAccessMenu()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "This wagon's Companions are still defending it -- they must be defeated first."));
            return null;
        }
        this.unpackChestVehicleLootTable(player);
        return new WagonMenu(ModMenus.WAGON.get(), containerId, inventory, this);
    }

    @Override
    protected boolean hasLiveDefendingCompanion() {
        return isLiveDefending(this.getItem(COMPANION_LEFT_SLOT)) || isLiveDefending(this.getItem(COMPANION_RIGHT_SLOT));
    }

    private static boolean isLiveDefending(final ItemStack stack) {
        return !stack.isEmpty() && LyfeSummoningBridge.isLoaded() && !LyfeSummoningBridge.isOnCooldown(stack, System.currentTimeMillis());
    }

    /**
     * Real live-test bug, 2026-10-26: "I was able to both click+drag and shift+click summon stones
     * out of the wagon without A) getting a warning, or B) any ever being destroyed... only works
     * with the pick up button." Root cause -- this method (and the whole slot-click confirm/roll
     * mechanism it feeds) was only ever extended to the two {@link #COMPANION_LEFT_SLOT}/
     * {@link #COMPANION_RIGHT_SLOT} cases; {@link #LEAD_SLOT}/{@link #TEAM_SLOT} (where a real
     * Summoning Stone actually lives) were never resolved at all, so {@code
     * AbstractDamageableDrawnEntity#confirmCompanionSlotRemoval}'s roll always had nothing to roll
     * against for a Mount slot specifically, even once {@code WagonMenu}'s own {@code clicked}/
     * {@code quickMoveStack} guards (see those classes' own doc) were widened to intercept
     * {@code MountSlot} too.
     */
    @Override
    protected Entity getCompanionEntityForSlot(final int slotIndex) {
        if (slotIndex == COMPANION_LEFT_SLOT) {
            return this.companionLeft;
        }
        if (slotIndex == COMPANION_RIGHT_SLOT) {
            return this.companionRight;
        }
        if (slotIndex == LEAD_SLOT) {
            return this.leadMount;
        }
        if (slotIndex == TEAM_SLOT) {
            return this.teamMount;
        }
        return null;
    }
}
