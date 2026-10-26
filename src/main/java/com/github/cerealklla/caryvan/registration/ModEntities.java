package com.github.cerealklla.caryvan.registration;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.entity.AnimalCartEntity;
import com.github.cerealklla.caryvan.entity.HandCartEntity;
import com.github.cerealklla.caryvan.entity.WagonEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    private ModEntities() {
    }

    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(CaryvanMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<HandCartEntity>> HAND_CART = ENTITIES.registerEntityType(
            "hand_cart",
            HandCartEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(1.4F, 0.9F));

    public static final DeferredHolder<EntityType<?>, EntityType<AnimalCartEntity>> ANIMAL_CART = ENTITIES.registerEntityType(
            "animal_cart",
            AnimalCartEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(1.6F, 1.1F));

    public static final DeferredHolder<EntityType<?>, EntityType<WagonEntity>> WAGON = ENTITIES.registerEntityType(
            "wagon",
            WagonEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(2.0F, 1.5F));
}
