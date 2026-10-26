package com.github.cerealklla.caryvan.network;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.caryvan.CaryvanMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server: the player clicked "Yes" on the dialog opened by {@link
 * OpenSlotRemovalConfirmPayload}. Performs the actual removal directly server-side (the original
 * slot click that triggered the dialog was cancelled outright, see {@code WagonMenu#clicked}'s own
 * doc -- this payload is what actually does the work, not a retry of that click).
 */
public record ConfirmSlotRemovalPayload(int cartEntityId, int slotIndex) implements CustomPacketPayload {

    public static final Type<ConfirmSlotRemovalPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "confirm_slot_removal"));

    public static final StreamCodec<ByteBuf, ConfirmSlotRemovalPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ConfirmSlotRemovalPayload::cartEntityId,
            ByteBufCodecs.VAR_INT, ConfirmSlotRemovalPayload::slotIndex,
            ConfirmSlotRemovalPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final net.minecraft.server.level.ServerPlayer player, final int cartEntityId, final int slotIndex) {
        if (player.level().getEntity(cartEntityId) instanceof com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity cart) {
            cart.confirmCompanionSlotRemoval(player, slotIndex);
        }
    }
}
