package com.github.cerealklla.caryvan.registration;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.menu.AnimalCartMenu;
import com.github.cerealklla.caryvan.menu.WagonMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, CaryvanMod.MODID);

    // No extra non-slot data needed -- the mount/cargo item contents all arrive via vanilla's own
    // normal slot-sync packets, same as every plain chest-like menu.
    public static final DeferredHolder<MenuType<?>, MenuType<AnimalCartMenu>> ANIMAL_CART = MENU_TYPES.register(
            "animal_cart",
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) -> new AnimalCartMenu(null, windowId, inventory)));

    public static final DeferredHolder<MenuType<?>, MenuType<WagonMenu>> WAGON = MENU_TYPES.register(
            "wagon",
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) -> new WagonMenu(null, windowId, inventory)));
}
