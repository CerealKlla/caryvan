package com.github.cerealklla.caryvan.command;

import com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity;
import com.github.cerealklla.caryvan.entity.AbstractDrawnEntity;
import com.github.cerealklla.caryvan.entity.CarriageEntity;
import com.github.cerealklla.caryvan.entity.WagonEntity;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Real user request, 2026-10-30: "did you add a command i can call to see what the stats are for
 * the carriage i'm riding?" -- followed immediately by a concrete spec: "give every horse speed,
 * every inventory capacity, and explain the calculated speed" (prompted by a real live report --
 * "I'm riding a wagon which is empty... with 2 horses and it's barely trotting, nowhere near max
 * speed"). {@code /caryvan stats} reads whatever cart/wagon entity the calling player is currently
 * a passenger of and dumps {@link WagonEntity#computeSpeedBreakdown()}'s real formula terms (or the
 * simpler fixed-multiplier equivalent {@link CarriageEntity#tickSteering()} uses, which has no team-
 * mount/load factoring at all yet -- reported as-is, not silently reshaped to match Wagon's formula).
 * Hand Cart/Animal Cart ({@link AbstractDrawnEntity}) have no calculated max-speed concept at all --
 * their speed is simply whatever their single puller does each tick -- so those just get a plain
 * explanatory message instead of a breakdown that doesn't exist.
 */
public final class CaryvanCommands {

    private CaryvanCommands() {
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("caryvan")
                .then(Commands.literal("stats")
                        .executes(context -> runStats(context.getSource()))));
    }

    private static int runStats(final CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can run this."));
            return 0;
        }
        final Entity vehicle = player.getVehicle();
        if (vehicle instanceof WagonEntity wagon) {
            sendWagonStats(player, wagon);
        } else if (vehicle instanceof CarriageEntity carriage) {
            sendCarriageStats(player, carriage);
        } else if (vehicle instanceof AbstractDrawnEntity || vehicle instanceof AbstractDamageableDrawnEntity) {
            player.sendSystemMessage(Component.literal(
                    "This cart has no calculated Max Speed -- its speed is simply whatever is directly pulling it."));
        } else {
            player.sendSystemMessage(Component.literal("You aren't riding a Caryvan cart."));
        }
        return 1;
    }

    private static void sendWagonStats(final ServerPlayer player, final WagonEntity wagon) {
        final WagonEntity.SpeedBreakdown breakdown = wagon.computeSpeedBreakdown();

        send(player, "=== Wagon Stats ===", ChatFormatting.GOLD);
        send(player, "Horses:", ChatFormatting.YELLOW);
        if (breakdown.mounts().isEmpty()) {
            send(player, "  (none hitched)", ChatFormatting.GRAY);
        } else {
            for (final WagonEntity.SpeedBreakdown.MountSpeed mount : breakdown.mounts()) {
                send(player, String.format("  %s: %.4f blocks/tick (%.2f blocks/sec)",
                        mount.label(), mount.movementSpeed(), mount.movementSpeed() * 20.0D), ChatFormatting.WHITE);
            }
        }

        send(player, "Inventory capacity:", ChatFormatting.YELLOW);
        send(player, String.format("  Cargo: %d/%d slots filled",
                breakdown.filledCargoSlots(), breakdown.cargoSlotCount()), ChatFormatting.WHITE);
        send(player, String.format("  Riders' own inventories: %d/%d slots used (27 per seated player, armor/hotbar excluded)",
                breakdown.riderUsedSlots(), breakdown.riderCapacitySlots()), ChatFormatting.WHITE);

        send(player, "Calculated Max Speed:", ChatFormatting.YELLOW);
        final double avgSpeed = breakdown.mounts().isEmpty() ? 0.0D
                : breakdown.mounts().stream().mapToDouble(WagonEntity.SpeedBreakdown.MountSpeed::movementSpeed).average().orElse(0.0D);
        send(player, String.format("  Average horse speed = %.4f", avgSpeed), ChatFormatting.WHITE);
        send(player, String.format("  Base Max Speed = average x %.0f%% = %.4f blocks/tick",
                breakdown.baseSpeedFraction() * 100.0D, breakdown.baseMaxSpeed()), ChatFormatting.WHITE);
        final int totalCapacity = breakdown.cargoSlotCount() + breakdown.riderCapacitySlots();
        send(player, String.format("  Load = (%d cargo + %d rider items) / (%d cargo + %d rider slots) = %.1f%%",
                breakdown.filledCargoSlots(), breakdown.riderUsedSlots(), breakdown.cargoSlotCount(),
                breakdown.riderCapacitySlots(), breakdown.loadFraction() * 100.0D), ChatFormatting.WHITE);
        send(player, String.format("  Speed penalty = Load x %.0f%% max penalty = %.1f%% off Base Max Speed",
                breakdown.loadSpeedPenalty() * 100.0D, breakdown.loadFraction() * breakdown.loadSpeedPenalty() * 100.0D), ChatFormatting.WHITE);
        send(player, String.format("  Final Max Speed = %.4f blocks/tick (~%.2f blocks/sec)",
                breakdown.finalMaxSpeed(), breakdown.finalMaxSpeed() * 20.0D), ChatFormatting.GREEN);
        if (totalCapacity <= 0) {
            send(player, "  (no cargo slots or seated riders -- load counted as 0%)", ChatFormatting.GRAY);
        }
        // This Max Speed is fed directly into Entity#move() as a flat per-tick displacement (see
        // WagonEntity#tickSteering) -- NOT run through vanilla's own travel()/friction-driven
        // acceleration model the way a directly-ridden horse's real observed gallop speed is. A raw
        // Attributes.MOVEMENT_SPEED value is tuned around that friction model producing vanilla's real
        // felt speed, so using it as a direct move-vector length here reads as noticeably slower than
        // actually riding the same horse -- a real, structural reason "barely trotting" can still be
        // correct and expected even when this math is working exactly as designed.
        send(player, "Note: this value is applied as a flat per-tick move distance, bypassing vanilla's",
                ChatFormatting.GRAY);
        send(player, "own friction/acceleration model -- a directly-ridden horse 'feels' faster than its",
                ChatFormatting.GRAY);
        send(player, "raw Movement Speed attribute alone would suggest here.", ChatFormatting.GRAY);
    }

    private static void sendCarriageStats(final ServerPlayer player, final CarriageEntity carriage) {
        send(player, "=== Carriage Stats ===", ChatFormatting.GOLD);
        send(player, "Horses:", ChatFormatting.YELLOW);
        describeMount(player, "Lead Horse", carriage.getLeadMount());
        describeMount(player, "Team Horse (left)", carriage.getTeamLeftMount());
        describeMount(player, "Team Horse (right)", carriage.getTeamRightMount());

        int filled = 0;
        final int total = carriage.getContainerSize();
        for (int i = 0; i < total; i++) {
            if (!carriage.getItem(i).isEmpty()) {
                filled++;
            }
        }
        send(player, "Inventory capacity:", ChatFormatting.YELLOW);
        send(player, String.format("  All slots (mounts/companions/cargo combined): %d/%d filled", filled, total), ChatFormatting.WHITE);

        send(player, "Calculated Max Speed:", ChatFormatting.YELLOW);
        if (carriage.getLeadMount() instanceof LivingEntity livingLead) {
            final double leadSpeed = livingLead.getAttributeValue(Attributes.MOVEMENT_SPEED);
            send(player, String.format("  Lead Horse speed x 2.0 = %.4f blocks/tick (~%.2f blocks/sec)",
                    leadSpeed * 2.0D, leadSpeed * 40.0D), ChatFormatting.GREEN);
            send(player, "  (Team horses and cargo load do NOT currently affect this -- unlike Wagon's formula)",
                    ChatFormatting.GRAY);
        } else {
            send(player, "  No lead horse hitched -- falls back to a fixed 0.2 blocks/tick.", ChatFormatting.WHITE);
        }
    }

    private static void describeMount(final ServerPlayer player, final String label, final Entity mount) {
        if (mount instanceof LivingEntity living && living.isAlive()) {
            final double speed = living.getAttributeValue(Attributes.MOVEMENT_SPEED);
            send(player, String.format("  %s: %.4f blocks/tick (%.2f blocks/sec)", label, speed, speed * 20.0D), ChatFormatting.WHITE);
        } else {
            send(player, "  " + label + ": (none hitched)", ChatFormatting.GRAY);
        }
    }

    private static void send(final ServerPlayer player, final String text, final ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }
}
