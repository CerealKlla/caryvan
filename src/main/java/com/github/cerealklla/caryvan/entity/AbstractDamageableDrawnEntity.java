package com.github.cerealklla.caryvan.entity;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.github.cerealklla.caryvan.util.CartWheel;
import com.github.cerealklla.caryvan.util.CaryvanWorld;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Sibling of {@link AbstractDrawnEntity} for Wagon/Carriage only (Hand Cart/Animal Cart keep the
 * original {@code VehicleEntity}-based hierarchy, completely untouched) -- the Wagon/Carriage
 * health/damage/repair/ownership/targeting system, 2026-10-26. See the approved plan
 * ({@code C:\Users\benja\.claude\plans\idempotent-floating-flamingo.md}) for the full design and the
 * real research behind it.
 *
 * <p><b>Why {@code LivingEntity}, not {@code VehicleEntity}</b> -- the user's own explicit,
 * "non-negotiable" requirement is that hostile mobs can genuinely target and attack a Wagon/
 * Carriage. Vanilla's hostile-mob targeting machinery ({@code Mob#setTarget}, every
 * {@code *TargetGoal}) is generically bound to {@code LivingEntity}, confirmed against the real
 * decompiled source -- a {@code VehicleEntity} (a sibling of {@code LivingEntity}, not a parent/
 * child) is structurally ineligible, full stop, no workaround possible without actually becoming
 * one. Direct, already-proven precedent in this exact codebase for "a mostly inert
 * {@code LivingEntity} with no AI/Mob baggage" is vanilla's own {@code ArmorStand} (and this
 * session's own {@code settlemynts.guardhouse.GuardKitStandEntity}, a literal {@code ArmorStand}
 * subclass) -- the overrides below ({@link #isEffectiveAi()}, {@link #travel}, {@link #isAlive()},
 * {@link #tickDeath()}, {@link #getMainArm()}, {@link #knockback}) mirror exactly what
 * {@code ArmorStand} itself overrides to stay inert, confirmed by reading its own real decompiled
 * source before writing any of this.
 *
 * <p><b>Health IS real vanilla health</b> -- {@link #getHealth()}/{@link #setHealth(float)}/
 * {@link #getMaxHealth()} (via the {@code Attributes.MAX_HEALTH} attribute every
 * {@code LivingEntity} subclass needs an {@link AttributeSupplier} for, confirmed by the exact
 * {@code NullPointerException} {@code GuardKitStandEntity} hit once for the identical reason) are
 * used directly as this entity's own HP store -- deliberately, not a parallel custom field. This is
 * what makes Nuclyus's existing floating stat-bar system "just work" with zero changes there at
 * all: {@code nuclyus.statbar.StatBarWorldOverlay} already filters on
 * {@code instanceof LivingEntity && instanceof StatBarOwner}, and
 * {@code nuclyus.statbar.HealthStatBarProvider} already reads {@code getHealth()}/
 * {@code getMaxHealth()} generically -- confirmed by reading both real source files before building
 * any of this, not assumed from the plan's own (more cautious) "may need to widen Nuclyus" framing.
 *
 * <p><b>Wheel count reflects the REAL current placeholder geometry (2), not an assumed 4</b> --
 * {@link AbstractDrawnEntity}'s own constructor hardcodes exactly two {@link CartWheel}s for every
 * cart type (Wagon/Carriage's real 4-wheel geometry is explicitly flagged future work in
 * {@code WagonEntity}'s own class doc, not built yet) -- this class does the same, and every
 * wheel-count-driven formula below (Temp Max HP, damage-to-wheel-break mapping) uses
 * {@code this.wheels.size()} generically rather than a hardcoded 4, so it stays correct once real
 * 4-wheel geometry eventually lands.
 */
public abstract class AbstractDamageableDrawnEntity extends LivingEntity implements com.github.cerealklla.caryvan.util.PulledCart {

    private static final double MAX_SLACK_BLOCKS = 3.0;
    private static final double CATCH_UP_RATE = 0.2D;

    private static final long RECENTLY_DAMAGED_MILLIS = 5L * 60L * 1000L;
    private static final long PASSIVE_REPAIR_MILLIS = 5L * 60L * 1000L;
    /** "healing up to 25% of the wagons' Max HP" over the 5-minute Passive Repair window -- real user spec. */
    private static final float PASSIVE_REPAIR_FRACTION_OF_BASE = 0.25F;

    private static final EntityDataAccessor<String> DATA_WOOD_TYPE =
            SynchedEntityData.defineId(AbstractDamageableDrawnEntity.class, EntityDataSerializers.STRING);
    /** One bit per {@link #wheels} index -- synced so the client-side renderer can pick the broken wheel texture. */
    private static final EntityDataAccessor<Integer> DATA_BROKEN_WHEEL_MASK =
            SynchedEntityData.defineId(AbstractDamageableDrawnEntity.class, EntityDataSerializers.INT);

    protected List<CartWheel> wheels;
    private Entity pulling;
    private UUID pullingUUID;
    private Vec3 pullerPrevPos;
    private final InterpolationHandler interpolation = new InterpolationHandler(this);

    /** Who placed this cart -- captured once at placement (see each concrete item's own {@code use}), never changes. */
    private UUID ownerId;

    // Cumulative raw damage since the last full Repair -- the single source of truth the HP/Temp-Max-HP/
    // wheel-break state is all derived from (see #recomputeFromDamage's own doc for why NOT deriving
    // this from getHealth() directly, which would cascade incorrectly once Temp Max HP itself shrinks).
    private float totalDamageTaken;
    /** Monotonic -- only ever raised by taking damage, only ever lowered by an explicit Repair (never by Passive Repair healing). */
    private int brokenWheelCount;
    private long recentlyDamagedUntilMillis;
    private long passiveRepairUntilMillis;

    // Real GUI-menu button ids (AbstractContainerMenu#clickMenuButton) -- same ids used by
    // WagonMenu/CarriageMenu's own override and by WagonScreen/CarriageScreen's buttons.
    public static final int PICK_UP_BUTTON_ID = 0;
    public static final int REPAIR_BUTTON_ID = 1;

    protected AbstractDamageableDrawnEntity(final EntityType<? extends AbstractDamageableDrawnEntity> entityType, final Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.wheels = Arrays.asList(new CartWheel(this, 0.9F), new CartWheel(this, -0.9F));
    }

    /** Mirrors {@code ArmorStand.createAttributes()}/{@code GuardEntity.createAttributes()}'s own real registration pattern -- every {@code LivingEntity} subclass needs one, registered in {@code CaryvanMod#registerAttributes}. */
    public static AttributeSupplier.Builder createAttributes(final double baseMaxHealth) {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, baseMaxHealth);
    }

    /** The per-type base Max HP constant (e.g. Wagon 100, Carriage 150 -- placeholder numbers, flagged for live-test tuning). */
    protected abstract float getBaseMaxHealth();

    protected abstract double getSpacing();

    public abstract Item getCartItem();

    public abstract Item getBrokenCartItem();

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    // --- Inert-LivingEntity overrides, mirroring ArmorStand's own real shape exactly ---------------

    @Override
    public boolean isEffectiveAi() {
        return false;
    }

    @Override
    public void travel(final Vec3 input) {
        // No-op -- this entity's own movement is entirely driven by #pulledTick()/#tick(), never
        // LivingEntity's generic swim/jump travel physics.
    }

    @Override
    public void knockback(final double strength, final double x, final double z) {
        // No-op -- a towed cart/wagon shouldn't go flying from a punch; its own chase physics
        // (#pulledTick()) is the sole real movement authority, same reasoning
        // WagonEntity#checkElasticInteractions already established for leash tension.
    }

    /**
     * Real live-test regression, 2026-10-26: the towed mount (horse) itself was "very stuttery"
     * after this migration, even though the wagon's own movement looked fine. Root cause, confirmed
     * against the real decompiled source: {@code Entity#isPushable()} hardcodes {@code false} (what
     * {@code VehicleEntity} -- which never overrides it -- always inherited, so the old cart could
     * never be shoved by anything); {@code LivingEntity#isPushable()} instead overrides it to {@code
     * isAlive() && !isSpectator() && !onClimbable()}, which evaluates {@code true} for this entity
     * now. That made the cart itself newly shovable via {@code Entity#push(Entity)}'s own mutual
     * push-apart physics -- and {@code LivingEntity#aiStep()} (run via {@code super.tick()}, see
     * {@link #tick()}'s own doc for the identical-shaped interpolation bug this session already
     * fixed) additionally calls {@code pushEntities()} every tick on both sides, a second push
     * mechanism that never ran at all under plain {@code VehicleEntity} (which never calls {@code
     * aiStep()}). With the mount sitting right at the harness position every tick, this produced a
     * real tug-of-war between vanilla's new collision push-apart and the towing code's own harness-
     * position hold -- exactly the reported stutter, isolated to the mount specifically (the cart's
     * own position stays authoritative via {@link #pulledTick()} regardless). Restoring the exact
     * old {@code VehicleEntity}/{@code Entity} default removes both push paths' effect on this
     * entity at once -- {@link #tick()}'s own existing explicit {@code this.push(entity)} loop
     * (ported from {@code AbstractDrawnEntity}, which already relied on this same hardcoded-false
     * default) still runs unchanged and still lets the cart shove other entities out of its own way;
     * only whether the cart itself can be shoved changes.
     */
    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * Ignore vanilla's health-gated default ({@code !isRemoved() && getHealth() > 0}) -- a Broken
     * (0 HP) wagon must still read as "alive" everywhere else in this suite's existing code (e.g.
     * {@link #shouldStopPulledTick()}'s own puller-liveness checks), exactly matching
     * {@code VehicleEntity}/plain {@code Entity}'s own health-independent behavior this cart always
     * had before this migration.
     */
    @Override
    public boolean isAlive() {
        return !this.isRemoved();
    }

    /**
     * No-op -- vanilla's default removes a dead {@code LivingEntity} 20 ticks after {@link #die}.
     * A Broken wagon must stay in the world indefinitely (model swapped, functionality reduced)
     * until a player explicitly picks it up -- see the approved plan's §3.
     */
    @Override
    protected void tickDeath() {
    }

    /**
     * Deliberately does NOT call {@code super.die()} -- skips vanilla's loot-table drop/wither-rose/
     * death-pose machinery entirely (none of it applies to a cart) and instead performs this
     * entity's own one-time "entered Broken state" transition: refreshes Recently Damaged (a
     * freshly-broken wagon can't be instantly repaired, per the user's own explicit requirement --
     * falls out for free since every hit, including the killing one, already refreshes this timer in
     * {@link #actuallyHurt}) and lets the concrete subclass de-summon its own bound mounts
     * (Companions explicitly keep defending -- see {@link #onEnterBrokenState()}'s own doc).
     */
    @Override
    public void die(final DamageSource source) {
        this.recentlyDamagedUntilMillis = System.currentTimeMillis() + RECENTLY_DAMAGED_MILLIS;
        this.passiveRepairUntilMillis = 0L;
        if (this.level() instanceof ServerLevel serverLevel) {
            this.onEnterBrokenState(serverLevel);
        }
    }

    /** Whether this cart is currently in the Broken (0 HP) state -- model-swap/companion-defense-gating hook for concrete subclasses. */
    public boolean isBroken() {
        return this.getHealth() <= 0.0F;
    }

    /** Called once, the instant this cart's HP reaches 0 -- overridden by {@code WagonEntity}/{@code CarriageEntity} to de-summon their own bound Summoning Stones (never Companions, which keep defending per the approved plan). */
    protected void onEnterBrokenState(final ServerLevel level) {
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public void setOwnerId(final UUID ownerId) {
        this.ownerId = ownerId;
    }

    public boolean isOwner(final net.minecraft.world.entity.player.Player player) {
        return this.ownerId != null && this.ownerId.equals(player.getUUID());
    }

    public boolean isRecentlyDamaged() {
        return System.currentTimeMillis() < this.recentlyDamagedUntilMillis;
    }

    // --- Damage intake -------------------------------------------------------------------------

    /**
     * Fully replaces vanilla's own {@code actuallyHurt} (armor/absorption/knockback bookkeeping that
     * doesn't apply to a cart) with this entity's own cumulative-damage model. {@link
     * #totalDamageTaken} -- not a value derived from {@link #getHealth()} -- is the single source of
     * truth for both the wheel-break count and the resulting HP/Temp-Max-HP; deriving from
     * {@code getHealth()} directly would cascade incorrectly, since lowering Temp Max HP itself
     * forces {@link #setHealth} to re-clamp downward with no new "real" damage having occurred.
     */
    @Override
    protected void actuallyHurt(final ServerLevel level, final DamageSource source, final float dmg) {
        if (dmg <= 0.0F) {
            return;
        }
        final float base = this.getBaseMaxHealth();
        this.totalDamageTaken = Mth.clamp(this.totalDamageTaken + dmg, 0.0F, base);
        this.recentlyDamagedUntilMillis = System.currentTimeMillis() + RECENTLY_DAMAGED_MILLIS;
        this.passiveRepairUntilMillis = 0L;

        final int wheelCount = this.wheels.size();
        if (wheelCount > 0) {
            final float fractionDamaged = Mth.clamp(this.totalDamageTaken / base, 0.0F, 1.0F);
            final int desiredBroken = Math.min(wheelCount, (int) Math.floor(fractionDamaged * wheelCount + 1.0e-4F));
            // Monotonic -- a hit only ever breaks MORE wheels, never un-breaks one (that's Repair's job only).
            if (desiredBroken > this.brokenWheelCount) {
                this.brokenWheelCount = desiredBroken;
            }
        }
        applyBrokenWheelCount();
        recomputeHealthAndMax();
        this.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ENTITY_DAMAGE);
    }

    /** Syncs {@link #brokenWheelCount} onto the real {@link CartWheel} objects (renderer reads this per-wheel) and the synced bitmask (client-visible). */
    private void applyBrokenWheelCount() {
        int mask = 0;
        for (int i = 0; i < this.wheels.size(); i++) {
            final boolean broken = i < this.brokenWheelCount;
            this.wheels.get(i).setBroken(broken);
            if (broken) {
                mask |= 1 << i;
            }
        }
        this.entityData.set(DATA_BROKEN_WHEEL_MASK, mask);
    }

    /** Temp Max HP = (base / wheelCount) * functionalWheelCount, per the approved plan's exact formula -- then re-derives current HP from {@link #totalDamageTaken} against that new ceiling. */
    private void recomputeHealthAndMax() {
        final float base = this.getBaseMaxHealth();
        final int wheelCount = this.wheels.size();
        final float tempMax = wheelCount > 0
                ? (base / wheelCount) * (wheelCount - this.brokenWheelCount)
                : base;
        final var maxHealthAttribute = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            // A hard floor of 1.0 -- an attribute base value of exactly 0 is untested territory
            // vanilla never exercises (every real LivingEntity always has positive max health); this
            // still reads/renders as fully broken (health itself still clamps to 0 below) without
            // risking undefined attribute behavior at a literal zero ceiling.
            maxHealthAttribute.setBaseValue(Math.max(1.0, tempMax));
        }
        this.setHealth(base - this.totalDamageTaken);
    }

    /**
     * A plain hit no longer destroys/picks up this cart at all -- the old {@code VehicleEntity}-era
     * {@code destroy(ServerLevel, DamageSource)} override is gone outright, replaced by the HP system
     * above; "Pick Up" (the GUI/interaction flow built in the concrete subclasses) is now the only
     * way this entity is ever actually removed from the world.
     */

    // --- Recently Damaged / Passive Repair state machine ----------------------------------------

    /** Called from {@link #tick()} every server tick -- drives the automatic Recently-Damaged-expires-into-Passive-Repair transition and the actual heal-over-time tick, per the approved plan's §4. */
    private void tickRepairStateMachine() {
        final long now = System.currentTimeMillis();
        if (this.recentlyDamagedUntilMillis > 0L && now >= this.recentlyDamagedUntilMillis) {
            // "Once the 'Recently Damaged' debuff ends, apply the 'Passive Repair' buff for 5
            // minutes" -- real user spec, automatic, not gated on the Repair button at all.
            this.recentlyDamagedUntilMillis = 0L;
            if (this.passiveRepairUntilMillis <= 0L) {
                this.passiveRepairUntilMillis = now + PASSIVE_REPAIR_MILLIS;
            }
        }
        if (this.passiveRepairUntilMillis > 0L) {
            if (now >= this.passiveRepairUntilMillis) {
                this.passiveRepairUntilMillis = 0L;
            } else if (this.totalDamageTaken > 0.0F) {
                final float healPerTick = (PASSIVE_REPAIR_FRACTION_OF_BASE * this.getBaseMaxHealth()) / (PASSIVE_REPAIR_MILLIS / 50L);
                this.totalDamageTaken = Math.max(0.0F, this.totalDamageTaken - healPerTick);
                // Deliberately does NOT touch #brokenWheelCount -- passive healing restores HP only,
                // never un-breaks a wheel; that's exclusively Repair's job (see #repair).
                recomputeHealthAndMax();
            }
        }
    }

    /** Server-tick throttle for {@link #tickStatusIndicator()} -- once per second, not every tick. */
    private int statusIndicatorCooldownTicks;

    /**
     * Real live-test gap, 2026-10-26: "No visible indicator of Recently Damaged / Passive Repair
     * state... there's no way to tell which buff/debuff is active." An actionbar message to every
     * seated player (reusing the exact {@code sendOverlayMessage} idiom {@link #handleRepairAttempt}
     * already uses for its own feedback), once per second -- simple and functional, matching this
     * suite's own "ship the real mechanic first, polish the UI later" precedent for a first pass.
     */
    private void tickStatusIndicator() {
        if (this.statusIndicatorCooldownTicks-- > 0) {
            return;
        }
        this.statusIndicatorCooldownTicks = 20;
        final net.minecraft.network.chat.Component status = currentStatusMessage();
        if (status == null) {
            return;
        }
        for (final Entity passenger : this.getPassengers()) {
            if (passenger instanceof net.minecraft.server.level.ServerPlayer player) {
                player.sendOverlayMessage(status);
            }
        }
    }

    private net.minecraft.network.chat.Component currentStatusMessage() {
        if (this.isBroken()) {
            // Already unmistakable (grey/tilted model, missing wheels) -- no extra text needed.
            return null;
        }
        if (this.isRecentlyDamaged()) {
            return net.minecraft.network.chat.Component.literal("Cart: Recently Damaged (Repair unavailable)")
                    .withStyle(net.minecraft.ChatFormatting.RED);
        }
        if (this.passiveRepairUntilMillis > System.currentTimeMillis()) {
            return net.minecraft.network.chat.Component.literal("Cart: Passive Repair active")
                    .withStyle(net.minecraft.ChatFormatting.GREEN);
        }
        return null;
    }

    /**
     * Explicit Repair action -- consumes up to {@code wheelsAvailable} matching Wheels items (the
     * caller already resolved/removed them from cargo), un-breaks that many wheels, and starts a
     * fresh 5-minute Passive Repair window regardless of current HP, per the approved plan's §5.
     * Only meaningful while {@link #isRecentlyDamaged()} is false -- the caller is responsible for
     * that gate (mirrors the GUI button's own "only clickable if not recently damaged" spec).
     *
     * @return how many wheels were actually un-broken (so the caller knows how many Wheels items were genuinely consumed).
     */
    public int repair(final int wheelsAvailable) {
        final int toFix = Math.min(wheelsAvailable, this.brokenWheelCount);
        if (toFix > 0) {
            this.brokenWheelCount -= toFix;
            applyBrokenWheelCount();
        }
        this.passiveRepairUntilMillis = System.currentTimeMillis() + PASSIVE_REPAIR_MILLIS;
        recomputeHealthAndMax();
        return toFix;
    }

    public int getBrokenWheelCount() {
        return this.brokenWheelCount;
    }

    /**
     * Owner-only, server-side-only. Sends {@code network.OpenCartPickUpConfirmPayload} directly to
     * {@code player} -- real client-side code in {@code CaryvanModClient} receives it and opens the
     * actual "Are you sure? Yes/No" {@code ConfirmActionScreen}. Both real trigger paths converge
     * here: the owner sneak+right-clicking with an empty hand ({@code #interact}, which runs on both
     * sides -- the {@code player instanceof ServerPlayer} check below is what makes calling this
     * from the client-side invocation a harmless no-op, no separate {@code isClientSide()} check
     * needed), and clicking "Pick Up" in the GUI ({@code AbstractContainerMenu#clickMenuButton},
     * which only ever runs server-side already). This method itself never references any client-only
     * type (the payload is plain data), so it's safe to call from this common-sourceset class.
     */
    public void requestPickUpConfirm(final net.minecraft.world.entity.player.Player player) {
        if (!this.isOwner(player) || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                new com.github.cerealklla.caryvan.network.OpenCartPickUpConfirmPayload(this.getId(), this.isBroken()));
    }

    /**
     * The actual Pick Up action -- called once confirmation has already happened (either the real
     * "Yes" on the {@code ConfirmScreen} opened by {@link #requestPickUpConfirm}, routed back via
     * {@code network.ConfirmCartPickUpPayload}, or directly from {@code
     * AbstractContainerMenu#clickMenuButton} for the GUI button path, which only ever runs
     * server-side after the player's own client-side "Yes" click). Re-checks ownership defensively
     * either way. Collapses the entity back into its item (intact {@link #getCartItem()}, or {@link
     * #getBrokenCartItem()} while Broken), spills cargo (see {@link #dumpCargoOnPickUp}), and lets
     * the concrete subclass roll/despawn its own bound mounts/Companions (see {@link #onPickUp}).
     */
    public void executePickUp(final net.minecraft.world.entity.player.Player player) {
        if (!this.isOwner(player) || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        final boolean broken = this.isBroken();
        this.onPickUp(level, broken);
        this.dumpCargoOnPickUp(level);
        final net.minecraft.world.item.ItemStack result = new net.minecraft.world.item.ItemStack(broken ? this.getBrokenCartItem() : this.getCartItem());
        if (!player.getInventory().add(result)) {
            this.spawnAtLocation(level, result);
        }
        this.discard();
    }

    /** Hook for the concrete subclass to roll/despawn its own bound mounts/Companions on Pick Up -- no-op by default. */
    protected void onPickUp(final ServerLevel level, final boolean broken) {
    }

    /** Hook for {@link AbstractDamageableDrawnInventoryEntity} to spill its real cargo -- no-op here (this base class has no inventory of its own). */
    protected void dumpCargoOnPickUp(final ServerLevel level) {
    }

    /**
     * Real user spec, 2026-10-26 (access-gate correction): "The owner can always access the wagon
     * inventory, no one else can unless the wagon is broken AND all companions are dead... When I
     * say dead, I mean the entity walking around is killed and their summon stone/companion item is
     * on cooldown." The owner bypasses this entirely (checked by the caller, see each concrete
     * subclass's own {@code #createMenu}) -- this only governs a non-owner's access. The real "dead"
     * signal is each companion/stone's own cooldown ({@link #hasLiveDefendingCompanion}), NOT
     * {@code Entity#isAlive()} -- a companion dies via combat out in the world, entirely independent
     * of this menu, so checking the cooldown (not a live-entity reference the menu itself would have
     * to clear) has no circularity: there's nothing for opening this menu to depend on that only
     * opening this menu could ever satisfy.
     */
    public boolean canNonOwnerAccessMenu() {
        return !this.isBroken() || !this.hasLiveDefendingCompanion();
    }

    /** Whether any of this cart's Companion slots currently holds a stack that is NOT yet on cooldown (i.e. genuinely still alive/defending) -- see {@link #canNonOwnerAccessMenu}'s own doc. */
    protected abstract boolean hasLiveDefendingCompanion();

    /**
     * Owner-only, sneak+right-click while holding a matching Wheels item -- real Repair action (see
     * the approved plan's §5). Only valid while not {@link #isRecentlyDamaged()} and while at least
     * one wheel is broken; consumes up to {@code wheelsHeld} from the held stack.
     */
    public void handleRepairAttempt(final net.minecraft.world.entity.player.Player player, final net.minecraft.world.item.ItemStack heldWheels) {
        if (this.isRecentlyDamaged()) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                    "This cart was recently damaged and can't be repaired yet."));
            return;
        }
        if (this.getBrokenWheelCount() <= 0) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                    "This cart's wheels are already in good repair."));
            return;
        }
        final int fixed = this.repair(heldWheels.getCount());
        if (fixed > 0) {
            heldWheels.shrink(fixed);
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                    "Repaired " + fixed + " wheel" + (fixed == 1 ? "" : "s") + "."));
        }
    }

    // --- Towing physics, ported unchanged from AbstractDrawnEntity ------------------------------

    @Override
    public float maxUpStep() {
        return 1.2F;
    }

    /**
     * Real live-test regression, 2026-10-26: towing was "incredibly smooth" on Production (plain
     * {@code VehicleEntity}/{@code Entity}, never auto-drives interpolation) but "very stuttery" on
     * this {@code LivingEntity}-based rebuild. Root cause, confirmed against the real decompiled
     * {@code LivingEntity#aiStep()}: it already calls {@code this.getInterpolation().interpolate()}
     * itself whenever {@link #isInterpolating()} -- generically, for any {@code LivingEntity}, which
     * this class now is -- via {@code super.tick()} just below (which reaches {@code LivingEntity
     * #tick()}, which unconditionally calls {@code aiStep()} on both sides). The code ported verbatim
     * from {@code AbstractDrawnEntity} (which genuinely needed this, since plain {@code Entity}/
     * {@code VehicleEntity} never drive interpolation on their own) called {@link
     * #interpolation}{@code .interpolate()} a SECOND time on the same client tick. Confirmed against
     * {@code InterpolationHandler#interpolate()}'s own real source that this is NOT idempotent -- it
     * decrements its own remaining-steps counter and re-bases its tracked previous position every
     * call, so two calls in one real tick consumed two interpolation steps instead of one, finishing
     * the smoothing sequence in 2/3 the intended time and producing exactly this uneven, stuttery
     * motion. The explicit call here is simply redundant now and removed outright -- {@code
     * super.tick()} already drives it correctly.
     */
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        tickRepairStateMachine();
        tickStatusIndicator();
        if (!this.isNoGravity()) {
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y - 0.08D, 0.0D);
        }
        if (this.pulling == null) {
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.attemptReattach();
        }
        for (final Entity entity : this.level().getEntities(this, this.getBoundingBox(), EntitySelector.pushableBy(this))) {
            this.push(entity);
        }
    }

    public void pulledTick() {
        if (this.pulling == null) {
            return;
        }
        final Vec3 pullerPos = this.pulling.position();
        final double pullerSpeed;
        if (this.pullerPrevPos == null) {
            pullerSpeed = 0.0D;
        } else {
            final double pdx = pullerPos.x - this.pullerPrevPos.x;
            final double pdz = pullerPos.z - this.pullerPrevPos.z;
            pullerSpeed = Math.sqrt(pdx * pdx + pdz * pdz);
        }
        this.pullerPrevPos = pullerPos;
        Vec3 targetVec = relativeTargetVec();
        this.setYRot(yawTowards(targetVec));
        while (this.getYRot() - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }
        while (this.getYRot() - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        }

        if (this.pulling.onGround()) {
            targetVec = new Vec3(targetVec.x, 0.0D, targetVec.z);
        }
        final double relativeSpacing = Math.max(this.getSpacing() + 0.5D * this.pulling.getBbWidth(), 1.0D);
        final double diff = targetVec.length() - relativeSpacing;
        if (diff > MAX_SLACK_BLOCKS && this.canDetachFromPuller()) {
            this.setPulling(null);
            return;
        }
        final double effectiveCatchUpRate = pullerSpeed + CATCH_UP_RATE;
        final double step = Math.min(Math.abs(diff), effectiveCatchUpRate) * Math.signum(diff);
        final Vec3 move = Math.abs(diff) < 1.0e-4D
                ? this.getDeltaMovement()
                : this.getDeltaMovement().add(targetVec.normalize().scale(step));

        this.setOnGround(true);
        this.move(MoverType.SELF, move);
        if (!this.isAlive()) {
            return;
        }
        if (relativeTargetVec().length() > relativeSpacing + MAX_SLACK_BLOCKS && this.canDetachFromPuller()) {
            this.setPulling(null);
        }
    }

    /** Defaults to {@code false}, same as {@link AbstractDrawnEntity#canDetachFromPuller()} -- Wagon/Carriage never voluntarily detach from a still-alive puller. */
    protected boolean canDetachFromPuller() {
        return false;
    }

    public void tickWheelsClient() {
        for (final CartWheel wheel : this.wheels) {
            wheel.tick();
        }
    }

    private Vec3 relativeTargetVec() {
        final double x = this.pulling.getX() - this.getX();
        final double y = this.pulling.getY() - this.getY();
        final double z = this.pulling.getZ() - this.getZ();
        final float yaw = (float) Math.toRadians(this.pulling.getYRot());
        final float nx = -Mth.sin(yaw);
        final float nz = Mth.cos(yaw);
        final double r = 0.2D;
        return new Vec3(x + nx * r, y, z + nz * r);
    }

    private static float yawTowards(final Vec3 vec) {
        return Mth.wrapDegrees((float) Math.toDegrees(-Mth.atan2(vec.x, vec.z)));
    }

    protected Vec3 mountSpawnPosition() {
        final float yaw = (float) Math.toRadians(this.getYRot());
        final double fx = -Mth.sin(yaw);
        final double fz = Mth.cos(yaw);
        return new Vec3(this.getX() + fx * this.getSpacing(), this.getY(), this.getZ() + fz * this.getSpacing());
    }

    public Entity getPulling() {
        return this.pulling;
    }

    protected boolean canBePulledBy(final Entity entity) {
        return (this.pulling == null || !this.pulling.isAlive()) && !this.hasPassenger(entity);
    }

    public void setPulling(final Entity entity) {
        if (this.level().isClientSide()) {
            return;
        }
        if (entity != null && !this.canBePulledBy(entity)) {
            return;
        }
        this.pulling = entity;
        this.pullingUUID = entity == null ? null : entity.getUUID();
        this.pullerPrevPos = null;
        for (final CartWheel wheel : this.wheels) {
            wheel.clearIncrement();
        }
        CaryvanWorld.get((ServerLevel) this.level()).setPulling(this, entity);
    }

    private void attemptReattach() {
        if (this.pullingUUID != null && this.level() instanceof ServerLevel serverLevel) {
            final Entity entity = serverLevel.getEntity(this.pullingUUID);
            if (entity != null && entity.isAlive()) {
                this.setPulling(entity);
            }
        }
    }

    public boolean shouldStopPulledTick() {
        if (!this.isAlive() || this.pulling == null || !this.pulling.isAlive()) {
            this.pulling = null;
            this.pullingUUID = null;
            return true;
        }
        if (this.horizontalCollision && this.canDetachFromPuller() && wallBetweenPullerAndCart()) {
            this.setPulling(null);
            return true;
        }
        return false;
    }

    private boolean wallBetweenPullerAndCart() {
        final Vec3 start = new Vec3(this.getX(), this.getY() + this.getBbHeight(), this.getZ());
        final Vec3 end = new Vec3(this.pulling.getX(), this.pulling.getY() + this.pulling.getBbHeight() / 2, this.pulling.getZ());
        final BlockHitResult result = this.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return result.getType() == HitResult.Type.BLOCK;
    }

    public double getWheelRotation(final int wheel) {
        return this.wheels.get(wheel).getRotation();
    }

    public boolean isWheelBroken(final int wheel) {
        return (this.entityData.get(DATA_BROKEN_WHEEL_MASK) & (1 << wheel)) != 0;
    }

    public WoodType getWoodType() {
        return WoodType.values()
                .filter(type -> type.name().equals(this.entityData.get(DATA_WOOD_TYPE)))
                .findFirst()
                .orElse(WoodType.OAK);
    }

    public void setWoodType(final WoodType woodType) {
        this.entityData.set(DATA_WOOD_TYPE, woodType.name());
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    @Override
    public boolean canBeCollidedWith(final Entity entity) {
        return true;
    }

    @Override
    public boolean canCollideWith(final Entity entity) {
        return (entity.canBeCollidedWith(this) || entity.isPushable()) && !this.isPassengerOfSameVehicle(entity);
    }

    @Override
    public net.minecraft.world.item.ItemStack getPickResult() {
        return new net.minecraft.world.item.ItemStack(this.isBroken() ? this.getBrokenCartItem() : this.getCartItem());
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WOOD_TYPE, WoodType.OAK.name());
        builder.define(DATA_BROKEN_WHEEL_MASK, 0);
    }

    /**
     * Real live-test report, 2026-10-26: the Broken visual did not survive a log-out/log-back-in.
     * Checked directly against the real decompiled source (not assumed) -- {@code LivingEntity} has
     * its own substantial, concrete {@code readAdditionalSaveData}/{@code addAdditionalSaveData}
     * (persists real health, absorption, active effects, equipment, hurt/death timers, etc.), unlike
     * {@code VehicleEntity}/{@code Entity}, where these are abstract with no body at all -- the
     * omission of a {@code super} call here was copied verbatim from {@code AbstractDrawnEntity},
     * which genuinely never needed one. This class's own recompute (the tail of this method) still
     * overwrites whatever {@code super} sets for health/max-health, so it alone isn't expected to be
     * the full explanation for the reported symptom -- but skipping LivingEntity's own real
     * persisted state is a genuine, independent correctness gap regardless (every other
     * {@code LivingEntity} subclass in the game calls {@code super} here), so it's restored.
     */
    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        this.pullingUUID = input.read("PullingUUID", UUIDUtil.CODEC).orElse(null);
        this.entityData.set(DATA_WOOD_TYPE, input.read("WoodType", com.mojang.serialization.Codec.STRING).orElse(WoodType.OAK.name()));
        this.ownerId = input.read("OwnerId", UUIDUtil.CODEC).orElse(null);
        this.totalDamageTaken = input.getFloatOr("TotalDamageTaken", 0.0F);
        this.brokenWheelCount = input.getIntOr("BrokenWheelCount", 0);
        this.recentlyDamagedUntilMillis = input.getLongOr("RecentlyDamagedUntilMillis", 0L);
        this.passiveRepairUntilMillis = input.getLongOr("PassiveRepairUntilMillis", 0L);
        applyBrokenWheelCount();
        recomputeHealthAndMax();
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("PullingUUID", UUIDUtil.CODEC, this.pullingUUID);
        output.store("WoodType", com.mojang.serialization.Codec.STRING, this.entityData.get(DATA_WOOD_TYPE));
        output.storeNullable("OwnerId", UUIDUtil.CODEC, this.ownerId);
        output.putFloat("TotalDamageTaken", this.totalDamageTaken);
        output.putInt("BrokenWheelCount", this.brokenWheelCount);
        output.putLong("RecentlyDamagedUntilMillis", this.recentlyDamagedUntilMillis);
        output.putLong("PassiveRepairUntilMillis", this.passiveRepairUntilMillis);
    }

    @Override
    protected void playStepSound(final net.minecraft.core.BlockPos blockPos, final net.minecraft.world.level.block.state.BlockState blockState) {
        // Silenced, same as AbstractDrawnEntity -- this entity is towed, not walking.
    }
}
