package com.github.cerealklla.caryvan.wainwright.preview;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.caryvan.CaryvanMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: the raw PNG bytes answering a {@link RequestWainwrightPreviewPayload}. {@code
 * MAX_IMAGE_BYTES} mirrors Blueprynts' own {@code BlueprintPreviewImagePayload} cap (2 MB) -- a
 * generous sanity ceiling, not a tuned limit; these images are tiny (512x384 flat-shaded boxes).
 */
public record WainwrightPreviewImagePayload(String key, byte[] pngBytes) implements CustomPacketPayload {

    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    public static final Type<WainwrightPreviewImagePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "wainwright_preview_image"));

    public static final StreamCodec<ByteBuf, WainwrightPreviewImagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WainwrightPreviewImagePayload::key,
            ByteBufCodecs.byteArray(MAX_IMAGE_BYTES), WainwrightPreviewImagePayload::pngBytes,
            WainwrightPreviewImagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
