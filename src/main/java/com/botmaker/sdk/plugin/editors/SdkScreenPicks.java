package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Region;
import com.botmaker.plugin.toolkit.ScreenPicks;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.sdk.plugin.screen.EditorFrame;
import com.botmaker.sdk.plugin.screen.FrameShotSource;
import com.botmaker.sdk.plugin.screen.PickSpace;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import com.botmaker.sdk.plugin.screen.ScreenOverlay;
import com.botmaker.sdk.plugin.source.SurfaceMenu;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.awt.Rectangle;
import java.util.function.Consumer;

/**
 * The overlay, as the toolkit asks for it: one {@link ScreenCapture} per pick, over this machine's screens.
 *
 * <p>Over the screens and not over the project's capture target, deliberately. These three are the picks a
 * widget makes with nothing but a slot in hand — a region, a coordinate, a colour — and a slot does not say
 * which project it belongs to. An editor that wants the bot's own frame asks {@code EditorFrame} for it by
 * name, which is what the colour and picture editors do.
 *
 * <p>That is still true of {@link #get()}. Since 2026-09-26 an editor holding a context asks
 * {@link #forSlot} instead: the context has services, so the user chooses the surface per pick, and the call
 * the slot sits in decides whether the numbers are relative to it.
 *
 * <h2>Why it is a class of its own, and why it is reached through {@link #get()}</h2>
 *
 * <p>It was a private nested class of {@code SdkPlugin}, registered once through the toolkit's
 * {@code Editors.pickWith(…)} — <b>one static field shared by every plugin in the process, last writer
 * winning silently</b>. That static is deleted; the picker is an argument to the widget that needs it.
 *
 * <p>So it is held here instead, and held lazily, for the reason {@code SdkPlugin}'s constructor javadoc
 * gives at length: {@code ScreenPicks} is JavaFX-typed and {@code javafx-controls} is {@code optional} in
 * this module, so <b>linking it from a constructor makes the plugin unconstructible on a headless host</b>
 * — the CLI's {@code validate}, the registry's CI. Reaching it through a method means only a host that is
 * drawing an editor ever loads it, and such a host has JavaFX by definition.
 */
public final class SdkScreenPicks implements ScreenPicks {

    private static volatile ScreenPicks instance;

    private SdkScreenPicks() {
    }

    /**
     * The picker every widget in this plugin uses.
     *
     * <p>A plain double-checked read: a second instance under a race costs one allocation and nothing else,
     * because this holds no state — each pick opens its own overlay.
     */
    public static ScreenPicks get() {
        ScreenPicks local = instance;
        if (local == null) {
            local = new SdkScreenPicks();
            instance = local;
        }
        return local;
    }

    /**
     * The picker for one slot or row (2026-09-26): asks where to pick ({@link SurfaceMenu}), grabs a frozen
     * frame of that surface, runs the overlay on it, and hands the result back in the space the call reads
     * ({@link PickSpace}). A context with no services — a headless test — falls back to {@link #get()}.
     */
    public static ScreenPicks forSlot(ValueContext ctx) {
        StudioServices services = ctx.services();
        if (services == null) return get();
        PickSpace space = PickSpace.of(ctx.slot().flatMap(SlotContext::enclosingExecutable), ctx.slot().isPresent());
        return new ScreenPicks() {
            @Override
            public void region(Consumer<Region> onSelected) {
                onFrame(services, space, (overlay, bounds, owner) -> overlay.selectRegion(owner, r -> {
                    int[] v = space.region(r, bounds);
                    onSelected.accept(new Region(v[0], v[1], v[2], v[3]));
                }));
            }

            @Override
            public void point(Consumer<Region> onPicked) {
                onFrame(services, space, (overlay, bounds, owner) -> overlay.pickPoint(owner, p -> {
                    int[] v = space.point(p, bounds);
                    onPicked.accept(new Region(v[0], v[1], 0, 0));
                }));
            }

            @Override
            public void color(Consumer<Color> onSampled) {
                onFrame(services, space, (overlay, bounds, owner) -> overlay.pickColor(owner, pick -> {
                    java.awt.Color c = pick.color();
                    onSampled.accept(Color.rgb(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha() / 255.0));
                }));
            }
        };
    }

    /**
     * An overlay ready to pick on, where its frame's top-left is on the desktop ({@code null} when nowhere —
     * {@link PickSpace#origin}), and its owner.
     */
    private interface OnFrame {
        void run(ScreenOverlay overlay, Rectangle origin, Window owner);
    }

    /**
     * Menu, grab, overlay. A grab that fails says why, then grabs the whole virtual desktop instead — a frame
     * whose origin is the desktop's, so an absolute pick on any monitor still adds the right offset (the live
     * desktop pick crops to one monitor and reports inside it, which no origin here could correct). An
     * emulator's frame has no desktop origin at all, and an absolute slot is told so.
     */
    private static void onFrame(StudioServices services, PickSpace space, OnFrame then) {
        Window owner = services.dialogs().ownerWindow().orElse(null);
        SurfaceMenu.choose(services, surface -> {
            Consumer<EditorFrame> onGrab = frame -> {
                Rectangle origin = PickSpace.origin(frame);
                if (origin == null && space == PickSpace.ABSOLUTE) {
                    services.status(frame.label() + " is not on the desktop: these are its own pixels, "
                            + "and this call reads desktop pixels.");
                }
                then.run(new ScreenOverlay(new FrameShotSource(frame)), origin, owner);
            };
            Consumer<EditorFrame.Failure> gaveUp = failure -> services.status(failure.headline());
            Consumer<EditorFrame.Failure> onFail = failure -> {
                if (CaptureLabels.isDesktop(surface.source())) {
                    gaveUp.accept(failure);
                    return;
                }
                services.status(space == PickSpace.RELATIVE
                        ? failure.headline() + " Picking on the whole desktop instead of " + surface.label()
                          + ": these are desktop pixels."
                        : failure.headline() + " Picking on the whole desktop instead.");
                EditorFrame.grabAsync(services, CaptureSource.desktop(), onGrab, gaveUp);
            };
            if (surface.botsOwn()) EditorFrame.grabAsync(services, onGrab, onFail);
            else EditorFrame.grabAsync(services, surface.source(), onGrab, onFail);
        });
    }

    @Override
    public void region(Consumer<Region> onSelected) {
        new ScreenCapture().selectRegion(null,
                r -> onSelected.accept(new Region(r[0], r[1], r[2], r[3])));
    }

    @Override
    public void point(Consumer<Region> onPicked) {
        // A Region with no size: the toolkit has one coordinate type, and a point is a region whose width
        // and height are nobody's business.
        new ScreenCapture().pickPoint(null, p -> onPicked.accept(new Region(p[0], p[1], 0, 0)));
    }

    @Override
    public void color(Consumer<Color> onSampled) {
        new ScreenCapture().pickColor(null, pick -> {
            java.awt.Color c = pick.color();
            onSampled.accept(Color.rgb(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha() / 255.0));
        });
    }
}
