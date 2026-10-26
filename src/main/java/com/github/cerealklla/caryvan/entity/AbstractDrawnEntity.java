package com.github.cerealklla.caryvan.entity;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.github.cerealklla.caryvan.registration.ModItems;
import com.github.cerealklla.caryvan.util.CaryvanWorld;
import com.github.cerealklla.caryvan.util.CartWheel;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shared base for every hand/animal-drawn cart or wagon (design doc §3). Adapted from NiftyCarts'
 * {@code AbstractDrawnEntity} (github.com/jmb05/nifty-carts, MIT licensed) -- see this repo's own
 * CLAUDE.md for the full attribution/licensing writeup. Reworked against this environment's current
 * entity API rather than ported line for line:
 *
 * <ul>
 *   <li>Extends vanilla's own {@link VehicleEntity} (what {@code AbstractBoat} extends too) instead
 *   of plain {@link Entity} -- it already provides the hurt/shake/destroy-and-drop machinery
 *   ({@code DATA_ID_HURT}/{@code DATA_ID_DAMAGE}, {@code hurtServer}, {@code destroy}) for free, so
 *   none of that needed hand-porting from the reference's own damage-handling code.</li>
 *   <li>No manual position/rotation lerp fields -- confirmed against the decompiled source that
 *   entity interpolation is now handled generically by {@link Entity#getInterpolation()} for any
 *   entity (the reference's own manual {@code lerpTo}/{@code tickLerp} override predates that and is
 *   simply unnecessary weight here).</li>
 *   <li>No clientbound "who is pulling this" sync payload -- {@link #pulling} is deliberately
 *   server-only state. The server is fully authoritative for a drawn entity's movement (see
 *   {@link #pulledTick()}), so the client never needs its own copy of this reference; it only ever
 *   sees the resulting position/rotation through vanilla's own generic entity sync, like any other
 *   entity in this suite (confirmed by every other custom {@code Entity} already in this project).</li>
 *   <li>No banner/wood-type decoration, no passenger/dismount handling, and no cart-pulling-a-cart
 *   chaining -- all real features of the reference this slice deliberately doesn't need yet (Hand
 *   Cart has no rider and no animal puller). Flagged, not an oversight -- passenger/dismount support
 *   in particular will need to be added back here once Phase 2 (Animal Cart) needs it, per the
 *   design document's own phased build order.</li>
 * </ul>
 */
public abstract class AbstractDrawnEntity extends VehicleEntity {

    // Real user report, 2026-10-25: "the connection... seems to snap when further than 1 block
    // apart... will be an issue while moving quickly." Raised from 2.0, and -- the actual bug --
    // the post-move check below used to have its own separate, tighter hardcoded +1.0D slack
    // instead of this constant, so a cart that hadn't fully caught up yet within the SAME tick
    // (e.g. while the puller is sprinting) could get cut loose even though the pre-move check
    // would have tolerated it. Both checks now share this one value.
    /** How far (in blocks, beyond the puller's own resting spacing) this cart will try to stay from its puller before giving up and detaching. */
    private static final double MAX_SLACK_BLOCKS = 3.0;
    private static final double CATCH_UP_RATE = 0.2D;

    // Wood-species variant (explicit user request, 2026-10-25: "add variants to respect the wood
    // species used when crafting") -- synced so the client-side renderer can pick the matching
    // texture, same String-keyed-by-WoodType#name() idiom NiftyCarts' own AbstractDrawnEntity
    // already used for this exact purpose (confirmed via the cached reference pulled earlier this
    // session), not a custom EntityDataSerializer (none exists for WoodType itself, and a plain
    // String round-trips through WoodType.values() filtering trivially).
    private static final EntityDataAccessor<String> DATA_WOOD_TYPE =
            SynchedEntityData.defineId(AbstractDrawnEntity.class, EntityDataSerializers.STRING);

    protected List<CartWheel> wheels;
    private Entity pulling;
    private UUID pullingUUID;
    // Real user report, 2026-10-25: "I am still able to run fast enough on the road that the cart
    // gets left behind." The previous fix measured the puller's current speed via
    // Entity#getDeltaMovement(), which is unreliable for a real Player on the server -- a player's
    // movement is largely client-reported/validated rather than attribute-simulated the way a Mob's
    // is, so deltaMovement doesn't reliably track real per-tick walking speed (road/potion bonuses
    // included). This instead tracks the puller's own literal position tick to tick -- real ground
    // truth, the same technique CartWheel already uses for its own distance-travelled measurement.
    private Vec3 pullerPrevPos;
    // Real user report, 2026-10-25: "still stutter teleporting instead of smoothly moving." Root
    // cause: Entity#getInterpolation() defaults to returning null (confirmed via the real decompiled
    // class -- NOT generic/automatic for every entity the way this class's own earlier doc assumed),
    // and the client's generic "apply an incoming position sync packet" path (Entity#moveOrInterpolateTo)
    // hard-snaps straight to the new position whenever that's null, with zero smoothing. Providing a
    // real handler here (the exact same pattern LivingEntity itself uses -- see its own
    // getInterpolation()/aiStep()) is what actually opts a non-living entity into smooth interpolated
    // motion; see this class's own tick() for the other required half (driving it forward each tick).
    private final InterpolationHandler interpolation = new InterpolationHandler(this);

    protected AbstractDrawnEntity(final EntityType<? extends AbstractDrawnEntity> entityType, final Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.wheels = Arrays.asList(new CartWheel(this, 0.9F), new CartWheel(this, -0.9F));
    }

    /** How far apart this cart should sit from whatever's pulling it, in blocks. */
    protected abstract double getSpacing();

    @Override
    public float maxUpStep() {
        return 1.2F;
    }

    @Override
    public void tick() {
        super.tick();
        // Real user report, 2026-10-25: "still stuttery (maybe 4x per second)... [wheels] don't
        // appear to be rotating." Root cause: this whole method runs on BOTH sides (normal Entity
        // lifecycle), but #pulling is deliberately server-only state, never synced to the client
        // (see this class's own top-level doc). That means the CLIENT's own copy of this entity
        // always sees pulling == null -- so without this guard, the client ran its OWN local
        // free-fall physics (gravity + move()) every tick, fighting against the server's real
        // chase-physics position corrections arriving via normal entity sync. That tug-of-war was
        // the stutter, and it also starved CartWheel#tick() of genuine towing motion to measure
        // (barely-visible wheel spin). Movement for this entity is 100% server-authoritative by
        // design -- the client should only ever interpolate received positions, never simulate its
        // own, so everything below is server-only now.
        //
        // That alone wasn't the whole fix, though (still reported as "stutter teleporting" with this
        // guard alone, wheels spinning fine by then): #getInterpolation() still needs a real handler
        // (see this class's own field doc) AND that handler needs driving forward every client tick,
        // exactly as LivingEntity#aiStep() does for every vanilla mob -- without this, the client-side
        // "apply an incoming position update" path (Entity#moveOrInterpolateTo) had nothing to
        // interpolate through and fell back to a hard, un-smoothed snap every single sync packet.
        if (this.level().isClientSide()) {
            if (this.isInterpolating()) {
                this.interpolation.interpolate();
            }
            return;
        }
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

    /**
     * Chases this cart's position/rotation toward its puller -- called once per server tick, in
     * puller-before-cart order, by {@link CaryvanWorld#tick()} rather than from this entity's own
     * {@link #tick()} (vanilla's normal entity-tick order makes no such guarantee, and getting this
     * wrong makes a pulled cart visibly lag a tick behind).
     */
    public void pulledTick() {
        if (this.pulling == null) {
            return;
        }
        // Real user report, 2026-10-25: "shift + right click on hand cart is not attaching/
        // detaching to player." Root cause, found by re-reading this method fresh: a leftover
        // per-tick "sneaking auto-dismounts" check used to live right here, from the ORIGINAL
        // interaction model (plain right-click toggled hitch, shift was a continuous "let go while
        // walking" gesture, no click needed). That model was flipped the same day ("same as horses/
        // chairs: right click toggles... replacing" -> later re-flipped to sneak+right-click
        // toggles) -- but this stale check was never removed. Under the CURRENT model, detaching is
        // only ever the explicit sneak+right-click toggle (see #tryToggleHitch's own "this.pulling
        // == player" branch); the moment a player sneak-right-clicked to ATTACH, they're still
        // physically holding shift for at least the next few ticks, so this check immediately
        // detached the cart again before they could ever see/feel it hitched. Removed outright.
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

        // Real user report, 2026-10-25: "when changing heights the box was rubber banding vertically
        // back to the originally placed Y position." First attempt at a fix removed this flattening
        // entirely, reasoning the reference's own flattening-while-grounded logic was the bug -- that
        // was wrong and caused a NEW regression ("still bouncing when the Y changes"): this class's
        // own tick() never applied real gravity while pulling != null (see tick()'s own fix, same
        // date), so with no flattening, this position-snap catch-up term became the ONLY thing
        // driving Y at all -- it recomputes a fresh correction every tick along the full 3D line to
        // the target, which overshoots/oscillates as that line's geometry shifts tick to tick. The
        // real, reference-matching fix is both halves together: tick() now applies genuine
        // accumulating gravity every tick (so the cart falls/settles naturally via real collision,
        // the same way any physics-driven entity does), and this flattening is restored for the
        // common grounded case so gravity -- not this snap-correction term -- owns vertical motion
        // then. The catch-up term keeps handling Y only while the puller is airborne (jumping),
        // exactly matching the reference's own tested split.
        if (this.pulling.onGround()) {
            targetVec = new Vec3(targetVec.x, 0.0D, targetVec.z);
        }
        final double relativeSpacing = Math.max(this.getSpacing() + 0.5D * this.pulling.getBbWidth(), 1.0D);
        final double diff = targetVec.length() - relativeSpacing;
        if (diff > MAX_SLACK_BLOCKS && this.canDetachFromPuller()) {
            this.setPulling(null);
            return;
        }
        // Real user report, 2026-10-25: "the wagon seems to move every .5 seconds, as opposed to
        // smoothly." Root cause: the formula this replaces (ported from the reference almost as-is)
        // is a dead-zone/snap controller, not a smooth pursuit one -- below CATCH_UP_RATE of slack it
        // applied zero horizontal correction at all (frozen), and once slack exceeded CATCH_UP_RATE it
        // snapped the cart to land *exactly* CATCH_UP_RATE short of the resting distance in a single
        // tick, however large the gap had grown. That's a real stepped pattern in the server-
        // authoritative position itself (confirmed not a client-interpolation issue -- this entity's
        // tracking range/update interval already match vanilla's own boat/minecart defaults). This
        // moves continuously every tick instead: at most the effective catch-up rate below,
        // clamped so it never overshoots past the resting distance for a small remaining gap.
        //
        // Real user request, 2026-10-25: the old FIXED CATCH_UP_RATE cap meant any puller speed
        // above it (a road/path speed bonus, Speed potion, sprinting) made the cart fall further
        // behind every tick until it crossed MAX_SLACK_BLOCKS and auto-detached -- "it should
        // dynamically match the player's current speed as the max speed." The cart's effective
        // per-tick catch-up speed is now the puller's own actual current speed (measured as a real
        // position delta above, not getDeltaMovement() -- see this class's own pullerPrevPos field
        // doc for why that was unreliable for a real Player), plus CATCH_UP_RATE of headroom so it
        // can still close an existing gap rather than just matching pace at a permanent deficit.
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

    /**
     * Whether this cart is ever allowed to voluntarily let go of a still-alive puller (excessive
     * slack, a wall in the way) -- explicit user spec, 2026-10-25: "for all but hand cart, the cart
     * should never detach from the entities driving it." Defaults to {@code false} so every future
     * cart type gets this for free; {@link HandCartEntity} is the one deliberate exception, since
     * its puller is the player themself and losing an obstructed/far-away cart is the expected,
     * player-visible behavior there. A dead/despawned puller still always clears {@link #pulling} --
     * that's a hard requirement (there's nothing left to chase), not a "give up early" case this
     * governs.
     */
    protected boolean canDetachFromPuller() {
        return false;
    }

    /**
     * Advances each wheel's rotation based on real position movement observed since the last call.
     * Deliberately NOT driven from {@link #pulledTick()} (the reference mod's own approach, ported
     * at first) -- {@link #pulledTick()} is only ever invoked server-side (see {@link CaryvanWorld}),
     * so a {@code level().isClientSide()} branch inside it can never actually run; wheels never spun
     * at all until this was caught (real user report, 2026-10-25: "the wheels actually rotated" in
     * the reference's own demo video but not here). Instead this is called once per render frame
     * from {@code HandCartRenderer#extractRenderState} -- {@link CartWheel#tick()} measures real
     * position deltas itself, so driving it from render frames (rather than ticks) is safe and
     * framerate-independent, and naturally reflects this entity's own generically-interpolated
     * client-side position (see this class's own doc on {@link Entity#getInterpolation()}) with no
     * separate client-side physics of its own needed.
     */
    public void tickWheelsClient() {
        for (final CartWheel wheel : this.wheels) {
            wheel.tick();
        }
    }

    /** The position this cart should always be facing/moving toward, relative to its own position. */
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

    /**
     * A sensible spawn point for a freshly-summoned mount -- {@link #getSpacing()} blocks ahead of
     * this cart's own current facing, rather than exactly on top of it. Real user report, 2026-10-25:
     * "the lead horse always turns to face the cart and just walks straight at it" -- root cause was
     * every mount-slot caller passing this cart's own exact coordinates as the summon position, so a
     * freshly-spawned mount starts at zero separation from the cart and immediately has to fight its
     * way out to the resting {@code relativeSpacing} the moment it's set as {@link #pulling} (see
     * {@link #pulledTick()}), producing visible back-and-forth jostling right at spawn.
     */
    protected Vec3 mountSpawnPosition() {
        final float yaw = (float) Math.toRadians(this.getYRot());
        final double fx = -Mth.sin(yaw);
        final double fz = Mth.cos(yaw);
        return new Vec3(this.getX() + fx * this.getSpacing(), this.getY(), this.getZ() + fz * this.getSpacing());
    }

    public Entity getPulling() {
        return this.pulling;
    }

    /** Whether {@code entity} is currently allowed to become this cart's puller. Hand Cart restricts this to players only -- see {@link HandCartEntity#canBePulledBy}. */
    protected boolean canBePulledBy(final Entity entity) {
        return (this.pulling == null || !this.pulling.isAlive()) && !this.hasPassenger(entity);
    }

    /** Attaches (or, with {@code null}, detaches) this cart to/from {@code entity}. Server-authoritative -- see this class's own doc for why no client-side mirror of {@link #pulling} is needed. */
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

    /**
     * "Right click toggles" (explicit user spec, 2026-10-25) -- same interaction idiom as mounting a
     * vanilla horse/boat: a plain right-click on the cart hitches {@code player} if nothing's
     * currently pulling it, or unhitches {@code player} if they're the one already pulling it.
     * Returns {@code null} (meaning "not consumed, try something else -- e.g. open the inventory")
     * if neither applies, e.g. the cart is already hitched to someone/something else.
     */
    protected net.minecraft.world.InteractionResult tryToggleHitch(final net.minecraft.world.entity.player.Player player) {
        if (this.pulling == player) {
            this.setPulling(null);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (this.pulling == null) {
            // Real user report, 2026-10-25: "shift + right click on hand cart is not attaching/
            // detaching to player." Root cause: this used to re-check `this.pulling == player`
            // right after calling setPulling() to decide the return value -- but setPulling() is a
            // deliberate no-op on the client (see its own doc, #pulling is server-only state), so
            // the CLIENT's own predictive execution of this exact method always saw #pulling still
            // null afterward and returned null ("not consumed"), making the client fall through to
            // attempting to open the cargo menu instead -- even though the server-side toggle
            // succeeded correctly. Client and server disagreeing about the same interaction's
            // outcome is the real bug; always returning SUCCESS here (not re-reading the
            // now-unreliable field) keeps both sides in agreement.
            this.setPulling(player);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return null;
    }

    /** Re-establishes {@link #pulling} after a chunk/entity reload, from the persisted UUID. */
    private void attemptReattach() {
        if (this.pullingUUID != null && this.level() instanceof ServerLevel serverLevel) {
            final Entity entity = serverLevel.getEntity(this.pullingUUID);
            if (entity != null && entity.isAlive()) {
                this.setPulling(entity);
            }
        }
    }

    /** True once this cart should stop receiving {@link #pulledTick()} calls -- its puller died/despawned, or a wall got between them. */
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

    public abstract Item getCartItem();

    /**
     * The generic (not wood-specific -- a wrecked cart no longer carries its original wood identity,
     * same reasoning {@link com.github.cerealklla.caryvan.registration.ModItems#BROKEN_WHEEL} is a
     * single item too) item a destroyed cart drops instead of its intact {@link #getCartItem()} --
     * real user spec, 2026-10-26, matching a video reference of the original mod this was adapted
     * from: a destroyed cart drops a repairable "Broken" item plus its own wheels (also broken), not
     * the finished product outright.
     */
    public abstract Item getBrokenCartItem();

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
    protected Item getDropItem() {
        return this.getCartItem();
    }

    /**
     * Overrides {@code VehicleEntity}'s own default (which would call {@link #getDropItem()} --
     * the intact cart) to drop a "Broken" cart item plus one Broken Wheel per real wheel this cart
     * has instead -- real user spec, 2026-10-26: "Broken Wheel and Broken Wagon, with a recipe to
     * combine Broken with N Non-Broken Wheels to 'repair' the wagon, and the Broken Wheel -> Iron
     * Ingots to scrap the broken wheels." Wheel count comes straight from {@link #wheels} (already
     * tracked generically per cart type -- 2 for Hand Cart/Animal Cart, 4 for Wagon/Carriage), no new
     * per-type count needed. Reuses the public {@code destroy(ServerLevel, Item)} for the cart body
     * itself (same {@code ENTITY_DROPS} game-rule gate/kill sequence vanilla's own default already
     * proves out), then separately spawns the wheels under that same gate.
     */
    @Override
    protected void destroy(final ServerLevel level, final DamageSource source) {
        final int wheelCount = this.wheels.size();
        this.destroy(level, this.getBrokenCartItem());
        if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            for (int i = 0; i < wheelCount; i++) {
                this.spawnAtLocation(level, new ItemStack(ModItems.BROKEN_WHEEL.get()));
            }
        }
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
        return new net.minecraft.world.item.ItemStack(this.getCartItem());
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WOOD_TYPE, WoodType.OAK.name());
    }

    // No super call -- Entity declares both of these abstract, and nothing between it and this
    // class (VehicleEntity included) provides a concrete body, confirmed by the compiler itself
    // ("abstract method ... cannot be accessed directly") when a super call was tried here. This is
    // the first real implementation in the chain; AbstractDrawnInventoryEntity's own override below
    // it in the hierarchy calls super into this one normally.
    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        this.pullingUUID = input.read("PullingUUID", UUIDUtil.CODEC).orElse(null);
        this.entityData.set(DATA_WOOD_TYPE, input.read("WoodType", com.mojang.serialization.Codec.STRING).orElse(WoodType.OAK.name()));
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        output.storeNullable("PullingUUID", UUIDUtil.CODEC, this.pullingUUID);
        output.store("WoodType", com.mojang.serialization.Codec.STRING, this.entityData.get(DATA_WOOD_TYPE));
    }

    @Override
    protected void playStepSound(final net.minecraft.core.BlockPos blockPos, final net.minecraft.world.level.block.state.BlockState blockState) {
        // Silences vanilla's default footstep-style sound for this entity's own "steps" (it has
        // none -- it's towed, not walking); a real rolling/creaking sound is a flagged follow-up,
        // not built this pass.
    }
}
