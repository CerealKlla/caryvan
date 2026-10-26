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
    /** The owner's real display name at placement time -- a plain cached String (not re-resolved from {@link #ownerId} every time, which would need an online/profile-cache lookup), surfaced via {@link #getDisplayName()} so the GUI's own title bar shows it -- real live-test request: "no way to confirm in-game who the game thinks owns a given wagon." */
    private String ownerName;

    // Real design correction, 2026-10-26, per the user's own words: "I never said I wanted
    // 'cumulative lifetime damage' tracked." A ratcheted totalDamageTaken field was invented during
    // implementation, never actually requested -- the original spec's own wording ("nF is the
    // number of functional wheels currently on the wagon") means live current state, not a
    // SEPARATE ever-accumulating damage ledger. Removed entirely.
    //
    // Real follow-up correction, same day: Repair must raise this ceiling WITHOUT any instant heal
    // of current health at all ("Repair... should not give an instant heal of any kind... raises
    // the Temp Max HP by (1/4 wagon max hp) * wheels replaced"). Since health no longer moves when
    // Repair runs, broken-wheel-count can no longer be a PURE function of current health either --
    // if it were, Repair would have nothing to actually lower. It's authoritative, stored state
    // again (and persisted -- see #addAdditionalSaveData), but ratcheted UP only by damage (via the
    // live health-derived formula in #computeBrokenWheelCount, not a separate damage number -- this
    // is the real distinction from the rejected totalDamageTaken model) and lowered ONLY by an
    // explicit #repair(int) call, never by any kind of healing.
    private int brokenWheelCount;
    private long recentlyDamagedUntilMillis;
    private long passiveRepairUntilMillis;

    // Real GUI-menu button ids (AbstractContainerMenu#clickMenuButton) -- same ids used by
    // WagonMenu/CarriageMenu's own override and by WagonScreen/CarriageScreen's buttons.
    public static final int PICK_UP_BUTTON_ID = 0;
    public static final int REPAIR_BUTTON_ID = 1;

    /**
     * Real bug, 2026-10-26: this used to hardcode exactly 2 {@link CartWheel}s, copy-pasted verbatim
     * from {@code AbstractDrawnEntity} -- correct THERE (Hand Cart/Animal Cart really do have 2
     * wheels, confirmed from their own models during the earlier Wheels-recipe-rework session), but
     * this class is the base for Wagon/Carriage specifically, which both have 4 real wheels --
     * confirmed independently two ways: that same Wheels-rework session's own repair recipes already
     * require 4 Wheels items for a Wagon/Carriage ("wheel count confirmed from WagonModel"), and
     * {@code WagonModel}/{@code CarriageModel} both declare 4 real wheel {@code ModelPart}s
     * (`leftWheelFront`/`rightWheelFront`/`leftWheelBack`/`rightWheelBack`, in that order -- matched
     * by index below: 0/1/2/3). The 2-wheel version silently broke the whole health system's wheel-
     * break math (it only ever had 2 slots to mark broken, each requiring 50%+ cumulative damage,
     * instead of 4 slots at a correct 25% each) -- the renderer/menu code (see {@code
     * WagonRenderer#extractRenderState}'s own updated doc) also needed updating to match, not just
     * this list. Offsets are placeholder-approximate (cosmetic -- only affects where rolling-dust
     * particles spawn, not any rotation/physics math, which is driven by real travel distance
     * regardless), sized relative to each concrete type's own real hitbox width via {@link
     * #getWheelTrackHalfWidth()}/{@link #getWheelAxleHalfSpacing()}.
     */
    protected AbstractDamageableDrawnEntity(final EntityType<? extends AbstractDamageableDrawnEntity> entityType, final Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        final float trackX = this.getWheelTrackHalfWidth();
        final float axleZ = this.getWheelAxleHalfSpacing();
        this.wheels = Arrays.asList(
                new CartWheel(this, trackX, -axleZ, WHEEL_CIRCUMFERENCE),   // 0: leftFront
                new CartWheel(this, -trackX, -axleZ, WHEEL_CIRCUMFERENCE),  // 1: rightFront
                new CartWheel(this, trackX, axleZ, WHEEL_CIRCUMFERENCE),    // 2: leftBack
                new CartWheel(this, -trackX, axleZ, WHEEL_CIRCUMFERENCE));  // 3: rightBack
    }

    /** Same default circumference the old 2-arg {@code CartWheel} constructor used. */
    private static final float WHEEL_CIRCUMFERENCE = (float) (10 * Math.PI * 2 / 16);

    /** Half the real hitbox width, roughly -- how far left/right each wheel sits from center. Overridden per concrete type to roughly match its own real hitbox size. */
    protected float getWheelTrackHalfWidth() {
        return 0.75F;
    }

    /** How far forward/back each axle sits from center. Overridden per concrete type to roughly match its own real hitbox size. */
    protected float getWheelAxleHalfSpacing() {
        return 0.75F;
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

    /** Captures both the owner's id and their real display name at placement time -- see {@link #ownerName}'s own doc for why the name is cached, not re-resolved later. */
    public void setOwner(final net.minecraft.world.entity.player.Player player) {
        this.ownerId = player.getUUID();
        this.ownerName = player.getName().getString();
    }

    public boolean isOwner(final net.minecraft.world.entity.player.Player player) {
        return this.ownerId != null && this.ownerId.equals(player.getUUID());
    }

    /**
     * Surfaces the owner's name directly in the GUI's own title bar (confirmed this already flows
     * through to the client unchanged -- {@code MenuProvider#getDisplayName()}'s return value is
     * what the vanilla menu-open packet sends as the screen's title, no new network code needed).
     */
    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        if (this.ownerName == null) {
            return super.getDisplayName();
        }
        return net.minecraft.network.chat.Component.literal(super.getDisplayName().getString() + " (Owner: " + this.ownerName + ")");
    }

    public boolean isRecentlyDamaged() {
        return System.currentTimeMillis() < this.recentlyDamagedUntilMillis;
    }

    // --- Damage intake -------------------------------------------------------------------------

    /**
     * Fully replaces vanilla's own {@code actuallyHurt} (armor/absorption/knockback bookkeeping that
     * doesn't apply to a cart) with this entity's own health model. Health itself is applied
     * directly below; {@link #brokenWheelCount} is then ratcheted UP (never down) to whatever the
     * resulting health alone implies, via {@link #computeBrokenWheelCount()} -- see
     * {@link #brokenWheelCount}'s own field doc for the real design history behind this shape.
     */
    @Override
    protected void actuallyHurt(final ServerLevel level, final DamageSource source, final float dmg) {
        if (dmg <= 0.0F) {
            return;
        }
        this.recentlyDamagedUntilMillis = System.currentTimeMillis() + RECENTLY_DAMAGED_MILLIS;
        this.passiveRepairUntilMillis = 0L;
        // The real hit itself -- the ONLY thing that ever lowers health, and the only thing health
        // ever derives from on the way down. setHealth() clamps internally to [0, the real,
        // never-changing Attributes.MAX_HEALTH], so this alone can never raise health.
        this.setHealth(this.getHealth() - dmg);
        // Ratchet UP only -- a hit only ever breaks MORE wheels than are already broken, never
        // un-breaks one (that's #repair's own job, exclusively). Driven live off current health via
        // #computeBrokenWheelCount, not a separate damage ledger -- the real distinction from the
        // rejected totalDamageTaken model.
        final int healthImplied = computeBrokenWheelCount();
        if (healthImplied > this.brokenWheelCount) {
            this.brokenWheelCount = healthImplied;
            applyBrokenWheelCount();
        }
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

    /**
     * What CURRENT health ALONE implies the broken-wheel count should be -- a dropped Max HP
     * fraction maps directly to a wheel count, recomputed fresh from {@link #getHealth()} every
     * call, with no memory of how it got there. This is a READ, never touching {@code setHealth}/
     * the max-health attribute. {@link #actuallyHurt} uses this to ratchet {@link #brokenWheelCount}
     * UP (never down) -- see that field's own doc for why this is the live replacement for the
     * rejected {@code totalDamageTaken} ledger, and why it's no longer the SOLE source of truth for
     * {@link #brokenWheelCount} the way it briefly was (health no longer moves when {@link
     * #repair(int)} lowers the broken count, so a pure function of health alone would have nothing
     * for Repair to actually change).
     */
    private int computeBrokenWheelCount() {
        final int wheelCount = this.wheels.size();
        final float base = this.getBaseMaxHealth();
        if (wheelCount <= 0 || base <= 0.0F) {
            return 0;
        }
        final float fractionDamaged = Mth.clamp(1.0F - this.getHealth() / base, 0.0F, 1.0F);
        return Math.min(wheelCount, (int) Math.floor(fractionDamaged * wheelCount + 1.0e-4F));
    }

    /**
     * Real design correction, 2026-10-26 -- supersedes the earlier "clamp current health DOWN into a
     * lower ceiling, never raise it" fix outright, not just patches it, per the user's own explicit
     * words: "You keep changing the 'Max HP'. Do not do that. That never changes. 'Temporary Max HP'
     * is not visually represented on the health bar at all; it only prevents passive repair from
     * repairing too much when no replacement wheel is consumed."
     *
     * <p>{@code Attributes.MAX_HEALTH} is set exactly once, at construction ({@link #createAttributes}),
     * to the real per-type base constant, and is **never touched again anywhere in this class** --
     * confirmed by re-reading this entire file after this fix: the only remaining
     * {@code getAttribute(Attributes.MAX_HEALTH)}/{@code setBaseValue} reference is gone outright.
     * {@link #getMaxHealth()} (and therefore what Nuclyus's stat bar displays) now always reads the
     * unchanging base constant, exactly as the user wants.
     *
     * <p>"Temp Max HP" ({@code (base / wheelCount) * functionalWheelCount}) still exists, but purely
     * as this internal, non-attribute computation -- it is never applied to the real max-health
     * attribute and never used to clamp current health directly (breaking a wheel no longer touches
     * current health AT ALL -- see {@link #actuallyHurt}'s own doc). Its one and only consumer is
     * {@link #tickRepairStateMachine()}'s Passive Repair heal-over-time tick, which must not heal
     * current health above whatever this returns *right now* (computed fresh off the live, current
     * {@link #brokenWheelCount} every time it's called, not a cached/attribute-backed value) --
     * passive, no-wheel-replaced healing can only ever get a cart back to "as healthy as its
     * remaining good wheels allow," while an explicit {@link #repair} swapping in a real Wheel lowers
     * {@link #brokenWheelCount}, which raises what this method returns, which lifts that cap for
     * future passive healing -- for good, without ever having touched health or the attribute itself.
     */
    private float getTempMaxHealthCeiling() {
        final float base = this.getBaseMaxHealth();
        final int wheelCount = this.wheels.size();
        return wheelCount > 0
                ? (base / wheelCount) * (wheelCount - this.brokenWheelCount)
                : base;
    }

    /**
     * A plain hit no longer destroys/picks up this cart at all -- the old {@code VehicleEntity}-era
     * {@code destroy(ServerLevel, DamageSource)} override is gone outright, replaced by the HP system
     * above; "Pick Up" (the GUI/interaction flow built in the concrete subclasses) is now the only
     * way this entity is ever actually removed from the world.
     */

    // --- Recently Damaged / Passive Repair state machine ----------------------------------------

    /**
     * Called from {@link #tick()} every server tick -- drives the automatic Recently-Damaged-expires-
     * into-Passive-Repair transition and runs the actual heal-over-time tick while that window is
     * active, per the approved plan's §4.
     *
     * <p>Real live-test correction, 2026-10-26, in two parts (a false start, then the real fix). The
     * first report -- "the wagon keeps healing itself after a few moments of me not riding it, its
     * passive health regen should be 0" -- was initially (and wrongly) diagnosed as this method's own
     * auto-start-on-expiry branch, which was removed outright on the theory that healing should only
     * ever follow a deliberate Repair click. The user's own follow-up correction: **that auto-start is
     * exactly right and matches the approved plan precisely** ("the wagon IS supposed to
     * auto-repair... but only after the 5 minute 'recently damaged' debuff is gone") -- restored below,
     * unchanged from the original design. The REAL root cause of the original report was the
     * since-corrected Max-HP-attribute bug (see {@link #getTempMaxHealthCeiling()}'s own doc): current
     * health was tracking/jumping whenever the real max-health attribute itself got dynamically
     * recomputed off wheel count, which read as unprompted "healing" even though no heal-over-time
     * tick was actually involved. With max-health now truly constant, "passive regen should be 0"
     * instead means exactly what it says structurally: outside an active Passive Repair window (this
     * method), current health never moves on its own at all -- re-confirmed true by grepping this
     * entire class for every {@code setHealth(} call site.
     *
     * <p>Real design correction, same day, walking back an earlier over-extension: a wheel must
     * NEVER un-break from passive healing of any kind (neither this auto-triggered window nor the
     * heal-over-time window {@link #repair(int)} itself kicks off) -- per the user's own exact words,
     * "they should ONLY be repaired by consuming a wheel with the repair button." The cap below
     * already guarantees this heal can never rise past the live {@link #getTempMaxHealthCeiling()}
     * (itself derived from {@link #brokenWheelCount}, which this method never touches directly
     * either way).
     *
     * <p>Real follow-up correction, same day: {@link #repair(int)} itself no longer does ANY instant
     * heal either -- "it instantly replaces a wheel and grants the passive regen buff again. There
     * is no instant heal at all for the wagon... should not give an instant heal of any kind."
     * Repair's real job is just three things: swap the wheel's own broken flag back to intact
     * (lowering {@link #brokenWheelCount} directly, with zero health change), let that naturally
     * raise {@link #getTempMaxHealthCeiling()}'s own return value, and grant/refresh THIS window --
     * all the real healing after a Repair click happens only here, over time, same as the
     * auto-triggered case.
     */
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
            } else {
                // Capped at the live Temp Max HP ceiling (re-derived from the current #brokenWheelCount
                // every call), NOT #getMaxHealth() -- the real max-health attribute never changes, so
                // capping against it would let no-wheel-replaced passive healing reach all the way back
                // to full, which is exactly what this ceiling exists to prevent (per the user's own
                // design correction -- see #getTempMaxHealthCeiling's own doc). This cap is also what
                // guarantees a wheel can never un-break from this heal alone -- #brokenWheelCount is
                // never touched here at all, only #repair(int) ever lowers it.
                final float tempMaxCeiling = getTempMaxHealthCeiling();
                if (this.getHealth() < tempMaxCeiling) {
                    final float healPerTick = (PASSIVE_REPAIR_FRACTION_OF_BASE * this.getBaseMaxHealth()) / (PASSIVE_REPAIR_MILLIS / 50L);
                    // Explicitly clamped to the ceiling -- setHealth() alone only clamps to the real,
                    // unchanging max-health attribute, which is no longer narrow enough to rely on.
                    this.setHealth(Math.min(this.getHealth() + healPerTick, tempMaxCeiling));
                }
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
     * caller already resolved/removed them from cargo) and starts/refreshes a fresh 5-minute Passive
     * Repair window, per the approved plan's §5. Only meaningful while {@link #isRecentlyDamaged()}
     * is false -- the caller is responsible for that gate (mirrors the GUI button's own "only
     * clickable if not recently damaged" spec).
     *
     * <p>Real design correction, 2026-10-26, per the user's own exact words: "it instantly replaces
     * a wheel and grants the passive regen buff again. There is no instant heal at all for the
     * wagon... should not give an instant heal of any kind. Visually puts on a new wheel, grants the
     * Passive Regen Buff, and raises the Temp Max HP by (1/4 wagon max hp) * wheels replaced." This
     * is now exactly those three things and nothing else: {@link #brokenWheelCount} is decremented
     * directly (the wheel's own visual/broken flag, via {@link #applyBrokenWheelCount()}), which
     * naturally raises what {@link #getTempMaxHealthCeiling()} returns from now on -- {@code
     * current health is never touched here at all}. All of the actual healing happens afterward,
     * over time, through the SAME Passive Repair window the auto-trigger uses (see {@link
     * #tickRepairStateMachine()}), capped at the new, now-higher ceiling this just raised.
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
    /**
     * Real gap, 2026-10-26: this never actually enforced the user's own explicit spec -- "Pick Up
     * should be disabled/blocked unless the wagon is at exactly full HP (100%) or exactly 0 HP
     * (fully Broken); any intermediate damage state should NOT allow Pick Up" (picking up a
     * partially-damaged wagon as a plain item with no HP persisted on the item itself would
     * otherwise be a free full-repair exploit). Checked here (before ever sending the confirm
     * dialog) and defensively again in {@link #executePickUp}.
     */
    public boolean canPickUp() {
        return this.isBroken() || this.getHealth() >= this.getMaxHealth();
    }

    public void requestPickUpConfirm(final net.minecraft.world.entity.player.Player player) {
        if (!this.isOwner(player) || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        if (!this.canPickUp()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "This cart can only be picked up at full health or once fully Broken -- repair it the rest of the way, or let it break."));
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
        if (!this.isOwner(player) || !this.canPickUp() || !(this.level() instanceof ServerLevel level)) {
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

    /**
     * Real live-test gap, 2026-10-26: removing a Companion/Summon Stone via a plain inventory-slot
     * click (not the GUI "Pick Up" button) rolled the 50%-destroy chance immediately, with no
     * warning -- real user spec: the warning must gate "any attempt to remove a Companion/Summon
     * Stone item from its slot while isBroken() is true, not just from the Pick Up button's own
     * flow." Mirrors {@link #requestPickUpConfirm}'s own real dialog mechanism exactly, just keyed
     * to a specific slot index instead of the whole cart. {@code WagonMenu}/{@code CarriageMenu
     * #clicked} is the real interception point (see its own doc) -- this method is only ever called
     * once that's already confirmed the click would otherwise remove a non-empty Companion slot
     * while Broken.
     */
    public void requestCompanionSlotRemovalConfirm(final net.minecraft.world.entity.player.Player player, final int slotIndex) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                new com.github.cerealklla.caryvan.network.OpenSlotRemovalConfirmPayload(this.getId(), slotIndex));
    }

    /**
     * The actual removal, performed directly server-side once the player clicks "Yes" (the original
     * slot click that triggered the dialog was cancelled outright, never retried). Rolls the real
     * 50%-destroy chance (only while still {@link #isBroken()} at the moment of confirmation -- if
     * the cart was repaired in the meantime, no risk at all, matching the clarified risk model
     * exactly) against the live Companion/Summoning-Stone-mount riding at {@code slotIndex} (see
     * {@link #getCompanionEntityForSlot(int)}), then either clean-despawns it (success) or discards
     * the live entity and leaves the player with nothing (failure) -- mirrors {@code
     * menu.CompanionSlot}/{@code menu.MountSlot#onTake}'s own roll exactly, kept there too as a
     * defense-in-depth safety net for any path that isn't this one.
     *
     * <p>Real live-test bug, 2026-10-26: "I was able to both click+drag and shift+click summon
     * stones out of the wagon without A) getting a warning, or B) any ever being destroyed... only
     * works with the pick up button." Root cause -- this whole mechanism (this method, {@link
     * #getCompanionEntityForSlot(int)}, and {@code WagonMenu}/{@code CarriageMenu}'s own
     * {@code clicked}/{@code quickMoveStack} guards) was only ever built for {@code CompanionSlot},
     * never extended to {@code MountSlot} (where a real Summoning Stone actually lives) -- all now
     * fixed together. Despawn method is now chosen by the stack's own real item type ({@code
     * LyfeSummoningBridge#isMountSummoningStone}), not by which slot it came from, since this method
     * is shared across both slot kinds now.
     */
    public void confirmCompanionSlotRemoval(final net.minecraft.world.entity.player.Player player, final int slotIndex) {
        if (!(this.level() instanceof ServerLevel level) || !(this instanceof net.minecraft.world.entity.vehicle.ContainerEntity container)) {
            return;
        }
        final net.minecraft.world.item.ItemStack stack = container.getItem(slotIndex);
        if (stack.isEmpty()) {
            return;
        }
        final Entity companion = this.getCompanionEntityForSlot(slotIndex);
        if (this.isBroken() && level.getRandom().nextFloat() < 0.5F) {
            if (companion != null) {
                companion.discard();
            }
            container.setItem(slotIndex, net.minecraft.world.item.ItemStack.EMPTY);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Too wounded to retrieve."));
            return;
        }
        if (com.github.cerealklla.caryvan.bridge.LyfeSummoningBridge.isLoaded()) {
            if (com.github.cerealklla.caryvan.bridge.LyfeSummoningBridge.isMountSummoningStone(stack)) {
                com.github.cerealklla.caryvan.bridge.LyfeSummoningBridge.despawnMountCleanly(level, stack);
            } else {
                com.github.cerealklla.caryvan.bridge.LyfeSummoningBridge.despawnCompanionCleanly(level, stack);
            }
        }
        final net.minecraft.world.item.ItemStack removed = container.removeItemNoUpdate(slotIndex);
        if (!player.getInventory().add(removed)) {
            player.drop(removed, false);
        }
    }

    /**
     * Whether removing whatever currently sits in {@code slotIndex} while this cart {@link
     * #isBroken()} should roll the real 50%-destroy chance -- true whenever the roll fails,
     * discarding the live bound entity for that slot (if any) as a side effect so the caller knows
     * to confiscate the stack instead of letting it reach the player. Returns {@code false}
     * unconditionally while not Broken (no risk at all outside that state). Shared by {@code
     * menu.CompanionSlot}/{@code menu.MountSlot#onTake} -- see those classes' own doc for why a
     * direct container-slot removal needs its own copy of this roll, separate from {@link
     * #confirmCompanionSlotRemoval}, which only ever runs after the player has already confirmed the
     * dialog and the original click was cancelled outright.
     */
    public boolean rollDestroyOnBrokenRemoval(final ServerLevel level, final int slotIndex) {
        if (!this.isBroken() || level.getRandom().nextFloat() >= 0.5F) {
            return false;
        }
        final Entity companion = this.getCompanionEntityForSlot(slotIndex);
        if (companion != null) {
            companion.discard();
        }
        return true;
    }

    /** Which live Companion/Summoning-Stone-mount entity (if any) currently occupies {@code slotIndex} -- overridden per concrete type, since slot layout differs between Wagon (2 mount + 2 companion slots) and Carriage (2 mount + 6 companion slots). */
    protected Entity getCompanionEntityForSlot(final int slotIndex) {
        return null;
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
     * which genuinely never needed one. Skipping LivingEntity's own real persisted state is a
     * genuine, independent correctness gap regardless (every other {@code LivingEntity} subclass in
     * the game calls {@code super} here), so it's restored.
     *
     * <p>Real design correction, 2026-10-26: this used to call a recompute that also re-set the real
     * max-health attribute off the just-restored {@link #brokenWheelCount} -- gone along with the
     * rest of that mechanism (see {@link #getTempMaxHealthCeiling()}'s own doc). {@code super}'s own
     * call above already restores the real, unchanging max-health attribute and current health
     * directly (confirmed via real decompiled source: {@code LivingEntity} persists both, including
     * the attribute via {@code AttributeInstance.Packed.LIST_CODEC}).
     *
     * <p>Real follow-up correction, same day: {@link #brokenWheelCount} briefly stopped being
     * persisted at all (when it was, for one round, a pure function of health) -- now that {@link
     * #repair(int)} can lower it WITHOUT any health change (see that method's own doc), it's
     * authoritative state again and genuinely needs its own NBT round-trip, same as before.
     */
    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        this.pullingUUID = input.read("PullingUUID", UUIDUtil.CODEC).orElse(null);
        this.entityData.set(DATA_WOOD_TYPE, input.read("WoodType", com.mojang.serialization.Codec.STRING).orElse(WoodType.OAK.name()));
        this.ownerId = input.read("OwnerId", UUIDUtil.CODEC).orElse(null);
        this.ownerName = input.getString("OwnerName").orElse(null);
        this.brokenWheelCount = input.getIntOr("BrokenWheelCount", 0);
        this.recentlyDamagedUntilMillis = input.getLongOr("RecentlyDamagedUntilMillis", 0L);
        this.passiveRepairUntilMillis = input.getLongOr("PassiveRepairUntilMillis", 0L);
        applyBrokenWheelCount();
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("PullingUUID", UUIDUtil.CODEC, this.pullingUUID);
        output.store("WoodType", com.mojang.serialization.Codec.STRING, this.entityData.get(DATA_WOOD_TYPE));
        output.storeNullable("OwnerId", UUIDUtil.CODEC, this.ownerId);
        if (this.ownerName != null) {
            output.putString("OwnerName", this.ownerName);
        }
        output.putInt("BrokenWheelCount", this.brokenWheelCount);
        output.putLong("RecentlyDamagedUntilMillis", this.recentlyDamagedUntilMillis);
        output.putLong("PassiveRepairUntilMillis", this.passiveRepairUntilMillis);
    }

    @Override
    protected void playStepSound(final net.minecraft.core.BlockPos blockPos, final net.minecraft.world.level.block.state.BlockState blockState) {
        // Silenced, same as AbstractDrawnEntity -- this entity is towed, not walking.
    }
}
