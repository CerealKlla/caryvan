package com.github.cerealklla.caryvan;

import com.github.cerealklla.caryvan.interaction.CartMountDeathListener;
import com.github.cerealklla.caryvan.interaction.CartMountDebugListener;
import com.github.cerealklla.caryvan.interaction.CartMountInteractionListener;
import com.github.cerealklla.caryvan.interaction.DamageableCartTargetingListener;
import com.github.cerealklla.caryvan.network.ToggleSlowPayload;
import com.github.cerealklla.caryvan.registration.ModAttachments;
import com.github.cerealklla.caryvan.registration.ModEntities;
import com.github.cerealklla.caryvan.registration.ModItems;
import com.github.cerealklla.caryvan.registration.ModMenus;
import com.github.cerealklla.caryvan.util.CaryvanWorld;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
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
        ModMenus.MENU_TYPES.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::registerAttributes);

        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        // Wagon/Carriage health system, 2026-10-26: makes hostile mobs actually choose to attack an
        // idle wagon/carriage, the user's own explicit "non-negotiable" requirement -- see that
        // class's own doc.
        NeoForge.EVENT_BUS.register(new DamageableCartTargetingListener());
        // Real user request, 2026-10-25: "I think once you disable being able to ride the horses
        // individually in a team-style wagon we won't have that possibility of a problem anymore"
        // (a player manually riding a team mount away, then dismounting, could strand it beyond
        // vanilla's own real leash-snap distance -- see entity.WagonEntity's own doc).
        NeoForge.EVENT_BUS.register(new CartMountInteractionListener());
        // Temporary diagnostic logging, 2026-10-25 -- see that class's own doc. Delete once the
        // real "horses went invulnerable mid-session" mechanism is confirmed and fixed for good.
        NeoForge.EVENT_BUS.register(new CartMountDebugListener());
        // Real user report, 2026-10-25: "it did drop 'free' leads when the horse died which it
        // should not do." See that class's own doc for the full real-bytecode-traced root cause.
        NeoForge.EVENT_BUS.register(new CartMountDeathListener());

        // Replaces manually crafting/finding a Hand Cart while testing -- contributed into Kyt's
        // mod-managed "Dev" Kyt (/kyt getDev), same pattern Blueprynts/Settlemynts already use for
        // their own debug items. 2026-10-25, explicit user request. Animal Cart added the same day,
        // once Phase 2's first slice shipped; Wagon added the same day too, once the 2-horse slice
        // shipped -- see Lyfe's own LyfeMod for the matching ready-bound Summoning Stone
        // contribution (now granting two stones, not one) this needs to actually be testable.
        if (ModList.get().isLoaded("kyt")) {
            // Oak specifically -- an arbitrary but reasonable default now that all three carts come
            // in 12 wood-species variants each (Wagon joined 2026-10-26) instead of one flat item.
            com.github.cerealklla.kyt.api.Kyt.registerDevKytContribution(() ->
                    java.util.List.of(
                            new ItemStack(ModItems.handCartFor(net.minecraft.world.level.block.state.properties.WoodType.OAK)),
                            new ItemStack(ModItems.animalCartFor(net.minecraft.world.level.block.state.properties.WoodType.OAK)),
                            new ItemStack(ModItems.wagonFor(net.minecraft.world.level.block.state.properties.WoodType.OAK)),
                            new ItemStack(ModItems.carriageFor(net.minecraft.world.level.block.state.properties.WoodType.OAK)),
                            // Wheel-rework testing items, 2026-10-26.
                            new ItemStack(ModItems.wheelsFor(net.minecraft.world.level.block.state.properties.WoodType.OAK), 8),
                            new ItemStack(ModItems.BROKEN_WAGON.get()),
                            new ItemStack(ModItems.BROKEN_WHEEL.get(), 8),
                            // Carriage redesign Phase 3 testing item, same day.
                            new ItemStack(ModItems.doorFor(net.minecraft.world.level.block.state.properties.WoodType.OAK), 4)));
        }
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        LOGGER.info("Caryvan common setup");
    }

    /**
     * Wagon/Carriage health system, 2026-10-26 -- both are now real {@code LivingEntity} subclasses
     * and need a registered {@code AttributeSupplier} or they crash the instant one is constructed
     * (confirmed via a real crash log once already this session, for an identical gap in
     * Settlemynts' {@code GuardKitStandEntity}). Hand Cart/Animal Cart are untouched, still plain
     * {@code VehicleEntity}s -- no attribute supplier needed for those.
     */
    private void registerAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(ModEntities.WAGON.get(), com.github.cerealklla.caryvan.entity.WagonEntity.createAttributes().build());
        event.put(ModEntities.CARRIAGE.get(), com.github.cerealklla.caryvan.entity.CarriageEntity.createAttributes().build());
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(ToggleSlowPayload.TYPE, ToggleSlowPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ToggleSlowPayload.handle(player);
                    }
                });
        registrar.playToServer(com.github.cerealklla.caryvan.network.ConfirmCartPickUpPayload.TYPE,
                com.github.cerealklla.caryvan.network.ConfirmCartPickUpPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.github.cerealklla.caryvan.network.ConfirmCartPickUpPayload.handle(player, payload.cartEntityId());
                    }
                });
        registrar.playToServer(com.github.cerealklla.caryvan.network.ConfirmSlotRemovalPayload.TYPE,
                com.github.cerealklla.caryvan.network.ConfirmSlotRemovalPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.github.cerealklla.caryvan.network.ConfirmSlotRemovalPayload.handle(player, payload.cartEntityId(), payload.slotIndex());
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
