package com.github.cerealklla.caryvan.registration;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.wainwright.WainwrightStationBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    private ModBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CaryvanMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WainwrightStationBlockEntity>> WAINWRIGHT_STATION =
            BLOCK_ENTITIES.register("wainwright_station",
                    () -> new BlockEntityType<>(WainwrightStationBlockEntity::new, ModBlocks.WAINWRIGHT_STATION.get()));
}
