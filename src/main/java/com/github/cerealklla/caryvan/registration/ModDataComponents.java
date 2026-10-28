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

    /**
     * Real gap found and fixed 2026-10-28, same day Wagon Repair first gained real per-wheel
     * species (see that feature's own doc): "And these are all stored on the item when it is picked
     * up right?" -- they were NOT. {@code AbstractDamageableDrawnEntity#executePickUp} builds its
     * result {@code ItemStack} via a plain {@code new ItemStack(getCartItem())} with no data
     * components at all, and {@link #WHEEL_WOOD_TYPE} above only ever carried ONE shared wheel
     * species (the Wainwright Station Body tab's own assembly-time choice, applied uniformly to
     * every wheel at spawn) -- genuinely mixed per-wheel species from an in-world Repair had no
     * component capable of surviving a round trip through an {@code ItemStack} at all. This one
     * does: exactly 4 entries, same 0=leftFront/1=rightFront/2=leftBack/3=rightBack order {@code
     * AbstractDamageableDrawnEntity#getWheelWoodType}'s own array already uses. Wagon-only --
     * Hand Cart/Animal Cart never gained per-wheel tracking in the first place (see that feature's
     * own doc for why), so they have nothing to carry here.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.List<String>>> WHEEL_WOOD_TYPES =
            DATA_COMPONENTS.registerComponentType("wheel_wood_types", builder -> builder
                    .persistent(com.mojang.serialization.Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())));
}
