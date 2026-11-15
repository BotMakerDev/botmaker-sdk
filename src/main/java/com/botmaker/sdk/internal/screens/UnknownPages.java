package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.emulator.ReadableSource;
import com.botmaker.sdk.internal.trace.MissFrames;
import com.botmaker.sdk.internal.trace.SdkRun;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * A page no screen of the run's map matched, saved masked in the run's folder ({@link PageUnknown}) so the bot's
 * author can map it from the run's report. Saved once per stretch the run spends lost: when it has been lost for
 * {@link #LOST}, or at once when the bot asks the user to look ({@link #now}). Only a run with a folder saves, and only
 * from a source that reads trees: a game window has no page to save.
 *
 * <p>The tree is masked before it leaves memory: Android's status and navigation bars are dropped, a field keeps no
 * value, and a text holding an {@code @} or a digit keeps only its length ({@link PageUnknown#masked}).
 */
public final class UnknownPages {

    /** How long the run is lost before the page it is on is saved. */
    public static final Duration LOST = Duration.ofSeconds(10);
    /** The key of the run's line value naming the page's file. */
    public static final String VALUE = "pageUnknown";
    private static final String SOURCE = "Screens";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final List<String> BARS = List.of("status_bar", "statusBarBackground", "navigation_bar",
            "navigationBarBackground");

    private static LongSupplier clock = System::currentTimeMillis;
    private static Supplier<Optional<Path>> folder = MissFrames::folder;
    private static Supplier<Optional<UiTree>> trees = UnknownPages::tree;
    /** The run folder the state below belongs to: another run starts it afresh. */
    private static Path stretchRun;
    /** When the run last looked where it is, or -1. */
    private static long lastLook = -1;
    /** When the run got lost, or -1 while it is on a known screen. */
    private static long lostSince = -1;
    /** Whether this stretch's page was tried: saved, or its tree could not be read. One try a stretch. */
    private static boolean saved;

    private UnknownPages() {}

    /**
     * The run looked where it is: {@code known} when a screen matched. Saves the page once it was lost {@link #LOST}.
     * A stretch is lost only while looked at: a gap of more than {@link #LOST} between two looks starts a new one.
     */
    public static void seen(boolean known) {
        Optional<Path> run = folder.get();
        if (run.isEmpty()) return;
        synchronized (UnknownPages.class) {
            long now = look(run.get());
            if (known) {
                lostSince = -1;
                saved = false;
                return;
            }
            if (lostSince < 0) lostSince = now;
            if (saved || now - lostSince < LOST.toMillis()) return;
            saved = true;
        }
        save(run.get());
    }

    /** The bot asks the user to look at a page it doesn't know: the page is saved now, unless this stretch's was. */
    public static void now() {
        Optional<Path> run = folder.get();
        if (run.isEmpty()) return;
        synchronized (UnknownPages.class) {
            long now = look(run.get());
            if (saved) return;
            if (lostSince < 0) lostSince = now;
            saved = true;
        }
        save(run.get());
    }

    /** Notes a look in run {@code run} now, the state started afresh for a new run or after a gap; the time. */
    private static long look(Path run) {
        long now = clock.getAsLong();
        if (!run.equals(stretchRun) || lastLook >= 0 && now - lastLook > LOST.toMillis()) {
            stretchRun = run;
            lostSince = -1;
            saved = false;
        }
        lastLook = now;
        return now;
    }

    private static void save(Path run) {
        Optional<UiTree> tree;
        try {
            tree = trees.get();
        } catch (RuntimeException e) {
            return;
        }
        if (tree.isEmpty()) return;
        try {
            Optional<Path> file = masked(tree.get(), clock.getAsLong()).writeTo(run);
            file.ifPresent(f -> SdkRun.info(new SdkRun.Origin(SOURCE, "", ""),
                    "No screen of the map is this page; saved it, masked, for its report: " + f.getFileName(),
                    Map.of(VALUE, f.getFileName().toString())));
        } catch (IOException | RuntimeException e) {
            SdkRun.warn(SOURCE, "A page no screen matched could not be saved: " + e.getMessage());
        }
    }

    /** {@code tree} as a page keeps it, masked; Android's bars dropped. */
    public static PageUnknown masked(UiTree tree, long atMillis) {
        return new PageUnknown(atMillis, tree.roots().stream().map(UnknownPages::node).filter(Objects::nonNull)
                .toList());
    }

    private static PageUnknown.Node node(UiNode node) {
        if (SYSTEM_UI.equals(node.packageName()) || BARS.contains(node.shortId())) return null;
        Rectangle b = node.bounds();
        Rect rect = b.isEmpty() ? null : new Rect(b.x, b.y, b.width, b.height);
        String type = node.className();
        boolean field = ElementMatch.isField(node) || node.has(Trait.PASSWORD);
        return new PageUnknown.Node(node.shortId(), type.substring(type.lastIndexOf('.') + 1), node.text(),
                node.label(), field, rect,
                node.children().stream().map(UnknownPages::node).filter(Objects::nonNull).toList());
    }

    private static Optional<UiTree> tree() {
        return Source.current().base() instanceof ReadableSource source ? source.tree() : Optional.empty();
    }

    /** Tests only: the clock, the run's folder and the tree, and a run not lost. */
    static synchronized void use(LongSupplier now, Supplier<Optional<Path>> run, Supplier<Optional<UiTree>> tree) {
        clock = now;
        folder = run;
        trees = tree;
        stretchRun = null;
        lastLook = -1;
        lostSince = -1;
        saved = false;
    }
}
