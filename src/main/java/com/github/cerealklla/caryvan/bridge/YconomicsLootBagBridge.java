package com.github.cerealklla.caryvan.bridge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Lets a Wagon/Carriage's Pick Up action spill its cargo through Yconomics' real Loot Bag mechanic
 * instead of plain ground drops -- real user correction, 2026-10-26, after the first pass
 * substituted plain {@code Containers.dropContents}: Yconomics' own {@code
 * bag.LootBagListener#depositOrScatter(ServerLevel, Vec3, ItemStack)} was confirmed (by reading its
 * real source directly) to already be {@code public static} specifically for this exact kind of
 * cross-mod reuse -- its own doc comment says it was widened from {@code private} so {@code
 * CoinPurseListener} could route drops through it "instead of spawning a raw ItemEntity that the
 * rest of this class would never actually see or bag." A raw {@code ItemEntity} (what plain {@code
 * Containers.dropContents} spawns) is genuinely invisible to Yconomics' own passive-clustering scan
 * (which only ever watches items it's explicitly told about via {@code ItemTossEvent}/{@code
 * LivingDeathEvent}), so calling the real method directly -- not spawning a raw entity and hoping
 * Yconomics notices it -- is the only way this actually works. Separate class from any other mod's
 * own bridge, per this suite's established isolation convention (a general-purpose class must never
 * itself reference an optional dependency's type).
 */
public final class YconomicsLootBagBridge {

    private YconomicsLootBagBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("yconomics");
    }

    /** Deposits {@code stack} into (or creates) a nearby Loot Bag at {@code pos}, same clustering behavior as a real voluntary item drop. */
    public static void depositOrScatter(final ServerLevel level, final Vec3 pos, final ItemStack stack) {
        com.github.cerealklla.yconomics.bag.LootBagListener.depositOrScatter(level, pos, stack);
    }
}
