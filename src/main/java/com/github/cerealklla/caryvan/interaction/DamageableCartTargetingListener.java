package com.github.cerealklla.caryvan.interaction;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Injects {@link AttackNearbyDamageableCartGoal} into every hostile mob's {@code targetSelector} on
 * join -- the real, concrete mechanism behind the approved plan's §6. {@code Mob#targetSelector} is
 * {@code public final} (confirmed against the real decompiled source -- unlike {@code #goalSelector},
 * which is what forces {@code lyfe.summoning.AllayGoalInjectionListener}'s own reflection-based
 * approach for goal injection elsewhere in this suite), so no reflection is needed here at all.
 */
public final class DamageableCartTargetingListener {

    @SubscribeEvent
    public void onJoin(final EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Mob mob && mob instanceof Enemy) {
            // Low priority (10) -- vanilla's own real targeting goals, added at construction time at
            // low numbers, always win when something better is around; this only engages once the
            // mob genuinely has no other target.
            mob.targetSelector.addGoal(10, new AttackNearbyDamageableCartGoal(mob));
        }
    }
}
