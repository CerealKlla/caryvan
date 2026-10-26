package com.github.cerealklla.caryvan;

import com.mojang.blaze3d.platform.InputConstants;

import com.github.cerealklla.caryvan.client.ModModelLayers;
import com.github.cerealklla.caryvan.client.renderer.entity.AnimalCartRenderer;
import com.github.cerealklla.caryvan.client.renderer.entity.HandCartRenderer;
import com.github.cerealklla.caryvan.client.renderer.entity.WagonRenderer;
import com.github.cerealklla.caryvan.client.renderer.entity.model.AnimalCartModel;
import com.github.cerealklla.caryvan.client.renderer.entity.model.HandCartModel;
import com.github.cerealklla.caryvan.client.renderer.entity.model.WagonModel;
import com.github.cerealklla.caryvan.menu.client.AnimalCartScreen;
import com.github.cerealklla.caryvan.menu.client.WagonScreen;
import com.github.cerealklla.caryvan.network.ToggleSlowPayload;
import com.github.cerealklla.caryvan.registration.ModEntities;
import com.github.cerealklla.caryvan.registration.ModMenus;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.HAND_CART.get(), HandCartRenderer::new);
        event.registerEntityRenderer(ModEntities.ANIMAL_CART.get(), AnimalCartRenderer::new);
        event.registerEntityRenderer(ModEntities.WAGON.get(), WagonRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ANIMAL_CART.get(), AnimalCartScreen::new);
        event.register(ModMenus.WAGON.get(), WagonScreen::new);
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
