package com.github.cerealklla.caryvan.interaction;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.caryvan.entity.WagonEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Real user request, 2026-10-30: "if the first horse in a wagon enters a portal, the entire wagon
 * should teleport too." A horse walking into a portal unridden crosses dimension via vanilla's own
 * fully generic, per-entity {@code Entity#handlePortal()} -- nothing Caryvan-specific ever reacted
 * to it, so {@code WagonEntity#tickLeadSlot}/{@code #tickTeamSlot} (which only ever re-resolve a
 * mount by searching THIS wagon's own current level) would simply stop finding it, silently
 * stranding the wagon and the other mount behind.
 *
 * <p><b>Event-driven, not tick-polled</b> -- an earlier version of this fix checked every tick
 * whether the lead mount had vanished from the wagon's own level, which is exactly the standing
 * "avoid tick-polling" rule this project has been corrected on before. Rebuilt on {@link
 * EntityJoinLevelEvent} instead -- the same "entity entered a level" hook {@code
 * summoning.AllayGoalInjectionListener} already relies on elsewhere in this suite -- confirmed
 * against the real decompiled {@code Entity#teleportCrossDimension}/{@code ServerLevel
 * #addDuringTeleport} that the brand-new entity instance a cross-dimension portal crossing creates
 * is added to the destination level through the ordinary add-entity path, which fires this event
 * generically for any entity, not just players. This only ever does real work in response to a
 * genuine occurrence (a tracked mount actually joining a new level) -- never a per-tick guess.
 *
 * <p>{@link WagonEntity#wagonOwning(UUID)} is the small UUID-keyed registry that lets this listener,
 * given only the mount entity that just joined, find which Wagon (if any) owns it -- maintained by
 * {@code WagonEntity} itself only at the existing points it already assigns/clears its own mount
 * fields, not a new poll of any kind.
 *
 * <p>Once the owning Wagon is found (searched across every loaded dimension, since it may still be
 * sitting in the OLD one -- a one-time, event-triggered lookup, not a repeated scan), the OTHER
 * mount (whichever one didn't just cross on its own) is teleported along too -- confirmed against
 * the real decompiled {@code Leashable}/{@code Entity#teleport} that a vanilla lead does NOT carry a
 * leashed mob across a dimension change by itself -- and finally the wagon itself, via {@code
 * Entity#teleport(TeleportTransition)}, generic for any {@code Entity} and confirmed to recursively
 * carry its own real passengers (the driver/passenger bench seats) along with it.
 */
public final class WagonMountPortalFollowListener {

    @SubscribeEvent
    public void onJoin(final EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel newLevel)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity mount) || !mount.isAlive()) {
            return;
        }
        final Optional<UUID> wagonId = WagonEntity.wagonOwning(mount.getUUID());
        if (wagonId.isEmpty()) {
            return;
        }

        WagonEntity wagon = null;
        for (final ServerLevel level : newLevel.getServer().getAllLevels()) {
            final Entity found = level.getEntity(wagonId.get());
            if (found instanceof WagonEntity foundWagon) {
                wagon = foundWagon;
                break;
            }
        }
        if (wagon == null || wagon.level() == newLevel) {
            // Not found at all (stale registry entry), or the wagon is already in the right
            // dimension (e.g. this mount just normally re-loaded with an already-settled wagon) --
            // nothing to do either way.
            return;
        }

        followWagonToMount(wagon, mount, newLevel);
    }

    private void followWagonToMount(final WagonEntity wagon, final LivingEntity mount, final ServerLevel destination) {
        final float destYaw = mount.getYRot();
        final Vec3 mountPos = mount.position();
        final float yawRad = (float) Math.toRadians(destYaw);
        // Behind the mount, opposite of WagonEntity#mountSpawnPosition's own forward vector.
        final double backX = Mth.sin(yawRad);
        final double backZ = -Mth.cos(yawRad);
        final Vec3 wagonTargetPos = mountPos.add(backX * wagon.getSpacing(), 0.0D, backZ * wagon.getSpacing());

        for (final Entity other : new Entity[]{wagon.getLeadMount(), wagon.getTeamMount()}) {
            if (other != null && other != mount && other.isAlive() && other.level() != destination) {
                other.teleport(new TeleportTransition(
                        destination, mountPos, Vec3.ZERO, destYaw, 0.0F, TeleportTransition.PLACE_PORTAL_TICKET));
            }
        }
        wagon.teleport(new TeleportTransition(
                destination, wagonTargetPos, Vec3.ZERO, destYaw, 0.0F, TeleportTransition.PLACE_PORTAL_TICKET));
    }
}
