package com.github.cerealklla.caryvan.network;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.util.CaryvanWorld;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Client-to-server: toggles a flat movement-speed penalty on the player while they're pulling a
 * cart -- "sprint toggles a slow mode" from the reference mods' own design, rebuilt here as its own
 * dedicated key rather than intercepting the real sprint key (which would need a mixin; this suite
 * has exactly one, a deliberate narrow exception elsewhere -- see the root project's own CLAUDE.md --
 * not a pattern to reach for casually). Adapted from NiftyCarts' {@code ToggleSlowPayload} (MIT
 * licensed, see this repo's own CLAUDE.md), simplified to the player-only case (no vehicle-riding
 * puller exists yet).
 */
public record ToggleSlowPayload() implements CustomPacketPayload {

    public static final Identifier PULL_SLOWLY_MODIFIER_ID = Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "pull_slowly");
    private static final double SLOW_SPEED_MULTIPLIER = -0.6D;

    public static final Type<ToggleSlowPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "toggle_slow"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ToggleSlowPayload> STREAM_CODEC =
            StreamCodec.unit(new ToggleSlowPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || CaryvanWorld.get(level).getDrawnBy(player.getUUID()).isEmpty()) {
            return;
        }
        final AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        final AttributeModifier existing = speed.getModifier(PULL_SLOWLY_MODIFIER_ID);
        if (existing == null) {
            speed.addTransientModifier(new AttributeModifier(PULL_SLOWLY_MODIFIER_ID, SLOW_SPEED_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            speed.removeModifier(PULL_SLOWLY_MODIFIER_ID);
        }
    }
}
