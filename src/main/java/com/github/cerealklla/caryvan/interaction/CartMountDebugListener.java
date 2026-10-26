package com.github.cerealklla.caryvan.interaction;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.registration.ModAttachments;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Temporary diagnostic logging, 2026-10-25 -- real user report: "the horses were immortal for some
 * reason... I punched it many times and it did not flash/make a sound/take damage in any visible
 * way," then "earlier they WERE taking damage normally" (a real regression, not stale captured
 * NBT). {@code entity.WagonEntity#tickLeadSlot}/{@code #tickTeamSlot} already force
 * {@code Entity#setInvulnerable(false)} on these mounts every tick as a defensive fix, but the real
 * mechanism was never confirmed -- this logs the two earliest real moments an attack on a tagged
 * cart mount could be lost, so a reproduction attempt actually tells us something concrete instead
 * of just "it didn't work":
 * <ul>
 *   <li>{@link AttackEntityEvent} -- fires inside {@code Player#attack(Entity)} itself, before
 *   anything else. If this never logs at all while punching a cart mount, the attack action isn't
 *   even reaching the entity server-side -- a client-side targeting/hitbox problem, not a damage
 *   pipeline one (a real candidate given {@code WagonEntity#tickSteering()}/{@code
 *   #syncTeamFollow()} now move these mounts via direct position/rotation field-mutation every
 *   tick, a new pattern as of this session).</li>
 *   <li>{@link LivingIncomingDamageEvent} -- fires inside {@code LivingEntity#hurt(DamageSource,
 *   float)}, but (per NeoForge's own real doc comment on the event) only AFTER vanilla's own
 *   invulnerability check already passed. If {@link AttackEntityEvent} logs but this one never
 *   does, the entity really is invulnerable at that moment (confirming the original theory); if
 *   both log but health doesn't actually drop, the cause is somewhere else entirely (a reduction
 *   modifier, a cancelled event further down the chain).</li>
 * </ul>
 * Delete this class (and its registration in {@code CaryvanMod}) once the real mechanism is
 * confirmed and fixed for good -- it's pure logging, no behavior change.
 */
public final class CartMountDebugListener {

    @SubscribeEvent
    public void onAttack(final AttackEntityEvent event) {
        if (!isTaggedCartMount(event.getTarget())) {
            return;
        }
        CaryvanMod.LOGGER.info(
                "[cart-mount-debug] AttackEntityEvent on {} (uuid={}) by {} -- isCancelled={}, invulnerable={}, health={}/{}",
                event.getTarget().getType(), event.getTarget().getUUID(), event.getEntity().getName().getString(),
                event.isCanceled(), event.getTarget().isInvulnerable(),
                event.getTarget() instanceof LivingEntity living ? living.getHealth() : -1,
                event.getTarget() instanceof LivingEntity living ? living.getMaxHealth() : -1);
    }

    @SubscribeEvent
    public void onIncomingDamage(final LivingIncomingDamageEvent event) {
        final LivingEntity entity = event.getEntity();
        if (!isTaggedCartMount(entity)) {
            return;
        }
        CaryvanMod.LOGGER.info(
                "[cart-mount-debug] LivingIncomingDamageEvent on {} (uuid={}) -- amount={}, source={}, isCancelled={}, invulnerable={}, health={}/{}",
                entity.getType(), entity.getUUID(), event.getAmount(), event.getSource().getMsgId(),
                event.isCanceled(), entity.isInvulnerable(), entity.getHealth(), entity.getMaxHealth());
    }

    private static boolean isTaggedCartMount(final net.minecraft.world.entity.Entity entity) {
        return entity.getExistingData(ModAttachments.CART_TEAM_MOUNT).isPresent();
    }
}
