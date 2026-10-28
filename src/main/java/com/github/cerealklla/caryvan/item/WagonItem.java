package com.github.cerealklla.caryvan.item;

import com.github.cerealklla.caryvan.registration.ModDataComponents;
import com.github.cerealklla.caryvan.registration.ModEntities;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Places a {@link com.github.cerealklla.caryvan.entity.WagonEntity} -- same raycast placement
 * copied from {@link HandCartItem}/{@link AnimalCartItem}. Same per-{@link WoodType} variant
 * handling as those two, added 2026-10-26 (explicit user request, "make sure all the wood variants
 * exist for the wagon") -- Wagon was the one cart type still a single plain item up to that point.
 */
public final class WagonItem extends Item {

    private final WoodType woodType;

    public WagonItem(final Properties properties, final WoodType woodType) {
        super(properties);
        this.woodType = woodType;
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.CONSUME;
        }
        final ItemStack stack = player.getItemInHand(hand);
        final BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResult.PASS;
        }

        final Vec3 lookVec = player.getLookAngle();
        final List<Entity> nearby = level.getEntities(player, player.getBoundingBox().expandTowards(lookVec.scale(5.0D)).inflate(1.0D),
                EntitySelector.NO_SPECTATORS.and(entity -> entity.canBeCollidedWith(player)));
        if (!nearby.isEmpty()) {
            final Vec3 eyePos = player.getEyePosition(1.0F);
            for (final Entity entity : nearby) {
                final AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
                if (box.contains(eyePos)) {
                    return InteractionResult.PASS;
                }
            }
        }

        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }

        final Entity cart = ModEntities.WAGON.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (cart == null) {
            return InteractionResult.PASS;
        }
        cart.snapTo(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, (player.getYRot() + 180) % 360, 0.0F);
        if (!level.noCollision(cart, cart.getBoundingBox().inflate(0.1F, -0.1F, 0.1F))) {
            return InteractionResult.FAIL;
        }

        final var damageable = (com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity) cart;
        damageable.setWoodType(this.woodType);
        // Real per-wheel species, from a Pick Up that captured genuinely mixed wheels (see
        // ModDataComponents#WHEEL_WOOD_TYPES' own doc, 2026-10-28) -- checked FIRST since it's the
        // more specific, more complete data when present. Falls back to the original single shared
        // WHEEL_WOOD_TYPE (Wainwright Station assembly's own one-species-for-all-4 choice, or a
        // plain wagon item with neither component -- e.g. straight from creative) otherwise, exactly
        // as before this fix.
        final java.util.List<String> perWheelSpecies = stack.get(ModDataComponents.WHEEL_WOOD_TYPES.get());
        if (perWheelSpecies != null && perWheelSpecies.size() == 4) {
            for (int i = 0; i < 4; i++) {
                final String name = perWheelSpecies.get(i);
                final WoodType resolved = WoodType.values().filter(type -> type.name().equals(name)).findFirst().orElse(this.woodType);
                damageable.setWheelWoodType(i, resolved);
            }
        } else {
            // Wainwright Station wood-species mixing, 2026-10-28 -- a Wagon Body assembled with a
            // chosen wheel species carries it via ModDataComponents.WHEEL_WOOD_TYPE; a plain wagon
            // item (no component present, e.g. picked straight from creative or the old
            // single-species recipe) just uses its own body species for the wheels too, matching
            // pre-Wainwright behavior.
            final WoodType wheelWoodType = stack.get(ModDataComponents.WHEEL_WOOD_TYPE.get()) == null
                    ? this.woodType
                    : WoodType.values()
                            .filter(type -> type.name().equals(stack.get(ModDataComponents.WHEEL_WOOD_TYPE.get())))
                            .findFirst()
                            .orElse(this.woodType);
            for (int i = 0; i < 4; i++) {
                damageable.setWheelWoodType(i, wheelWoodType);
            }
        }
        // Owner capture, 2026-10-26 (health system work) -- Pick Up/Repair are owner-only actions.
        damageable.setOwner(player);
        level.addFreshEntity(cart);
        level.playSound(null, cart.getX(), cart.getY(), cart.getZ(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.75F, 0.8F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
