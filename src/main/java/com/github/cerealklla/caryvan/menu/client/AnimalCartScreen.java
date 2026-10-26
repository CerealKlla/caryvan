package com.github.cerealklla.caryvan.menu.client;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.menu.AnimalCartMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Animal Cart's screen -- two genuinely separate panels, matched against a reference mockup the
 * user provided, 2026-10-25, after several earlier attempts at a single stitched/seamless panel
 * all looked wrong (either a visible double-corner artifact, or missing rounding at the join).
 * Explicit follow-up spec: "if your goal was to have a separate window on the left that's fine,
 * but there needs to be space between the two, and the bottom edges of both need to have rounded
 * corners."
 *
 * <p><b>Cargo + player-inventory panel</b>: drawn with vanilla's own exact real coordinates for a
 * 3-row container screen, confirmed by decompiling {@code net.minecraft.client.gui.screens.inventory.ContainerScreen}
 * itself (the class vanilla's own {@code ChestMenu.threeRows} screen already uses, which Hand Cart
 * already relies on unmodified) rather than assumed: the item-grid blit is {@code rows*18+17} tall,
 * and the player-inventory blit starts at that EXACT same Y with zero gap between them -- two
 * adjacent blits of genuinely unmodified texture, so both its top and bottom corners are correctly
 * rounded natively, no custom corner-drawing needed here at all.
 *
 * <p><b>Mount slot panel</b>: a real standalone custom texture ({@code textures/gui/mount_slot_panel.png},
 * generated to match vanilla's own border/bevel colors and its real corner-rounding pixel pattern,
 * sampled directly from the vanilla texture rather than guessed), since vanilla has no existing
 * small single-slot panel asset at this size with correct rounding on all four corners. Positioned
 * with a real {@link #GAP}, not touching the cargo panel.
 */
public final class AnimalCartScreen extends AbstractContainerScreen<AnimalCartMenu> {

    private static final Identifier CARGO_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final Identifier MOUNT_PANEL = Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "textures/gui/mount_slot_panel.png");
    private static final int MOUNT_PANEL_SIZE = 29;
    private static final int MOUNT_PANEL_Y_OFFSET = 21;
    private static final int GAP = 6;
    private static final int CARGO_PANEL_X = MOUNT_PANEL_SIZE + GAP;
    // Vanilla's own exact real small-chest-screen formula (3 rows): rows*18+17.
    private static final int TOP_PANEL_HEIGHT = 3 * 18 + 17;
    private static final int PLAYER_INV_HEIGHT = 96;

    public AnimalCartScreen(final AnimalCartMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title, CARGO_PANEL_X + 176, TOP_PANEL_HEIGHT + PLAYER_INV_HEIGHT);
        this.inventoryLabelY = this.imageHeight - 94;
        // Default title/"Inventory" label X positions are relative to the window's own left edge,
        // which is now the mount panel, not the cargo panel's own title/player-inventory area --
        // shift both to sit over the cargo panel instead.
        this.titleLabelX = CARGO_PANEL_X + 8;
        this.inventoryLabelX = CARGO_PANEL_X + 8;
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, MOUNT_PANEL, this.leftPos, this.topPos + MOUNT_PANEL_Y_OFFSET, 0.0F, 0.0F, MOUNT_PANEL_SIZE, MOUNT_PANEL_SIZE, MOUNT_PANEL_SIZE, MOUNT_PANEL_SIZE);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CARGO_BACKGROUND, this.leftPos + CARGO_PANEL_X, this.topPos, 0.0F, 0.0F, 176, TOP_PANEL_HEIGHT, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CARGO_BACKGROUND, this.leftPos + CARGO_PANEL_X, this.topPos + TOP_PANEL_HEIGHT, 0.0F, 126.0F, 176, PLAYER_INV_HEIGHT, 256, 256);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
