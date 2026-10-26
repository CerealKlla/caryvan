package com.github.cerealklla.caryvan.entity;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.bridge.LyfeSummoningBridge;
import com.github.cerealklla.caryvan.menu.CarriageMenu;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Carriage (design-document.md §8's diagram, built 2026-10-26) -- a bigger Wagon originally, now a
 * real enclosed-cabin redesign (Phase 1-4, same day): a real 3-horse "triangle"/"spike" team (lead
 * alone out front, 2 team horses abreast behind it -- see {@link #leadSpawnPosition()}/{@link
 * #teamSpawnPosition(boolean)}), a real enclosed cabin with 2 independently-repairable doors (see
 * this class's own Door-repair section, {@code CarriageModel}'s own doc), and genuinely separate
 * interior passenger seating reached only through those doors (see {@link #enterInteriorSeat}).
 *
 * <p>Still has 6 real "roof/running-board" Companion seats too -- real user spec: "Carriage should
 * allow Driver and Passenger up front, and have 4 internal seats for non-combatants," later corrected
 * (Phase 2 dispatch): these 6 are NOT the cabin's real interior seats at all, they're companions
 * riding on the roof or standing on the side running boards (visible in the user's own reference
 * photo) -- unchanged by the redesign. The front two ({@link #FRONT_LEFT_SLOT}/
 * {@link #FRONT_RIGHT_SLOT}) are the exact same dual-mode seat {@code WagonEntity} already has (a
 * player can click to sit directly, driver=left steers; a bound Allay in a Bottle placed in the
 * matching slot rides there too) -- the 4 others ({@link #INTERIOR_SLOTS}, a slightly stale name
 * now that real interior seating exists separately -- kept to avoid an unnecessary rename churn) are
 * Companion-slot-only, not directly player-clickable. {@link #companions}' index 0/1 are the front
 * pair, 2-5 the rest, each with its own fixed left/right + front-to-back position; see
 * {@link #seatLocalOffset} for the actual per-seat coordinates.
 *
 * <p>Body is "a little longer and wider than the Wagon to fit 4 inside comfortably" (explicit user
 * spec) -- see {@code CarriageModel}'s own doc for the real scale factors.
 */
public final class CarriageEntity extends AbstractDamageableDrawnInventoryEntity implements Leashable, com.github.cerealklla.nuclyus.statbar.StatBarOwner {

    /** Placeholder, flagged for live-test tuning -- see the approved plan's §2. */
    private static final float BASE_MAX_HEALTH = 150.0F;

    // Carriage redesign Phase 4, 2026-10-26: triangle/"spike" team formation -- real user spec, "1
    // lead horse alone out front, 2 horses abreast behind it (closer to the carriage, forming the
    // base of the triangle)." A genuine 3rd mount slot, not a 2-slot system -- every slot after this
    // one shifts by 1 from the old layout (FRONT_LEFT_SLOT was 2, now 3; see CarriageMenu's own
    // layout, which mirrors this exactly). See #leadSpawnPosition/#teamSpawnPosition for the real
    // triangle positional math this slot split feeds.
    private static final int LEAD_SLOT = 0;
    private static final int TEAM_LEFT_SLOT = 1;
    private static final int TEAM_RIGHT_SLOT = 2;
    private static final int FRONT_LEFT_SLOT = 3;
    private static final int FRONT_RIGHT_SLOT = 4;
    private static final int[] INTERIOR_SLOTS = {5, 6, 7, 8};
    // All 6 Companion-type slots, front pair first -- same order #companions/#seatLocalOffset use.
    private static final int[] COMPANION_SLOTS = {FRONT_LEFT_SLOT, FRONT_RIGHT_SLOT, 5, 6, 7, 8};
    private static final int[] MOUNT_SLOTS = {LEAD_SLOT, TEAM_LEFT_SLOT, TEAM_RIGHT_SLOT};

    // Same reasoning as WagonEntity#SPACING_BLOCKS -- a bigger body needs more breathing room from
    // the lead mount, scaled up proportionally with the body's own real length increase (see
    // CarriageModel's LENGTH_SCALE). Untested placeholder, like Wagon's own first pass was. Now the
    // TEAM row's own distance specifically (the base of the triangle, closer to the carriage) --
    // #leadSpawnPosition adds #LEAD_EXTRA_DISTANCE on top of this for the lead's own further-out tip.
    private static final double SPACING_BLOCKS = 4.5;
    // Real spec, same day: the lead sits further out than the team row -- untested placeholder, like
    // every other formation-geometry constant in this file.
    private static final double LEAD_EXTRA_DISTANCE = 2.0D;
    // How far left/right each team horse sits from the carriage's own forward center-line -- the
    // base of the triangle. Untested placeholder.
    private static final double TEAM_ABREAST_OFFSET = 1.0D;

    private Entity leadMount;
    private Entity teamLeftMount;
    private Entity teamRightMount;
    private final Entity[] companions = new Entity[COMPANION_SLOTS.length];
    private Leashable.LeashData leashData;

    private Entity leftSeatOccupant;
    private Entity rightSeatOccupant;

    // Real spec, 2026-10-26 (Carriage redesign Phase 2, "cabin, doors, interior seating"): genuinely
    // separate from both the front bench (leftSeatOccupant/rightSeatOccupant) and the existing
    // "interior" Companion slots (now correctly understood as roof/running-board companions, per the
    // user's own real reference photo -- those stay completely unchanged, see this class's own
    // original doc). A reasonable starting count, explicitly flagged tunable -- the user's own words,
    // "2-4 is a sensible placeholder."
    private static final int INTERIOR_SEAT_COUNT = 4;
    private final Entity[] interiorSeatOccupants = new Entity[INTERIOR_SEAT_COUNT];

    // Carriage redesign Phase 3, 2026-10-26: "Doors have their own independent health/repair
    // system, entirely separate from the Carriage's own HP pool" -- not an attribute, not tied to
    // Temp Max HP at all, just a simple broken/intact flag per door (0=left, 1=right), driven by the
    // SAME damage-crossing-a-threshold shape wheels already use (AbstractDamageableDrawnEntity#
    // computeBrokenCountForSlots, reused generically rather than inventing a second formula), ratcheted
    // up only by #onDamageTaken and lowered only by an explicit Repair consuming a Door item (see
    // #repairAdditionalParts) -- same "ratchet, not pure derivation" shape #brokenWheelCount itself
    // settled on, for the identical reason (Repair must be able to lower it with zero HP involved).
    private static final int DOOR_COUNT = 2;
    private static final EntityDataAccessor<Integer> DATA_BROKEN_DOOR_MASK =
            SynchedEntityData.defineId(CarriageEntity.class, EntityDataSerializers.INT);
    private int brokenDoorCount;

    private static final double STEER_SPEED_FALLBACK = 0.2D;
    private static final double TEAM_FOLLOW_SPACING = 3.0D;
    private static final double TEAM_CATCH_UP_RATE = 0.6D;

    public CarriageEntity(final EntityType<? extends CarriageEntity> entityType, final Level level) {
        super(entityType, level, CarriageMenu.CONTAINER_SIZE);
    }

    /** Registered in {@code CaryvanMod#registerAttributes} -- see {@code WagonEntity#createAttributes}'s own doc. */
    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return AbstractDamageableDrawnEntity.createAttributes(BASE_MAX_HEALTH);
    }

    @Override
    protected float getBaseMaxHealth() {
        return BASE_MAX_HEALTH;
    }

    /** Carriage's real hitbox is bigger than Wagon's (2.6 vs 2.0 wide) -- see {@code AbstractDamageableDrawnEntity}'s own doc for why this exists. */
    @Override
    protected float getWheelTrackHalfWidth() {
        return 0.95F;
    }

    @Override
    protected float getWheelAxleHalfSpacing() {
        return 0.95F;
    }

    @Override
    protected double getSpacing() {
        return SPACING_BLOCKS;
    }

    @Override
    public Item getCartItem() {
        return ModItems.carriageFor(this.getWoodType());
    }

    @Override
    public Item getBrokenCartItem() {
        return ModItems.BROKEN_CARRIAGE.get();
    }

    /** Mirrors {@code WagonEntity#onEnterBrokenState}'s own doc -- de-summons all 3 mount Summoning Stones (Carriage redesign Phase 4: now a real 3-horse triangle, not 2), leaves all 6 Companions defending. */
    @Override
    protected void onEnterBrokenState(final ServerLevel level) {
        if (!LyfeSummoningBridge.isLoaded()) {
            return;
        }
        for (final int slot : MOUNT_SLOTS) {
            final ItemStack stone = this.getItem(slot);
            if (!stone.isEmpty()) {
                LyfeSummoningBridge.despawnMountCleanly(level, stone);
            }
        }
    }

    /** Mirrors {@code WagonEntity#onPickUp}'s own doc -- rolls 50%-destroy per live Companion (all 6 seats) when picking up a Broken carriage. */
    @Override
    protected void onPickUp(final ServerLevel level, final boolean broken) {
        if (!LyfeSummoningBridge.isLoaded()) {
            return;
        }
        for (final int slot : MOUNT_SLOTS) {
            final ItemStack stone = this.getItem(slot);
            if (!stone.isEmpty()) {
                LyfeSummoningBridge.despawnMountCleanly(level, stone);
            }
        }
        for (int i = 0; i < COMPANION_SLOTS.length; i++) {
            final ItemStack stack = this.getItem(COMPANION_SLOTS[i]);
            if (stack.isEmpty()) {
                continue;
            }
            if (broken && level.getRandom().nextFloat() < 0.5F) {
                if (this.companions[i] != null) {
                    this.companions[i].discard();
                }
                this.setItem(COMPANION_SLOTS[i], ItemStack.EMPTY);
            } else {
                LyfeSummoningBridge.despawnCompanionCleanly(level, stack);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.validateSeatOccupants();
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // See WagonEntity#tick's own doc for the real re-summon bug this fixes, and the explicit
        // user spec it also satisfies ("they should not respawn if the wagon is broken either").
        if (!this.isBroken()) {
            tickMounts(serverLevel);
        }
        tickCompanionSlots(serverLevel);
        syncTeamLeash();
        syncWagonPullTarget();
        syncWagonLeashVisual();
        tickSteering();
        syncTeamFollow();
    }

    /** Same per-tick self-cleaning idiom as {@code WagonEntity}'s own version. Runs on both client and server, see that class's own doc for why. */
    private void validateSeatOccupants() {
        if (this.leftSeatOccupant != null && !this.hasPassenger(this.leftSeatOccupant)) {
            this.leftSeatOccupant = null;
        }
        if (this.rightSeatOccupant != null && !this.hasPassenger(this.rightSeatOccupant)) {
            this.rightSeatOccupant = null;
        }
        for (int i = 0; i < this.interiorSeatOccupants.length; i++) {
            if (this.interiorSeatOccupants[i] != null && !this.hasPassenger(this.interiorSeatOccupants[i])) {
                this.interiorSeatOccupants[i] = null;
            }
        }
    }

    /**
     * Generic, entity-agnostic interior-seat entry, triggered by right-clicking a door (see
     * {@link #interact}) -- not a raw "sit anywhere" interact; the door is the entry point. Takes a
     * plain {@link Entity}, not just {@link Player} -- real user spec: "Build the interior-seat
     * mechanism generically... it needs to be usable by any entity in the future" (NPC passengers
     * for future Myssions escort-shape quests), even though only players need to work end-to-end
     * this round. Returns false if every seat is already taken, or this cart is Broken (no boarding
     * a wrecked carriage through its own door).
     */
    public boolean enterInteriorSeat(final Entity entity) {
        if (this.isBroken()) {
            return false;
        }
        for (int i = 0; i < this.interiorSeatOccupants.length; i++) {
            if (this.interiorSeatOccupants[i] == null) {
                this.interiorSeatOccupants[i] = entity;
                entity.startRiding(this, true, true);
                return true;
            }
        }
        return false;
    }

    /**
     * Generic eject, mirroring {@link #enterInteriorSeat}'s own doc -- real user spec: "Shift-
     * clicking, or re-interacting with their own occupied seat, auto-ejects them back out through
     * that same door." Simplified this first pass to vanilla's own default dismount placement
     * ({@code stopRiding}) rather than precisely teleporting back to whichever specific door the
     * entity originally entered through -- doors are static/decorative this whole dispatch anyway
     * (no open/close animation), so there's no door-specific point to walk back out of yet; flagged
     * as a reasonable future refinement, not built here. Returns false if {@code entity} isn't
     * actually occupying one of these seats.
     */
    public boolean ejectFromInteriorSeat(final Entity entity) {
        for (int i = 0; i < this.interiorSeatOccupants.length; i++) {
            if (this.interiorSeatOccupants[i] == entity) {
                this.interiorSeatOccupants[i] = null;
                entity.stopRiding();
                return true;
            }
        }
        return false;
    }

    private boolean isInInteriorSeat(final Entity entity) {
        return interiorSeatIndexOf(entity) >= 0;
    }

    private int interiorSeatIndexOf(final Entity entity) {
        for (int i = 0; i < this.interiorSeatOccupants.length; i++) {
            if (this.interiorSeatOccupants[i] == entity) {
                return i;
            }
        }
        return -1;
    }

    // Entity-relative mirror of CarriageModel's own real door geometry -- that class is client-only
    // (ModelPart etc.), can't be referenced from this common-sourceset entity class (see the root
    // project's own "client-only type in a common class crashes the dedicated server" rule), so this
    // is independently duplicated instead, same established pattern #SEAT_X_OFFSET/#FRONT_SEAT_Z
    // already use for the front bench. Placeholder, needs live-test alignment against the real
    // visual door position, like every other geometry constant in this file.
    private static final double DOOR_Z_NEAR = 1.2D;
    private static final double DOOR_Z_FAR = 2.0D;

    /**
     * Real door hit-test -- mirrors {@link #isLeftSideClick}'s own real-world-position technique
     * (the PLAYER's actual position relative to the cart, yaw-rotated), not the click
     * {@code location} vector itself: that's only ever offset relative to the entity's own position,
     * never rotated into the cart's own local/yaw frame (confirmed against the real decompiled
     * {@code ServerGamePacketListenerImpl#handleInteract}, which sends the client's own raw,
     * world-axis-aligned relative offset) -- so a world-aligned {@code location.z} alone can't tell
     * whether a click actually landed within the door's own local Z span once the cart is facing
     * anything but a cardinal direction.
     */
    private boolean isNearDoor(final Player player) {
        final double dx = player.getX() - this.getX();
        final double dz = player.getZ() - this.getZ();
        final double yawRad = Math.toRadians(this.getYRot());
        final double forwardDot = dx * -Math.sin(yawRad) + dz * Math.cos(yawRad);
        return forwardDot >= DOOR_Z_NEAR && forwardDot <= DOOR_Z_FAR;
    }

    // Entity-relative mirror of CarriageModel's own interior bench rows (interiorBench1Z/
    // interiorBench2Z) -- same independent-duplication reasoning as #DOOR_Z_NEAR/FAR above.
    // Placeholder, needs live-test alignment.
    private static final double INTERIOR_SEAT_X_OFFSET = 0.34375D;
    private static final double INTERIOR_SEAT_ROW_1_Z = -0.7D;
    private static final double INTERIOR_SEAT_ROW_2_Z = -1.5D;
    private static final double[] INTERIOR_SEAT_Z_BY_INDEX = {INTERIOR_SEAT_ROW_1_Z, INTERIOR_SEAT_ROW_1_Z, INTERIOR_SEAT_ROW_2_Z, INTERIOR_SEAT_ROW_2_Z};

    private boolean stoneClaims(final int slot, final Entity entity) {
        final ItemStack stone = this.getItem(slot);
        if (stone.isEmpty() || !LyfeSummoningBridge.isLoaded()) {
            return false;
        }
        return LyfeSummoningBridge.getSummonedMountId(stone).map(id -> id.equals(entity.getUUID())).orElse(false);
    }

    /**
     * Real bug fix, 2026-10-26 (Carriage redesign Phase 4): "the current team is missing the
     * lead-slot horse entirely." Root cause, confirmed by re-reading the real previous
     * {@code tickLeadSlot}/{@code tickTeamSlot} methods side by side -- both called the exact same
     * {@code mountSpawnPosition()} (a single fixed point directly in front of the carriage) for
     * their OWN independent summon, meaning the lead and team mount both spawned at the IDENTICAL
     * position. {@code LyfeSummoningBridge.tickCompanion}'s own real implementation (confirmed via
     * {@code summoning.GuardCompanionSupport#tickMount}) only ever uses the given position at the
     * moment of a fresh summon -- once something is alive and tracked, later calls with the same
     * position are ignored entirely, so this wasn't a continuous fight over one spot; the lead
     * mount, having nothing else to reposition it afterward (unlike the team mount, which {@link
     * #syncTeamFollow} actively chases away to its own formation slot every tick), simply sat
     * exactly on top of the carriage's own pull point, visually indistinguishable from "not there at
     * all" to a live player. Fixed by giving the lead its own real, distinct position -- see {@link
     * #leadSpawnPosition()}/{@link #teamSpawnPosition(boolean)} for the actual triangle math this
     * naturally folds into, since both problems ("no distinct lead position" and "no real triangle
     * formation") had the same root fix.
     *
     * <p>Generalizes the old separate {@code tickLeadSlot}/{@code tickTeamSlot} methods into one,
     * called once per real mount slot from {@link #tickMounts}.
     */
    private Entity tickMountSlot(final ServerLevel level, final int slot, final Entity current, final Vec3 spawnPos) {
        Entity resolved = current;
        if (resolved != null && !stoneClaims(slot, resolved)) {
            resolved.discard();
            resolved = null;
        }
        final ItemStack stone = this.getItem(slot);
        if (stone.isEmpty() || !LyfeSummoningBridge.isLoaded()) {
            return resolved;
        }
        LyfeSummoningBridge.tickCompanion(level, stone, this.getUUID(), spawnPos.x, spawnPos.y, spawnPos.z, this.getYRot());
        final Optional<UUID> mountId = LyfeSummoningBridge.getSummonedMountId(stone);
        if (mountId.isEmpty()) {
            return resolved;
        }
        final Entity mount = level.getEntity(mountId.get());
        if (mount != null && mount.isAlive()) {
            mount.setInvulnerable(false);
            if (resolved != mount) {
                prepareCartMount(mount);
                resolved = mount;
            }
        }
        return resolved;
    }

    private void tickMounts(final ServerLevel level) {
        this.leadMount = tickMountSlot(level, LEAD_SLOT, this.leadMount, leadSpawnPosition());
        this.teamLeftMount = tickMountSlot(level, TEAM_LEFT_SLOT, this.teamLeftMount, teamSpawnPosition(true));
        this.teamRightMount = tickMountSlot(level, TEAM_RIGHT_SLOT, this.teamRightMount, teamSpawnPosition(false));
    }

    /** The triangle's own tip -- straight out in front, further than the team row (see {@link #LEAD_EXTRA_DISTANCE}'s own doc). */
    private Vec3 leadSpawnPosition() {
        final float yaw = (float) Math.toRadians(this.getYRot());
        final double fx = -Mth.sin(yaw);
        final double fz = Mth.cos(yaw);
        final double dist = this.getSpacing() + LEAD_EXTRA_DISTANCE;
        return new Vec3(this.getX() + fx * dist, this.getY(), this.getZ() + fz * dist);
    }

    /**
     * The triangle's own base -- 2 horses abreast, closer to the carriage than the lead. {@code
     * left}'s sign convention matches {@link #isLeftSideClick}'s own real, already-live-test-proven
     * "left/right" basis (positive along the {@code (cos(yaw), sin(yaw))} vector is left), not
     * guessed independently.
     */
    private Vec3 teamSpawnPosition(final boolean left) {
        final float yaw = (float) Math.toRadians(this.getYRot());
        final double fx = -Mth.sin(yaw);
        final double fz = Mth.cos(yaw);
        final double rx = Mth.cos(yaw);
        final double rz = Mth.sin(yaw);
        final double sideSign = left ? 1.0D : -1.0D;
        final double dist = this.getSpacing();
        return new Vec3(
                this.getX() + fx * dist + rx * TEAM_ABREAST_OFFSET * sideSign,
                this.getY(),
                this.getZ() + fz * dist + rz * TEAM_ABREAST_OFFSET * sideSign);
    }

    /**
     * Generalized version of {@code WagonEntity#tickCompanionSlots} for 6 seats instead of 2 -- same
     * real-passenger fix (see that class's own doc for the fly-away bug this already fixed once),
     * just looped over {@link #COMPANION_SLOTS} rather than two named fields.
     */
    private void tickCompanionSlots(final ServerLevel level) {
        for (int i = 0; i < COMPANION_SLOTS.length; i++) {
            this.companions[i] = tickCompanionSlot(level, COMPANION_SLOTS[i], this.companions[i], i);
        }
    }

    private Entity tickCompanionSlot(final ServerLevel level, final int slot, final Entity current, final int seatIndex) {
        Entity resolved = current;
        if (resolved != null && !companionClaims(slot, resolved)) {
            resolved.discard();
            resolved = null;
        }
        final ItemStack stack = this.getItem(slot);
        if (stack.isEmpty() || !LyfeSummoningBridge.isLoaded()) {
            return resolved;
        }
        final Vec3 spawnPos = this.companionSpawnPosition(seatIndex);
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

    private static void prepareCartCompanion(final Entity entity) {
        if (entity instanceof Mob mob) {
            mob.removeAllGoals(goal -> true);
        }
        entity.setInvulnerable(false);
    }

    private boolean companionClaims(final int slot, final Entity entity) {
        final ItemStack stack = this.getItem(slot);
        if (stack.isEmpty() || !LyfeSummoningBridge.isLoaded()) {
            return false;
        }
        return LyfeSummoningBridge.getSummonedAllayId(stack).map(id -> id.equals(entity.getUUID())).orElse(false);
    }

    /** Pre-mount spawn spot beside the body, roughly where each seat sits -- same "simple fixed offset, not real formation AI" scope as {@code WagonEntity#companionSpawnPosition}. */
    private Vec3 companionSpawnPosition(final int seatIndex) {
        final boolean left = seatIndex % 2 == 0;
        final double xOffset = left ? 1.0D : -1.0D;
        final Vec3 local = new Vec3(xOffset, 1.0D, 0.0D);
        final float yawRad = (float) Math.toRadians(this.getYRot());
        return this.position().add(local.yRot(-yawRad));
    }

    /** Same idiom as {@code WagonEntity#prepareCartMount} -- see that method's own doc for why {@code removeAllGoals}, not {@code setNoAi}. */
    private static void prepareCartMount(final Entity entity) {
        if (entity instanceof Mob mob) {
            mob.removeAllGoals(goal -> true);
        }
        entity.setInvulnerable(false);
        entity.setData(ModAttachments.CART_TEAM_MOUNT, Boolean.TRUE);
    }

    /** Generalized for 2 team horses (Carriage redesign Phase 4) -- both leash directly to the SAME lead, matching the real "lead to team" daisy chain, just 2 parallel leashes instead of 1. */
    private void syncTeamLeash() {
        syncOneTeamLeash(this.teamLeftMount);
        syncOneTeamLeash(this.teamRightMount);
    }

    private void syncOneTeamLeash(final Entity team) {
        if (team == null || !team.isAlive() || this.leadMount == null || !this.leadMount.isAlive()) {
            return;
        }
        if (team instanceof Leashable leashable && leashable.getLeashHolder() != this.leadMount) {
            leashable.setLeashedTo(this.leadMount, true);
        }
    }

    /**
     * Generalized for 2 team horses, Carriage redesign Phase 4 -- real user spec, "2 horses abreast
     * behind it." Unlike the old single-team-horse version (which just maintained a fixed DISTANCE
     * directly behind the lead, direction-agnostic), each team horse now chases a real target POINT
     * (the lead's own live position, offset back and to its own side by {@link #TEAM_FOLLOW_SPACING}/
     * {@link #TEAM_ABREAST_OFFSET}) -- the only way to actually hold a left/right-distinguished
     * abreast slot instead of both horses converging on the same trailing spot.
     */
    private void syncTeamFollow() {
        syncOneTeamFollow(this.teamLeftMount, true);
        syncOneTeamFollow(this.teamRightMount, false);
    }

    // Real live-test bug, 2026-10-26: both team horses visibly "turning in place of their own
    // accord" while stationary in formation (and, in an earlier report, the active puller
    // specifically drifting/"wandering" -- see #syncOneTeamFollow's own doc for why that's believed
    // to be the same root cause). The old guards only skipped re-aiming/re-stepping once within
    // sqrt(1e-6)=0.001 blocks of the target -- far tighter than the real per-tick positional noise
    // (the lead's own tiny physics settling, leash elasticity) a horse actually converges to in
    // practice, so they almost never actually triggered. A real "close enough, hold still" distance
    // instead.
    private static final double TEAM_SETTLE_DISTANCE = 0.15D;

    /**
     * Real root-cause fix, 2026-10-26: once a team horse is basically at its formation slot but not
     * within the old guard's sub-millimeter tolerance, {@code Mth.atan2} on the resulting near-zero
     * {@code direction} vector is numerically unstable -- a sub-millimeter shift in either horse's
     * own position (simple physics settling, no AI involved) flips the computed heading by a large
     * angle, every tick, for both {@code setYRot} and {@code setYHeadRot} -- reading as continuous
     * spinning. The same noisy {@code direction} also drove {@code team.move(...)}, so it also
     * produced small, direction-flipping real displacement each tick, which accumulated over many
     * ticks reads as aimless wandering -- worse for whichever horse is also the active puller, since
     * its position additionally feeds {@link #syncWagonPullTarget()}/{@code pulledTick()}'s own
     * chase physics, compounding the jitter into real cumulative drift the non-puller horse's
     * identical jitter doesn't get fed back into. Real fix: stop re-aiming AND re-stepping entirely
     * once within a real {@link #TEAM_SETTLE_DISTANCE}, instead of chasing an exact, noise-sensitive
     * point every tick regardless of how close the horse already is.
     */
    private void syncOneTeamFollow(final Entity team, final boolean left) {
        if (team == null || !team.isAlive() || this.leadMount == null || !this.leadMount.isAlive()) {
            return;
        }
        final float leadYawRad = (float) Math.toRadians(this.leadMount.getYRot());
        final double bx = -Mth.sin(leadYawRad);
        final double bz = Mth.cos(leadYawRad);
        final double rx = Mth.cos(leadYawRad);
        final double rz = Mth.sin(leadYawRad);
        final double sideSign = left ? 1.0D : -1.0D;
        final Vec3 targetPos = this.leadMount.position()
                .subtract(new Vec3(bx, 0.0D, bz).scale(TEAM_FOLLOW_SPACING))
                .add(new Vec3(rx, 0.0D, rz).scale(TEAM_ABREAST_OFFSET * sideSign));
        final Vec3 toTarget = targetPos.subtract(team.position());
        final Vec3 horizontal = new Vec3(toTarget.x, 0.0D, toTarget.z);
        final double dist = horizontal.length();
        if (dist < TEAM_SETTLE_DISTANCE) {
            return;
        }
        final Vec3 direction = horizontal.normalize();
        final float heading = (float) Math.toDegrees(-Mth.atan2(direction.x, direction.z));
        team.setYRot(heading);
        team.setYHeadRot(heading);
        final double step = Math.min(dist, TEAM_CATCH_UP_RATE);
        team.move(MoverType.SELF, direction.scale(step));
    }

    /**
     * Generalized for 2 team horses -- same "daisy chain, rearmost alive mount actually pulls"
     * philosophy {@code WagonEntity}'s own doc explains, now with a defined preference order between
     * the two parallel team horses (left, then right) when both are alive, since vanilla's own
     * single-entity leash/pull-chase mechanism can't literally split across two pullers at once.
     */
    private void syncWagonPullTarget() {
        final Entity rearmost;
        if (this.teamLeftMount != null && this.teamLeftMount.isAlive()) {
            rearmost = this.teamLeftMount;
        } else if (this.teamRightMount != null && this.teamRightMount.isAlive()) {
            rearmost = this.teamRightMount;
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
     * Real structural fix, 2026-10-26 -- user report from a close-up screenshot: "the placement of
     * the carriage seems to be centered behind the left horse, not centered between the team
     * horses," with the second horse's own rope visually dangling instead of connecting anywhere
     * real. Root cause: {@link #syncWagonPullTarget()}'s own left-then-right preference has to
     * register exactly one real {@link Entity} as {@link #getPulling()} -- vanilla's {@code
     * Leashable} interface genuinely only supports one holder, that part isn't changeable -- but the
     * base class's own towing PHYSICS ({@code AbstractDamageableDrawnEntity#pulledTick()}) used to
     * chase that single registered entity's literal position too, which is what actually produced
     * the off-center hitch. Overriding {@link #pullAnchor()} decouples the two: {@link #getPulling()}
     * still names one real horse (for the vanilla leash-holder bookkeeping {@code
     * #syncWagonLeashVisual()} needs), but the physics this cart's body actually chases is now the
     * real midpoint between both live team mounts -- reading as genuinely, symmetrically hitched
     * between them. The rope-rendering fix from earlier the same day needed no changes at all: each
     * rope's own start point was already computed as an offset from this entity's real position (see
     * {@code CarriageRenderer#buildLeashRopes}), so once that real position is correctly centered,
     * both ropes read as properly connected on their own.
     */
    @Override
    protected PullAnchor pullAnchor() {
        final boolean leftAlive = this.teamLeftMount != null && this.teamLeftMount.isAlive();
        final boolean rightAlive = this.teamRightMount != null && this.teamRightMount.isAlive();
        if (leftAlive && rightAlive) {
            final Vec3 midpoint = this.teamLeftMount.position().add(this.teamRightMount.position()).scale(0.5D);
            final float yRot = this.leadMount != null && this.leadMount.isAlive() ? this.leadMount.getYRot() : this.teamLeftMount.getYRot();
            final boolean onGround = this.teamLeftMount.onGround() && this.teamRightMount.onGround();
            final double bbWidth = (this.teamLeftMount.getBbWidth() + this.teamRightMount.getBbWidth()) / 2.0D + TEAM_ABREAST_OFFSET * 2.0D;
            return new PullAnchor(midpoint, yRot, onGround, bbWidth);
        }
        return super.pullAnchor();
    }

    /** Same shape as {@code WagonEntity#syncWagonLeashVisual}. */
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

    /** Same shape as {@code WagonEntity#getQuadLeashOffsets}. */
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

    @Override
    public boolean checkElasticInteractions(final Entity entity, final Leashable.LeashData leashData) {
        return false;
    }

    @Override
    public double leashSnapDistance() {
        return 1000.0D;
    }

    @Override
    public void dropLeash() {
        this.removeLeash();
    }

    @Override
    protected boolean canBePulledBy(final Entity entity) {
        return stoneClaims(LEAD_SLOT, entity) || stoneClaims(TEAM_LEFT_SLOT, entity) || stoneClaims(TEAM_RIGHT_SLOT, entity);
    }

    /**
     * Real client-side need, 2026-10-26: {@code CarriageRenderer}'s own custom leash-rope rendering
     * needs both live team mounts directly (not just whichever single one vanilla's own one-holder
     * {@link #getLeashHolder()} happens to be) to draw the real triangle rope topology (lead-to-each-
     * team, team-to-team, and each team horse individually down to the carriage -- see the user's own
     * reference diagram). {@link #getCompanionEntityForSlot(int)} already resolves these but is
     * {@code protected} (same-package/subclass only); the renderer lives in a different package, so
     * these two small public getters exist purely for that cross-package read.
     */
    public Entity getTeamLeftMount() {
        return this.teamLeftMount;
    }

    public Entity getTeamRightMount() {
        return this.teamRightMount;
    }

    private static final double BENCH_CLICK_HEIGHT_FRACTION = 0.6;

    /**
     * Same shape as {@code WagonEntity#interact} -- only the front bench is directly player-clickable;
     * the 4 interior seats are Companion-slot-only (see this class's own doc). Same real correction as
     * {@code WagonEntity}'s own: a front Companion already occupying its side blocks a player from
     * also claiming it, not the other way around -- see that class's own doc for the full writeup.
     *
     * <p>Unlike the Wagon (exactly 2 seats total, so a plain passenger-count cap doubles as "a seat is
     * free"), the Carriage also carries up to 4 *interior* Companions as real passengers that have
     * nothing to do with the front bench at all -- a plain {@code getPassengers().size() < 2} gate
     * would incorrectly refuse a player even with both front seats empty, the instant 2+ interior
     * seats were filled. The gate here checks front-seat availability directly instead.
     *
     * <p>{@code location.y} is used directly, not {@code location.y - this.getY()} -- see
     * {@code WagonEntity#interact}'s own doc for the real root cause (the client already sends this
     * relative to the entity's own position; subtracting it again produced a ~70-block-off garbage
     * value that could never clear the threshold on Production's real terrain).
     */
    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand, final Vec3 location) {
        // Real spec, 2026-10-26 (Carriage redesign Phase 2): re-interacting with the cart at all
        // while already seated in an interior seat -- shift-click or plain click alike -- always
        // ejects back out, checked first and unconditionally. Deliberately takes priority over
        // everything below, including the owner's own Pick Up gesture just after this -- a seated
        // passenger (owner or not) should always be able to get back out with a plain re-interact,
        // not get redirected into a Pick Up confirm dialog instead.
        if (this.isInInteriorSeat(player)) {
            this.ejectFromInteriorSeat(player);
            return InteractionResult.SUCCESS;
        }
        // Pick Up / Repair, 2026-10-26 -- same owner-only sneak+right-click gesture as WagonEntity's
        // own #interact, see its doc for the full reasoning. Checked first; a non-owner's (or an
        // owner holding something else's) sneak+right-click is unaffected.
        if (player.isShiftKeyDown() && this.isOwner(player)) {
            final ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty()) {
                this.requestPickUpConfirm(player);
                return InteractionResult.SUCCESS;
            }
            if (held.getItem() == ModItems.wheelsFor(this.getWoodType())) {
                if (!this.level().isClientSide()) {
                    this.handleRepairAttempt(player, held);
                }
                return InteractionResult.SUCCESS;
            }
        }
        // Real spec, same day: right-clicking a door (not shift-clicking, not already seated) auto-
        // seats the player in an available interior seat -- the door is the entry point, not a raw
        // "sit anywhere" interact. Checked before the front-bench click zone below, since the door
        // sits close enough to the bench that both real-world click spots need telling apart --
        // #isNearDoor's own Z-range gate is what actually distinguishes them.
        if (!this.isBroken() && !player.isShiftKeyDown() && !this.hasPassenger(player)
                && this.isNearDoor(player) && location.y > this.getBbHeight() * BENCH_CLICK_HEIGHT_FRACTION
                && this.enterInteriorSeat(player)) {
            return InteractionResult.SUCCESS;
        }
        final boolean leftFree = this.leftSeatOccupant == null && this.companions[0] == null;
        final boolean rightFree = this.rightSeatOccupant == null && this.companions[1] == null;
        // See WagonEntity#interact's own doc -- a Broken carriage must never be drivable.
        if (!this.isBroken() && !player.isShiftKeyDown() && (leftFree || rightFree)
                && location.y > this.getBbHeight() * BENCH_CLICK_HEIGHT_FRACTION) {
            final boolean alreadyRiding = this.hasPassenger(player);
            final boolean wantsLeft = this.isLeftSideClick(player);
            if (alreadyRiding) {
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
            player.startRiding(this, true, true);
            return InteractionResult.SUCCESS;
        }
        return super.interact(player, hand, location);
    }

    /** Same shape as {@code WagonEntity#isLeftSideClick}. */
    private boolean isLeftSideClick(final Player player) {
        final double dx = player.getX() - this.getX();
        final double dz = player.getZ() - this.getZ();
        final double yawRad = Math.toRadians(this.getYRot());
        final double leftDot = dx * Math.cos(yawRad) + dz * Math.sin(yawRad);
        return leftDot > 0.0D;
    }

    // Same real "left = +X at yaw 0" convention WagonEntity already established. Front bench at the
    // same relative Z the Wagon's own bench uses; the two interior rows sit further back into the
    // body, spaced out to fit 4 people "comfortably" (real user spec) -- untested placeholder
    // spacing, like every one of WagonEntity's own seat constants started out as before live-test
    // correction.
    private static final double SEAT_X_OFFSET = 0.34375D;
    private static final double FRONT_SEAT_Z = 1.75D;
    private static final double INTERIOR_SEAT_1_Z = 0.0D;
    private static final double INTERIOR_SEAT_2_Z = -1.75D;
    private static final double[] SEAT_Z_BY_INDEX = {FRONT_SEAT_Z, FRONT_SEAT_Z, INTERIOR_SEAT_1_Z, INTERIOR_SEAT_1_Z, INTERIOR_SEAT_2_Z, INTERIOR_SEAT_2_Z};

    private int companionSeatIndexOf(final Entity entity) {
        for (int i = 0; i < this.companions.length; i++) {
            if (this.companions[i] == entity) {
                return i;
            }
        }
        return -1;
    }

    /** Generalized version of {@code WagonEntity#getPassengerAttachmentPoint} for 6 seats -- the front pair still also checks {@link #leftSeatOccupant}/{@link #rightSeatOccupant} for a real player. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(final Entity entity, final EntityDimensions dimensions, final float scale) {
        final int interiorIndex = interiorSeatIndexOf(entity);
        if (interiorIndex >= 0) {
            final boolean leftSide = interiorIndex % 2 == 0;
            final double xOffset = leftSide ? INTERIOR_SEAT_X_OFFSET : -INTERIOR_SEAT_X_OFFSET;
            final double z = INTERIOR_SEAT_Z_BY_INDEX[interiorIndex];
            final Vec3 local = new Vec3(xOffset, this.getBbHeight() * 0.85D, z).scale(scale);
            final float yawRad = (float) Math.toRadians(this.getYRot());
            return local.yRot(-yawRad);
        }
        final int seatIndex;
        if (entity == this.leftSeatOccupant) {
            seatIndex = 0;
        } else if (entity == this.rightSeatOccupant) {
            seatIndex = 1;
        } else {
            final int found = companionSeatIndexOf(entity);
            seatIndex = found >= 0 ? found : 0;
        }
        final boolean leftSide = seatIndex % 2 == 0;
        final double xOffset = leftSide ? SEAT_X_OFFSET : -SEAT_X_OFFSET;
        final double z = SEAT_Z_BY_INDEX[seatIndex];
        final Vec3 local = new Vec3(xOffset, this.getBbHeight() * 0.85D, z).scale(scale);
        final float yawRad = (float) Math.toRadians(this.getYRot());
        return local.yRot(-yawRad);
    }

    /**
     * Generalized version of {@code WagonEntity#canAddPassenger} -- real user correction, 2026-10-26
     * (see {@code WagonEntity#canAddPassenger}'s own doc for the full writeup): only the front pair
     * ({@link #companions}[0]/[1]) ever competes with a player for a seat, mirroring {@link #interact}'s
     * own {@code leftFree}/{@code rightFree}; the 4 interior companions never compete with anything,
     * since no click zone ever lets a player claim one of those seats in the first place.
     */
    @Override
    protected boolean canAddPassenger(final Entity passenger) {
        if (isInInteriorSeat(passenger)) {
            return true;
        }
        if (passenger == this.companions[0]) {
            return this.leftSeatOccupant == null;
        }
        if (passenger == this.companions[1]) {
            return this.rightSeatOccupant == null;
        }
        for (int i = 2; i < this.companions.length; i++) {
            if (passenger == this.companions[i]) {
                return true;
            }
        }
        final boolean leftFree = this.leftSeatOccupant == null && this.companions[0] == null;
        final boolean rightFree = this.rightSeatOccupant == null && this.companions[1] == null;
        return leftFree || rightFree;
    }

    /** Same shape as {@code WagonEntity#tickSteering}. */
    private void tickSteering() {
        if (this.leadMount == null || !this.leadMount.isAlive()) {
            return;
        }
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
        double speed = STEER_SPEED_FALLBACK;
        if (this.leadMount instanceof LivingEntity livingLead) {
            speed = livingLead.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.0D;
        }
        this.leadMount.move(MoverType.SELF, direction.scale(speed));
    }

    @Override
    public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
        if (!this.isOwner(player) && !this.canNonOwnerAccessMenu()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "This carriage's Companions are still defending it -- they must be defeated first."));
            return null;
        }
        this.unpackChestVehicleLootTable(player);
        return new CarriageMenu(ModMenus.CARRIAGE.get(), containerId, inventory, this);
    }

    @Override
    protected boolean hasLiveDefendingCompanion() {
        for (final int slot : COMPANION_SLOTS) {
            if (isLiveDefending(this.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLiveDefending(final ItemStack stack) {
        return !stack.isEmpty() && LyfeSummoningBridge.isLoaded() && !LyfeSummoningBridge.isOnCooldown(stack, System.currentTimeMillis());
    }

    /** Mirrors {@code WagonEntity}'s own doc exactly -- {@link #LEAD_SLOT}/{@link #TEAM_LEFT_SLOT}/{@link #TEAM_RIGHT_SLOT} (real Summoning Stones) were never resolved here before, leaving them with zero slot-click destroy-roll protection regardless of click type. */
    @Override
    protected Entity getCompanionEntityForSlot(final int slotIndex) {
        if (slotIndex == LEAD_SLOT) {
            return this.leadMount;
        }
        if (slotIndex == TEAM_LEFT_SLOT) {
            return this.teamLeftMount;
        }
        if (slotIndex == TEAM_RIGHT_SLOT) {
            return this.teamRightMount;
        }
        for (int i = 0; i < COMPANION_SLOTS.length; i++) {
            if (COMPANION_SLOTS[i] == slotIndex) {
                return this.companions[i];
            }
        }
        return null;
    }

    // --- Door health/repair, Carriage redesign Phase 3 -------------------------------------------

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BROKEN_DOOR_MASK, 0);
    }

    /** Ratchets {@link #brokenDoorCount} UP (never down) to whatever current health alone implies for {@link #DOOR_COUNT} equally-weighted slots -- mirrors {@code AbstractDamageableDrawnEntity}'s own wheel ratchet exactly, reusing its generic formula. */
    @Override
    protected void onDamageTaken() {
        final int healthImplied = this.computeBrokenCountForSlots(DOOR_COUNT);
        if (healthImplied > this.brokenDoorCount) {
            this.brokenDoorCount = healthImplied;
            applyBrokenDoorCount();
        }
    }

    private void applyBrokenDoorCount() {
        int mask = 0;
        for (int i = 0; i < DOOR_COUNT; i++) {
            if (i < this.brokenDoorCount) {
                mask |= 1 << i;
            }
        }
        this.entityData.set(DATA_BROKEN_DOOR_MASK, mask);
    }

    /** 0 = left door, 1 = right door -- read by {@code CarriageRenderer#extractRenderState} to pick the hang-off-hinges geometry variant. */
    public boolean isDoorBroken(final int doorIndex) {
        return (this.entityData.get(DATA_BROKEN_DOOR_MASK) & (1 << doorIndex)) != 0;
    }

    public int getBrokenDoorCount() {
        return this.brokenDoorCount;
    }

    /**
     * Real spec, 2026-10-26: "The existing Repair button must also consume Door items from cargo
     * when present, alongside its existing Wheel-consumption logic, fixing damaged doors the same
     * way it fixes wheels (no instant heal involved here either -- doors don't affect HP at all,
     * this is purely a separate visual/functional repair state)." Unlike wheels, there's no Temp Max
     * HP ceiling to raise and no instant-heal-adjacent math at all -- doors never touch health in
     * any way, so this is just a direct decrement, consuming exactly as many Door items as doors
     * actually get fixed.
     */
    @Override
    protected boolean repairAdditionalParts(final Player player) {
        if (this.brokenDoorCount <= 0) {
            return false;
        }
        final Item doorItem = ModItems.doorFor(this.getWoodType());
        int available = 0;
        for (int i = 0; i < this.getContainerSize(); i++) {
            final ItemStack stack = this.getItem(i);
            if (stack.getItem() == doorItem) {
                available += stack.getCount();
            }
        }
        if (available <= 0) {
            return false;
        }
        final int toFix = Math.min(available, this.brokenDoorCount);
        this.brokenDoorCount -= toFix;
        applyBrokenDoorCount();
        int remaining = toFix;
        for (int i = 0; i < this.getContainerSize() && remaining > 0; i++) {
            final ItemStack stack = this.getItem(i);
            if (stack.getItem() == doorItem) {
                final int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                "Repaired " + toFix + " door" + (toFix == 1 ? "" : "s") + "."));
        return true;
    }

    /** {@link #brokenDoorCount} is authoritative state (not purely derived from health, same reasoning as {@code AbstractDamageableDrawnEntity#brokenWheelCount}), so it needs its own real NBT round-trip. */
    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        this.brokenDoorCount = input.getIntOr("BrokenDoorCount", 0);
        applyBrokenDoorCount();
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BrokenDoorCount", this.brokenDoorCount);
    }
}
