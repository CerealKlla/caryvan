package com.github.cerealklla.caryvan.wainwright.preview;

import net.neoforged.bus.api.Event;

/**
 * Posted by the common {@code CaryvanMod#registerPayloads} handler the instant a {@link
 * WainwrightPreviewImagePayload} arrives -- same bridge pattern {@code
 * network.OpenCartPickUpConfirmReceivedEvent} already established (see its own doc for the full
 * reasoning): a {@code playToClient} handler must be registered in the COMMON class so a dedicated
 * server also knows the channel exists, but a common-class handler can never directly touch
 * client-only types ({@code Minecraft}/{@code NativeImage}/{@code DynamicTexture}) -- so it only
 * posts this plain event; {@code CaryvanModClient} is the one that actually stores the image.
 */
public final class WainwrightPreviewImageReceivedEvent extends Event {

    private final WainwrightPreviewImagePayload payload;

    public WainwrightPreviewImageReceivedEvent(final WainwrightPreviewImagePayload payload) {
        this.payload = payload;
    }

    public WainwrightPreviewImagePayload payload() {
        return this.payload;
    }
}
