package com.github.cerealklla.caryvan.network;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.caryvan.CaryvanMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server: the player clicked "Yes" on the real {@code ConfirmActionScreen} opened by
 * {@code CaryvanModClient} in response to {@code OpenCartPickUpConfirmPayload} -- sent for either
 * real trigger path (sneak+right-click with an empty hand, or the GUI "Pick Up" button; see that
 * payload's own doc for why both converge on one clientbound payload). Carries the cart's real
 * network entity id so the server can resolve it directly ({@code ServerLevel#getEntity(int)})
 * without needing a live world scan, and without depending on any menu still being open.
 */
public record ConfirmCartPickUpPayload(int cartEntityId) implements CustomPacketPayload {

    public static final Type<ConfirmCartPickUpPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "confirm_cart_pick_up"));

    public static final StreamCodec<ByteBuf, ConfirmCartPickUpPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ConfirmCartPickUpPayload::cartEntityId,
            ConfirmCartPickUpPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final net.minecraft.server.level.ServerPlayer player, final int cartEntityId) {
        if (player.level().getEntity(cartEntityId) instanceof com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity cart) {
            cart.executePickUp(player);
        }
    }
}
