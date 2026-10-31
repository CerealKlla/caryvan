package com.github.cerealklla.caryvan.interaction;

import java.util.EnumSet;
import java.util.List;

import com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
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

    /**
     * Real report, 2026-10-30: "zombie pigmen [...] automatically target wagons" -- same root cause,
     * and the same fix, as {@code settlemynts.guardhouse.GuardSightGoal#isActivelyHostile} (which
     * stopped Guards unprovoked-attacking a calm Zombified Piglin): {@code ZombifiedPiglin implements
     * NeutralMob} and is only ever hostile once something has actually provoked it ({@code isAngry()}
     * -- confirmed against the same decompiled source that fix already cited), but this goal's plain
     * {@code instanceof Enemy} injection in {@link DamageableCartTargetingListener} treated every
     * hostile mob, Zombified Piglin included, as unconditionally willing to attack a Wagon/Carriage on
     * sight. A {@link NeutralMob} candidate now only uses this goal once it's already {@link
     * NeutralMob#isAngry() angry} -- every other {@link net.minecraft.world.entity.monster.Enemy}
     * (Zombie, Skeleton, Phantom, Enderman, etc., none of which implement {@link NeutralMob}) is
     * unaffected.
     */
    @Override
    public boolean canUse() {
        if (this.mob instanceof NeutralMob neutral && !neutral.isAngry()) {
            return false;
        }
        return this.mob.getTarget() == null && findNearestCart() != null;
    }

    /**
     * Real live report, 2026-10-29: "enemy AI continue to target a wagon which has died." Root
     * cause: {@link AbstractDamageableDrawnEntity#isAlive()} is deliberately overridden to stay
     * {@code true} while Broken (0 HP) -- a Broken cart is never removed until Pick Up, by design,
     * so vanilla's own usual "the target died, drop it" cleanup never fires; and this goal itself
     * never revisited an already-set target at all ({@link #canUse()} only re-picks once {@code
     * getTarget()} is already {@code null}). The initial pick in {@link #findNearestCart()} already
     * excludes a Broken cart correctly ({@code getHealth() > 0.0F}) -- the real gap was a cart that
     * breaks mid-fight never getting dropped afterward. Clearing the target here (not just returning
     * {@code false}) matters -- otherwise the mob would stay frozen rather than going back to {@link
     * #canUse()} and finding something else to do.
     */
    @Override
    public boolean canContinueToUse() {
        if (this.mob.getTarget() instanceof AbstractDamageableDrawnEntity cart && cart.isBroken()) {
            this.mob.setTarget(null);
            return false;
        }
        return this.mob.getTarget() != null;
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
                .getEntitiesOfClass(AbstractDamageableDrawnEntity.class, box, cart -> !cart.isBroken() && cart.isAlive());
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
