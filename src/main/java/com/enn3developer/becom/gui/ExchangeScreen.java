package com.enn3developer.becom.gui;

import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.gui.machine.ExchangeUi;
import com.enn3developer.becom.gui.paint.CanvasWidget;

/** The Exchange's screen: its view, synced from the server, on the Router board's panel. */
public final class ExchangeScreen {

    private ExchangeScreen() {}

    /** {@code view} is asked on the server only. */
    public static ModularPanel build(Supplier<ExchangeView> view, PanelSyncManager sync) {
        ViewSync<ExchangeView> synced = new ViewSync<>(
            view,
            ExchangeView::write,
            ExchangeView::read,
            ExchangeView.EMPTY);
        sync.syncValue("becomExchange", synced);
        CanvasWidget widget = new CanvasWidget(new ExchangeUi(synced::view), ExchangeUi.scale(BeCom.proxy.guiHeight()));
        return new ModularPanel("becom_exchange").size(widget.width(), widget.height())
            .background(IDrawable.EMPTY)
            .child(widget);
    }
}
