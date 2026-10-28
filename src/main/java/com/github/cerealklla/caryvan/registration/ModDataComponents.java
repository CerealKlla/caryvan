package com.github.cerealklla.caryvan.registration;

import com.github.cerealklla.caryvan.CaryvanMod;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {

    private ModDataComponents() {
    }

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CaryvanMod.MODID);

    // Wainwright Station wood-species mixing, 2026-10-28 -- carries the wheel species chosen on the
    // Body tab separately from the result item's own baked-in body species (a plain Item instance
    // can't hold per-stack mixed data on its own). Stored as the real WoodType's name, matching the
    // String-name convention every entity-side WoodType field in this mod already uses (confirmed
    // against AbstractDamageableDrawnEntity/AbstractDrawnEntity's own DATA_WOOD_TYPE/
    // DATA_WHEEL_WOOD_TYPE NBT save-load) rather than inventing a dedicated WoodType codec. Read back
    // by HandCartItem/AnimalCartItem/WagonItem#use alongside the item's own body species when the
    // entity is actually spawned.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> WHEEL_WOOD_TYPE =
            DATA_COMPONENTS.registerComponentType("wheel_wood_type", builder -> builder
                    .persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));
}
