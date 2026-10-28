package com.github.cerealklla.caryvan.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;

/**
 * One wheel's rotation state for a cart entity -- adapted from NiftyCarts
 * (github.com/jmb05/nifty-carts, MIT licensed; see this repo's own CLAUDE.md for the full
 * attribution/licensing writeup). Tracks how far the cart has actually travelled since the last
 * tick (not just how fast it's nominally moving) so the wheel visually rotates the right amount
 * regardless of pull speed, and kicks up a block-break-style particle matching whatever the wheel
 * is currently rolling over.
 *
 * <p>Widened from {@code AbstractDrawnEntity} to plain {@link Entity} 2026-10-26 (Wagon/Carriage
 * health system work) -- every real usage here (`getX`/`getY`/`getYRot`/`level()`) is already
 * plain {@code Entity} API, so this one change lets the same class serve both the original
 * {@code VehicleEntity}-based cart hierarchy (Hand Cart/Animal Cart) and the new
 * {@code LivingEntity}-based one (Wagon/Carriage) with no duplication.
 */
public final class CartWheel {
    private float rotation;
    private float rotationIncrement;
    private final float offsetX;
    private final float offsetZ;
    private final float circumference;
    private double posX;
    private double posZ;
    private double prevPosX;
    private double prevPosZ;
    private final Entity cart;
    // Wagon/Carriage health system, 2026-10-26: whether this wheel is currently in the "broken"
    // visual/functional state (see AbstractDamageableDrawnEntity's own doc for the damage ->
    // wheel-break formula). Always false for Hand Cart/Animal Cart, which never set it.
    private boolean broken;
    // Wainwright Station wood-species mixing, 2026-10-28 -- this wheel's own species, independent
    // of the cart's body WoodType. Defaults to OAK (same default the body's own single WoodType
    // field already used before this feature existed) until the owning entity's placement/load
    // logic sets the real value. Not synced by this class itself -- see the owning entity's own
    // packed-sync field, same "plain POJO, owner entity handles sync" shape #broken already uses.
    private WoodType woodType = WoodType.OAK;

    public CartWheel(final Entity cart, final float offsetX, final float offsetZ, final float circumference) {
        this.cart = cart;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
        this.circumference = circumference;
        this.posX = this.prevPosX = cart.getX();
        this.posZ = this.prevPosZ = cart.getZ();
    }

    public CartWheel(final Entity cart, final float offsetX) {
        this(cart, offsetX, 0.0F, (float) (10 * Math.PI * 2 / 16));
    }

    public boolean isBroken() {
        return this.broken;
    }

    public void setBroken(final boolean broken) {
        this.broken = broken;
    }

    public WoodType getWoodType() {
        return this.woodType;
    }

    public void setWoodType(final WoodType woodType) {
        this.woodType = woodType;
    }

    public void tick() {
        this.rotation += this.rotationIncrement;
        this.prevPosX = this.posX;
        this.prevPosZ = this.posZ;
        final float yaw = (float) Math.toRadians(this.cart.getYRot());
        final float nx = -Mth.sin(yaw);
        final float nz = Mth.cos(yaw);
        this.posX = this.cart.getX() + nx * this.offsetZ - nz * this.offsetX;
        this.posZ = this.cart.getZ() + nz * this.offsetZ + nx * this.offsetX;
        final double dx = this.posX - this.prevPosX;
        final double dz = this.posZ - this.prevPosZ;
        final float distanceTravelled = (float) Math.sqrt(dx * dx + dz * dz);
        if (distanceTravelled <= 1.0e-5F) {
            this.rotationIncrement = 0.0F;
            return;
        }
        final double dxNormalized = dx / distanceTravelled;
        final double dzNormalized = dz / distanceTravelled;
        final float travelledForward = Mth.sign(dxNormalized * nx + dzNormalized * nz);
        if (distanceTravelled > 0.2) {
            final BlockPos blockPos = new BlockPos(Mth.floor(this.posX), Mth.floor(this.cart.getY() - 0.2F), Mth.floor(this.posZ));
            final BlockState blockState = this.cart.level().getBlockState(blockPos);
            if (blockState.getRenderShape() != RenderShape.INVISIBLE) {
                this.cart.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, blockState), this.posX, this.cart.getY(), this.posZ, dx, distanceTravelled, dz);
            }
        }
        this.rotationIncrement = travelledForward * distanceTravelled * this.circumference * 0.2F;
    }

    public void clearIncrement() {
        this.rotationIncrement = 0.0F;
    }

    public float getRotation() {
        return this.rotation;
    }
}
