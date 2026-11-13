package com.github.cerealklla.caryvan.interaction;

import com.github.cerealklla.caryvan.entity.WagonEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Real user request, 2026-10-30: "if the first horse in a wagon enters a portal, the entire wagon
 * should teleport too." Originally built around a horse crossing on its own via vanilla's generic
 * {@code Entity#handlePortal()}, since nothing Caryvan-specific reacted to it and {@code
 * WagonEntity#tickLeadSlot}/{@code #tickTeamSlot} (which only ever re-resolve a mount by searching
 * THIS wagon's own current level) would simply stop finding it, silently stranding the wagon and
 * the other mount behind.
 *
 * <p><b>Rebuilt 2026-11-13</b> -- real follow-up instruction: "Caryvan needs to also be blocked.
 * Only a player right clicking a portal can go through them now. The driver of the caravan would
 * have to right click it while riding the wagon." Nuclyus's new {@code
 * portal.VanillaPortalAutoTeleportListener} already blocks a lead/team mount from crossing a portal
 * entirely on its own (see its own CLAUDE.md) -- correct and intended, since an unridden mount
 * wandering into a portal and crossing independently is exactly the "nothing tracking it" class of
 * bug that feature exists to close. The only way a Caryvan rig can still cross at all is a player's
 * deliberate right-click, and since {@code interaction.CartMountInteractionListener} already
 * refuses to let a player ride a lead/team mount directly ({@code WagonEntity#prepareCartMount}'s
 * own doc), the driver can only ever be riding the **wagon itself** (its bench seats) -- so the
 * wagon is now always the entity that crosses first (via {@code nuclyus.api.Nuclyus
 * #attemptPortalCrossing}, carrying its driver passenger recursively, same as any other vanilla
 * cross-dimension teleport), with the lead/team mounts needing to follow it instead of the other
 * way around. This class's trigger flipped to match: {@link EntityJoinLevelEvent} for the
 * **wagon** joining a new level (not a mount), pulling {@code WagonEntity#getLeadMount}/
 * {@code #getTeamMount} along to it. {@code WagonEntity#wagonOwning}/the mount-to-wagon registry
 * this class used to depend on is unused here now, but is left in place -- {@code
 * WagonEntity#registerMount}/{@code #unregisterMount} still run at the same points they always did
 * (mount summon/despawn bookkeeping), independent of portal-crossing.
 *
 * <p>Still event-driven, not tick-polled -- the same "entity entered a level" hook {@code
 * summoning.AllayGoalInjectionListener} already relies on elsewhere in this suite, confirmed fired
 * generically for the brand-new entity instance any cross-dimension teleport creates in the
 * destination level (vanilla auto-portal or, now, Nuclyus's manual crossing -- both end up inside
 * the same real {@code Entity#teleportCrossDimension}/{@code ServerLevel#addDuringTeleport} call
 * chain).
 *
 * <p><b>Clearance logic unchanged in substance</b> (see the original 2026-10-30 "pile" bug fix) --
 * vanilla deliberately lands a solo entity exiting a portal literally inside the portal's own block
 * plane, relying only on portal cooldown to prevent an instant re-trigger, not any real distance
 * from the frame -- fine for the one entity that actually crossed (the wagon), not for a mount
 * landing in the exact same spot a moment later. {@link #findNearbyPortalAxis} scans the block grid
 * around the wagon's own landing position for the real portal block it just exited, and the correct
 * clearance direction is the horizontal axis PERPENDICULAR to the portal's own {@code
 * HORIZONTAL_AXIS} (the portal's plane spans along its own axis; an entity passes through it along
 * the other one) -- a portal exit's rotation is only ever a 0/90-degree axis-relative twist from the
 * entry side, never a guaranteed "facing away from the portal," so the wagon's own post-teleport yaw
 * is not a trustworthy clearance direction on its own, only this real geometry is. The lead mount
 * lands furthest out along that direction, the team mount closer in (between the lead and the
 * wagon) -- normal towing chase physics ({@code WagonEntity#syncTeamFollow}/{@code
 * AbstractDrawnEntity#pulledTick}) pulls everything back into its real resting formation over the
 * next few ticks regardless of the exact initial spacing used here, so precision isn't needed, only
 * genuine separation. Falls back to the wagon's own facing only if no real portal block is found
 * nearby at all (e.g. it had already wandered off, or a non-portal teleport, before this fired).
 *
 * <p>Every direct {@code .teleport(TeleportTransition)} call below is wrapped in {@code
 * Nuclyus#runPortalCrossingBypassed} -- the mounts routinely land on/near the same portal plane the
 * wagon just crossed through, and this is a deliberate, mod-initiated crossing, not vanilla's own
 * (now-disabled) detection, so it must not be cancelled just because of where it happens to land.
 */
public final class WagonMountPortalFollowListener {

    /** How far the lead mount is pushed clear of the portal along the real perpendicular axis. */
    private static final double LEAD_CLEARANCE_BLOCKS = 10.0D;
    /** How far out the team mount lands -- between the wagon (already placed) and the lead. */
    private static final double TEAM_GAP_BLOCKS = 3.0D;
    private static final int PORTAL_SCAN_RADIUS = 3;

    @SubscribeEvent
    public void onJoin(final EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel newLevel)) {
            return;
        }
        if (!(event.getEntity() instanceof WagonEntity wagon) || !wagon.isAlive()) {
            return;
        }
        followMountsToWagon(wagon, newLevel);
    }

    private void followMountsToWagon(final WagonEntity wagon, final ServerLevel destination) {
        final Vec3 landingPos = wagon.position();
        final Vec3 clearDirection = clearanceDirection(destination, wagon.blockPosition(), wagon.getYRot());
        final float destYaw = wagon.getYRot();

        final Vec3 teamPos = landingPos.add(clearDirection.scale(TEAM_GAP_BLOCKS));
        final Vec3 leadPos = landingPos.add(clearDirection.scale(LEAD_CLEARANCE_BLOCKS));

        com.github.cerealklla.nuclyus.api.Nuclyus.runPortalCrossingBypassed(() -> {
            final Entity lead = wagon.getLeadMount();
            if (lead != null && lead.isAlive() && lead.level() != destination) {
                lead.teleport(new TeleportTransition(
                        destination, leadPos, Vec3.ZERO, destYaw, 0.0F, TeleportTransition.PLACE_PORTAL_TICKET));
            }
            final Entity team = wagon.getTeamMount();
            if (team != null && team.isAlive() && team.level() != destination) {
                team.teleport(new TeleportTransition(
                        destination, teamPos, Vec3.ZERO, destYaw, 0.0F, TeleportTransition.PLACE_PORTAL_TICKET));
            }
        });
    }

    /**
     * A real horizontal unit vector pointing away from the portal the wagon just exited --
     * perpendicular to the portal's own {@link BlockStateProperties#HORIZONTAL_AXIS} (the direction
     * an entity actually walks through it), not the wagon's own post-teleport yaw (see this class's
     * own doc for why that's unreliable). Checks both perpendicular directions and prefers whichever
     * one isn't itself another portal block. Falls back to {@code fallbackYawDegrees} if no real
     * portal block is found nearby at all.
     */
    private Vec3 clearanceDirection(final ServerLevel level, final BlockPos pos, final float fallbackYawDegrees) {
        final Direction.Axis portalAxis = findNearbyPortalAxis(level, pos);
        if (portalAxis == null) {
            final float yawRad = (float) Math.toRadians(fallbackYawDegrees);
            return new Vec3(-Mth.sin(yawRad), 0.0D, Mth.cos(yawRad));
        }
        final Vec3 step = portalAxis == Direction.Axis.X ? new Vec3(0.0D, 0.0D, 1.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        final BlockPos stepPos = BlockPos.containing(Vec3.atCenterOf(pos).add(step.scale(LEAD_CLEARANCE_BLOCKS)));
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
