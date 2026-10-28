package com.github.cerealklla.caryvan.network;

import net.neoforged.bus.api.Event;

/** Mirrors {@link OpenCartPickUpConfirmReceivedEvent} exactly, for {@link OpenSlotRemovalConfirmPayload} instead. */
public final class OpenSlotRemovalConfirmReceivedEvent extends Event {

    private final OpenSlotRemovalConfirmPayload payload;

    public OpenSlotRemovalConfirmReceivedEvent(final OpenSlotRemovalConfirmPayload payload) {
        this.payload = payload;
    }

    public OpenSlotRemovalConfirmPayload payload() {
        return this.payload;
    }
}
