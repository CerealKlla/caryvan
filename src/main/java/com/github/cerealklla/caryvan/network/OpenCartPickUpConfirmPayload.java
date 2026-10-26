package com.github.cerealklla.caryvan.network;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.caryvan.CaryvanMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: "show the real Pick Up confirmation dialog for this specific cart." Sent by
 * {@code entity.AbstractDamageableDrawnEntity#requestPickUpConfirm} from either real trigger path --
 * the owner sneak+right-clicking with an empty hand (server-side {@code interact()}), or clicking
 * "Pick Up" in {@code WagonScreen}/{@code CarriageScreen} (server-side {@code
 * AbstractContainerMenu#clickMenuButton}) -- both converge on this single payload so there's exactly
 * one real client-side confirm-dialog-opening path to maintain. Carrying the entity id here (rather
 * than relying on the GUI case's own currently-open menu) is what lets the actual "Yes" confirmation
 * ({@code ConfirmCartPickUpPayload}) work correctly even after the confirm dialog's own {@code
 * Minecraft#setScreen} call has already closed the cargo menu out from under it -- confirmed against
 * the real decompiled {@code Minecraft#setScreen}/{@code AbstractContainerScreen#removed} that ANY
 * screen transition (even to a different non-null screen) tears the open container down, so a GUI
 * button flow that tried to keep relying on the menu's own {@code containerId} after that point would
 * silently fail server-side.
 */
public record OpenCartPickUpConfirmPayload(int cartEntityId, boolean broken) implements CustomPacketPayload {

    public static final Type<OpenCartPickUpConfirmPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "open_cart_pick_up_confirm"));

    public static final StreamCodec<ByteBuf, OpenCartPickUpConfirmPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenCartPickUpConfirmPayload::cartEntityId,
            ByteBufCodecs.BOOL, OpenCartPickUpConfirmPayload::broken,
            OpenCartPickUpConfirmPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
