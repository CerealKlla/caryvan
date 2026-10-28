package com.github.cerealklla.caryvan.wainwright.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.github.cerealklla.caryvan.registration.ModItems;
import com.github.cerealklla.caryvan.wainwright.WainwrightStationMenu;
import com.github.cerealklla.caryvan.wainwright.preview.RequestWainwrightPreviewPayload;
import com.github.cerealklla.caryvan.wainwright.preview.WainwrightPreviewStorage;
import com.github.cerealklla.caryvan.wainwright.preview.client.ClientWainwrightPreviewCache;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.WoodType;

/**
 * Wainwright Station's screen -- two vertical left-edge tabs (Parts/Body), craft area on the right.
 * Species picklists are derived live each frame from {@code menu.slots} (the viewer's own synced
 * inventory), not a server-sent snapshot -- see {@link WainwrightStationMenu}'s own class doc.
 *
 * <p>Body-tab preview image (Plan Part 3, built 2026-10-28 -- see {@code CaryvanImager/CLAUDE.md}
 * and {@code wainwright.preview} package): once Type + both species are picked, this requests (and
 * caches) a rendered preview via {@link ClientWainwrightPreviewCache}/{@link
 * RequestWainwrightPreviewPayload}, same cache-miss-triggers-a-request pattern Blueprynts' own
 * {@code BlueprintPickerScreen} uses. Falls back to the plain text summary (always shown alongside
 * it) while the image is still in flight or hasn't been rendered by CaryvanImager yet.
 */
public class WainwrightStationScreen extends AbstractContainerScreen<WainwrightStationMenu> {

    // Widened 320 -> 420, 2026-10-28, to fit the Parts tab's 4 now-longer part labels ("Hand Cart
    // Body"/"Animal Cart Body") laid out with dynamic per-label widths (see PART_LABELS/#renderTypeRow),
    // matching the Body tab's own type-button approach instead of the fixed-width row used before the
    // Cart Body split (see ModItems' own doc for why that split happened). Confirmed by the user
    // 2026-10-28 (see the IMAGE_HEIGHT entry right below) that 420 wide is NOT the problem -- plenty
    // of horizontal room at their real resolution/GUI Scale.
    private static final int IMAGE_WIDTH = 420;
    // Real live-test bug, 2026-10-28: most of the panel's own content was rendering off-screen
    // ("the majority of UI elements on the edges are still completely outside the monitor"). Root
    // cause (confirmed by reading Lyfe's own CraftingStructureScreen, which hit and fixed the exact
    // same class of bug 2026-10-03): vanilla's Auto GUI Scale only guarantees a logical canvas of
    // 320x240 -- at a GUI Scale picked for a large/widescreen monitor, the real logical height
    // available can land well under this screen's old fixed 330, which pushed `(height - imageHeight)
    // / 2` negative and moved the whole panel (and everything anchored to its origin) up/off the top
    // of the screen. The user confirmed width (420) wasn't the issue, just height -- fixed by
    // compressing the Body tab's tallest-content layout (not by shrinking width): the preview image's
    // summary/cost text now sits BESIDE it instead of below it (there was always horizontal room to
    // spare, per the user's own direction), smaller grid icons (20 -> 16px), and tighter gaps
    // throughout. 330 -> 270.
    // No longer a fixed constant, 2026-10-28 -- see WainwrightStationMenu's own "shrink to fit" doc.
    // This is now this screen's PREFERRED design height only (mirrored by
    // WainwrightStationMenu.PREFERRED_IMAGE_HEIGHT, kept in sync manually); the real imageHeight used
    // at construction is derived from the menu's own already-fixed inventoryY instead, so it can never
    // exceed what the player's actual window had room for.
    private static final int IMAGE_HEIGHT = 275;

    private static final int PREVIEW_BOX_WIDTH = 80;
    private static final int PREVIEW_BOX_HEIGHT = 60;
    // Width reserved for the summary/cost text column to the preview image's left -- wide enough for
    // the longest real cost line ("X / Y <wood> Animal Cart Body") at this screen's font.
    private static final int INFO_COLUMN_WIDTH = 230;
    // The full-size render CaryvanImager writes -- see its own Main.java default --width/--height.
    private static final int PREVIEW_SOURCE_WIDTH = 512;
    private static final int PREVIEW_SOURCE_HEIGHT = 384;

    private static final int TAB_X = 6;
    private static final int TAB_WIDTH = 56;
    private static final int TAB_HEIGHT = 20;

    private static final int CONTENT_X = 70;
    // Real live-test bug, 2026-10-28: vanilla's own auto-drawn title ("Wainwright Station," at
    // titleLabelX=CONTENT_X, titleLabelY=8) was landing directly on top of this button row (which
    // used to start at y=6) -- the two were never actually given separate vertical space. Moved the
    // button row down to clear the title text (which sits roughly y=8 to y=17) instead of shrinking
    // or relocating the title itself, matching Lyfe's own CraftingStructureScreen precedent (title
    // in the first ~20px, real content starts after that).
    private static final int TYPE_BUTTON_Y = 22;

    // Dynamic per-label width (font.width(label) + 10, cursor-walked) for both rows -- the Parts
    // tab switched to this from a fixed TYPE_BUTTON_WIDTH, 2026-10-28, once "Hand Cart Body"/
    // "Animal Cart Body" (the Cart Body split, see ModItems' own doc) no longer fit a fixed 76px cell.
    private static final String[] PART_LABELS = {"Wheel", "Hand Cart Body", "Animal Cart Body", "Wagon Body"};
    private static final String[] TYPE_LABELS = {"Hand Cart", "Animal Cart", "Wagon"};

    // Shrunk 20 -> 16 (vanilla's own native icon size, no padding) as part of the 2026-10-28
    // vertical-compression fix -- see IMAGE_HEIGHT's own doc.
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 2;
    private static final int ICON_CELL = ICON_SIZE + ICON_GAP;
    private static final int GRID_COLUMNS = 6;

    // Real live-test bug, 2026-10-28 ("Significant UI overlap"): the type-button row spans
    // [TYPE_BUTTON_Y, TYPE_BUTTON_Y + TAB_HEIGHT] = [6, 26], but "Body species:" was drawn at
    // BODY_SPECIES_GRID_Y - 12 = 20 -- squarely inside that range. CONTENT_ROW_GAP is the real fix:
    // every row below the type buttons now starts at least this far past their bottom edge, with
    // SPECIES_GRID_Y additionally padded so its own label (drawn at GRID_Y - 12) clears the gap too,
    // not just the icon grid underneath it. Both gaps tightened further 2026-10-28 (10->6, 12->8) as
    // part of the vertical-compression fix -- see IMAGE_HEIGHT's own doc.
    private static final int TYPE_BUTTON_BOTTOM = TYPE_BUTTON_Y + TAB_HEIGHT;
    private static final int CONTENT_ROW_GAP = 8;
    private static final int PART_GRID_Y = TYPE_BUTTON_BOTTOM + CONTENT_ROW_GAP;
    // Body species (left) and Wheel species (right) grids sit on the SAME row, not stacked -- see
    // IMAGE_HEIGHT's own doc. Each is always given room for the worst-case 2-row grid
    // (GRID_MAX_HEIGHT) regardless of how many rows a given viewer's own carried-species count
    // actually needs, so content below never has to guess/collide -- a real latent bug a naive
    // stacked layout would have too (a fixed Y assuming the body grid was always exactly 1 row would
    // overlap once a 7+-species body grid pushed it to 2).
    private static final int SPECIES_GRID_Y = TYPE_BUTTON_BOTTOM + CONTENT_ROW_GAP + 10;
    private static final int GRID_MAX_ROWS = 2; // ceil(12 species / GRID_COLUMNS)
    private static final int GRID_MAX_HEIGHT = GRID_MAX_ROWS * ICON_CELL;
    private static final int WHEEL_GRID_X_OFFSET = GRID_COLUMNS * ICON_CELL + 30;

    private static final int PANEL_BODY_COLOR = 0xFFC6C6C6;
    private static final int PANEL_OUTER_BORDER_COLOR = 0xFF373737;
    private static final int PANEL_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_SHADOW_COLOR = 0xFF8B8B8B;
    private static final int LABEL_TEXT_COLOR = 0xFF404040;
    private static final int SELECTION_COLOR = 0xFFFFFFFF;
    private static final int COST_MET_COLOR = 0xFF2E7D32;
    private static final int COST_SHORT_COLOR = 0xFFB71C1C;
    private static final int COST_LINE_HEIGHT = 10;

    private boolean bodyTab;
    private int selectedPart = -1;
    private int selectedType = -1;
    private int selectedSpecies = -1;
    private int selectedBodySpecies = -1;
    private int selectedWheelSpecies = -1;

    private Button craftButton;
    private String lastRequestedPreviewKey;

    public WainwrightStationScreen(final WainwrightStationMenu menu, final Inventory inventory, final Component title) {
        // imageHeight derived from the menu's own already-fixed inventoryY (2026-10-28 "shrink to
        // fit" fix, see WainwrightStationMenu's own doc) instead of the fixed IMAGE_HEIGHT constant --
        // can never exceed what the player's real window had room for at construction time.
        super(menu, inventory, title, IMAGE_WIDTH, WainwrightStationMenu.imageHeightForInventoryY(menu.inventoryY()));
        this.titleLabelX = CONTENT_X;
        this.titleLabelY = 8;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = menu.inventoryY() - 10;
    }

    @Override
    protected void init() {
        super.init();
        final int x = (width - imageWidth) / 2;
        final int y = (height - imageHeight) / 2;

        addRenderableWidget(Button.builder(Component.literal("Parts"), b -> switchTab(false))
                .bounds(x + TAB_X, y + 30, TAB_WIDTH, TAB_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Body"), b -> switchTab(true))
                .bounds(x + TAB_X, y + 30 + TAB_HEIGHT + 2, TAB_WIDTH, TAB_HEIGHT).build());

        craftButton = addRenderableWidget(Button.builder(Component.literal("Craft"), b -> onCraftClicked())
                .bounds(x + imageWidth - 90, y + imageHeight - 44, 80, 20).build());
        updateCraftButtonState();
    }

    /**
     * Real live-test report, 2026-10-28: "changing the tabs shouldn't unselect the species." Used
     * to reset every selection field on every switch; now only flips which tab is showing -- each
     * tab's own selection fields are independent of the other's (a leftover {@code selectedPart}
     * from the Parts tab has no effect while {@code bodyTab} is true, since every Body-tab check
     * only ever reads {@code selectedType}/{@code selectedBodySpecies}/{@code selectedWheelSpecies},
     * and vice versa), so there's nothing to reconcile by clearing them.
     */
    private void switchTab(final boolean toBody) {
        this.bodyTab = toBody;
        updateCraftButtonState();
    }

    private void onCraftClicked() {
        if (!bodyTab) {
            if (selectedPart < 0 || selectedSpecies < 0) {
                return;
            }
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    WainwrightStationMenu.craftPartButtonId(selectedPart, selectedSpecies));
        } else {
            if (selectedType < 0 || selectedBodySpecies < 0 || selectedWheelSpecies < 0) {
                return;
            }
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    WainwrightStationMenu.assembleButtonId(selectedType, selectedBodySpecies, selectedWheelSpecies));
        }
    }

    private void updateCraftButtonState() {
        if (craftButton == null) {
            return;
        }
        craftButton.setMessage(Component.literal(bodyTab ? "Assemble" : "Craft"));
        craftButton.active = bodyTab
                ? selectedType >= 0 && selectedBodySpecies >= 0 && selectedWheelSpecies >= 0
                : selectedPart >= 0 && selectedSpecies >= 0;
    }

    private static List<WoodType> allSpecies() {
        return WoodType.values().toList();
    }

    private static Item planksItem(final WoodType species) {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(species.name() + "_planks"));
    }

    /** Which real {@link WoodType}s the viewer currently carries at least one of, for {@code itemFor}'s resolved item. */
    private List<WoodType> carriedSpecies(final Function<WoodType, Item> itemFor) {
        final List<WoodType> carried = new ArrayList<>();
        for (final WoodType species : allSpecies()) {
            final Item item = itemFor.apply(species);
            for (final Slot slot : menu.slots) {
                if (slot.hasItem() && slot.getItem().getItem() == item) {
                    carried.add(species);
                    break;
                }
            }
        }
        return carried;
    }

    private Item bodyItemFor(final int typeIndex, final WoodType species) {
        return switch (typeIndex) {
            case WainwrightStationMenu.TYPE_ANIMAL_CART -> ModItems.animalCartBodyFor(species);
            case WainwrightStationMenu.TYPE_WAGON -> ModItems.wagonBodyFor(species);
            default -> ModItems.handCartBodyFor(species);
        };
    }

    private boolean isInsideGrid(final int gridLeft, final int gridTop, final int count, final double mouseX, final double mouseY) {
        final int rows = (count + GRID_COLUMNS - 1) / GRID_COLUMNS;
        return mouseX >= gridLeft && mouseX < gridLeft + GRID_COLUMNS * ICON_CELL
                && mouseY >= gridTop && mouseY < gridTop + Math.max(1, rows) * ICON_CELL;
    }

    private int gridIndexAt(final int gridLeft, final int gridTop, final double mouseX, final double mouseY) {
        final int col = (int) ((mouseX - gridLeft) / ICON_CELL);
        final int row = (int) ((mouseY - gridTop) / ICON_CELL);
        return row * GRID_COLUMNS + col;
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        final int x = (width - imageWidth) / 2;
        final int y = (height - imageHeight) / 2;

        if (!bodyTab) {
            int partCursor = x + CONTENT_X;
            for (int i = 0; i < PART_LABELS.length; i++) {
                final int w = font.width(PART_LABELS[i]) + 10;
                if (hitsButton(partCursor, y + TYPE_BUTTON_Y, w, TAB_HEIGHT, event.x(), event.y())) {
                    // Real live-test report, 2026-10-28 (same report that already fixed the
                    // Parts/Body switchTab reset): switching which PART is selected used to also
                    // wipe selectedSpecies -- species is a cross-cutting choice independent of which
                    // part/type you're configuring (the Parts tab's available-species list doesn't
                    // even depend on which part is selected today), so there's nothing to reconcile
                    // by clearing it here either.
                    selectedPart = i;
                    updateCraftButtonState();
                    return true;
                }
                partCursor += w + 2;
            }
            final List<WoodType> species = carriedSpecies(WainwrightStationScreen::planksItem);
            final int gridLeft = x + CONTENT_X;
            final int gridTop = y + PART_GRID_Y;
            if (isInsideGrid(gridLeft, gridTop, species.size(), event.x(), event.y())) {
                final int index = gridIndexAt(gridLeft, gridTop, event.x(), event.y());
                if (index >= 0 && index < species.size()) {
                    selectedSpecies = allSpecies().indexOf(species.get(index));
                    updateCraftButtonState();
                    return true;
                }
            }
        } else {
            int cursor = x + CONTENT_X;
            for (int i = 0; i < TYPE_LABELS.length; i++) {
                final int w = font.width(TYPE_LABELS[i]) + 10;
                if (hitsButton(cursor, y + TYPE_BUTTON_Y, w, TAB_HEIGHT, event.x(), event.y())) {
                    // Same fix as the Parts tab's part-switch above -- switching Hand Cart/Animal
                    // Cart/Wagon used to wipe both species picks. Kept now: a stale body-species
                    // index just won't highlight in the new type's own grid (harmless, matches the
                    // existing "not in this list" case) and the wheel-species pool isn't even
                    // type-dependent to begin with.
                    selectedType = i;
                    updateCraftButtonState();
                    return true;
                }
                cursor += w + 2;
            }
            if (selectedType >= 0) {
                final List<WoodType> bodySpecies = carriedSpecies(species -> bodyItemFor(selectedType, species));
                final int bodyGridLeft = x + CONTENT_X;
                final int bodyGridTop = y + SPECIES_GRID_Y;
                if (isInsideGrid(bodyGridLeft, bodyGridTop, bodySpecies.size(), event.x(), event.y())) {
                    final int index = gridIndexAt(bodyGridLeft, bodyGridTop, event.x(), event.y());
                    if (index >= 0 && index < bodySpecies.size()) {
                        selectedBodySpecies = allSpecies().indexOf(bodySpecies.get(index));
                        updateCraftButtonState();
                        return true;
                    }
                }
                final List<WoodType> wheelSpecies = carriedSpecies(ModItems::wheelsFor);
                final int wheelGridLeft = x + CONTENT_X + WHEEL_GRID_X_OFFSET;
                final int wheelGridTop = y + SPECIES_GRID_Y;
                if (isInsideGrid(wheelGridLeft, wheelGridTop, wheelSpecies.size(), event.x(), event.y())) {
                    final int index = gridIndexAt(wheelGridLeft, wheelGridTop, event.x(), event.y());
                    if (index >= 0 && index < wheelSpecies.size()) {
                        selectedWheelSpecies = allSpecies().indexOf(wheelSpecies.get(index));
                        updateCraftButtonState();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static boolean hitsButton(final int bx, final int by, final int bw, final int bh, final double mouseX, final double mouseY) {
        return mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        final int x = (width - imageWidth) / 2;
        final int y = (height - imageHeight) / 2;

        if (!bodyTab) {
            int partCursor = x + CONTENT_X;
            for (int i = 0; i < PART_LABELS.length; i++) {
                final int w = font.width(PART_LABELS[i]) + 10;
                drawToggleRow(graphics, partCursor, y + TYPE_BUTTON_Y, w, TAB_HEIGHT, PART_LABELS[i], i == selectedPart);
                partCursor += w + 2;
            }
            drawSpeciesGrid(graphics, x + CONTENT_X, y + PART_GRID_Y, carriedSpecies(WainwrightStationScreen::planksItem),
                    species -> new ItemStack(planksItem(species)), selectedSpecies, mouseX, mouseY);
            if (selectedPart >= 0) {
                // Real live-test report, 2026-10-28: "I also have no idea what materials are needed
                // to create things" -- this used to require BOTH a selected part AND a selected
                // species, but the species grid is only ever populated with carried species, so a
                // player with none of the relevant raw material yet (e.g. a brand new player with no
                // wood at all) could never see a cost at all. Now shows as soon as a part is picked,
                // falling back to Oak as a representative example species if none is actually
                // selected/carried yet -- real info beats no info.
                final boolean realSpecies = selectedSpecies >= 0;
                final WoodType costSpecies = realSpecies ? allSpecies().get(selectedSpecies) : WoodType.OAK;
                final int costX = x + CONTENT_X + GRID_COLUMNS * ICON_CELL + 20;
                final String costLabel = realSpecies ? "Cost:" : "Cost (Oak shown as example):";
                graphics.text(font, Component.literal(costLabel), costX, y + PART_GRID_Y, LABEL_TEXT_COLOR, false);
                drawCostList(graphics, costX, y + PART_GRID_Y + COST_LINE_HEIGHT + 2,
                        WainwrightStationMenu.costForPart(selectedPart, costSpecies));
            }
        } else {
            int cursor = x + CONTENT_X;
            for (int i = 0; i < TYPE_LABELS.length; i++) {
                final int w = font.width(TYPE_LABELS[i]) + 10;
                drawToggleRow(graphics, cursor, y + TYPE_BUTTON_Y, w, TAB_HEIGHT, TYPE_LABELS[i], i == selectedType);
                cursor += w + 2;
            }
            if (selectedType >= 0) {
                // Side by side, not stacked -- see IMAGE_HEIGHT's own doc (real live-test report:
                // "not properly stretching," a large unused gap from double-reserving 2-row grid
                // height for two grids stacked vertically).
                graphics.text(font, Component.literal("Body species:"), x + CONTENT_X, y + SPECIES_GRID_Y - 12, LABEL_TEXT_COLOR, false);
                drawSpeciesGrid(graphics, x + CONTENT_X, y + SPECIES_GRID_Y, carriedSpecies(species -> bodyItemFor(selectedType, species)),
                        species -> new ItemStack(bodyItemFor(selectedType, species)), selectedBodySpecies, mouseX, mouseY);
                graphics.text(font, Component.literal("Wheel species:"), x + CONTENT_X + WHEEL_GRID_X_OFFSET, y + SPECIES_GRID_Y - 12, LABEL_TEXT_COLOR, false);
                drawSpeciesGrid(graphics, x + CONTENT_X + WHEEL_GRID_X_OFFSET, y + SPECIES_GRID_Y, carriedSpecies(ModItems::wheelsFor),
                        species -> new ItemStack(ModItems.wheelsFor(species)), selectedWheelSpecies, mouseX, mouseY);

                // Same "show real info instead of none at all" fix as the Parts tab, 2026-10-28 --
                // was gated behind BOTH species actually being selected (only ever possible once the
                // player already carries a real Body item and real Wheels), so a player with neither
                // yet could never see what assembling a cart would even cost. The preview IMAGE still
                // only renders once both are real choices (it's a genuine combo to visualize, not a
                // reference table, and requesting a render for an arbitrary fallback would be noise).
                final boolean bothSelected = selectedBodySpecies >= 0 && selectedWheelSpecies >= 0;
                final WoodType body = selectedBodySpecies >= 0 ? allSpecies().get(selectedBodySpecies) : WoodType.OAK;
                final WoodType wheel = selectedWheelSpecies >= 0 ? allSpecies().get(selectedWheelSpecies) : WoodType.OAK;
                final int previewY = y + SPECIES_GRID_Y + GRID_MAX_HEIGHT + 6;
                // Summary/cost text on the left, preview image on the right -- explicit user
                // request 2026-10-28, swapped from the original image-left/text-right layout.
                final int infoX = x + CONTENT_X;
                if (bothSelected) {
                    drawPreview(graphics, infoX + INFO_COLUMN_WIDTH, previewY, body, wheel);
                }

                final String summary = bothSelected
                        ? TYPE_LABELS[selectedType] + ": " + displayName(body) + " body / " + displayName(wheel) + " wheels"
                        : TYPE_LABELS[selectedType] + " cost (Oak shown as example):";
                graphics.text(font, Component.literal(summary), infoX, previewY, LABEL_TEXT_COLOR, false);

                final java.util.Map<Item, Integer> assembleCost = new java.util.LinkedHashMap<>();
                assembleCost.put(bodyItemFor(selectedType, body), 1);
                assembleCost.put(ModItems.wheelsFor(wheel), WainwrightStationMenu.wheelCountFor(selectedType));
                graphics.text(font, Component.literal("Cost:"), infoX, previewY + 12, LABEL_TEXT_COLOR, false);
                drawCostList(graphics, infoX, previewY + 12 + COST_LINE_HEIGHT + 2, assembleCost);
            } else {
                graphics.text(font, Component.literal("Pick a type above."), x + CONTENT_X, y + SPECIES_GRID_Y, LABEL_TEXT_COLOR, false);
            }
        }
    }

    /** Must match {@code CaryvanImager}'s own {@code model.CartGeometry#boxesFor} keys exactly. */
    private static String typeId(final int typeIndex) {
        return switch (typeIndex) {
            case WainwrightStationMenu.TYPE_HAND_CART -> "hand_cart";
            case WainwrightStationMenu.TYPE_ANIMAL_CART -> "animal_cart";
            case WainwrightStationMenu.TYPE_WAGON -> "wagon";
            default -> "hand_cart";
        };
    }

    private void drawPreview(final GuiGraphicsExtractor graphics, final int boxX, final int boxY, final WoodType body, final WoodType wheel) {
        final String type = typeId(selectedType);
        final String key = WainwrightPreviewStorage.keyFor(type, body.name(), wheel.name());
        graphics.fill(boxX - 1, boxY - 1, boxX + PREVIEW_BOX_WIDTH + 1, boxY + PREVIEW_BOX_HEIGHT + 1, PANEL_OUTER_BORDER_COLOR);
        graphics.fill(boxX, boxY, boxX + PREVIEW_BOX_WIDTH, boxY + PREVIEW_BOX_HEIGHT, PANEL_SHADOW_COLOR);

        final var texture = ClientWainwrightPreviewCache.get(key);
        if (texture.isPresent()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture.get(), boxX, boxY, 0f, 0f,
                    PREVIEW_BOX_WIDTH, PREVIEW_BOX_HEIGHT, PREVIEW_SOURCE_WIDTH, PREVIEW_SOURCE_HEIGHT, PREVIEW_SOURCE_WIDTH, PREVIEW_SOURCE_HEIGHT);
            return;
        }
        if (!key.equals(lastRequestedPreviewKey)) {
            lastRequestedPreviewKey = key;
            minecraft.getConnection().send(new ServerboundCustomPayloadPacket(
                    new RequestWainwrightPreviewPayload(type, body.name(), wheel.name())));
        }
        graphics.text(font, Component.literal("Rendering..."), boxX + 4, boxY + PREVIEW_BOX_HEIGHT / 2 - 4, LABEL_TEXT_COLOR, false);
    }

    private static String displayName(final WoodType species) {
        final String name = species.name().replace('_', ' ');
        return name.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + name.substring(1);
    }

    private static String displayName(final Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    /**
     * How many of {@code item} the viewer carries, scanned over the SAME 27-slot main-inventory
     * range the server's own {@code WainwrightStationMenu#hasAndConsume} draws from (this screen's
     * first 27 {@code menu.slots} entries, per {@code WainwrightStationMenu#layoutSlots}) -- not the
     * hotbar, so this never shows "enough" for something that would actually fail to craft because
     * it's only sitting in the hotbar.
     */
    private int countHeld(final Item item) {
        int total = 0;
        for (int i = 0; i < 27 && i < menu.slots.size(); i++) {
            final Slot slot = menu.slots.get(i);
            if (slot.hasItem() && slot.getItem().getItem() == item) {
                total += slot.getItem().getCount();
            }
        }
        return total;
    }

    /** Real live-test report, 2026-10-28: "it needs to show the actual recipe costs so the user knows what they have and what they are lacking." One line per ingredient, green if held >= required, red otherwise. */
    private void drawCostList(final GuiGraphicsExtractor graphics, final int x, final int y, final java.util.Map<Item, Integer> cost) {
        int lineY = y;
        for (final var entry : cost.entrySet()) {
            final int held = countHeld(entry.getKey());
            final int required = entry.getValue();
            final String line = held + " / " + required + " " + displayName(entry.getKey());
            graphics.text(font, Component.literal(line), x, lineY, held >= required ? COST_MET_COLOR : COST_SHORT_COLOR, false);
            lineY += COST_LINE_HEIGHT;
        }
    }

    private void drawToggleRow(final GuiGraphicsExtractor graphics, final int bx, final int by, final int bw, final int bh, final String label, final boolean selected) {
        graphics.fill(bx, by, bx + bw, by + bh, selected ? SELECTION_COLOR : PANEL_SHADOW_COLOR);
        graphics.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, selected ? PANEL_HIGHLIGHT_COLOR : PANEL_BODY_COLOR);
        graphics.text(font, Component.literal(label), bx + 5, by + 6, LABEL_TEXT_COLOR, false);
    }

    private void drawSpeciesGrid(final GuiGraphicsExtractor graphics, final int gridLeft, final int gridTop, final List<WoodType> species,
            final Function<WoodType, ItemStack> iconFor, final int selectedIndex, final int mouseX, final int mouseY) {
        if (species.isEmpty()) {
            graphics.text(font, Component.literal("(none carried)"), gridLeft, gridTop, LABEL_TEXT_COLOR, false);
            return;
        }
        for (int i = 0; i < species.size(); i++) {
            final int col = i % GRID_COLUMNS;
            final int row = i / GRID_COLUMNS;
            final int cellX = gridLeft + col * ICON_CELL;
            final int cellY = gridTop + row * ICON_CELL;
            final WoodType w = species.get(i);
            final boolean selected = allSpecies().indexOf(w) == selectedIndex;
            if (selected) {
                graphics.fill(cellX - 1, cellY - 1, cellX + ICON_SIZE + 1, cellY + ICON_SIZE + 1, SELECTION_COLOR);
            }
            // Real live-test report, 2026-10-28: icons weren't centered in their highlighted cell.
            // This +2 offset was tuned for ICON_SIZE=20 (back when the selection box was ICON_SIZE+2
            // = 22px, so centering a native 16px item icon needed a 3px margin -- offset cellX-1+3 =
            // cellX+2). ICON_SIZE shrank to 16 as part of the same day's vertical-compression fix, but
            // this offset was never recomputed: the selection box is now only ICON_SIZE+2 = 18px, so
            // centering a 16px icon needs just a 1px margin -- offset cellX-1+1 = cellX. Matches the
            // hit-test region below, which already assumed the icon starts at cellX, not cellX+2.
            graphics.item(iconFor.apply(w), cellX, cellY);
            if (mouseX >= cellX && mouseX < cellX + ICON_SIZE && mouseY >= cellY && mouseY < cellY + ICON_SIZE) {
                graphics.setTooltipForNextFrame(font, Component.literal(displayName(w)), mouseX, mouseY);
            }
        }
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        final int xo = (this.width - this.imageWidth) / 2;
        final int yo = (this.height - this.imageHeight) / 2;

        graphics.fill(xo, yo, xo + imageWidth, yo + imageHeight, PANEL_OUTER_BORDER_COLOR);
        graphics.fill(xo + 1, yo + 1, xo + imageWidth - 1, yo + imageHeight - 1, PANEL_HIGHLIGHT_COLOR);
        graphics.fill(xo + 2, yo + 2, xo + imageWidth - 1, yo + imageHeight - 1, PANEL_SHADOW_COLOR);
        graphics.fill(xo + 2, yo + 2, xo + imageWidth - 2, yo + imageHeight - 2, PANEL_BODY_COLOR);

        for (final var slot : menu.slots) {
            final int x = xo + slot.x;
            final int y = yo + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, PANEL_OUTER_BORDER_COLOR);
            graphics.fill(x, y, x + 16, y + 16, PANEL_SHADOW_COLOR);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
