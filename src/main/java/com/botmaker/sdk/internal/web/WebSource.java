// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.web;

import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.internal.emulator.ReadableSource;
import com.botmaker.shared.emulator.tree.UiTree;
import com.botmaker.shared.tools.Downloads;
import com.botmaker.shared.web.Budget;
import com.botmaker.shared.web.Chromium;
import com.botmaker.shared.web.ChromiumInstall;
import com.botmaker.shared.web.Consent;
import com.botmaker.shared.web.ConsoleMessage;
import com.botmaker.shared.web.Guard;
import com.botmaker.shared.web.Pace;
import com.botmaker.shared.web.Presses;
import com.botmaker.shared.web.Robots;
import com.botmaker.shared.web.Scope;
import com.botmaker.shared.web.WebNode;
import com.botmaker.shared.web.FormProbe;
import com.botmaker.shared.web.WebPage;
import com.botmaker.shared.web.WebTree;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A website as a run sees and acts on it: one page of BotMaker's own Chromium, under the read-only {@link Guard}
 * (2026-11-18, spec {@code 2026-11-18-web-discovery-design.md}). A {@code web:} launch target makes it the run's
 * capture source, so the screen map, its walk and <em>Keep exploring</em> drive a website as they drive an emulator:
 * a look is the page's frame, the tree is the page's accessibility tree ({@link WebTree#asUiTree}), a click presses
 * the node under the point.
 *
 * <p>Every press goes through the guard: one {@link Presses} refuses (a submit, a buy) is not made, and its refusal is
 * the press's outcome; a way off the site, a write and a path {@code robots.txt} disallows are stopped and said. Actions
 * are paced ({@link Pace}), page loads counted against a {@link Budget} whose end stops the run, and a cookie banner is
 * answered on each look ({@link Consent}): rejected when it offers a way to, else the run stops naming it.
 *
 * <p>The browser starts at the first look or press, not when the source is made, and closes when the source is
 * closed or the run's JVM ends.
 */
public final class WebSource implements CaptureSource, ReadableSource, AutoCloseable {

    /**
     * What a source is built with: Chromium's binary and extra switches, the rules it reads {@code robots.txt} by, its
     * pace and budget, and what ends the run. A test gives its own; a run takes {@link #defaults}.
     */
    public record Options(Supplier<Optional<Path>> chromium, List<String> switches, Robots robots, Pace pace,
                          Budget budget, Consumer<String> stop) {

        public Options {
            switches = List.copyOf(switches);
        }

        /** Chromium installed into the cache, robots read over HTTP, the guard's own pace and budget. */
        public static Options defaults() {
            String version = WebSource.class.getPackage().getImplementationVersion();
            return new Options(() -> ChromiumInstall.install(Downloads.Progress.IGNORED), List.of(),
                    Robots.over(version), new Pace(), new Budget(), Bot::stop);
        }
    }

    /** The run property showing the browser in a window rather than headless ({@code Discover --show}). */
    public static final String SHOW = "botmaker.web.show";

    private static final Object CURRENT_LOCK = new Object();
    private static WebSource current;

    static {
        // One hook for the JVM, closing the source that is current then: a closed one holds nothing to close.
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
            WebSource last;
            synchronized (CURRENT_LOCK) {
                last = current;
            }
            if (last != null) last.close();
        }));
    }

    private final String url;
    private final Options options;
    private Chromium chromium;
    private WebPage page;
    private Guard guard;
    private String lastUrl;
    private boolean loadedLast;
    /** The tree the last frame's look read, for {@link #tree} to hand on once instead of reading it again. */
    private WebTree looked;
    /** The page a shown browser last outlined every input of. */
    private String outlined;
    /** What the guard stopped since {@link #stopped()} was last asked. */
    private final List<Guard.Stopped> stoppedSince = new ArrayList<>();

    public WebSource(String url, Options options) {
        this.url = url;
        this.options = options;
    }

    /**
     * The run's source for the website at {@code url}: the one already made for it while it is open, so a reloaded
     * bot keeps its browser; another closes the one before.
     */
    public static WebSource over(String url) {
        synchronized (CURRENT_LOCK) {
            if (current != null && current.url.equals(url)) return current;
            if (current != null) current.close();
            current = new WebSource(url, Options.defaults());
            return current;
        }
    }

    /** The run's web source when its capture is one: {@code source}'s base, else empty. */
    public static Optional<WebSource> of(CaptureSource source) {
        return source != null && source.base() instanceof WebSource web ? Optional.of(web) : Optional.empty();
    }

    /** The website it opens. */
    public String url() {
        return url;
    }

    /** Opens the start page, starting the browser first when it is not up. */
    public synchronized void open() {
        // A browser that died is started again, not asked over its closed socket.
        if (page != null && !chromium.alive()) closeBrowser();
        if (page == null) {
            start();
            return;
        }
        load(url);
    }

    /** Whether the browser is up with its page. */
    public synchronized boolean isOpen() {
        return page != null && chromium.alive();
    }

    @Override
    public synchronized BufferedImage capture() {
        WebPage on = page();
        WebTree tree = on.tree();
        looked = answerConsent(on, tree) ? null : tree;
        try {
            return ImageIO.read(new ByteArrayInputStream(on.frame()));
        } catch (IOException e) {
            throw new UncheckedIOException("the page's frame could not be read", e);
        }
    }

    @Override
    public Point origin() {
        return new Point(0, 0);
    }

    @Override
    public boolean isPresent() {
        return isOpen();
    }

    @Override
    public synchronized Optional<UiTree> tree() {
        WebTree tree = looked != null ? looked : page().tree();
        looked = null;
        return Optional.of(tree.asUiTree());
    }

    /**
     * The page's part inside {@code box}, a tree's box, rendered by the browser where the viewport does not reach too:
     * an input's picture. Empty with no page up, for an empty box, or when the browser could not render it.
     */
    public synchronized Optional<BufferedImage> picture(Rectangle box) {
        if (page == null || box == null || box.isEmpty()) return Optional.empty();
        try {
            return Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(page.picture(box))));
        } catch (IOException | RuntimeException unrendered) {
            return Optional.empty();
        }
    }

    /** The whole page from its top, below the viewport too: a screen's shot. Empty with no page up, or unrendered. */
    public synchronized Optional<BufferedImage> whole() {
        if (page == null) return Optional.empty();
        try {
            return Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(page.whole())));
        } catch (IOException | RuntimeException unrendered) {
            return Optional.empty();
        }
    }

    /** Presses the node under {@code p} through the guard; one it refuses is not pressed, and the refusal is said. */
    @Override
    public synchronized void click(Point p) {
        java.awt.Point at = new java.awt.Point(p.x(), p.y());
        act(() -> {
            WebTree tree = page().tree();
            Optional<WebNode> node = tree.pressedAt(at);
            if (Boolean.getBoolean(SHOW)) show(tree, node);
            if (node.isEmpty()) {
                guard.pressAt(at);
                return;
            }
            guard.press(node.get()).ifPresent(refused -> Debug.log("not pressed, " + node.get().line() + ": "
                    + refused.line()));
        });
    }

    /** How long a shown browser outlines a page's every input when it first comes, and then the part pressed. */
    static final Duration SHOWN_INPUTS = Duration.ofMillis(1500);
    static final Duration SHOWN_PRESS = Duration.ofMillis(800);

    /**
     * For a person watching a shown browser: on a page first pressed on, its every input outlined with its name, then
     * the part about to be pressed outlined red, scrolled into view. Taken off before the press, so nothing the run
     * reads after it holds the outlines.
     */
    private void show(WebTree tree, Optional<WebNode> pressed) {
        try {
            String now = page.url();
            if (!now.equals(outlined)) {
                outlined = now;
                page.mark(marks(tree, null, null));
                Thread.sleep(SHOWN_INPUTS);
            }
            if (pressed.isPresent()) {
                Optional<Rectangle> box = page.inView(pressed.get());
                if (box.isPresent()) {
                    page.mark(marks(page.tree(), pressed.get(), box.get()));
                    Thread.sleep(SHOWN_PRESS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException unshown) {
            Debug.log("the press was not outlined: " + unshown);
        } finally {
            page.unmark();
        }
    }

    /** {@code tree}'s laid-out inputs as marks, {@code pressed} red at {@code box} and the rest blue. */
    private static List<WebPage.Mark> marks(WebTree tree, WebNode pressed, Rectangle box) {
        List<WebPage.Mark> marks = new ArrayList<>();
        for (WebNode input : tree.interactive()) {
            if (!input.laidOut() || pressed != null && input.backendId() == pressed.backendId()) continue;
            marks.add(new WebPage.Mark(input.box(), label(input), false));
        }
        if (pressed != null) marks.add(new WebPage.Mark(box, label(pressed), true));
        return marks;
    }

    private static String label(WebNode node) {
        String role = node.role().name().toLowerCase(Locale.ROOT);
        return node.name().isBlank() ? role : role + " “" + node.name().strip() + "”";
    }

    /** A hold, the same point twice, is a press there; a page is not dragged. */
    @Override
    public synchronized void drag(Point from, Point to, long durationMs) {
        if (from.equals(to)) {
            click(to);
            return;
        }
        Debug.log("a website is not dragged: nothing was done from " + from + " to " + to);
    }

    @Override
    public synchronized void scroll(Point at, int notches) {
        act(() -> page().wheel(new java.awt.Point(at.x(), at.y()), 0, -notches * WHEEL_STEP));
    }

    /** How far one wheel notch scrolls, in the page's pixels. */
    static final int WHEEL_STEP = 100;

    /** {@code combo} as one key on the page; a combination with a modifier is not pressed, being no browsing. */
    public synchronized void keys(Combo combo) {
        List<Key> keys = combo.keys();
        Optional<String> name = keys.size() == 1 ? domKey(keys.getFirst()) : Optional.empty();
        if (name.isEmpty()) {
            Debug.log("not pressed on a website: " + combo + " (one key is, no combination)");
            return;
        }
        act(() -> guard.key(name.get()));
    }

    /**
     * Types {@code text} where the focus is, the field's text selected first when {@code clears}, then Enter when
     * {@code submits}: a form's write it sends is failed by the guard.
     */
    public synchronized void type(String text, boolean clears, boolean submits) {
        act(() -> {
            if (clears) {
                page().session().call("Runtime.evaluate", Map.of("expression",
                        "(() => { const e = document.activeElement; if (e && e.select) e.select(); })()"));
            }
            guard.insert(text);
            if (submits) guard.key("Enter");
        });
    }

    /** {@code key}'s name in a page, empty for a modifier or a key a page is not pressed with. */
    static Optional<String> domKey(Key key) {
        String name = key.name();
        if (name.length() == 1) return Optional.of(name.toLowerCase(Locale.ROOT));
        if (name.startsWith("NUM") && name.length() == 4) return Optional.of(name.substring(3));
        if (name.matches("F\\d{1,2}")) return Optional.of(name);
        return Optional.ofNullable(switch (key) {
            case ENTER -> "Enter";
            case ESCAPE -> "Escape";
            case SPACE -> " ";
            case TAB -> "Tab";
            case BACKSPACE -> "Backspace";
            case DELETE -> "Delete";
            case LEFT -> "ArrowLeft";
            case UP -> "ArrowUp";
            case RIGHT -> "ArrowRight";
            case DOWN -> "ArrowDown";
            case HOME -> "Home";
            case END -> "End";
            case PAGE_UP -> "PageUp";
            case PAGE_DOWN -> "PageDown";
            default -> null;
        });
    }

    /**
     * Opens {@code path} on the site, a screen's example: {@code "/case-studies/alpha"}, as a load the budget counts.
     */
    public synchronized void openPath(String path) {
        page();
        load(URI.create(url).resolve(path).toString());
    }

    /** The path the page is at now, with no query nor {@code #part}; empty before the browser is up. */
    public synchronized Optional<String> path() {
        if (page == null) return Optional.empty();
        try {
            URI at = URI.create(page.url());
            // about:blank, Chrome's error page and data: are at no page of the site, its root least of all.
            if (!"http".equals(at.getScheme()) && !"https".equals(at.getScheme())) return Optional.empty();
            String path = at.getPath();
            return Optional.of(path == null || path.isEmpty() ? "/" : path);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * The page's tree read now, for judging a press: the tree the last look read is left for {@link #tree} to hand on.
     */
    public synchronized UiTree treeNow() {
        return page().tree().asUiTree();
    }

    /**
     * Tries each form the page shows, typing through the guard and never submitting, and leaves every field it typed
     * into empty: what the tries showed. A form whose try fails is left out, said in the debug log.
     */
    public synchronized List<FormProbe.Result> forms() {
        WebPage on = page();
        List<FormProbe.Result> results = new ArrayList<>();
        List<FormProbe.Form> forms;
        try {
            forms = FormProbe.forms(on);
        } catch (RuntimeException e) {
            Debug.log("the page's forms were not read: " + e);
            return List.of();
        }
        try {
            for (FormProbe.Form form : forms) {
                if (form.fields().isEmpty()) continue;
                try {
                    results.add(FormProbe.probe(guard, on, form));
                } catch (RuntimeException e) {
                    Debug.log("the form " + (form.submit().isEmpty() ? "with no submit" : form.submit())
                            + " was not tried: " + e);
                }
            }
        } finally {
            // The fields were typed into: the tree last looked at no longer says the page.
            looked = null;
            sayStopped();
        }
        return List.copyOf(results);
    }

    /** What the guard stopped since the last call: each said in the run's log already, handed on once. */
    public synchronized List<Guard.Stopped> stopped() {
        if (guard != null) sayStopped();
        List<Guard.Stopped> since = List.copyOf(stoppedSince);
        stoppedSince.clear();
        return since;
    }

    /** The HTTP status the page was last loaded with (a 431 error page); 0 before the browser is up or any load. */
    public synchronized int status() {
        return page == null ? 0 : page.documentStatus();
    }

    /** The page's console errors since the last call; none before the browser is up. */
    public synchronized List<String> consoleErrors() {
        if (page == null) return List.of();
        return page.console().stream().filter(m -> m.level() == ConsoleMessage.Level.ERROR).map(ConsoleMessage::text)
                .toList();
    }

    /**
     * Why the guard would refuse a press at {@code at}, read off the page without pressing: a submit, a buy. Empty
     * for a press it lets through, or with no node there or no browser up.
     */
    public synchronized Optional<String> refusal(Point at) {
        if (page == null) return Optional.empty();
        return page.tree().pressedAt(new java.awt.Point(at.x(), at.y()))
                .flatMap(node -> Presses.press(node, page.pressFacts(node))).map(Presses.Refusal::line);
    }

    @Override
    public synchronized void close() {
        closeBrowser();
        synchronized (CURRENT_LOCK) {
            if (current == this) current = null;
        }
    }

    @Override
    public String toString() {
        return "website " + url;
    }

    /** The page, the browser started and the start page opened when it is not up. */
    private WebPage page() {
        if (page == null) start();
        return page;
    }

    private void start() {
        Path binary = options.chromium().get().orElseThrow(() -> new IllegalStateException(
                "no Chromium for this computer, or its download failed: a website cannot be opened"));
        try {
            chromium = Chromium.start(binary, Boolean.getBoolean(SHOW), options.switches());
        } catch (IOException e) {
            throw new UncheckedIOException("Chromium did not start", e);
        }
        URI start = URI.create(url);
        try {
            page = chromium.newPage();
            guard = Guard.on(page, Scope.of(start), options.robots());
        } catch (RuntimeException e) {
            closeBrowser();    // never a browser left running with no page to reach it by
            throw e;
        }
        if (options.robots().allowsNothing(start)) {
            options.stop().accept("robots.txt of " + start.getHost() + " lets BotMaker read nothing of the site");
            return;
        }
        options.robots().crawlDelay(start).ifPresent(options.pace()::slowTo);
        load(url);
    }

    /** Loads {@code where} in its turn and as a load of the budget, then says what the guard stopped. */
    private void load(String where) {
        looked = null;
        spend();
        try {
            options.pace().load();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        WebPage.Opened opened = page.open(where);
        if (!opened.ok()) Debug.log(where + " did not open: " + opened.error());
        lastUrl = page.url();
        loadedLast = false;
        sayStopped();
    }

    /** One load of the budget; the run stops when it is spent. */
    private void spend() {
        if (!options.budget().load()) {
            Budget.Limit limit = options.budget().out().orElse(Budget.Limit.UNKNOWN);
            options.stop().accept("the website's budget is spent: " + options.budget().line(limit));
        }
    }

    /**
     * {@code action} in its turn, then the page settled, what the guard stopped said, and a page it moved to counted as a
     * load. The turn after a load waits the load's gap.
     */
    /** Closes the guard, the page and the browser, each that is up. */
    private void closeBrowser() {
        if (guard != null) guard.close();
        if (page != null) page.close();
        if (chromium != null) chromium.close();
        guard = null;
        page = null;
        chromium = null;
        looked = null;
    }

    private void act(Runnable action) {
        page();
        looked = null;
        try {
            if (loadedLast) options.pace().load();
            else options.pace().action();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        action.run();
        page.settled();
        sayStopped();
        String now = page.url();
        loadedLast = !withoutPart(now).equals(withoutPart(lastUrl));
        lastUrl = now;
        if (loadedLast) spend();
    }

    private void sayStopped() {
        for (Guard.Stopped stopped : guard.stopped()) {
            stoppedSince.add(stopped);
            Debug.log("the guard stopped " + stopped.kind().displayName().toLowerCase(Locale.ROOT) + ": "
                    + stopped.method() + " " + stopped.url());
        }
    }

    /** A cookie banner answered: rejected through the guard when it can be, else the run stops naming it. */
    /** Whether the page changed by it: a reject pressed. */
    private boolean answerConsent(WebPage on, WebTree tree) {
        Consent.Answer answer = Consent.of(tree);
        switch (answer.kind()) {
            case REJECT -> {
                if (answer.button().isEmpty()) return false;
                guard.press(answer.button().get()).ifPresent(refused -> Debug.log(
                        "the cookie banner's reject was refused: " + refused.line()));
                on.settled();
                sayStopped();
                return true;
            }
            case STOP -> options.stop().accept("a cookie banner offers no way to refuse: " + answer.dialog());
            default -> {
            }
        }
        return false;
    }

    private static String withoutPart(String url) {
        if (url == null) return "";
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }
}
