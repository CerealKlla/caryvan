package com.github.cerealklla.caryvan;

import com.mojang.blaze3d.platform.InputConstants;

import com.github.cerealklla.caryvan.client.ModModelLayers;
import com.github.cerealklla.caryvan.client.renderer.entity.AnimalCartRenderer;
import com.github.cerealklla.caryvan.client.renderer.entity.CarriageRenderer;
import com.github.cerealklla.caryvan.client.renderer.entity.HandCartRenderer;
import com.github.cerealklla.caryvan.client.renderer.entity.WagonRenderer;
import com.github.cerealklla.caryvan.client.renderer.entity.model.AnimalCartModel;
import com.github.cerealklla.caryvan.client.renderer.entity.model.CarriageModel;
import com.github.cerealklla.caryvan.client.renderer.entity.model.HandCartModel;
import com.github.cerealklla.caryvan.client.renderer.entity.model.WagonModel;
import com.github.cerealklla.caryvan.menu.client.AnimalCartScreen;
import com.github.cerealklla.caryvan.menu.client.CarriageScreen;
import com.github.cerealklla.caryvan.menu.client.ConfirmActionScreen;
import com.github.cerealklla.caryvan.menu.client.WagonScreen;
import com.github.cerealklla.caryvan.network.ConfirmCartPickUpPayload;
import com.github.cerealklla.caryvan.network.ConfirmSlotRemovalPayload;
import com.github.cerealklla.caryvan.network.OpenCartPickUpConfirmPayload;
import com.github.cerealklla.caryvan.network.OpenCartPickUpConfirmReceivedEvent;
import com.github.cerealklla.caryvan.network.OpenSlotRemovalConfirmPayload;
import com.github.cerealklla.caryvan.network.OpenSlotRemovalConfirmReceivedEvent;
import com.github.cerealklla.caryvan.network.ToggleSlowPayload;
import com.github.cerealklla.caryvan.registration.ModEntities;
import com.github.cerealklla.caryvan.registration.ModMenus;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.lwjgl.glfw.GLFW;

// This class will not load on dedicated servers. Accessing client side code from here is safe --
// see the root project's own CLAUDE.md for why a client-only type must never leak into a
// common-sourceset class instead (this entire file is the isolation boundary for that).
@Mod(value = CaryvanMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CaryvanMod.MODID, value = Dist.CLIENT)
public class CaryvanModClient {

    public CaryvanModClient(ModContainer container) {
    }

    /**
     * Real bug fix, 2026-10-28: the payload TYPE/CODEC registration moved to the common {@code
     * CaryvanMod#registerPayloads} (a dedicated server needs to know this channel exists, and this
     * class never loads there at all -- see {@code OpenCartPickUpConfirmReceivedEvent}'s own doc for
     * the full story). This class now just listens for the bridge event that handler posts, and does
     * the actual client-only GUI work here, same as before.
     */
    @SubscribeEvent
    static void onOpenPickUpConfirmReceived(OpenCartPickUpConfirmReceivedEvent event) {
        onOpenPickUpConfirm(event.payload());
    }

    @SubscribeEvent
    static void onOpenSlotRemovalConfirmReceived(OpenSlotRemovalConfirmReceivedEvent event) {
        onOpenSlotRemovalConfirm(event.payload());
    }

    /**
     * Opens the real "Are you sure?" {@link ConfirmActionScreen} for Pick Up -- the actual client-
     * side half of {@code AbstractDamageableDrawnEntity#requestPickUpConfirm}. "Yes" sends {@link
     * ConfirmCartPickUpPayload} back with the cart's real entity id (carried in the payload itself,
     * not re-derived from any currently-open menu -- see {@code OpenCartPickUpConfirmPayload}'s own
     * doc for why that matters); "No"/closing the screen does nothing.
     */
    private static void onOpenPickUpConfirm(OpenCartPickUpConfirmPayload payload) {
        final String warning = payload.broken()
                ? " Any Companions still aboard have a 50% chance each of being too wounded to retrieve."
                : "";
        Minecraft.getInstance().setScreen(new ConfirmActionScreen(
                Component.literal("Pick Up Cart"),
                Component.literal("Pick up this cart?" + warning),
                () -> send(new ConfirmCartPickUpPayload(payload.cartEntityId()))));
    }

    /** Mirrors {@link #onOpenPickUpConfirm}'s own doc exactly, for a single Companion/Summon Stone slot removal instead of the whole cart. */
    private static void onOpenSlotRemovalConfirm(OpenSlotRemovalConfirmPayload payload) {
        Minecraft.getInstance().setScreen(new ConfirmActionScreen(
                Component.literal("Remove Companion"),
                Component.literal("Remove this Companion/Summon Stone? This cart is Broken -- there's a 50% chance it's too wounded to retrieve."),
                () -> send(new ConfirmSlotRemovalPayload(payload.cartEntityId(), payload.slotIndex()))));
    }

    // Hitch/unhitch no longer has its own keybind at all, 2026-10-25, explicit user correction --
    // "same as horses/chairs: right click toggles, left shift auto dismounts" -- see
    // entity.AbstractDrawnEntity#tryToggleHitch/#pulledTick's own shift-check. Only the slow-pull
    // toggle still needs a dedicated key.
    public static final KeyMapping TOGGLE_SLOW = new KeyMapping(
            "key.caryvan.toggle_slow", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, KeyMapping.Category.MISC);

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_SLOW);
    }

    @SubscribeEvent
    static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModModelLayers.HAND_CART, HandCartModel::createLayer);
        event.registerLayerDefinition(ModModelLayers.ANIMAL_CART, AnimalCartModel::createLayer);
        event.registerLayerDefinition(ModModelLayers.WAGON, WagonModel::createLayer);
        event.registerLayerDefinition(ModModelLayers.CARRIAGE, CarriageModel::createLayer);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.HAND_CART.get(), HandCartRenderer::new);
        event.registerEntityRenderer(ModEntities.ANIMAL_CART.get(), AnimalCartRenderer::new);
        event.registerEntityRenderer(ModEntities.WAGON.get(), WagonRenderer::new);
        event.registerEntityRenderer(ModEntities.CARRIAGE.get(), CarriageRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ANIMAL_CART.get(), AnimalCartScreen::new);
        event.register(ModMenus.WAGON.get(), WagonScreen::new);
        event.register(ModMenus.CARRIAGE.get(), CarriageScreen::new);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().screen != null) {
            // Draining consumeClick() below still happens even with a screen open if we don't
            // early-return -- same guard every other keybind handler in this suite already uses, so
            // a held key doesn't fire the instant a GUI closes.
            return;
        }
        while (TOGGLE_SLOW.consumeClick()) {
            send(new ToggleSlowPayload());
        }
    }

    private static void send(CustomPacketPayload payload) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }
}
