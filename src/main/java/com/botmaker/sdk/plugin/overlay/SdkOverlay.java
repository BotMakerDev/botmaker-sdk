package com.botmaker.sdk.plugin.overlay;

import com.botmaker.plugin.api.overlay.OverlayContext;
import com.botmaker.plugin.api.overlay.OverlayPart;
import com.botmaker.plugin.api.overlay.Probe;
import com.botmaker.plugin.api.overlay.Watched;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.vision.ImageClicker;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.ImageWaiter;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.capture.SessionSource;
import com.botmaker.sdk.internal.capture.Window;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.source.SourcePicker;

import java.time.Duration;
import java.util.Optional;

/**
 * The SDK's part of the overlay editor.
 *
 * <ul>
 *   <li><b>Targets:</b> every activity body, by type. A bot passes each one to {@code Flow.activity} as an
 *       {@link ActivityBody} method reference, so Studio offers {@code Collect.body()} as a chip.</li>
 *   <li><b>Watched:</b> the screen {@code Sdk.captureSource()} names. ⇄ Change opens the 🎯 Capture Source
 *       picker.</li>
 *   <li><b>Tools:</b> {@link SdkTools}.</li>
 *   <li><b>Probes:</b> {@link SdkProbes}, on the one-picture calls. A click's probe is
 *       {@link Probe#acting}: it says where the click would land, and Try never computes one.</li>
 * </ul>
 *
 * <p>Building {@link #PART} links JavaFX through the tool panes, so it sits behind
 * {@code PluginDeclaration.overlay}'s supplier.
 */
public final class SdkOverlay {

    public static final OverlayPart PART = OverlayPart.of()
            .targets(ActivityBody.class, "Activities")
            .watched(SdkOverlay::watched)
            .changeWatched(() -> SourcePicker::choose)
            .tool(SdkTools.PICTURE)
            .tool(SdkTools.POINT)
            .tool(SdkTools.FLOW)
            .probe(ImageFinder::find, ImageTemplate.class, SdkProbes::find)
            .probe(ImageWaiter::waitFor, ImageTemplate.class, Duration.class, SdkProbes::find)
            .probe(ImageWaiter::waitUntilGone, ImageTemplate.class, Duration.class, SdkProbes::gone)
            .probe(ImageClicker::click, ImageTemplate.class, Probe.acting(SdkProbes::wouldClick))
            .probe(ImageWaiter::waitAndClick, ImageTemplate.class, Duration.class, Probe.acting(SdkProbes::wouldClick));

    private SdkOverlay() {}

    /** The screen {@code Sdk.captureSource()} names, when it is one the overlay can open over. */
    public static Optional<Watched> watched(OverlayContext context) {
        return of(CaptureValue.current(context.services()));
    }

    /**
     * {@code source} as a screen Studio can find.
     *
     * <ul>
     *   <li>A window is found by its title.</li>
     *   <li>The private display is the session.</li>
     *   <li>A region keeps the screen it narrows, so the panel docks beside the whole window.</li>
     *   <li>A monitor, the desktop and an emulator are empty, so Studio asks which window to use.</li>
     * </ul>
     */
    static Optional<Watched> of(CaptureSource source) {
        if (source == null) return Optional.empty();
        CaptureSource base = source.base();
        if (base instanceof NamedWindow named) return Optional.of(Watched.window(named.titleSubstring()));
        if (base instanceof Window window && window.title() != null && !window.title().isBlank()) {
            return Optional.of(Watched.window(window.title()));
        }
        if (base instanceof SessionSource) return Optional.of(Watched.session());
        return Optional.empty();
    }
}
