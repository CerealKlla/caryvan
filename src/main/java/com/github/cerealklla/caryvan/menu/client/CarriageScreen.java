package com.github.cerealklla.caryvan.menu.client;

import com.github.cerealklla.caryvan.CaryvanMod;
import com.github.cerealklla.caryvan.entity.AbstractDamageableDrawnEntity;
import com.github.cerealklla.caryvan.menu.CarriageMenu;

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
 * Carriage's screen -- same two-panel layout {@code WagonScreen} already proved out, just a taller
 * mount/seat panel ({@code carriage_mount_panel.png}, 58x129 -- same width as the Wagon's own,
 * 2 Summon Stone slots stacked above 3 rows of 2 Companion seats: front Driver/Passenger, then 2
 * interior rows). {@link CarriageMenu}'s own slot widget positions were derived directly from this
 * panel's generation script, same technique as {@code WagonMenu}.
 */
public final class CarriageScreen extends AbstractContainerScreen<CarriageMenu> {

    private static final Identifier CARGO_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final Identifier MOUNT_PANEL = Identifier.fromNamespaceAndPath(CaryvanMod.MODID, "textures/gui/carriage_mount_panel.png");
    private static final int MOUNT_PANEL_WIDTH = 58;
    private static final int MOUNT_PANEL_HEIGHT = 129;
    private static final int MOUNT_PANEL_Y_OFFSET = 21;
    private static final int GAP = 6;
    private static final int CARGO_PANEL_X = MOUNT_PANEL_WIDTH + GAP;
    private static final int TOP_PANEL_HEIGHT = CarriageMenu.CARGO_ROWS * 18 + 17;
    private static final int PLAYER_INV_HEIGHT = 96;

    public CarriageScreen(final CarriageMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title, CARGO_PANEL_X + 176, TOP_PANEL_HEIGHT + PLAYER_INV_HEIGHT);
        this.inventoryLabelY = this.imageHeight - 94;
        this.titleLabelX = CARGO_PANEL_X + 8;
        this.inventoryLabelX = CARGO_PANEL_X + 8;
    }

    /** Real "Pick Up"/"Repair" buttons -- mirrors {@code WagonScreen#init}'s own doc exactly, placed below the taller mount/seat panel here. */
    @Override
    protected void init() {
        super.init();
        final int buttonX = this.leftPos;
        final int buttonY = this.topPos + MOUNT_PANEL_Y_OFFSET + MOUNT_PANEL_HEIGHT + 4;
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
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Same idiom as {@code WagonScreen#extractTooltip} -- spells out each reserved slot's meaning while it's empty. */
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

    /**
     * Real layout shift, Carriage redesign Phase 4, 2026-10-26: the mount row grew from 2 slots to
     * 3 (lead, plus 2 team slots abreast -- see {@code CarriageMenu}'s own doc for the real triangle
     * formation this matches) -- every index from the old Driver seat on shifts by 1.
     */
    private static Component slotLabel(final Slot slot) {
        return switch (slot.index) {
            case 0 -> Component.literal("Lead Mount").withStyle(ChatFormatting.GRAY);
            case 1 -> Component.literal("Team Mount (left)").withStyle(ChatFormatting.GRAY);
            case 2 -> Component.literal("Team Mount (right)").withStyle(ChatFormatting.GRAY);
            case 3 -> Component.literal("Driver / NPC Pilot Seat").withStyle(ChatFormatting.BLUE);
            case 4 -> Component.literal("Passenger Seat").withStyle(ChatFormatting.DARK_GREEN);
            case 5 -> Component.literal("Interior Seat (left)").withStyle(ChatFormatting.BLUE);
            case 6 -> Component.literal("Interior Seat (right)").withStyle(ChatFormatting.DARK_GREEN);
            case 7 -> Component.literal("Interior Seat (left)").withStyle(ChatFormatting.BLUE);
            case 8 -> Component.literal("Interior Seat (right)").withStyle(ChatFormatting.DARK_GREEN);
            default -> null;
        };
    }
}
