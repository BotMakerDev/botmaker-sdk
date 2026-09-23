package com.botmaker.sdk.plugin.screen;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.capture.CurrentSource;

import java.util.Optional;

/**
 * The {@code @Managed("capture")} value — where a bot reads pixels from, written as the one expression
 * {@code Sdk.captureSource()} returns.
 *
 * <p>This is the only copy of the answer. {@code Sdk.captureSource()} is Java the bot compiles, so a
 * developer with no BotMaker installed can read it, change it and see the change take effect.
 *
 * <h2>It is allowed to fail quietly</h2>
 *
 * <p>A project may have no {@code Sdk.java}, and a user may have written {@code captureSource()} by hand in a
 * shape the host does not read. In both cases there is nothing to write and nothing has gone wrong: the Java
 * the user wrote is still theirs.
 */
public final class CaptureValue {

    /** The id the plugin declares and the shipped {@code Sdk.java} annotates its method with. */
    public static final String ID = "capture";

    private CaptureValue() {}

    /** The value behind {@code capture}, or empty when the project has none to edit. */
    public static Optional<ValueContext> open(StudioServices services) {
        return services == null ? Optional.empty() : services.pluginValues().open(ID);
    }

    /**
     * The project's capture source, or {@code null} when its Java names none the host can read.
     *
     * <p>{@code null} covers an absent {@code Sdk.java}, a body the host will not read, an expression the
     * user wrote themselves, and {@code Source.current()}, which in the editor names nothing to look at.
     * Read on every call rather than held: a source changed in another window has to take effect at once.
     */
    public static CaptureSource current(StudioServices services) {
        try {
            return open(services).flatMap(ctx -> ctx.value(CaptureSource.class))
                    .filter(source -> !(source instanceof CurrentSource))
                    .orElse(null);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * Points {@code Sdk.captureSource()} at {@code source}, or does nothing when there is no such method to
     * write to. Call it on the JavaFX application thread.
     *
     * @param source the project's capture source, or null for the whole desktop
     */
    public static void point(StudioServices services, CaptureSource source) {
        point(services, source, null);
    }

    /**
     * As {@link #point(StudioServices, CaptureSource)}, narrowed to {@code region} — a rectangle in the
     * <em>source's own</em> pixel space, so the narrowing survives the window moving. {@code null} is the
     * whole source, which is what almost every pick means.
     */
    public static void point(StudioServices services, CaptureSource source, java.awt.Rectangle region) {
        CaptureSource base = source == null ? CaptureSource.desktop() : source;
        CaptureSource value = region != null && region.width > 0 && region.height > 0
                ? CaptureSource.region(base, new Rect(region.x, region.y, region.width, region.height))
                : base;
        // The value: the host writes it through CaptureTypes, as the call the value is.
        open(services).ifPresent(ctx -> ctx.set(value));
    }
}
