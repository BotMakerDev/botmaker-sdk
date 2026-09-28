package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.capture.CurrentSource;
import com.botmaker.sdk.internal.capture.RegionSource;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.sdk.plugin.source.SourcePicker;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;

import java.awt.Rectangle;
import java.util.List;

/**
 * The editor for a {@code CaptureSource} — what a bot reads pixels from — in a slot and a Parameters row: a
 * pill naming the source that opens the same tile picker as the toolbar's Capture Source, its Project default
 * tile included, since {@code Source.current()} is a value like any other.
 *
 * <p>Values in, values out: the host writes {@code window("G").region(r)} through {@code CaptureTypes}. A value
 * the host could not read (a variable, {@code Sdk.captureSource()}) is shown as written and replaced only by a
 * pick.
 */
public final class CaptureSourceEditors {

    private CaptureSourceEditors() {}

    public static Node source(ValueContext ctx) {
        CaptureSource current = ctx.value(CaptureSource.class).orElse(null);
        MenuButton pill = Pills.bare(current != null ? label(current) : Slots.sourceOr(ctx, "Choose…"));
        Pills.onOpen(pill, () -> List.of(Pills.item("Choose a capture source…", () ->
                new SourcePicker(ctx.services(), Modals.owner(ctx.services()), true)
                        .preselect(ctx.value(CaptureSource.class).orElse(null))
                        .showAndWait()
                        .ifPresent(selection -> {
                            CaptureSource picked = valueOf(selection);
                            ctx.set(picked);
                            pill.setText(label(picked));
                        }))));
        return pill;
    }

    /** The source's name beside a declared choice, or {@code null} for the plain label. */
    public static Node preview(ValueContext ctx) {
        return ctx.value(CaptureSource.class).map(source -> (Node) new Label(label(source))).orElse(null);
    }

    /** What a pick writes: {@code Source.current()}, the source, or the source narrowed to the rectangle. */
    public static CaptureSource valueOf(SourcePicker.Selection selection) {
        if (selection instanceof SourcePicker.Selection.Concrete concrete) {
            Rectangle r = concrete.region();
            return r == null ? concrete.target()
                    : concrete.target().region(new Rect(r.x, r.y, r.width, r.height));
        }
        return new CurrentSource();
    }

    /**
     * The pill's words: {@link CaptureLabels#shortLabel}, except for the two it cannot say — it reads the
     * project default as the whole desktop, and a narrowed source as whatever it narrows.
     */
    public static String label(CaptureSource source) {
        if (source instanceof CurrentSource) return "Project default";
        if (source instanceof RegionSource region) return label(region.parent()) + " (region)";
        return CaptureLabels.shortLabel(source);
    }
}
