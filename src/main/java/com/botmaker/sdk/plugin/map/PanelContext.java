package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.overlay.Area;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.capture.Window;
import com.botmaker.sdk.internal.capture.WindowHandles;
import com.botmaker.sdk.plugin.assist.ScreenDesk;
import com.botmaker.shared.capture.FrameGrab;
import com.botmaker.shared.capture.FrameSink;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * What the assistant's tools are handed when 🗺 Screens runs them: the project's capture source, the screen the bot
 * watches, in place of the overlay the host gives the assistant. Built on the FX thread, where the source is read;
 * used off it.
 */
final class PanelContext implements AgentContext {

    private final StudioServices services;
    private final CaptureSource source;
    /** Where the last frame sat on the desktop; shared with the context {@link #found()} made of this one. */
    private final AtomicReference<Area> last;

    /** A context over {@code source}, the project's capture source; null when it names none. */
    PanelContext(StudioServices services, CaptureSource source) {
        this(services, source, new AtomicReference<>());
    }

    private PanelContext(StudioServices services, CaptureSource source, AtomicReference<Area> last) {
        this.services = services;
        this.source = source;
        this.last = last;
    }

    /**
     * This context over the window its source names, found now, so a read finds no window again: a window named by
     * its class is otherwise found among every window on each read, twice, and a live feed reads 30 times a second
     * (2026-10-22). Itself when the source names no window by its class, or names one not open. Off the FX thread.
     */
    PanelContext found() {
        return source instanceof NamedWindow named && named.base() instanceof Window open
                ? new PanelContext(services, open, last) : this;
    }

    /**
     * Where a live feed reads the game: the private display or the emulator as the look tools read them, else the
     * window the source names, grabbed and kept open, else the source's frames. Off the FX thread.
     */
    LiveFeed live() {
        Optional<Supplier<Optional<BufferedImage>>> away = ScreenDesk.away(this);
        if (away.isPresent()) return LiveFeed.of(away.get());
        if (source instanceof Window open && WindowHandles.id(open) != null) {
            // A window moved is a place of its own: its grab opened again says where its frames sit now.
            Point origin = open.origin();
            return new LiveFeed(List.of(WindowHandles.id(open), origin), () -> placed(WindowHandles.grab(open), origin));
        }
        return LiveFeed.of(this::frame);
    }

    /** {@code grab}, where each frame sits on the desktop kept as {@link #frame} keeps it, for a click on it. */
    private FrameGrab placed(FrameGrab grab, Point origin) {
        return new FrameGrab() {
            @Override
            public boolean grab(FrameSink sink) {
                return grab.grab((width, height) -> {
                    last.set(new Area(origin.x(), origin.y(), width, height));
                    return sink.pixels(width, height);
                });
            }

            @Override
            public void close() {
                grab.close();
            }
        };
    }

    @Override
    public StudioServices services() {
        return services;
    }

    @Override
    public Optional<BufferedImage> frame() {
        if (source == null) return Optional.empty();
        BufferedImage image;
        try {
            image = source.capture();
        } catch (RuntimeException failed) {
            return Optional.empty();
        }
        if (image == null) return Optional.empty();
        Point origin = source.origin();
        last.set(new Area(origin.x(), origin.y(), image.getWidth(), image.getHeight()));
        return Optional.of(image);
    }

    @Override
    public Optional<Area> watchedArea() {
        if (last.get() == null) frame();
        return Optional.ofNullable(last.get());
    }
}
