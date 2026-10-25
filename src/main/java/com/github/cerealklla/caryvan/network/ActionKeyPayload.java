package com.github.cerealklla.caryvan.network;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.entity.AbstractDrawnEntity;
import com.github.cerealklla.caryvan.util.CaryvanWorld;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Comparator;
import java.util.Optional;

/**
 * Client-to-server: the hitch/unhitch action key. A toggle -- if the player is already pulling a
 * cart, this detaches it; otherwise it attaches to the nearest unattached {@link AbstractDrawnEntity}
 * within 2 blocks. Adapted from NiftyCarts' {@code ActionKeyPayload} (MIT licensed, see this repo's
 * own CLAUDE.md), simplified: no vehicle-riding case yet (Hand Cart never rides, see Phase 1's own
 * scope notes), and no adventure-mode interaction gate (no such config exists here yet).
 */
public record ActionKeyPayload() implements CustomPacketPayload {

    public static final Type<ActionKeyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "action_key"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ActionKeyPayload> STREAM_CODEC =
            StreamCodec.unit(new ActionKeyPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        final Optional<AbstractDrawnEntity> currentlyPulling = CaryvanWorld.get(level).getDrawnBy(player.getUUID());
        if (currentlyPulling.isPresent()) {
            currentlyPulling.get().setPulling(null);
            return;
        }
        final Entity puller = player;
        level.getEntitiesOfClass(AbstractDrawnEntity.class, puller.getBoundingBox().inflate(2.0D), cart -> cart.getPulling() == null)
                .stream()
                .min(Comparator.comparingDouble(puller::distanceToSqr))
                .ifPresent(cart -> cart.setPulling(puller));
    }
}
