package com.github.cerealklla.caryvan.interaction;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.caryvan.entity.WagonEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 * <p><b>Real follow-up report, same day</b>: "it needs to set the lead horse far enough from the
 * portal that you don't get stuck in it... all the horses end up in a pile with the wagon inside
 * the portal and nothing can move." Root cause, confirmed against the real decompiled {@code
 * NetherPortalBlock#createDimensionTransition}: vanilla deliberately lands a SOLO entity exiting a
 * portal literally inside the portal's own block plane (relying only on portal cooldown to prevent
 * an instant re-trigger, not any real distance from the frame) -- fine for one entity, since nothing
 * else is competing for that same tiny footprint. The first version of this fix placed the team
 * mount at that exact same position (guaranteed overlap) and the wagon only a fixed resting-distance
 * behind it -- both landing at or right next to the portal's own blocks, which is exactly the pile
 * described. Also confirmed the original fallback assumption wrong before relying on it: a portal
 * exit's rotation is only ever a 0/90-degree axis-relative twist from the entry side (`{@code
 * outputRotation = portalAxis == axis ? 0 : 90}`), not a guaranteed "facing away from the portal" --
 * so the mount's own post-teleport yaw is not a trustworthy clearance direction.
 *
 * <p>Fixed by reading the REAL portal geometry instead: {@link #findNearbyPortalAxis} scans the
 * block grid immediately around the mount's own landing position for the actual portal block it
 * just came out of, and the correct clearance direction is the horizontal axis PERPENDICULAR to the
 * portal's own {@code HORIZONTAL_AXIS} (the portal's plane spans along its own axis; an entity
 * passes through it along the other one). The lead mount is nudged a safe distance along that real
 * direction first (via a plain {@code teleportTo}, not a new portal transition), and the team
 * mount/wagon are then placed progressively closer to the portal but still well clear of it along
 * that SAME line -- not stacked on the lead at all. Normal towing chase physics
 * ({@code AbstractDamageableDrawnEntity#pulledTick}/{@code WagonEntity#syncTeamFollow}) pulls
 * everything back into its real resting formation over the next few ticks regardless of the exact
 * initial spacing used here, so precision isn't needed, only genuine separation. Falls back to the
 * mount's own facing (the original, less reliable heuristic) only if no real portal block is found
 * nearby at all -- e.g. the mount had already wandered off before this fired.
 *
 * <p>Once clear, the wagon itself is teleported via {@code Entity#teleport(TeleportTransition)},
 * generic for any {@code Entity} and confirmed to recursively carry its own real passengers (the
 * driver/passenger bench seats) along with it.
 */
public final class WagonMountPortalFollowListener {

    /** How far the lead mount is pushed clear of the portal along the real perpendicular axis. */
    private static final double LEAD_CLEARANCE_BLOCKS = 10.0D;
    /** How far behind the (now-cleared) lead the team mount lands -- mirrors {@code WagonEntity}'s own private {@code TEAM_FOLLOW_SPACING}. */
    private static final double TEAM_GAP_BLOCKS = 3.0D;
    private static final int PORTAL_SCAN_RADIUS = 3;

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
        final Vec3 landingPos = mount.position();
        final Vec3 clearDirection = clearanceDirection(destination, mount);

        final Vec3 leadPos = landingPos.add(clearDirection.scale(LEAD_CLEARANCE_BLOCKS));
        mount.teleportTo(leadPos.x, leadPos.y, leadPos.z);

        final Vec3 teamPos = landingPos.add(clearDirection.scale(LEAD_CLEARANCE_BLOCKS - TEAM_GAP_BLOCKS));
        final Vec3 wagonPos = landingPos.add(clearDirection.scale(LEAD_CLEARANCE_BLOCKS - TEAM_GAP_BLOCKS - wagon.getSpacing()));
        final float destYaw = mount.getYRot();

        for (final Entity other : new Entity[]{wagon.getLeadMount(), wagon.getTeamMount()}) {
            if (other != null && other != mount && other.isAlive() && other.level() != destination) {
                other.teleport(new TeleportTransition(
                        destination, teamPos, Vec3.ZERO, destYaw, 0.0F, TeleportTransition.PLACE_PORTAL_TICKET));
            }
        }
        wagon.teleport(new TeleportTransition(
                destination, wagonPos, Vec3.ZERO, destYaw, 0.0F, TeleportTransition.PLACE_PORTAL_TICKET));
    }

    /**
     * A real horizontal unit vector pointing away from the portal {@code mount} just exited --
     * perpendicular to the portal's own {@link BlockStateProperties#HORIZONTAL_AXIS} (the direction
     * an entity actually walks through it), not the mount's own post-teleport yaw (see this class's
     * own doc for why that's unreliable). Checks both perpendicular directions and prefers whichever
     * one isn't itself another portal block. Falls back to the mount's own facing if no real portal
     * block is found nearby at all.
     */
    private Vec3 clearanceDirection(final ServerLevel level, final LivingEntity mount) {
        final Direction.Axis portalAxis = findNearbyPortalAxis(level, mount.blockPosition());
        if (portalAxis == null) {
            final float yawRad = (float) Math.toRadians(mount.getYRot());
            return new Vec3(-Mth.sin(yawRad), 0.0D, Mth.cos(yawRad));
        }
        final Vec3 step = portalAxis == Direction.Axis.X ? new Vec3(0.0D, 0.0D, 1.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        final BlockPos stepPos = BlockPos.containing(mount.position().add(step.scale(LEAD_CLEARANCE_BLOCKS)));
        if (level.getBlockState(stepPos).getBlock() instanceof Portal) {
            return step.scale(-1.0D);
        }
        return step;
    }

    private Direction.Axis findNearbyPortalAxis(final ServerLevel level, final BlockPos center) {
        for (int dx = -PORTAL_SCAN_RADIUS; dx <= PORTAL_SCAN_RADIUS; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -PORTAL_SCAN_RADIUS; dz <= PORTAL_SCAN_RADIUS; dz++) {
                    final BlockState state = level.getBlockState(center.offset(dx, dy, dz));
                    if (state.getBlock() instanceof Portal && state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
                        return state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
                    }
                }
            }
        }
        return null;
    }
}
