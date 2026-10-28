package com.github.cerealklla.caryvan.wainwright.preview;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.caryvan.CaryvanMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server: "send me the preview image for this Body-tab combo." Mirrors Blueprynts'
 * {@code RequestBlueprintPreviewPayload} shape exactly, minus the mtime-freshness concept that
 * payload needs and this one doesn't -- see {@link WainwrightPreviewStorage}'s own doc for why. The
 * client only sends this once per combo per screen session (deduped client-side, same as
 * Blueprynts' own {@code lastRequestedKey} pattern).
 *
 * <p>Field named {@code cartType}, not {@code type} -- a record component named {@code type} would
 * generate an accessor {@code type()} returning {@code String}, colliding with {@link
 * CustomPacketPayload}'s own required {@code type()} method (must return {@code Type<...>}); real
 * compile error caught directly, not guessed.
 */
public record RequestWainwrightPreviewPayload(String cartType, String bodySpecies, String wheelSpecies) implements CustomPacketPayload {

    public static final Type<RequestWainwrightPreviewPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "request_wainwright_preview"));

    public static final StreamCodec<ByteBuf, RequestWainwrightPreviewPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestWainwrightPreviewPayload::cartType,
            ByteBufCodecs.STRING_UTF8, RequestWainwrightPreviewPayload::bodySpecies,
            ByteBufCodecs.STRING_UTF8, RequestWainwrightPreviewPayload::wheelSpecies,
            RequestWainwrightPreviewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
