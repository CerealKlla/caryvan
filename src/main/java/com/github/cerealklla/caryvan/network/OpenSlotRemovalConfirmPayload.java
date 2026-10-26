package com.github.cerealklla.caryvan.network;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.caryvan.CaryvanMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: "show the real 'Are you sure?' dialog before removing this Companion/Summon
 * Stone from its slot" -- real user spec, 2026-10-26: the 50%-destroy roll fired immediately on a
 * plain inventory-slot click while Broken, with no warning first, which only the GUI "Pick Up"
 * button had (see {@code WagonMenu#clicked}'s own doc for the real interception point this backs).
 * Mirrors {@code OpenCartPickUpConfirmPayload} exactly -- same reasoning for carrying both the cart
 * id and the slot index directly, rather than depending on the menu still being open by the time
 * the player clicks "Yes" (it will be, here, since no screen transition happens before that click,
 * but carrying the real ids keeps this consistent and robust regardless).
 */
public record OpenSlotRemovalConfirmPayload(int cartEntityId, int slotIndex) implements CustomPacketPayload {

    public static final Type<OpenSlotRemovalConfirmPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "open_slot_removal_confirm"));

    public static final StreamCodec<ByteBuf, OpenSlotRemovalConfirmPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenSlotRemovalConfirmPayload::cartEntityId,
            ByteBufCodecs.VAR_INT, OpenSlotRemovalConfirmPayload::slotIndex,
            OpenSlotRemovalConfirmPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
