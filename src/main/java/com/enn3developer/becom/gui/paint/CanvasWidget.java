package com.enn3developer.becom.gui.paint;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.IFocusedWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.enn3developer.becom.BeCom;

/**
 * The widget a {@link CanvasScreen} lives in: it sizes itself to the board at {@code scale}, paints it through the
 * client's canvas, and hands it clicks, the wheel and keys. The screen keeps the keyboard while it's open.
 */
public final class CanvasWidget extends Widget<CanvasWidget> implements Interactable, IFocusedWidget {

    private final CanvasScreen screen;
    private final float scale;
    private boolean focused;

    public CanvasWidget(CanvasScreen screen, float scale) {
        this.screen = screen;
        this.scale = scale;
        size(width(), height());
    }

    /** How big the screen is in GUI pixels, for the panel around it. */
    public int width() {
        return Math.round(screen.width() * scale);
    }

    public int height() {
        return Math.round(screen.height() * scale);
    }

    @Override
    public void onInit() {
        super.onInit();
        if (isValid()) getContext().focus(this);
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!focused) context.focus(this);
        BeCom.proxy.paint(screen, scale, context.getMouseX() / scale, context.getMouseY() / scale, context);
    }

    @Override
    public Result onMousePressed(int mouseButton) {
        if (!isHovering()) return Result.IGNORE;
        return screen.click(getContext().getMouseX() / scale, getContext().getMouseY() / scale, mouseButton)
            ? Result.SUCCESS
            : Result.ACCEPT;
    }

    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        screen.scroll(scrollDirection.isUp() ? 1 : -1);
        return true;
    }

    @Override
    public Result onKeyPressed(char typedChar, int keyCode) {
        return screen.key(typedChar, keyCode) ? Result.SUCCESS : Result.IGNORE;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void onFocus(ModularGuiContext context) {
        focused = true;
    }

    @Override
    public void onRemoveFocus(ModularGuiContext context) {
        focused = false;
    }
}
