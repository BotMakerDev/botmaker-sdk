package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.SlotEditor;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;

import java.util.List;

/**
 * Every editor this plugin offers, in the order it wants them consulted.
 *
 * <p>Order matters only within this list — the host consults its own editors first and its JDK/enum fallbacks
 * last, whatever a plugin claims. So this is not a precedence table so much as a table of contents, and the
 * one rule inside it is that a narrower match comes before a wider one.
 *
 * <p><b>Everything here matches on the type, not on the call site.</b> That is deliberate and it is what the
 * contract's {@code ValueContext} bought: an editor chosen by type is drawn both in a bot's source and in the
 * Parameters window, while one chosen by {@code enclosingMethod()} can only ever appear in the first. Where a
 * call site genuinely is what decides — a Steam app id and a window title are both {@code String} — the editor
 * asks {@link com.botmaker.plugin.api.ValueContext#asSlot()} and declines when the answer is {@code null}.
 */
public final class SdkEditors {

    private SdkEditors() {}

    /** Built once and shared: an editor holds no state, the value lives in the context it is handed. */
    public static final List<SlotEditor> ALL = List.of(
            SlotEditor.of(ctx -> ctx.type().is(Rect.class), GeometryEditors::rect),
            SlotEditor.of(ctx -> ctx.type().is(Point.class), GeometryEditors::point),
            SlotEditor.of(ctx -> ctx.type().is(Size.class), GeometryEditors::size));
}
