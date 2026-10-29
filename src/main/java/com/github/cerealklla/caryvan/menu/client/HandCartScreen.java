package com.github.cerealklla.caryvan.menu.client;

import com.github.cerealklla.caryvan.entity.AbstractDrawnInventoryEntity;
import com.github.cerealklla.caryvan.menu.HandCartMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Hand Cart's screen -- vanilla's own exact 3-row chest texture/dimensions (the same ones {@code
 * ChestMenu.threeRows}'s generic screen already used, before {@code HandCartMenu} replaced it),
 * plus one "Pick Up" button in a column to the right of it (real user spec, 2026-10-29 -- see
 * {@code HandCartMenu}'s own doc). First-pass layout, not yet checked against a live screenshot --
 * see {@code AnimalCartScreen}'s own doc history for how much this suite's cart-screen layouts
 * tend to need live-test iteration.
 */
public final class HandCartScreen extends AbstractContainerScreen<HandCartMenu> {

    private static final Identifier CARGO_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    // Vanilla's own exact real small-chest-screen formula (3 rows): rows*18+17.
    private static final int TOP_PANEL_HEIGHT = 3 * 18 + 17;
    private static final int PLAYER_INV_HEIGHT = 96;
    private static final int BUTTON_COLUMN_WIDTH = 60;
    private static final int BUTTON_COLUMN_GAP = 6;

    public HandCartScreen(final HandCartMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title, 176 + BUTTON_COLUMN_GAP + BUTTON_COLUMN_WIDTH, TOP_PANEL_HEIGHT + PLAYER_INV_HEIGHT);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.literal("Pick Up"), b ->
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, AbstractDrawnInventoryEntity.PICK_UP_BUTTON_ID))
                .bounds(this.leftPos + 176 + BUTTON_COLUMN_GAP, this.topPos + 8, BUTTON_COLUMN_WIDTH, 20).build());
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, CARGO_BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F, 176, TOP_PANEL_HEIGHT, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CARGO_BACKGROUND, this.leftPos, this.topPos + TOP_PANEL_HEIGHT, 0.0F, 126.0F, 176, PLAYER_INV_HEIGHT, 256, 256);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
