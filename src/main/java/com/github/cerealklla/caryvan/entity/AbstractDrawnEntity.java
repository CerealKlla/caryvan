package com.github.cerealklla.caryvan.entity;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.github.cerealklla.caryvan.util.CaryvanWorld;
import com.github.cerealklla.caryvan.util.CartWheel;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
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

    /** How far (in blocks, beyond the puller's own resting spacing) this cart will try to stay from its puller before giving up and detaching. */
    private static final double MAX_SLACK_BLOCKS = 2.0;
    private static final double CATCH_UP_RATE = 0.2D;

    protected List<CartWheel> wheels;
    private Entity pulling;
    private UUID pullingUUID;

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
        if (this.pulling == null) {
            this.setDeltaMovement(this.getDeltaMovement().x, this.isNoGravity() ? 0.0D : this.getDeltaMovement().y - 0.08D, this.getDeltaMovement().z);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.attemptReattach();
        }
        if (!this.level().isClientSide()) {
            for (final Entity entity : this.level().getEntities(this, this.getBoundingBox(), EntitySelector.pushableBy(this))) {
                this.push(entity);
            }
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
        // "Left shift auto dismounts" (explicit user spec, 2026-10-25, same convention as mounting
        // any vanilla vehicle) -- checked every tick, not just at interact-time, so sneaking while
        // already walking/pulling detaches immediately with no click needed.
        if (this.pulling instanceof net.minecraft.world.entity.player.Player player && player.isShiftKeyDown()) {
            this.setPulling(null);
            return;
        }
        final Vec3 targetVec = relativeTargetVec();
        this.setYRot(yawTowards(targetVec));
        while (this.getYRot() - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }
        while (this.getYRot() - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        }

        // Real user report, 2026-10-25: "when changing heights the box was rubber banding vertically
        // back to the originally placed Y position." Root cause: the reference mod's own logic
        // (ported faithfully at first) flattens the target vector's Y to 0 whenever the puller is
        // "on ground" -- true almost all the time while walking, including on stairs/slopes -- so
        // the cart's own Y was never actually chased at all, only nudged by vanilla's incidental
        // step-up-on-collision behavior. Using the real 3D target vector throughout (never flattened)
        // fixes it: the cart now genuinely chases the puller's real height, not just their XZ position.
        final double relativeSpacing = Math.max(this.getSpacing() + 0.5D * this.pulling.getBbWidth(), 1.0D);
        final double diff = targetVec.length() - relativeSpacing;
        if (diff > MAX_SLACK_BLOCKS) {
            this.setPulling(null);
            return;
        }
        final Vec3 move = Math.abs(diff) < CATCH_UP_RATE
                ? this.getDeltaMovement()
                : this.getDeltaMovement().add(targetVec.subtract(targetVec.normalize().scale(relativeSpacing + CATCH_UP_RATE * Math.signum(diff))));

        this.setOnGround(true);
        this.move(MoverType.SELF, move);
        if (!this.isAlive()) {
            return;
        }
        if (this.level().isClientSide()) {
            for (final CartWheel wheel : this.wheels) {
                wheel.tick();
            }
        } else if (relativeTargetVec().length() > relativeSpacing + 1.0D) {
            this.setPulling(null);
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
            this.setPulling(player);
            return this.pulling == player ? net.minecraft.world.InteractionResult.SUCCESS : null;
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
        if (this.horizontalCollision && wallBetweenPullerAndCart()) {
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

    @Override
    protected Item getDropItem() {
        return this.getCartItem();
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
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
    }

    // No super call -- Entity declares both of these abstract, and nothing between it and this
    // class (VehicleEntity included) provides a concrete body, confirmed by the compiler itself
    // ("abstract method ... cannot be accessed directly") when a super call was tried here. This is
    // the first real implementation in the chain; AbstractDrawnInventoryEntity's own override below
    // it in the hierarchy calls super into this one normally.
    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        this.pullingUUID = input.read("PullingUUID", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        output.storeNullable("PullingUUID", UUIDUtil.CODEC, this.pullingUUID);
    }

    @Override
    protected void playStepSound(final net.minecraft.core.BlockPos blockPos, final net.minecraft.world.level.block.state.BlockState blockState) {
        // Silences vanilla's default footstep-style sound for this entity's own "steps" (it has
        // none -- it's towed, not walking); a real rolling/creaking sound is a flagged follow-up,
        // not built this pass.
    }
}
