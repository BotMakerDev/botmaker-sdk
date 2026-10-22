package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.overlay.Area;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.capture.Window;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
