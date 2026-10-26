package com.github.cerealklla.caryvan.registration;

import java.util.function.Supplier;

import com.github.cerealklla.caryvan.CaryvanMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * A pure in-memory marker (no serialize/sync persistence needed -- see {@code entity.WagonEntity}'s
 * own doc for why it's freely re-applied each tick a mount is (re)resolved) flagging a summoned
 * horse as currently part of a {@code WagonEntity}'s team, so
 * {@code interaction.CartMountInteractionListener} can refuse a player trying to ride it directly.
 * Synced to the client (unlike a purely server-side marker) specifically so the client's own
 * predictive interaction handling also refuses the mount attempt, rather than briefly showing a
 * mount that the server then reverts -- same real precedent Lyfe's own
 * {@code SUMMONED_ALLAY_OWNER}/{@code SUMMONED_MOUNT_OWNER} attachments already established for
 * exactly this reason.
 */
public final class ModAttachments {

    private ModAttachments() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CaryvanMod.MODID);

    public static final Supplier<AttachmentType<Boolean>> CART_TEAM_MOUNT = ATTACHMENT_TYPES.register(
            "cart_team_mount",
            () -> AttachmentType.builder(holder -> Boolean.TRUE)
                    .sync(ByteBufCodecs.BOOL)
                    .build());
}
