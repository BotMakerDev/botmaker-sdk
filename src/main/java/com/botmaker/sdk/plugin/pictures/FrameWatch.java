package com.botmaker.sdk.plugin.pictures;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.capture.StillSource;
import com.botmaker.sdk.internal.vision.Images;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Watches one box of the live game until the thing in it has shown every pose it loops through, keeping one frame
 * per pose: what a moving picture ({@link FrameStrip}) is cut from, by the assistant's {@code crop_pictures} and
 * Studio's ◉ Watch alike. Headless: no JavaFX, no project.
 *
 * <p>The rule: each grab is a new pose when no kept frame matches it at the bot's confidence, and is then kept.
 * Watching stops once, since the last new pose, at least as long has passed as from the start to that pose, and at
 * least {@link #SHORTEST} in all (the loop has come round: {@link Ending#LOOP_CLOSED}); at {@link #LONGEST}; at a
 * pose past {@link #CAP}; or when the feed gives no frame.
 */
public final class FrameWatch {

    private FrameWatch() {}

    /** The most frames a watch keeps: a picture's. */
    public static final int CAP = FrameStrip.MOST;
    /** The least a watch lasts, so a slow loop's second pose is seen. */
    public static final Duration SHORTEST = Duration.ofSeconds(1);
    /** The most a watch lasts. */
    public static final Duration LONGEST = Duration.ofSeconds(10);
    /** Grabs a second under which a quick pose may slip between two grabs. */
    public static final int SLOW_FEED = 20;
    /** Empty grabs in a row that end a watch as {@link Ending#FEED_LOST}. */
    static final int MISSES = 5;

    /** Why a watch stopped. */
    public enum Ending {
        LOOP_CLOSED("its loop came round"),
        LONGEST_REACHED("it was watched the longest a watch lasts"),
        TOO_MANY_POSES("it showed more poses than a picture holds"),
        FEED_LOST("the game's picture went away");

        private final String displayName;

        Ending(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * What a watch kept: one frame per pose, frame 1 first; how many grabs it read and how fast; the time from the
     * start until frame 1's pose came back after another (empty when it never did); and why it stopped.
     */
    public record Watched(List<BufferedImage> frames, int grabs, double grabsPerSecond, Duration watched,
                          Optional<Duration> loop, Ending ending) {

        public Watched {
            frames = List.copyOf(frames);
        }

        /** Whether its frames make a picture: the loop closed, or the longest watch saw it out. */
        public boolean saveable() {
            return !frames.isEmpty() && (ending == Ending.LOOP_CLOSED || ending == Ending.LONGEST_REACHED);
        }

        /** Whether the feed was too slow to be sure no quick pose slipped by. */
        public boolean slow() {
            return grabsPerSecond < SLOW_FEED;
        }
    }

    /** {@code box} of each frame {@code feed} grabs, watched from nothing kept. */
    public static Watched watch(Supplier<Optional<BufferedImage>> feed, Rect box, double confidence,
                                InstantSource clock) {
        return watch(feed, box, confidence, clock, List.of());
    }

    /** As {@link #watch(Supplier, Rect, double, InstantSource)}, watching longer from the frames {@code kept}. */
    public static Watched watch(Supplier<Optional<BufferedImage>> feed, Rect box, double confidence,
                                InstantSource clock, List<BufferedImage> kept) {
        return poses(() -> feed.get().filter(f -> inside(f, box)).map(f -> Images.cut(f, box)), confidence, clock,
                kept);
    }

    /**
     * The watch itself, over {@code poses}: each one the box already cut out of a grab (its shape applied), empty when
     * the feed went away. A pose of another size than the first is the feed going away too: the game changed size.
     */
    public static Watched poses(Supplier<Optional<BufferedImage>> poses, double confidence, InstantSource clock,
                                List<BufferedImage> kept) {
        List<BufferedImage> frames = new ArrayList<>(kept);
        List<ImageTemplate> templates = new ArrayList<>();
        try {
            for (int i = 0; i < frames.size(); i++) templates.add(ImageTemplate.of("frame" + (i + 1), frames.get(i)));
            Instant start = clock.instant();
            Instant lastNew = start;
            Instant now = start;
            Duration loop = null;
            boolean awayFromFirst = false;
            int grabs = 0;
            int missed = 0;
            Ending ending;
            while (true) {
                Optional<BufferedImage> pose = poses.get();
                now = clock.instant();
                if (pose.isEmpty() || !frames.isEmpty() && !sameSize(pose.get(), frames.getFirst())) {
                    // One grab the capture had no frame for is a hiccup; several in a row, the game gone.
                    if (++missed < MISSES) continue;
                    ending = Ending.FEED_LOST;
                    break;
                }
                missed = 0;
                grabs++;
                int seen = matching(templates, pose.get(), confidence);
                if (seen < 0) {
                    if (frames.size() >= CAP) {
                        ending = Ending.TOO_MANY_POSES;
                        break;
                    }
                    frames.add(pose.get());
                    templates.add(ImageTemplate.of("frame" + frames.size(), pose.get()));
                    lastNew = now;
                    seen = frames.size() - 1;
                }
                if (seen != 0) awayFromFirst = true;
                else if (awayFromFirst && loop == null) loop = Duration.between(start, now);
                Duration all = Duration.between(start, now);
                if (all.compareTo(LONGEST) >= 0) {
                    ending = Ending.LONGEST_REACHED;
                    break;
                }
                if (all.compareTo(SHORTEST) >= 0
                        && Duration.between(lastNew, now).compareTo(Duration.between(start, lastNew)) >= 0) {
                    ending = Ending.LOOP_CLOSED;
                    break;
                }
            }
            Duration watched = Duration.between(start, now);
            double seconds = watched.toNanos() / 1e9;
            return new Watched(frames, grabs, seconds <= 0 ? 0 : grabs / seconds, watched, Optional.ofNullable(loop),
                    ending);
        } finally {
            templates.forEach(ImageTemplate::close);
        }
    }

    /** The first kept frame {@code pose} matches at {@code confidence}, the best of them; -1 when none does. */
    private static int matching(List<ImageTemplate> templates, BufferedImage pose, double confidence) {
        int best = -1;
        double score = confidence;
        StillSource source = new StillSource(pose);
        for (int i = 0; i < templates.size(); i++) {
            MatchResult match = ImageFinder.bestMatch(templates.get(i), source);
            if (match.isFound() && match.confidence() >= score) {
                best = i;
                score = match.confidence();
            }
        }
        return best;
    }

    private static boolean inside(BufferedImage frame, Rect box) {
        return box.x() >= 0 && box.y() >= 0 && box.x() + box.width() <= frame.getWidth()
                && box.y() + box.height() <= frame.getHeight();
    }

    private static boolean sameSize(BufferedImage a, BufferedImage b) {
        return a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight();
    }
}
