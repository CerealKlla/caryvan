package com.github.cerealklla.caryvan;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Hand carts, horse-drawn carts, and wagons -- see {@code context/design-document.md} for the full
 * design. Registration wiring is deliberately bare right now (scaffold only, 2026-10-25) -- no
 * entities/items/menus exist yet.
 */
@Mod(CaryvanMod.MODID)
public class CaryvanMod {

    public static final String MODID = "caryvan";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CaryvanMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        LOGGER.info("Caryvan common setup");
    }
}
