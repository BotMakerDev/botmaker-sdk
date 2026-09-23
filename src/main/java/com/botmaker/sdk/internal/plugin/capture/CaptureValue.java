package com.botmaker.sdk.internal.plugin.capture;

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
 * <h2>Why the properties file is still written too</h2>
 *
 * <p>Making a target the project's default has always written two places: {@code capture.json}, which is the
 * list of targets and belongs to the Capture Targets window, and {@code botmaker-project.properties}'
 * {@code capture.source}, which is the bot's side of the same question. This adds a third, and it is the one
 * that matters: {@code Sdk.captureSource()} is Java the bot compiles, so a developer with no BotMaker
 * installed can read it, change it and see the change take effect.
 *
 * <p>The properties key is not a second answer either. Both are written by this one code path at one
 * instant, from the same target, and the Remote Pilot and the host's own tooling still read the key. What
 * retires it is the phase that stops a bot reading it, not this one.
 *
 * <h2>It is allowed to fail quietly</h2>
 *
 * <p>A project may have no {@code Sdk.java} — the SDK was added before this file existed, or the user
 * deleted it — and a user may have written {@code captureSource()} by hand. In both cases there is nothing
 * to write and nothing has gone wrong: the target is still set, the properties key still says so, and the
 * Java the user wrote is still theirs. Saying "couldn't point the bot at that window" would be false.
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
