package com.botmaker.sdk.plugin.screen;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.ManagedHandle;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.capture.CurrentSource;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.launch.WindowIdentity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code @SdkValue(SdkValue.Id.CAPTURE)} value — where a bot reads pixels from, written as the one expression
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

    /** The value, declared once in {@link SdkValues}. */
    public static final ManagedHandle<CaptureSource> CAPTURE = ManagedHandle.of(SdkValues.CAPTURE);

    private CaptureValue() {}

    /**
     * The project's capture source, or {@code null} when its Java names none the host can read.
     *
     * <p>{@code null} covers an absent {@code Sdk.java}, a body the host will not read, an expression the
     * user wrote themselves, and {@code Source.current()}, which in the editor names nothing to look at.
     * Read on every call rather than held: a source changed in another window has to take effect at once.
     */
    public static CaptureSource current(StudioServices services) {
        return CAPTURE.read(services).filter(source -> !(source instanceof CurrentSource)).orElse(null);
    }

    /**
     * The overlay's ⌖ Point bot here press: writes the window the overlay is drawn over into this project's
     * capture source.
     *
     * <p><b>This is the direction the fact travels, and it only travels this way.</b> The host tells the
     * plugin which window its own HUD is drawn over — something only the host can know — and the plugin
     * writes it into the bot's own Java. Studio holds no capture source of its own; see
     * {@code docs/refactor/28-overlay-items.md}.
     */
    public static void pointHere(ActionContext context) {
        StudioServices services = context.services();
        String title = context.overWindowTitle().orElse(null);
        if (title == null) {
            services.status("Nothing to point at — the overlay is not over a window.");
            return;
        }
        Optional<CaptureSource> window = windowTitled(title);
        if (window.isEmpty()) {
            services.status("\"" + title + "\" has no window class to find it by — pick it in 🎯 Capture Source.");
            return;
        }
        point(services, window.get());
        services.status("Capture source is now \"" + title + "\".");
    }

    /**
     * The open window titled {@code title} as a source, by its class and, when that class is generic, its title
     * ({@link WindowIdentity}); empty when no such window is open, it has no class, or the windows cannot be listed.
     * Only {@linkplain OpenWindows#pickable pickable} windows, and the exact title ignoring case: a nearest title
     * would pick "#tools | Firestone Idle RPG - Discord" for "Firestone".
     */
    public static Optional<CaptureSource> windowTitled(String title) {
        if (title == null || title.isBlank()) return Optional.empty();
        List<GenericWindow> open;
        try {
            open = OpenWindows.pickable(NativeControllerFactory.get().getAllWindows());
        } catch (RuntimeException | LinkageError noWindows) {
            return Optional.empty();
        }
        return open.stream()
                .filter(w -> w.getTitle().trim().equalsIgnoreCase(title.trim()))
                .map(WindowIdentity::of).filter(Objects::nonNull)
                .findFirst().map(NamedWindow::of);
    }

    /**
     * Points {@code Sdk.captureSource()} at {@code source}, asking the host to create {@code Sdk.java} first
     * when the project has none; does nothing when there is still no such method to write to. Call it on the
     * JavaFX application thread.
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
        // A pick is the user asking for a source to be kept, so a project with no Sdk.java gets one first.
        CAPTURE.write(services, value).ifPresent(refused -> {
            if (services != null) services.status(refused);
        });
    }
}
