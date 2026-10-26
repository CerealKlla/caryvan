package com.github.cerealklla.caryvan.bridge;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Lets an {@link com.github.cerealklla.caryvan.entity.AnimalCartEntity}'s mount slot carry and use a
 * bound Summoning Stone -- same summon-once/re-summon-after-cooldown upkeep Settlemynts' own Guards
 * already use for their companions (see {@code com.github.cerealklla.lyfe.summoning.GuardCompanionSupport}'s
 * own doc for the full behavior this wraps). Separate class from Settlemynts' own identically-shaped
 * {@code bridge.LyfeSummoningBridge} -- each mod's optional dependency on Lyfe must go through its
 * own dedicated bridge, never a shared one, per this suite's established isolation convention (a
 * general-purpose class must never itself reference an optional dependency's type).
 */
public final class LyfeSummoningBridge {

    private LyfeSummoningBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("lyfe");
    }

    /** Specifically a Summoning Stone (a bound mount) -- not the broader Allay Bottle set, which never belongs in a mount slot. */
    public static boolean isMountSummoningStone(ItemStack stack) {
        return com.github.cerealklla.lyfe.api.Lyfe.isMountSummoningStone(stack);
    }

    /**
     * Specifically an Allay in a Bottle -- not the broader Summoning Stone set, which never belongs
     * in a Companion slot. New 2026-10-26, for the real Companion slots design-document.md §8 point
     * 6 calls for (the mirror-image predicate of {@link #isMountSummoningStone}).
     */
    public static boolean isAllayBottleItem(ItemStack stack) {
        return com.github.cerealklla.lyfe.api.Lyfe.isAllayBottleItem(stack);
    }

    /** Call periodically while {@code stack} sits in a mount/companion slot -- summons/re-summons/tracks cooldown, mutating {@code stack}'s own data component in place. Already generic over both Summoning Stones and Allay Bottles (see {@code GuardCompanionSupport#tick}'s own doc), so this one method covers both slot kinds. */
    public static void tickCompanion(ServerLevel level, ItemStack stack, UUID ownerId, double x, double y, double z, float yRot) {
        com.github.cerealklla.lyfe.api.Lyfe.tickGuardCompanion(level, stack, ownerId, x, y, z, yRot);
    }

    /** The UUID of {@code stack}'s currently-summoned mount, if any is out right now -- still needs resolving against a real level to get a live entity reference. */
    public static java.util.Optional<UUID> getSummonedMountId(ItemStack stack) {
        return com.github.cerealklla.lyfe.api.Lyfe.getSummonedMountId(stack);
    }

    /** The UUID of {@code stack}'s currently-summoned Allay, if any is out right now -- mirror-image of {@link #getSummonedMountId}. */
    public static java.util.Optional<UUID> getSummonedAllayId(ItemStack stack) {
        return com.github.cerealklla.lyfe.api.Lyfe.getSummonedAllayId(stack);
    }
}
