package com.github.cerealklla.caryvan.util;

import com.github.cerealklla.caryvan.entity.AbstractDrawnEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One wheel's rotation state for a {@link AbstractDrawnEntity} -- adapted from NiftyCarts
 * (github.com/jmb05/nifty-carts, MIT licensed; see this repo's own CLAUDE.md for the full
 * attribution/licensing writeup). Tracks how far the cart has actually travelled since the last
 * tick (not just how fast it's nominally moving) so the wheel visually rotates the right amount
 * regardless of pull speed, and kicks up a block-break-style particle matching whatever the wheel
 * is currently rolling over.
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
    private final AbstractDrawnEntity cart;

    public CartWheel(final AbstractDrawnEntity cart, final float offsetX, final float offsetZ, final float circumference) {
        this.cart = cart;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
        this.circumference = circumference;
        this.posX = this.prevPosX = cart.getX();
        this.posZ = this.prevPosZ = cart.getZ();
    }

    public CartWheel(final AbstractDrawnEntity cart, final float offsetX) {
        this(cart, offsetX, 0.0F, (float) (10 * Math.PI * 2 / 16));
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
