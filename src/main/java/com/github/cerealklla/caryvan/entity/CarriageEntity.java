package com.github.cerealklla.caryvan.entity;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.bridge.LyfeSummoningBridge;
import com.github.cerealklla.caryvan.menu.CarriageMenu;
import com.github.cerealklla.caryvan.registration.ModAttachments;
import com.github.cerealklla.caryvan.registration.ModItems;
import com.github.cerealklla.caryvan.registration.ModMenus;

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
 * Carriage (design-document.md §8's diagram, built 2026-10-26) -- a bigger Wagon: same daisy-chained
 * lead/team Summon Stone mount pair ({@link WagonEntity}'s own doc covers that mechanic in full, not
 * repeated here), but 6 real Companion seats instead of 2 -- real user spec: "Carriage should allow
 * Driver and Passenger up front, and have 4 internal seats for non-combatants." The front two
 * ({@link #FRONT_LEFT_SLOT}/{@link #FRONT_RIGHT_SLOT}) are the exact same dual-mode seat
 * {@code WagonEntity} already has (a player can click to sit directly, driver=left steers; a bound
 * Allay in a Bottle placed in the matching slot rides there too) -- the 4 interior ones
 * ({@link #INTERIOR_SLOTS}) are Companion-slot-only, not directly player-clickable, matching the "for
 * non-combatants" framing (a companion, not a second driver). {@link #companions}' index 0/1 are the
 * front pair, 2-5 the interior ones, each with its own fixed left/right + front-to-back seat
 * position; see {@link #seatLocalOffset} for the actual per-seat coordinates.
 *
 * <p>Body is "a little longer and wider than the Wagon to fit 4 inside comfortably" (explicit user
 * spec) -- see {@code CarriageModel}'s own doc for the real scale factors.
 */
public final class CarriageEntity extends AbstractDrawnInventoryEntity implements Leashable {

    private static final int LEAD_SLOT = 0;
    private static final int TEAM_SLOT = 1;
    private static final int FRONT_LEFT_SLOT = 2;
    private static final int FRONT_RIGHT_SLOT = 3;
    private static final int[] INTERIOR_SLOTS = {4, 5, 6, 7};
    // All 6 Companion-type slots, front pair first -- same order #companions/#seatLocalOffset use.
    private static final int[] COMPANION_SLOTS = {FRONT_LEFT_SLOT, FRONT_RIGHT_SLOT, 4, 5, 6, 7};

    // Same reasoning as WagonEntity#SPACING_BLOCKS -- a bigger body needs more breathing room from
    // the lead mount, scaled up proportionally with the body's own real length increase (see
    // CarriageModel's LENGTH_SCALE). Untested placeholder, like Wagon's own first pass was.
    private static final double SPACING_BLOCKS = 4.5;

    private Entity leadMount;
    private Entity teamMount;
    private final Entity[] companions = new Entity[COMPANION_SLOTS.length];
    private Leashable.LeashData leashData;

    private Entity leftSeatOccupant;
    private Entity rightSeatOccupant;

    private static final double STEER_SPEED_FALLBACK = 0.2D;
    private static final double TEAM_FOLLOW_SPACING = 3.0D;
    private static final double TEAM_CATCH_UP_RATE = 0.6D;

    public CarriageEntity(final EntityType<? extends CarriageEntity> entityType, final Level level) {
        super(entityType, level, CarriageMenu.CONTAINER_SIZE);
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
    public void tick() {
        super.tick();
        this.validateSeatOccupants();
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        tickLeadSlot(serverLevel);
        tickTeamSlot(serverLevel);
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
    }

    private boolean stoneClaims(final int slot, final Entity entity) {
        final ItemStack stone = this.getItem(slot);
        if (stone.isEmpty() || !LyfeSummoningBridge.isLoaded()) {
            return false;
        }
        return LyfeSummoningBridge.getSummonedMountId(stone).map(id -> id.equals(entity.getUUID())).orElse(false);
    }

    /** Same shape as {@code WagonEntity#tickLeadSlot}. */
    private void tickLeadSlot(final ServerLevel level) {
        if (this.leadMount != null && !stoneClaims(LEAD_SLOT, this.leadMount)) {
            this.leadMount.discard();
            this.leadMount = null;
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
            mount.setInvulnerable(false);
            if (this.leadMount != mount) {
                prepareCartMount(mount);
                this.leadMount = mount;
            }
        }
    }

    /** Same shape as {@link #tickLeadSlot}, tracking {@link #teamMount} instead. */
    private void tickTeamSlot(final ServerLevel level) {
        if (this.teamMount != null && !stoneClaims(TEAM_SLOT, this.teamMount)) {
            this.teamMount.discard();
            this.teamMount = null;
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
            mount.setInvulnerable(false);
            if (this.teamMount != mount) {
                prepareCartMount(mount);
                this.teamMount = mount;
            }
        }
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

    /** Same shape as {@code WagonEntity#syncTeamLeash}. */
    private void syncTeamLeash() {
        if (this.teamMount == null || !this.teamMount.isAlive() || this.leadMount == null || !this.leadMount.isAlive()) {
            return;
        }
        if (this.teamMount instanceof Leashable leashable && leashable.getLeashHolder() != this.leadMount) {
            leashable.setLeashedTo(this.leadMount, true);
        }
    }

    /** Same shape as {@code WagonEntity#syncTeamFollow}. */
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

    /** Same shape as {@code WagonEntity#syncWagonPullTarget}. */
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
        return stoneClaims(LEAD_SLOT, entity) || stoneClaims(TEAM_SLOT, entity);
    }

    private static final double BENCH_CLICK_HEIGHT_FRACTION = 0.6;

    /** Same shape as {@code WagonEntity#interact} -- only the front bench is directly player-clickable; the 4 interior seats are Companion-slot-only (see this class's own doc). */
    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand, final Vec3 location) {
        if (!player.isShiftKeyDown() && this.realPlayerPassengerCount() < 2
                && location.y - this.getY() > this.getBbHeight() * BENCH_CLICK_HEIGHT_FRACTION) {
            final boolean alreadyRiding = this.hasPassenger(player);
            final boolean wantsLeft = this.isLeftSideClick(player);
            final boolean leftFree = this.leftSeatOccupant == null;
            final boolean rightFree = this.rightSeatOccupant == null;
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

    /** Generalized version of {@code WagonEntity#canAddPassenger} -- any of the 6 tracked companions always get on; a real player is still capped at 2 (the front bench only). */
    @Override
    protected boolean canAddPassenger(final Entity passenger) {
        for (final Entity companion : this.companions) {
            if (passenger == companion) {
                return true;
            }
        }
        return this.realPlayerPassengerCount() < 2;
    }

    /** Same bug/fix as {@code WagonEntity#realPlayerPassengerCount} -- see that method's own doc. */
    private long realPlayerPassengerCount() {
        return this.getPassengers().stream().filter(p -> companionSeatIndexOf(p) < 0).count();
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
        this.unpackChestVehicleLootTable(player);
        return new CarriageMenu(ModMenus.CARRIAGE.get(), containerId, inventory, this);
    }
}
