package com.github.cerealklla.caryvan.registration;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.wainwright.WainwrightStationBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Caryvan's first player-placeable blocks -- Wainwright Station (Part 2 of the wood-species-mixing feature). */
public final class ModBlocks {

    private ModBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CaryvanMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CaryvanMod.MODID);

    public static final DeferredBlock<WainwrightStationBlock> WAINWRIGHT_STATION = BLOCKS.register(
            "wainwright_station",
            id -> new WainwrightStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredItem<BlockItem> WAINWRIGHT_STATION_ITEM = ITEMS.registerSimpleBlockItem(WAINWRIGHT_STATION);
}
