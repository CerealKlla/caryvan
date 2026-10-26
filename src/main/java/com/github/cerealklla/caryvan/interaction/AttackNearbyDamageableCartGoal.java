package com.github.cerealklla.caryvan.interaction;

import java.util.EnumSet;
import java.util.List;

import com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/**
 * The actual "mobs attack wagons" behavior -- the user's own explicit, "non-negotiable" requirement
 * (see the approved plan's §6). Injected into every hostile mob's {@code targetSelector} by {@link
 * DamageableCartTargetingListener} at a deliberately low priority, so vanilla's own real targeting
 * goals (a nearby Player, an alerted neighbor, etc.) always win when something better is around --
 * this only ever engages when the mob genuinely has nothing else to go after.
 *
 * <p>Becoming a real {@code LivingEntity} (see {@code AbstractDamageableDrawnEntity}'s own doc) is
 * necessary but NOT sufficient for this on its own -- vanilla's hostile mobs' own targeting goals are
 * hardcoded to specific classes (Player, AbstractVillager, ...) at construction time, so a cart being
 * a valid {@code setTarget} argument doesn't by itself make anything choose to target one. This goal
 * is the other half that actually closes that gap.
 */
public final class AttackNearbyDamageableCartGoal extends Goal {

    private static final double RANGE_BLOCKS = 16.0;

    private final Mob mob;

    public AttackNearbyDamageableCartGoal(final Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return this.mob.getTarget() == null && findNearestCart() != null;
    }

    @Override
    public void start() {
        final LivingEntity cart = findNearestCart();
        if (cart != null) {
            this.mob.setTarget(cart);
        }
    }

    private LivingEntity findNearestCart() {
        final AABB box = this.mob.getBoundingBox().inflate(RANGE_BLOCKS);
        final List<AbstractDamageableDrawnEntity> candidates = this.mob.level()
                .getEntitiesOfClass(AbstractDamageableDrawnEntity.class, box, cart -> cart.getHealth() > 0.0F && cart.isAlive());
        LivingEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (final AbstractDamageableDrawnEntity candidate : candidates) {
            final double distSq = this.mob.distanceToSqr(candidate);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = candidate;
            }
        }
        return nearest;
    }
}
