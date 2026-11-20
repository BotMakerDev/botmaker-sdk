// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.text.TextResult;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.sdk.internal.emulator.ReadableSource;
import com.botmaker.sdk.internal.explore.tree.Effect;
import com.botmaker.sdk.internal.explore.tree.Locator;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.web.WebSource;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import com.botmaker.shared.web.FormProbe;
import com.botmaker.shared.web.Guard;
import com.botmaker.sdk.internal.params.RunFacts;
import com.botmaker.sdk.internal.plan.RunGame;
import com.botmaker.sdk.internal.screens.ElementMatch;
import com.botmaker.sdk.internal.screens.LiveMoves;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.MapWalk.Took;
import com.botmaker.sdk.internal.screens.Reach;
import com.botmaker.sdk.internal.screens.RunReach;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenGraph.Edge;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import com.botmaker.sdk.internal.trace.MissFrames;
import com.botmaker.sdk.internal.trace.Say;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * {@link ExploreLoop}'s play on the running bot's game: the map's walk and presses by {@link LiveMoves}, the frame read
 * by the run's {@link Reach}, the readouts into the facts, a round of the plan. A reload hands it the new map and plan
 * ({@link #use}).
 */
public final class LivePlay implements ExploreLoop.Play {

    /** The run property giving <em>Keep exploring</em> a budget in hours; unset, it runs until stopped. */
    public static final String HOURS = "botmaker.explore.hours";

    /**
     * Explores the game with the bot's classes as {@code first} loads them, the game already up, until stopped or
     * past {@code budget}, whose minutes win over {@link #HOURS}: each resume compiles the bot in {@code project} as
     * the next generation and plays it.
     */
    public static ExploreLoop.End explore(Generation first, Path project, Runnable restart, ExploreBudget budget) {
        return explore(first, project, restart, budget, null, List.of(), pending(project));
    }

    /**
     * Explores as {@link #explore(Generation, Path, Runnable, ExploreBudget)} does, {@code settled} handed on with the
     * first stop the run settles: what its start settled already. It goes on from {@code pending} ({@link #pending}),
     * and keeps {@code budget} and {@code discover} (null: a <em>Keep exploring</em> run) in the memory a held fault
     * writes, for a restart started with no arguments to take.
     */
    public static ExploreLoop.End explore(Generation first, Path project, Runnable restart, ExploreBudget budget,
                                          DiscoverArgs discover, List<TreeAnswer> settled,
                                          Optional<ResumeFile> pending) {
        Plan plan = first.plan();
        Journal journal = Journal.of(project);
        journal.forgetPage();
        LivePlay play = new LivePlay(ScreenMaps.installed(), plan, plan == null ? null : RunGame.rounds(plan, first.holders()),
                restart, journal);
        AtomicReference<Generation> current = new AtomicReference<>(first);
        ExploreLoop.Reload reload = () -> switch (current.get().next(project, current.get().number() + 1)) {
            case Generation.Ok(Generation next) -> {
                next.install();
                current.set(next);
                Plan now = next.plan();
                play.use(ScreenMaps.installed(), now, now == null ? null : RunGame.rounds(now, next.holders()));
                yield new ExploreLoop.Reload.Reloaded(next.number(), ScreenMaps.installed(), guards(next), List.of());
            }
            case Generation.Refused(List<String> errors) ->
                    new ExploreLoop.Reload.Reloaded(current.get().number(), ScreenMaps.installed(), null, errors);
        };
        return new ExploreLoop(ScreenMaps.installed(), play, reload, guards(first), journal,
                ExploreLoop.Settings.DEFAULT.within(budget.time() != null ? budget.time() : hours())
                        .upTo(budget.screens()), System::currentTimeMillis).handOn(settled)
                .startedWith(budget, discover).resuming(pending).run();
    }

    /**
     * The resume file the run in {@code project} starting now goes on from ({@link ResumeFile#pending}), read before
     * the entry takes its arguments, so a restart started with none takes those it kept.
     */
    public static Optional<ResumeFile> pending(Path project) {
        return ResumeFile.pending(Journal.of(project), why -> Say.warn(why, Map.of()));
    }

    /** A play of {@code map} with no plan, {@code restart} restarting the game: a test's, on a source it set. */
    public static LivePlay over(ScreenGraph map, Runnable restart) {
        return new LivePlay(map, null, null, restart, null);
    }

    /** The guards with the author's words of {@code generation}'s bot. */
    private static Guards guards(Generation generation) {
        return Guards.with(InstalledGame.marked(SdkValue.Id.GUARD_WORDS, generation.holders()) instanceof List<?> words
                ? words.stream().map(String::valueOf).toList() : List.of());
    }

    /** {@link #HOURS} as a time; null when unset, not a number, or not above zero. */
    static Duration hours() {
        try {
            double hours = Double.parseDouble(System.getProperty(HOURS, "").strip());
            return hours > 0 ? Duration.ofMillis(Math.round(hours * 3_600_000)) : null;
        } catch (NumberFormatException unset) {
            return null;
        }
    }

    private final Reach reach = new RunReach();
    private final Runnable restart;
    /** Where a website's page is written as it is looked at; null: nowhere. */
    private final Journal journal;
    /** When the page was last written to {@link #journal}. */
    private volatile long pageAt;
    private volatile ScreenGraph map;
    private volatile LiveMoves moves;
    private volatile BooleanSupplier rounds;
    private volatile Plan plan = Plan.NONE;

    /**
     * Plays {@code map} with {@code plan} (null: none); {@code restart} restarts the game; a website's page goes to
     * {@code journal} (null: nowhere).
     */
    private LivePlay(ScreenGraph map, Plan plan, BooleanSupplier rounds, Runnable restart, Journal journal) {
        this.restart = restart;
        this.journal = journal;
        use(map, plan, rounds);
    }

    /** Plays {@code map} and {@code plan} by its {@code rounds} from now on: a new generation's. */
    public void use(ScreenGraph map, Plan plan, BooleanSupplier rounds) {
        this.map = map;
        this.moves = new LiveMoves(map, reach, restart);
        this.plan = plan == null ? Plan.NONE : plan;
        this.rounds = rounds;
    }

    @Override
    public Plan plan() {
        return plan;
    }

    @Override
    public ExploreLoop.Look look(String screen) {
        if (screen != null) RunFacts.seen(screen);
        Optional<BufferedImage> frame = reach.look();
        if (frame.isEmpty()) return ExploreLoop.Look.NOTHING;
        showPage(frame.get());
        List<Stop.Text> texts = read(frame.get()).stream().map(text -> new Stop.Text(text.text(), text.bounds())).toList();
        List<Rect> covered = new ArrayList<>();
        Optional<MapScreen> mapped = screen == null ? Optional.empty() : map.screen(screen);
        for (Readout readout : mapped.map(s -> s.readouts().values()).orElse(List.of())) {
            if (readout.area() == null) continue;
            reach.find(readout.area().anchor(), frame.get()).map(readout.area()::place).ifPresent(covered::add);
        }
        return new ExploreLoop.Look(texts, List.of(), Map.of(), covered);
    }

    /**
     * {@code frame} as a website's page for the Explore window: the run's browser is its own, headless or not, so the
     * window sees the page only through the journal, which it reads once a second, so no more often than that.
     */
    private void showPage(BufferedImage frame) {
        long now = System.currentTimeMillis();
        if (journal != null && now - pageAt >= 1_000 && WebSource.of(Source.current()).isPresent()) {
            pageAt = now;
            journal.page(frame);
        }
    }

    private List<TextResult> read(BufferedImage frame) {
        try {
            return reach.read(frame);
        } catch (Reach.TextUnread unread) {
            return List.of();
        }
    }

    /**
     * The text near {@code input}'s button, found by its picture or, a tree element, by its node's place, and the nodes
     * it is found at for the field guards; no text when a button is not found. Any other input has no box of its own
     * to find: it is judged by all the frame's text, so a price on a text button still refuses it.
     */
    @Override
    public Guards.Around around(Input input) {
        Optional<BufferedImage> frame = reach.look();
        if (input == null || frame.isEmpty()) return new Guards.Around(List.of(), Foreground.now(), List.of());
        List<TextResult> text = read(frame.get());
        if (!(input instanceof Button button)) return new Guards.Around(text, Foreground.now(), List.of());
        List<UiNode> at = nodes(reach, button, frame.get());
        return new Guards.Around(Guards.near(box(reach, button, frame.get(), at), text), Foreground.now(), at);
    }

    /** The tree's nodes {@code button}, a tree element, is found at on {@code frame}; none for a picture's button. */
    private static List<UiNode> nodes(Reach reach, Button button, BufferedImage frame) {
        return button.isElement() && reach.readsTrees() ? reach.elements(button.element(), frame) : List.of();
    }

    /** Where {@code button} is on {@code frame}, or null ({@link #box(Reach, Button, BufferedImage, List)}). */
    static Rect box(Reach reach, Button button, BufferedImage frame) {
        return box(reach, button, frame, nodes(reach, button, frame));
    }

    /**
     * Where {@code button} is on {@code frame}, or null: a tree element is the place of {@code nodes}' first, the nodes
     * found for it. Without it, a page's footer link was judged by every price on the page (2026-11-20).
     */
    private static Rect box(Reach reach, Button button, BufferedImage frame, List<UiNode> nodes) {
        if (button.isElement()) return nodes.isEmpty() ? null : ElementMatch.place(nodes.getFirst());
        return button.picture() == null ? null : reach.find(button.picture(), frame).orElse(null);
    }

    @Override
    public Foreground front() {
        return Foreground.now();
    }

    @Override
    public String keep(String name) {
        Optional<Path> folder = MissFrames.folder();
        Optional<BufferedImage> frame = reach.look();
        // A website's stop on a page no screen matches looks at nothing else: its kept frame is the page shown.
        frame.ifPresent(this::showPage);
        if (folder.isEmpty() || frame.isEmpty()) return "";
        try {
            Path file = Files.createDirectories(folder.get()).resolve(name + ".png");
            ImageIO.write(frame.get(), "png", file.toFile());
            return file.toString();
        } catch (IOException | RuntimeException unsaved) {
            return "";
        }
    }

    @Override
    public boolean planRound() {
        BooleanSupplier now = rounds;
        return now != null && now.getAsBoolean();
    }

    @Override
    public void idle(Duration time) {
        Wait.time(time);
    }

    @Override
    public void say(ExploreEvent event) {
        switch (event) {
            case ExploreEvent.Stopped(Stop stop, boolean again, long ignored) ->
                    Say.warn((again ? "Stopped again (" : "Stopped (") + stop.kind().displayName() + "): " + stop.missing(),
                            Map.of());
            case ExploreEvent.Refused(String screen, String by, Guards.Kind guard, String why, long ignored) ->
                    Say.info("Refused " + by + " on " + screen + ": " + why, Map.of());
            case ExploreEvent.NewScreen(String screen, String from, String by, long ignored) ->
                    Say.info("New screen: " + screen + (from == null ? "" : ", from " + from + " by " + by), Map.of());
            case ExploreEvent.Reloaded reloaded -> Say.info(reloaded.refused()
                    ? "Resume refused: " + String.join("; ", reloaded.errors())
                    : "Reloaded as generation " + reloaded.generation(), Map.of());
            case ExploreEvent.Parked(String stopId, long ignored) -> Say.info("Parked " + stopId, Map.of());
            case ExploreEvent.Faulted faulted -> Say.warn("Held " + faulted.stopId() + " as a Discover fault: "
                    + faulted.why(), Map.of());
            case ExploreEvent.Fixed(String stopId, String why, long ignored) -> Say.info("Fixed " + stopId + ": " + why, Map.of());
            case ExploreEvent.ReplayLost(String faultId, String why, long ignored) ->
                    Say.warn("Not replayed " + faultId + ": " + why, Map.of());
            case ExploreEvent.Restarted(String faultId, boolean dev, long ignored) ->
                    Say.info("Restarted on the rebuilt SDK, replaying " + faultId, Map.of());
            default -> {}
        }
    }

    @Override
    public Optional<String> where() {
        return moves.where();
    }

    @Override
    public String lost() {
        return moves.lost();
    }

    @Override
    public boolean goTo(String screen) {
        return moves.goTo(screen);
    }

    @Override
    public boolean open(Edge edge) {
        return moves.open(edge);
    }

    @Override
    public Took take(Edge edge, long withinMs) {
        return moves.take(edge, withinMs);
    }

    @Override
    public boolean recover() {
        return moves.recover();
    }

    @Override
    public void restart() {
        moves.restart();
    }

    /**
     * The capture's tree, a website's with its path and console errors; an app's with no place, its activity unread.
     * Empty for a game window, which has no tree.
     */
    @Override
    public Optional<Effect.State> state() {
        CaptureSource source = Source.current();
        if (source == null || !(source.base() instanceof ReadableSource readable)) return Optional.empty();
        Optional<WebSource> web = WebSource.of(source);
        // A website's read leaves the tree its last look took for the walk's own next read.
        Optional<UiTree> tree = web.isPresent() ? Optional.of(web.get().treeNow()) : readable.tree();
        if (tree.isEmpty()) return Optional.empty();
        String place = web.flatMap(WebSource::path).orElse("");
        List<String> errors = web.map(WebSource::consoleErrors).orElse(List.of());
        int status = web.map(WebSource::status).orElse(0);
        return Optional.of(new Effect.State(tree.get(), place, List.of(), errors, status));
    }

    @Override
    public List<Guard.Stopped> stopped() {
        return WebSource.of(Source.current()).map(WebSource::stopped).orElse(List.of());
    }

    /** A website's guard, asked of the input's centre; an app's inputs are all free. */
    @Override
    public Optional<String> refusal(Locator.Located input) {
        Rectangle box = input.node().bounds();
        Point centre = new Point(box.x + box.width / 2, box.y + box.height / 2);
        return WebSource.of(Source.current()).flatMap(web -> web.refusal(centre));
    }

    @Override
    public Optional<String> press(String screen, String field, long withinMs) {
        return moves.press(screen, field, withinMs);
    }

    /** A website loads the address it is at again; an app has none. */
    @Override
    public boolean reopen() {
        return WebSource.of(Source.current()).map(web -> {
            web.path().ifPresent(web::openPath);
            return web.path().isPresent();
        }).orElse(false);
    }

    /** A website's forms, tried through its guard; an app's none. */
    @Override
    public List<FormProbe.Result> forms() {
        return WebSource.of(Source.current()).map(WebSource::forms).orElse(List.of());
    }

    @Override
    public Optional<String> picture(String name, Rectangle box) {
        Optional<Path> folder = MissFrames.folder();
        Optional<BufferedImage> rendered = folder.isEmpty() ? Optional.empty()
                : WebSource.of(Source.current()).flatMap(web -> web.picture(box));
        if (rendered.isEmpty()) return Optional.empty();
        try {
            Path file = Files.createDirectories(folder.get().resolve("inputs")).resolve(name + ".png");
            ImageIO.write(rendered.get(), "png", file.toFile());
            return Optional.of(file.toString());
        } catch (IOException | RuntimeException unsaved) {
            return Optional.empty();
        }
    }
}
