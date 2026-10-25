package com.github.cerealklla.caryvan;

import com.github.cerealklla.caryvan.network.ActionKeyPayload;
import com.github.cerealklla.caryvan.network.ToggleSlowPayload;
import com.github.cerealklla.caryvan.registration.ModEntities;
import com.github.cerealklla.caryvan.registration.ModItems;
import com.github.cerealklla.caryvan.util.CaryvanWorld;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Hand carts, horse-drawn carts, and wagons -- see {@code context/design-document.md} for the full
 * design. Phase 1 (foundation + Hand Cart) built 2026-10-25.
 */
@Mod(CaryvanMod.MODID)
public class CaryvanMod {

    public static final String MODID = "caryvan";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CaryvanMod(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        LOGGER.info("Caryvan common setup");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(ActionKeyPayload.TYPE, ActionKeyPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ActionKeyPayload.handle(player);
                    }
                });
        registrar.playToServer(ToggleSlowPayload.TYPE, ToggleSlowPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ToggleSlowPayload.handle(player);
                    }
                });
    }

    /** The one place {@link AbstractDrawnEntity#pulledTick()} is actually called -- see {@link CaryvanWorld}'s own doc for why. */
    private void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            CaryvanWorld.get(level).tick();
        }
    }
}
