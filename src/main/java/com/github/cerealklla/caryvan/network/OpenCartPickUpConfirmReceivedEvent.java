package com.github.cerealklla.caryvan.network;

import net.neoforged.bus.api.Event;

/**
 * Fired by the common {@code CaryvanMod#registerPayloads} the instant an {@link
 * OpenCartPickUpConfirmPayload} is actually received -- the real bug-fix mechanism, 2026-10-28:
 * the payload's TYPE/CODEC used to be registered only in {@code CaryvanModClient} ({@code
 * Dist.CLIENT}-only), which never loads on a dedicated server at all, so the real Production
 * server never learned this channel existed -- confirmed live via a real "channel missing on the
 * server side" disconnect screen the instant Production was actually used for the first time this
 * session (the single-JVM dev `runClient` environment shares one process for client+server, which
 * silently masked this gap every time it was tested there).
 *
 * <p>The fix registers the channel exactly once, in the common class -- but the common class can
 * never directly call client-only code ({@code Minecraft}/{@code ConfirmActionScreen}) from its own
 * handler lambda, per the root project's own CLAUDE.md (a client-only type referenced from any
 * method of a common-sourceset class crashes a dedicated server at registry-dispatch time, even
 * behind a runtime {@code Dist} check -- the JVM verifier resolves every type a class's methods
 * reference regardless of which branch would run). So the common handler only ever posts this
 * plain, client-agnostic event; {@code CaryvanModClient} is the one that actually listens for it and
 * opens the real GUI. Posting it on a dedicated server (where nothing ever subscribes, since
 * {@code CaryvanModClient} never loads there) is a harmless no-op.
 */
public final class OpenCartPickUpConfirmReceivedEvent extends Event {

    private final OpenCartPickUpConfirmPayload payload;

    public OpenCartPickUpConfirmReceivedEvent(final OpenCartPickUpConfirmPayload payload) {
        this.payload = payload;
    }

    public OpenCartPickUpConfirmPayload payload() {
        return this.payload;
    }
}
