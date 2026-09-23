package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.record.RecordedValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Vision;
import com.botmaker.sdk.authoring.TemplateLibrary;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The project picture under a recorded click — what turns a click on the Collect button into
 * {@code ImageClicker.click(Pictures.COLLECT)} rather than a click at a spot.
 *
 * <p>Every picture in the project is matched against the window as it looked just before the click, and the
 * best match whose rectangle holds the click wins. A picture that is merely somewhere on screen does not count:
 * the recording is of what was clicked.
 */
public final class PictureAt implements RecordedValue<ImageTemplate> {

    /** How sure a match must be to name the thing clicked. Stricter than a bot's default: a wrong guess is code. */
    private static final double CONFIDENCE = 0.9;

    @Override
    public Class<ImageTemplate> type() {
        return ImageTemplate.class;
    }

    @Override
    public Optional<ImageTemplate> at(StudioServices services, Spot spot) {
        if (spot == null || spot.frame() == null || services == null) return Optional.empty();
        Path resources = services.resourcesDir();
        if (resources == null) return Optional.empty();
        CaptureSource frame = new Frame(spot.frame());
        Path best = null;
        double bestScore = 0;
        for (Path file : TemplateLibrary.list(resources)) {
            if (TemplateLibrary.isUnmodifiedDefaultTemplate(file)) continue;
            try (ImageTemplate template = new ImageTemplate(file.toAbsolutePath().toString())) {
                if (!ImageFinder.find(template, frame, CONFIDENCE)) continue;
                MatchResult match = Vision.lastMatch();
                if (match == null || !contains(match.rect(), spot.x(), spot.y())) continue;
                if (match.confidence() > bestScore) {
                    bestScore = match.confidence();
                    best = file;
                }
            } catch (RuntimeException | LinkageError e) {
                // A picture that will not load or match is simply not what was clicked.
            }
        }
        return best == null ? Optional.empty() : Optional.of(new ImageTemplate(TemplateLibrary.pathFor(best)));
    }

    private static boolean contains(Rect rect, int x, int y) {
        return rect != null && x >= rect.x() && y >= rect.y()
                && x < rect.x() + rect.width() && y < rect.y() + rect.height();
    }

    /** The grabbed frame as a source whose origin is its own top-left, so a match is in window pixels. */
    private record Frame(BufferedImage image) implements CaptureSource {

        @Override
        public BufferedImage capture() {
            return image;
        }

        @Override
        public Point origin() {
            return new Point(0, 0);
        }

        @Override
        public void click(Point p) {
        }
    }
}
