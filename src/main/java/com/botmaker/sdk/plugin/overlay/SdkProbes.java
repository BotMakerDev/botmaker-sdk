package com.botmaker.sdk.plugin.overlay;

import com.botmaker.plugin.api.overlay.ProbeContext;
import com.botmaker.plugin.api.overlay.ProbeResult;
import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.plugin.toolkit.ManagedHandle;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.bot.SdkValues;

import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Optional;

/**
 * What the SDK's picture calls would answer now, on the frame the overlay editor watches — the probes
 * {@link SdkOverlay} declares.
 *
 * <p>Each reads the call's picture, matches it once with {@link ImageFinder#bestMatch} and compares the score with
 * the <em>project's</em> confidence ({@code Sdk.settings()}), not the confidence of the Studio process, which runs
 * no bot. Nothing here clicks: {@link #wouldClick} says where a click would land.
 *
 * <p>Links no JavaFX; called off the FX thread, up to a few times a second.
 */
public final class SdkProbes {

    private static final ManagedHandle<BotSettings> SETTINGS = ManagedHandle.of(SdkValues.SETTINGS);

    private SdkProbes() {}

    /** {@code ImageFinder.find(picture)} and the waits: is the picture on screen now, and where. */
    public static ProbeResult find(ProbeContext context) {
        return match(context).map(m -> m.found()
                        ? ProbeResult.found("found " + score(m.result) + " at " + at(m.result), area(m.result))
                        : ProbeResult.missing("not found — best " + score(m.result) + ", needs " + score(m.needs)))
                .orElseGet(() -> ProbeResult.unknown(why(context)));
    }

    /** {@code ImageFinder.waitUntilGone(picture, …)}: found means it would still be waiting. */
    public static ProbeResult gone(ProbeContext context) {
        return match(context).map(m -> m.found()
                        ? ProbeResult.missing("still there (" + score(m.result) + " at " + at(m.result) + ")")
                        : ProbeResult.found("gone — best " + score(m.result), null))
                .orElseGet(() -> ProbeResult.unknown(why(context)));
    }

    /** {@code ImageClicker.click(picture)} and {@code waitAndClick}: where the click would land. Never clicks. */
    public static ProbeResult wouldClick(ProbeContext context) {
        return match(context).map(m -> {
            if (!m.found()) {
                return ProbeResult.missing("would not click — best " + score(m.result) + ", needs " + score(m.needs));
            }
            Point center = m.result.center();
            return ProbeResult.found("would click " + center.x() + "," + center.y() + " (" + score(m.result) + ")",
                    area(m.result));
        }).orElseGet(() -> ProbeResult.unknown(why(context)));
    }

    /** One best match and the confidence it is held to. */
    private record Matched(MatchResult result, double needs) {
        boolean found() {
            return result.confidence() >= needs;
        }
    }

    private static Optional<Matched> match(ProbeContext context) {
        Optional<ImageTemplate> picture = context.argument(0, ImageTemplate.class);
        Optional<BufferedImage> frame = context.frame();
        if (picture.isEmpty() || frame.isEmpty()) return Optional.empty();
        Area where = context.watchedArea().orElse(new Area(0, 0, frame.get().getWidth(), frame.get().getHeight()));
        MatchResult best;
        // Released after each probe: a probe runs twice a second, and an unreleased template is native memory.
        // A template closed here loads again on its next use.
        try (ImageTemplate template = picture.get()) {
            best = ImageFinder.bestMatch(template, new Frame(frame.get(), new Point(where.x(), where.y())));
        }
        if (!best.isFound()) return Optional.empty();
        double needs = SETTINGS.read(context.services()).orElse(BotSettings.DEFAULTS).confidence();
        return Optional.of(new Matched(best, needs));
    }

    private static String why(ProbeContext context) {
        if (context.argument(0, ImageTemplate.class).isEmpty()) return "the picture is not one the overlay can read";
        if (context.frame().isEmpty()) return "the watched screen cannot be captured";
        return "the picture could not be matched";
    }

    private static Area area(MatchResult result) {
        Rect rect = result.rect();
        return new Area(rect.x(), rect.y(), rect.width(), rect.height());
    }

    private static String at(MatchResult result) {
        return result.rect().x() + "," + result.rect().y();
    }

    private static String score(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String score(MatchResult result) {
        return score(result.confidence());
    }

    /**
     * The probed frame as a source whose origin is where it sits in the bot's pixels — the desktop's, or a private
     * session's own — so matches are in the pixels the bot clicks.
     */
    record Frame(BufferedImage image, Point origin) implements CaptureSource {

        @Override
        public BufferedImage capture() {
            return image;
        }

        @Override
        public void click(Point p) {
        }
    }
}
