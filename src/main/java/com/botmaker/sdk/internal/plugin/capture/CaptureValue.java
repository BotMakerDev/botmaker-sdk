package com.botmaker.sdk.internal.plugin.capture;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.ValueContext;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.authoring.CaptureTargetModel;

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
     * Points {@code Sdk.captureSource()} at {@code target}, or does nothing when there is no such method to
     * write to. Call it on the JavaFX application thread.
     *
     * @param target the project's default target, or null for the whole desktop
     */
    /*
     * One target writes an expression the value codec will not read back: an emulator, which is
     * `new EmulatorSource("…")` rather than one of CaptureSource's three factories. That is deliberate and
     * harmless — it is correct Java, it compiles, and the bot captures from the emulator. The only
     * consequence is that a picker shown over it says the value was written by hand, which is the safe way
     * round: the editor declines to replace an expression it cannot spell rather than replacing it wrongly.
     */
    public static void point(StudioServices services, CaptureTargetModel target) {
        // Fully qualified, as every initialiser this platform writes is, so the expression compiles wherever
        // the user has moved the method to and no import has to be added beside it.
        write(services, CaptureExpr.of(target));
    }

    /** Writes {@code expression} — a {@link CaptureSource} factory call — where there is somewhere to write. */
    static void write(StudioServices services, String expression) {
        open(services).ifPresent(ctx -> ctx.set(expression));
    }
}
