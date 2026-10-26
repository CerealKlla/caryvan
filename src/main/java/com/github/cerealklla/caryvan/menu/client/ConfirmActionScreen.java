package com.github.cerealklla.caryvan.menu.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Generic "Are you sure? Yes/No" confirmation dialog -- real user spec, 2026-10-26 (Wagon/Carriage
 * health system work): "There should be a warning before attempting [Pick Up a Broken cart] that
 * says 'Are you sure?' Yes/No." A real, reusable Screen class (not vanilla's own
 * {@code ConfirmScreen}, to match this suite's own established hand-rolled-dialog convention --
 * see {@code lyfe.recallcinite.client.RecallciniteBindConfirmScreen}, the precedent this is
 * modeled on) so any future confirm-before-acting flow in this mod can reuse it directly instead of
 * writing a new one-off screen.
 *
 * <p>{@code onConfirm} is a plain {@link Runnable} -- the caller supplies whatever client-side
 * action (typically sending a payload) "Yes" should trigger; "No"/closing the screen does nothing.
 */
public final class ConfirmActionScreen extends Screen {

    private final Component message;
    private final Runnable onConfirm;

    public ConfirmActionScreen(final Component title, final Component message, final Runnable onConfirm) {
        super(title);
        this.message = message;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        final LinearLayout layout = LinearLayout.vertical().spacing(8);
        layout.defaultCellSetting().alignHorizontallyCenter();
        layout.addChild(new StringWidget(this.getTitle(), this.font));
        layout.addChild(new MultiLineTextWidget(this.message, this.font).setMaxWidth(this.width - 50).setMaxRows(6).setCentered(true));
        final LinearLayout buttons = layout.addChild(LinearLayout.horizontal().spacing(4));
        buttons.defaultCellSetting().paddingTop(16);
        buttons.addChild(Button.builder(net.minecraft.network.chat.CommonComponents.GUI_YES, b -> {
            this.onConfirm.run();
            this.onClose();
        }).build());
        buttons.addChild(Button.builder(net.minecraft.network.chat.CommonComponents.GUI_NO, b -> this.onClose()).build());
        layout.visitWidgets(this::addRenderableWidget);
        layout.arrangeElements();
        net.minecraft.client.gui.layouts.FrameLayout.centerInRectangle(layout, this.getRectangle());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
