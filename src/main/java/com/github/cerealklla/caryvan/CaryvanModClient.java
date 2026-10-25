package com.github.cerealklla.caryvan;

import com.mojang.blaze3d.platform.InputConstants;

import com.github.cerealklla.caryvan.client.renderer.entity.HandCartRenderer;
import com.github.cerealklla.caryvan.network.ActionKeyPayload;
import com.github.cerealklla.caryvan.network.ToggleSlowPayload;
import com.github.cerealklla.caryvan.registration.ModEntities;

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
import org.lwjgl.glfw.GLFW;

// This class will not load on dedicated servers. Accessing client side code from here is safe --
// see the root project's own CLAUDE.md for why a client-only type must never leak into a
// common-sourceset class instead (this entire file is the isolation boundary for that).
@Mod(value = CaryvanMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CaryvanMod.MODID, value = Dist.CLIENT)
public class CaryvanModClient {

    public CaryvanModClient(ModContainer container) {
    }

    // "R" is already Settlemynts' own Auto-Drop key -- confirmed harmless (every mod's keybinds are
    // independent, this is purely to avoid confusing a player who has both mods installed), so this
    // uses "H" instead, free across the whole suite as of 2026-10-25. Easy-to-rebind placeholder,
    // same as every other keybind in this project.
    public static final KeyMapping HITCH = new KeyMapping(
            "key.caryvan.hitch", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, KeyMapping.Category.MISC);

    public static final KeyMapping TOGGLE_SLOW = new KeyMapping(
            "key.caryvan.toggle_slow", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, KeyMapping.Category.MISC);

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(HITCH);
        event.register(TOGGLE_SLOW);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.HAND_CART.get(), HandCartRenderer::new);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().screen != null) {
            // Draining consumeClick() below still happens even with a screen open if we don't
            // early-return -- same guard every other keybind handler in this suite already uses, so
            // a held key doesn't fire the instant a GUI closes.
            return;
        }
        while (HITCH.consumeClick()) {
            send(new ActionKeyPayload());
        }
        while (TOGGLE_SLOW.consumeClick()) {
            send(new ToggleSlowPayload());
        }
    }

    private static void send(CustomPacketPayload payload) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }
}
