package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Custom;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.MapWalk.Arrival;
import com.botmaker.sdk.internal.screens.MapWalk.Took;

import java.util.Optional;

/**
 * {@link MapWalk}'s moves on the game, by a run's {@link ScreenWalk}: walks as {@code Navigate.to} does (an overlay the
 * map lacks cleared once, popups closed by the walk), presses as {@code Navigate.press} does, and takes an edge with no
 * field by {@link ScreenWalk#take} — that edge, not the shortest way. A walk the run stopped stops the bot.
 */
public final class LiveMoves implements MapWalk.Moves {

    private final ScreenGraph graph;
    private final Reach reach;
    private final Runnable restart;

    /** Moves on {@code graph} through {@code reach}; {@code restart} restarts the game, waiting first as it sees fit. */
    public LiveMoves(ScreenGraph graph, Reach reach, Runnable restart) {
        this.graph = graph;
        this.reach = reach;
        this.restart = restart;
    }

    private ScreenWalk walk() {
        return new ScreenWalk(graph, reach, ScreenWalk.STEP_MS, ScreenMaps::seen);
    }

    @Override
    public boolean goTo(String screen) {
        return went(walk().uncovering().go(screen, ScreenMaps.lastSeen())).arrived();
    }

    /**
     * Whether {@code edge} is one a run takes now. A way back with no input is while the map has its close button to
     * press for it. One whose lead needs a look of its button is taken while the button shows that look, or is not
     * in view (its press then says it is absent), or is pressed until it shows it. The bot's own code is not: exploring
     * never runs it, since no one knows what it spends.
     */
    @Override
    public boolean open(ScreenGraph.Edge edge) {
        if (edge.via() == null) return edge.onItsOwn() || graph.close() != null;
        if (edge.via() instanceof Custom) return false;
        if (edge.state() == null || !(edge.via() instanceof Button button)) return true;
        if (edge.pick() != null && edge.pick().mode() == Pick.Mode.UNTIL) return true;
        Optional<Strip> strip = graph.screen(edge.from()).flatMap(screen -> screen.stripOf(button));
        return walk().closePopups(reach.look())
                .flatMap(frame -> reach.seen(button, strip.orElse(null), edge.pick(), frame))
                .map(seen -> edge.state().equals(seen.state())).orElse(true);
    }

    @Override
    public Took take(ScreenGraph.Edge edge) {
        if (edge.input() != null) {
            // A tab's copied way is its parent's field, a way back through another screen's names it "SCREEN.field":
            // pressed on the screen holding it, as a walk's step does.
            String field = graph.nameOf(edge.via()).map(BotConstants.InputName::field).orElse(edge.input());
            String holder = graph.holder(edge);
            ScreenWalk.Press press = walk().press(holder, field, edge.pick());
            // A press that did not move the game says the screen holding the button: the game is still on the tab.
            if (holder.equals(press.at()) && !holder.equals(edge.from())) {
                press = new ScreenWalk.Press(press.result(), edge.from(), press.times(), press.why(), press.state());
            }
            if (press.at() == null) ScreenMaps.lost();
            else ScreenMaps.seen(press.at());
            return pressed(edge, press);
        }
        ScreenWalk.Result took = went(walk().take(edge));
        if (took.arrived()) return new Took(Arrival.AT_TARGET, edge.to());
        return arrived(edge, took.at(), took.why());
    }

    /** Long enough for a game's first loader the map lacks ("Initializing…") to end. */
    static final long FIRST_SCREEN_MS = 60_000;

    /** Why the last {@link #where()} found nothing; null after one that found a screen. */
    private String lost;

    /** Why the game shows no screen: frozen by the run's own verdict ({@link Reach#frozen()}), else unseen or unmapped. */
    @Override
    public Optional<String> where() {
        Optional<String> at = walk().whereWithin(FIRST_SCREEN_MS);
        at.ifPresentOrElse(ScreenMaps::seen, ScreenMaps::lost);
        lost = at.isPresent() ? null : reach.frozen().orElseGet(() -> reach.look().isEmpty()
                ? "nothing of the game can be seen"
                : "the game shows no screen of the map for " + FIRST_SCREEN_MS / 1000 + " s");
        return at;
    }

    @Override
    public String lost() {
        return lost;
    }

    /**
     * On any screen of the map at once, else walked to the start: one look before a walk, so a start the game no
     * longer shows is not walked to each time.
     */
    @Override
    public boolean recover() {
        return walk().where(ScreenMaps.lastSeen()).isPresent()
                || graph.start().map(this::goTo).orElse(false);
    }

    @Override
    public void restart() {
        restart.run();
    }

    private static ScreenWalk.Result went(ScreenWalk.Result walked) {
        if (walked.outcome() == ScreenWalk.Outcome.STOPPED) Bot.stop();
        if (walked.at() == null) ScreenMaps.lost();
        return walked;
    }

    /**
     * A press's end as an arrival. Nothing pressed because its way is closed now (locked, its method, a look that
     * blocks) is {@link Arrival#NOT_NOW}, no finding; nothing pressed because its button is not there is nothing
     * happening, said by the press's own why.
     */
    static Took pressed(ScreenGraph.Edge edge, ScreenWalk.Press press) {
        return switch (press.result()) {
            case DONE, LEFT -> arrived(edge, press.at(), null);
            case ABSENT -> new Took(Arrival.UNCHANGED, press.at(), press.why());
            default -> new Took(Arrival.NOT_NOW, press.at(), press.why());
        };
    }

    /** Where {@code edge} left the game, {@code at} (null: no screen of the map), as an arrival. */
    static Took arrived(ScreenGraph.Edge edge, String at, String why) {
        if (at == null) return new Took(Arrival.UNKNOWN, null, why);
        if (at.equals(edge.to())) return new Took(Arrival.AT_TARGET, at);
        if (at.equals(edge.from())) return new Took(Arrival.UNCHANGED, at, why);
        return new Took(Arrival.ELSEWHERE, at, why);
    }
}
