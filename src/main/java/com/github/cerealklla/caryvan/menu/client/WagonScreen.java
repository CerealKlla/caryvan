package com.github.cerealklla.caryvan.menu.client;

import java.util.Comparator;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity;
import com.github.cerealklla.caryvan.entity.WagonEntity;
import com.github.cerealklla.caryvan.menu.WagonMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Wagon's screen -- the same two-panel layout {@code AnimalCartScreen} already proved out (a real
 * standalone mount-slot panel, vanilla's own exact real small-chest-screen coordinates for the
 * cargo+player-inventory side). The mount panel ({@code wagon_mount_panel.png}) grew again
 * 2026-10-26 (design-document.md §8's own diagram) -- 58x79, two Summon Stone slots stacked above
 * two Companion slots side by side, instead of just the two Summon Stone slots alone. {@link
 * WagonMenu}'s own slot widget positions were derived directly from this panel's generation
 * script, same technique as before.
 */
public final class WagonScreen extends AbstractContainerScreen<WagonMenu> {

    private static final Identifier CARGO_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final Identifier MOUNT_PANEL = Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "textures/gui/wagon_mount_panel.png");
    private static final int MOUNT_PANEL_WIDTH = 58;
    private static final int MOUNT_PANEL_HEIGHT = 79;
    private static final int MOUNT_PANEL_Y_OFFSET = 21;
    private static final int GAP = 6;
    private static final int CARGO_PANEL_X = MOUNT_PANEL_WIDTH + GAP;
    // Vanilla's own exact real small-chest-screen formula: rows*18+17 -- now WagonMenu.CARGO_ROWS
    // (6, a double chest's worth, real user request 2026-10-25) instead of Animal Cart's 3. The
    // player-inventory section's own V=126.0F offset into generic_54.png stays fixed regardless of
    // row count -- that texture is already sized for a full 6-row chest, so a smaller screen (like
    // Animal Cart's 3-row one) just blits less of its top portion, not a different inventory offset.
    private static final int TOP_PANEL_HEIGHT = WagonMenu.CARGO_ROWS * 18 + 17;
    private static final int PLAYER_INV_HEIGHT = 96;

    // Real user spec, 2026-10-29: Max Speed is a real, live-recalculated stat (see WagonEntity's own
    // doc) that should be visible right on this screen. Client-side menu reconstruction has no live
    // entity reference at all (see WagonMenu's own doc on why its own `container` is a dummy
    // SimpleContainer here) -- but the viewing player is necessarily standing right next to the real
    // Wagon to have opened this screen, so the nearest one is resolved once at init time purely for
    // this display line, never for anything that needs to be authoritative.
    private WagonEntity cartEntity;

    public WagonScreen(final WagonMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title, CARGO_PANEL_X + 176, TOP_PANEL_HEIGHT + PLAYER_INV_HEIGHT);
        this.inventoryLabelY = this.imageHeight - 94;
        this.titleLabelX = CARGO_PANEL_X + 8;
        this.inventoryLabelX = CARGO_PANEL_X + 8;
    }

    private int mountButtonAreaY() {
        return this.topPos + MOUNT_PANEL_Y_OFFSET + MOUNT_PANEL_HEIGHT + 4;
    }

    private WagonEntity resolveCartEntity() {
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) {
            return null;
        }
        return this.minecraft.level.getEntitiesOfClass(WagonEntity.class, this.minecraft.player.getBoundingBox().inflate(8.0))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(this.minecraft.player))).orElse(null);
    }

    /**
     * Real "Pick Up"/"Repair" buttons, 2026-10-26 (health system work) -- stacked in the free space
     * directly below the mount panel (that panel only spans {@code MOUNT_PANEL_WIDTH}x{@code
     * MOUNT_PANEL_HEIGHT} starting at {@code MOUNT_PANEL_Y_OFFSET}; the cargo/player-inventory area
     * sits in a completely different X range starting at {@code CARGO_PANEL_X}, so this space is
     * genuinely free). Both route through vanilla's own real menu-button mechanism
     * ({@code WagonMenu#clickMenuButton}, see its own doc) -- "Repair" fires immediately (no confirm
     * needed); "Pick Up" also just fires the button click -- the server's own
     * {@code #requestPickUpConfirm} is what actually sends back the real confirm dialog (a
     * clientbound payload, handled in {@code CaryvanModClient}), not this click itself. Deliberately
     * NOT opening {@link ConfirmActionScreen} directly from here: that would transition away from
     * this screen immediately, which tears down the open container (confirmed against the real
     * decompiled {@code Minecraft#setScreen}/{@code AbstractContainerScreen#removed}) before the
     * player ever gets to click "Yes" -- see {@code OpenCartPickUpConfirmPayload}'s own doc for the
     * full reasoning.
     */
    @Override
    protected void init() {
        super.init();
        this.cartEntity = resolveCartEntity();
        final int buttonX = this.leftPos;
        final int buttonY = mountButtonAreaY();
        this.addRenderableWidget(Button.builder(Component.literal("Repair"), b ->
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, AbstractDamageableDrawnEntity.REPAIR_BUTTON_ID))
                .bounds(buttonX, buttonY, MOUNT_PANEL_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Pick Up"), b ->
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, AbstractDamageableDrawnEntity.PICK_UP_BUTTON_ID))
                .bounds(buttonX, buttonY + 24, MOUNT_PANEL_WIDTH, 20).build());
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, MOUNT_PANEL, this.leftPos, this.topPos + MOUNT_PANEL_Y_OFFSET, 0.0F, 0.0F, MOUNT_PANEL_WIDTH, MOUNT_PANEL_HEIGHT, MOUNT_PANEL_WIDTH, MOUNT_PANEL_HEIGHT);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CARGO_BACKGROUND, this.leftPos + CARGO_PANEL_X, this.topPos, 0.0F, 0.0F, 176, TOP_PANEL_HEIGHT, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CARGO_BACKGROUND, this.leftPos + CARGO_PANEL_X, this.topPos + TOP_PANEL_HEIGHT, 0.0F, 126.0F, 176, PLAYER_INV_HEIGHT, 256, 256);
        if (this.cartEntity != null) {
            final String speedText = String.format("Max Speed: %.3f", this.cartEntity.getMaxSpeed());
            graphics.text(this.font, Component.literal(speedText), this.leftPos, mountButtonAreaY() + 48, 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Real user follow-up, 2026-10-26: the new panel art (see this class's own doc) color-codes the
     * Driver/Passenger seats but doesn't spell it out in words anywhere -- this adds that directly in
     * the GUI itself via a hover tooltip on the 4 reserved slots, same idiom as every vanilla slot's
     * own item tooltip, just shown for an EMPTY reserved slot too (vanilla never bothers, since a
     * plain chest slot has no fixed meaning to explain). Only fires when the slot has no item of its
     * own to show a tooltip for -- once a Summon Stone/Allay Bottle is actually placed, that item's
     * own tooltip (handled by {@code super}) takes over, same as any other slot.
     */
    @Override
    protected void extractTooltip(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (this.hoveredSlot != null && !this.hoveredSlot.hasItem()) {
            final Component label = slotLabel(this.hoveredSlot);
            if (label != null) {
                graphics.setTooltipForNextFrame(this.font, label, mouseX, mouseY);
            }
        }
    }

    private static Component slotLabel(final Slot slot) {
        return switch (slot.index) {
            case 0 -> Component.literal("Lead Mount").withStyle(ChatFormatting.GRAY);
            case 1 -> Component.literal("Team Mount").withStyle(ChatFormatting.GRAY);
            case 2 -> Component.literal("Driver / NPC Pilot Seat").withStyle(ChatFormatting.BLUE);
            case 3 -> Component.literal("Passenger Seat").withStyle(ChatFormatting.DARK_GREEN);
            default -> null;
        };
    }
}
