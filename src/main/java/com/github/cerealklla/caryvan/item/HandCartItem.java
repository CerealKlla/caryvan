package com.github.cerealklla.caryvan.item;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Places a {@link com.github.cerealklla.caryvan.entity.HandCartEntity} where the player is looking
 * (a raycast against both blocks and entities, so you can't place one through another entity's own
 * hitbox) -- adapted from NiftyCarts' {@code CartItem} (MIT licensed, see this repo's own CLAUDE.md).
 */
public final class HandCartItem extends Item {

    public HandCartItem(final Properties properties) {
        super(properties);
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

        final Entity cart = ModEntities.HAND_CART.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (cart == null) {
            return InteractionResult.PASS;
        }
        cart.snapTo(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, (player.getYRot() + 180) % 360, 0.0F);
        if (!level.noCollision(cart, cart.getBoundingBox().inflate(0.1F, -0.1F, 0.1F))) {
            return InteractionResult.FAIL;
        }

        level.addFreshEntity(cart);
        // Reuses a vanilla sound -- no real custom audio authored yet, same "functional first,
        // no art exists" precedent used elsewhere in this suite (see HandCartRenderer's own doc).
        level.playSound(null, cart.getX(), cart.getY(), cart.getZ(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.75F, 0.8F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
