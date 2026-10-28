package com.github.cerealklla.caryvan.wainwright;

import com.github.cerealklla.caryvan.registration.ModBlockEntities;
import com.github.cerealklla.caryvan.registration.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * No persisted state at all -- unlike {@code CraftingStructureBlockEntity} (which carries a
 * {@code tier}), Wainwright Station is a single, untiered structure. The Parts/Body species
 * picklists are computed fresh by {@link WainwrightStationMenu} from the opening player's own live
 * inventory slots every time the menu opens, so nothing needs to round-trip through {@code
 * writeClientSideData} the way {@code CraftingStructureBlockEntity} needs for its tier/recipe list
 * -- the client already has its own inventory contents via vanilla's normal slot sync.
 */
public class WainwrightStationBlockEntity extends BlockEntity implements MenuProvider {

    public WainwrightStationBlockEntity(final BlockPos pos, final BlockState state) {
        super(ModBlockEntities.WAINWRIGHT_STATION.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Wainwright Station");
    }

    @Override
    public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
        return new WainwrightStationMenu(ModMenus.WAINWRIGHT_STATION.get(), containerId, inventory, this);
    }
}
