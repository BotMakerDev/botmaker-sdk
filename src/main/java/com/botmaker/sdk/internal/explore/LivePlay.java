package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.text.TextResult;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.sdk.internal.params.RunFacts;
import com.botmaker.sdk.internal.plan.RunGame;
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
     * past {@link #HOURS}: each resume compiles the bot in {@code project} as the next generation and plays it.
     */
    public static ExploreLoop.End explore(Generation first, Path project, Runnable restart) {
        Plan plan = first.plan();
        LivePlay play = new LivePlay(ScreenMaps.installed(), plan == null ? null : RunGame.rounds(plan, first.holders()),
                restart);
        AtomicReference<Generation> current = new AtomicReference<>(first);
        ExploreLoop.Reload reload = () -> switch (current.get().next(project, current.get().number() + 1)) {
            case Generation.Ok(Generation next) -> {
                next.install();
                current.set(next);
                Plan now = next.plan();
                play.use(ScreenMaps.installed(), now == null ? null : RunGame.rounds(now, next.holders()));
                yield new ExploreLoop.Reload.Reloaded(next.number(), ScreenMaps.installed(), guards(next), List.of());
            }
            case Generation.Refused(List<String> errors) ->
                    new ExploreLoop.Reload.Reloaded(current.get().number(), ScreenMaps.installed(), null, errors);
        };
        return new ExploreLoop(ScreenMaps.installed(), play, reload, guards(first), Journal.of(project),
                ExploreLoop.Settings.DEFAULT.within(hours()), System::currentTimeMillis).run();
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
    private volatile ScreenGraph map;
    private volatile LiveMoves moves;
    private volatile BooleanSupplier rounds;

    /** Plays {@code map} with the plan's {@code rounds} (null: no plan); {@code restart} restarts the game. */
    private LivePlay(ScreenGraph map, BooleanSupplier rounds, Runnable restart) {
        this.restart = restart;
        use(map, rounds);
    }

    /** Plays {@code map} and {@code rounds} from now on: a new generation's. */
    void use(ScreenGraph map, BooleanSupplier rounds) {
        this.map = map;
        this.moves = new LiveMoves(map, reach, restart);
        this.rounds = rounds;
    }

    @Override
    public ExploreLoop.Look look(String screen) {
        if (screen != null) RunFacts.seen(screen);
        Optional<BufferedImage> frame = reach.look();
        if (frame.isEmpty()) return ExploreLoop.Look.NOTHING;
        List<Stop.Text> texts = read(frame.get()).stream().map(text -> new Stop.Text(text.text(), text.bounds())).toList();
        List<Rect> covered = new ArrayList<>();
        Optional<MapScreen> mapped = screen == null ? Optional.empty() : map.screen(screen);
        for (Readout readout : mapped.map(s -> s.readouts().values()).orElse(List.of())) {
            if (readout.area() == null) continue;
            reach.find(readout.area().anchor(), frame.get()).map(readout.area()::place).ifPresent(covered::add);
        }
        return new ExploreLoop.Look(texts, List.of(), Map.of(), covered);
    }

    private List<TextResult> read(BufferedImage frame) {
        try {
            return reach.read(frame);
        } catch (Reach.TextUnread unread) {
            return List.of();
        }
    }

    /** The text near {@code edge}'s button; all the frame's text when it has none to find, so nothing is missed. */
    @Override
    public Guards.Around around(Edge edge) {
        Optional<BufferedImage> frame = reach.look();
        List<TextResult> text = frame.map(this::read).orElse(List.of());
        Rect box = edge.via() instanceof Button button && frame.isPresent()
                ? reach.find(button.picture(), frame.get()).orElse(null) : null;
        return new Guards.Around(Guards.near(box, text), Foreground.now(), List.of());
    }

    @Override
    public Foreground front() {
        return Foreground.now();
    }

    @Override
    public String keep(String name) {
        Optional<Path> folder = MissFrames.folder();
        Optional<BufferedImage> frame = reach.look();
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
            case ExploreEvent.Fixed(String stopId, String why, long ignored) -> Say.info("Fixed " + stopId + ": " + why, Map.of());
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
}
