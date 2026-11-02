package com.botmaker.sdk.api.vision;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.internal.vision.BotScreen;
import com.botmaker.sdk.internal.vision.TemplateCache;
import com.botmaker.shared.opencv.OpenCvNative;
import com.botmaker.shared.opencv.OpencvManager;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Public handle for a template image used by the vision API.
 *
 * <p>It holds the file path and the id derived from it, and lazily owns the underlying OpenCV {@link Mat}. The
 * {@code Mat} is loaded from disk on first use and released by {@link #unload()} / {@link #close()}. How sure a
 * match must be is {@code BotSettings}' confidence, not the template's: the per-template threshold was never
 * read by a matcher nor set by the picture picker, and was deleted on 2026-10-01.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): the members that describe the template are offered
 * ({@link #id()}, {@link #filePath()}, {@link #width()}, {@link #height()}). {@link #unload()} and
 * {@link #close()} are hidden as the pair they are: they are memory management for a {@code Mat} the bot
 * cannot see, on a handle whose loading is lazy precisely so nobody has to think about it. A bot that offers
 * them a menu entry is being invited to release image data it did not know it had allocated, and the failure
 * mode is a silent reload rather than an error — which is to say, nothing the user could learn from.
 * {@code AutoCloseable} is implemented for the matchers' own try-with-resources, not for a bot to call.
 */
public class ImageTemplate implements AutoCloseable {

    private final String filePath;
    private final String id;
    /** The frame it was cut from when that is not the bot's screen size; null when it is. */
    private final Size frame;
    /** Where in that frame it was cut; null when unknown. */
    private final Rect place;
    /** The pixels themselves, for a picture held in memory and never saved; null for a file's. */
    private final BufferedImage image;
    /** The tags 🖼 Manage Pictures files it under; a run never reads them. */
    private final List<PictureTag> tags;
    /** The activities 🖼 Manage Pictures files it under; a run never reads them. */
    private final List<Activity> activities;
    /** How many frames its file holds side by side; 1 for a picture that holds still. */
    private final int frames;

    // Lazily-loaded OpenCV image data: the whole file, then one view of it per frame. Null until first asked for.
    private Mat mat;
    private List<Mat> frameMats;

    /**
     * Constructor using file path.
     * @param filePath Path to the image (e.g. "images/accept_button.png")
     */
    public ImageTemplate(String filePath) {
        this(filePath, null, null, null);
    }

    /**
     * A picture held in memory, matched as one read from a file would be: the assistant checks a cut before it is
     * saved without writing it to disk. {@code name} stands for the file's path and is its {@link #id()}.
     */
    @Hidden("the assistant's check of a picture it has not saved yet; a bot names its pictures by file")
    public static ImageTemplate of(String name, BufferedImage image) {
        return new ImageTemplate(name, null, null, Objects.requireNonNull(image, "image"));
    }

    private ImageTemplate(String filePath, Size frame, Rect place, BufferedImage image) {
        this(filePath, frame, place, image, List.of(), List.of(), 1);
    }

    private ImageTemplate(String filePath, Size frame, Rect place, BufferedImage image, List<PictureTag> tags,
                          List<Activity> activities, int frames) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("File path cannot be empty");
        }
        if (frames < 1) {
            throw new IllegalArgumentException("A picture has at least one frame, not " + frames);
        }
        this.frames = frames;
        this.filePath = filePath;
        this.frame = frame == null || frame.empty() ? null : frame;
        this.place = place == null || place.empty() ? null : place;
        this.image = image;
        this.tags = tags == null ? List.of() : tags.stream().filter(Objects::nonNull).distinct().toList();
        this.activities = activities == null ? List.of()
                : activities.stream().filter(a -> a != null && !a.isNone()).distinct().toList();

        // Extract ID from filename: "images/btn_ok.png" -> "btn_ok"
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        this.id = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    public String id() {
        return id;
    }

    public String filePath() {
        return filePath;
    }

    /**
     * This picture, cut at {@code place} in its frame — {@code new ImageTemplate("…").at(new Rect(820, 610, 96,
     * 40))}. Written by the windows that cut it, so where a picture belongs is in the bot's Java.
     */
    public ImageTemplate at(Rect place) {
        return new ImageTemplate(filePath, frame, place, image, tags, activities, frames);
    }

    /**
     * This picture, cut from a frame of {@code frame} rather than the bot's screen size ({@code Sdk.screen()}):
     * the matcher rescales it by the live frame against this size. Written only when the two differ.
     */
    public ImageTemplate in(Size frame) {
        return new ImageTemplate(filePath, frame, place, image, tags, activities, frames);
    }

    /**
     * This picture, filed under {@code tags} in 🖼 Manage Pictures — {@code .tags(List.of(Tags.UI))}, each a constant
     * of the bot's {@code Tags} class. A run never reads them.
     */
    public ImageTemplate tags(List<PictureTag> tags) {
        return new ImageTemplate(filePath, frame, place, image, tags, activities, frames);
    }

    /**
     * This picture, filed under the activities it belongs to in 🖼 Manage Pictures —
     * {@code .activities(List.of(Activities.Collect.class))}, each an activity's enum, so the group follows a rename
     * and goes with the activity. A run never reads them.
     */
    public ImageTemplate activities(List<Class<? extends Outcome>> activities) {
        return new ImageTemplate(filePath, frame, place, image, tags,
                activities == null ? List.of() : activities.stream().map(Activity::of).toList(), frames);
    }

    /** {@link #activities(List)} by the activities' names, as an editor holds them: it cannot load the bot's enums. */
    @Hidden("an editor's way of filing a picture; a bot names its activities by their enums")
    public ImageTemplate inActivities(List<Activity> activities) {
        return new ImageTemplate(filePath, frame, place, image, tags, activities, frames);
    }

    /**
     * This picture, a thing that moves, held as {@code count} frames side by side in its file —
     * {@code new ImageTemplate("images/plant.png").at(…).frames(4)}: an idle monster, a pulsing highlight. It is found
     * wherever any frame matches, each at the bot's confidence, and its size is one frame's. Frames carry no meaning
     * and none is told from another; a button's picture per state is a {@code Look}, not a frame. Written by the
     * windows and tools that watch it.
     *
     * @throws IllegalArgumentException when {@code count} is under 1
     */
    public ImageTemplate frames(int count) {
        return new ImageTemplate(filePath, frame, place, image, tags, activities, count);
    }

    /** How many frames it holds; 1 for a picture that holds still. */
    public int frames() {
        return frames;
    }

    /** The tags it is filed under; empty when none. */
    public List<PictureTag> tags() {
        return tags;
    }

    /** The activities it is filed under; empty when none. */
    public List<Activity> activities() {
        return activities;
    }

    /** Where in its frame it was cut, or null when that is unknown. */
    public Rect place() {
        return place;
    }

    /** The frame it was cut from when that is not the bot's screen size, else null. */
    public Size frame() {
        return frame;
    }

    /**
     * Returns the OpenCV image data, loading it from disk on first access.
     * The returned {@link Mat} is owned by this template — do not release it directly; use
     * {@link #unload()} instead.
     *
     * <p><b>Package-private since 1.1.0, deliberately.</b> {@code Mat} is {@code org.opencv.core}'s type, and
     * a public method returning it put a third-party class the SDK does not version into the surface that is
     * under contract from 1.1.0 — an OpenCV upgrade could then break a bot with nothing here to notice. Every
     * caller is a matcher in this package ({@link ImageFinder}, {@link ImageClicker}) plus this package's own
     * tests; no bot has ever had a reason to hold a {@code Mat}. A bot that genuinely needs the pixels should
     * be given an SDK-owned type instead, which stays possible as an addition at any time.
     *
     * <p><b>The OpenCV native is loaded here rather than in a {@code static {}} block.</b> This class links
     * {@code Mat} in exactly one method, and holding a template is something a bot does without ever asking
     * for its pixels, so a class-initialiser load would make every bot that merely names a picture extract
     * and link the native library. {@link OpenCvNative#ensureLoaded()} is idempotent and
     * synchronized, so paying it per call costs a volatile read after the first one.
     */
    Mat getMat() {
        return frameMats().getFirst();
    }

    /**
     * One {@link Mat} per frame, in the file's order, each a view of the file's pixels {@link #getMat()} also reads
     * (for a still picture, the file itself): the matcher matches each and keeps the best. Owned by this template,
     * as {@code getMat()}'s is.
     *
     * @throws IllegalStateException when the file's width is not {@link #frames()} times a frame's
     */
    synchronized List<Mat> frameMats() {
        // Locked: a Pictures constant is one object every thread of a run shares (the guard and the body, the
        // matcher's pool), and two first reads at once each decoded it, the later one leaking the other's pixels.
        OpenCvNative.ensureLoaded();
        if (mat == null || mat.empty()) {
            // The file as written first, then the classpath (TemplateSource says why): a bot run from an IDE
            // rooted elsewhere, or from its own jar, has no src/main/resources relative to its working dir.
            // TemplateCache decodes each file once while it stays unchanged on disk.
            releaseFrames();
            mat = image != null ? OpencvManager.bufferedImageToMat(image) : TemplateCache.load(filePath);
        }
        if (frameMats == null) {
            if (frames == 1) {
                frameMats = List.of(mat);
            } else {
                if (mat.cols() % frames != 0) {
                    throw new IllegalStateException(filePath + " is " + mat.cols() + " px wide: " + frames
                            + " frames need a width that is a multiple of " + frames);
                }
                int width = mat.cols() / frames;
                List<Mat> each = new ArrayList<>(frames);
                for (int i = 0; i < frames; i++) each.add(mat.colRange(i * width, (i + 1) * width));
                frameMats = List.copyOf(each);
            }
        }
        return frameMats;
    }

    /** Releases the frames' views of the file; the file's own pixels stay. */
    private void releaseFrames() {
        if (frameMats != null && frames > 1) frameMats.forEach(Mat::release);
        frameMats = null;
    }

    /**
     * The resolution the matcher rescales against: the frame this template was cut from ({@link #in}), else the
     * bot's screen size, else {@code null} when the bot names none and the template is matched as it is.
     *
     * <p>A {@link java.awt.Dimension} because that is the form shared's matcher takes ({@code shared.opencv}
     * cannot see the SDK's {@code Size}). This one method is the whole SDK↔shared mapping for authored
     * resolution; the matching call sites go through it rather than each converting.
     */
    java.awt.Dimension authoredSize() {
        return frame != null ? new java.awt.Dimension(frame.width(), frame.height()) : BotScreen.dimension();
    }

    public int width() {
        return getMat().cols();
    }

    public int height() {
        return getMat().rows();
    }

    /**
     * Releases the underlying image memory. Safe to call repeatedly; the Mat is reloaded on the
     * next {@link #getMat()}.
     */
    @Hidden("memory management the runtime does on its own; a bot that unloads a template mid-run "
            + "pays a reload on the next find and gains nothing")
    public synchronized void unload() {
        releaseFrames();
        if (mat != null) {
            mat.release();
            mat = null;
        }
    }

    @Override
    @Hidden("the AutoCloseable half of unload(), and hidden for the same reason")
    public void close() {
        unload();
    }

    /**
     * The same file, cut at the same place from the same frame, filed under the same tags and activities; the loaded
     * pixels are not compared, and a picture held in memory equals only one holding the same image.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof ImageTemplate that && filePath.equals(that.filePath)
                && Objects.equals(frame, that.frame) && Objects.equals(place, that.place) && image == that.image
                && frames == that.frames && Set.copyOf(tags).equals(Set.copyOf(that.tags))
                && Set.copyOf(activities).equals(Set.copyOf(that.activities));
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, frame, place, frames);
    }

    @Override
    public String toString() {
        return "ImageTemplate{id='" + id + "', path='" + filePath + "'"
                + (place == null ? "" : ", at=" + place) + (frame == null ? "" : ", in=" + frame)
                + (frames == 1 ? "" : ", frames=" + frames) + "}";
    }
}
