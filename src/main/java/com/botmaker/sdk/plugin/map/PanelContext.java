package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.overlay.Area;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;

import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * What the assistant's tools are handed when 🗺 Screens runs them: the project's capture source, the screen the bot
 * watches, in place of the overlay the host gives the assistant. Built on the FX thread, where the source is read;
 * used off it.
 */
final class PanelContext implements AgentContext {

    private final StudioServices services;
    private final CaptureSource source;
    private volatile Area last;

    /** A context over {@code source}, the project's capture source; null when it names none. */
    PanelContext(StudioServices services, CaptureSource source) {
        this.services = services;
        this.source = source;
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
        last = new Area(origin.x(), origin.y(), image.getWidth(), image.getHeight());
        return Optional.of(image);
    }

    @Override
    public Optional<Area> watchedArea() {
        if (last == null) frame();
        return Optional.ofNullable(last);
    }
}
