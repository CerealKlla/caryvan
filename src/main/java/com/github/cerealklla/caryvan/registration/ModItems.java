package com.github.cerealklla.caryvan.registration;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.item.HandCartItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    private ModItems() {
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CaryvanMod.MODID);

    public static final DeferredItem<HandCartItem> HAND_CART = ITEMS.register(
            "hand_cart",
            id -> new HandCartItem(new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM, id))));
}
