package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Kind;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenWalk;
import com.botmaker.sdk.internal.vision.FrameChange;

import java.awt.image.BufferedImage;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Discovery's map, kept by a tool rather than by the assistant's memory (the user's call, 2026-10-14: mapping
 * Firestone, the assistant clicked through a dozen screens, cut nothing and wrote nothing until asked; then cut a
 * screen's buttons and left without trying one). The map is the bot's {@code Screens} class ({@link ScreenBook}):
 * every input cut on a screen with crop_picture is untried until try_input clicks it (or scrolls over it, a list's),
 * and try_input records what it did — opened a screen (a new constant, owing its marker), led to one mapped already
 * (a way into it, or this one's way back), changed this one in place, or nothing. A screen opened is finished before
 * the next input of the one it was opened from: the tool refuses a screen while one reached from it has an untried
 * input, and any screen while one owes its marker ({@link ScreenGraph}). go_to_screen walks the map as a run does.
 */
final class MapTools {

    /** The share of the still screen an input must change to have opened a screen: Firestone's side panels. */
    static final double SCREEN_SHARE = 0.15;
    /** How long an input is given to answer when the call says nothing: as long as a click's. */
    static final int SETTLE_MS = 5000;
    /** How much longer a small change is read on while the screen's marker still shows: Garage drew a second late. */
    static final int SLOW_SCREEN_MS = 2_500;
    /** How long go_to_screen gives each step for the next screen's marker, as {@code Navigate.to} does. */
    static final long STEP_MS = 10_000;

    /** What a screen's name cannot hold: markdown a notes heading would read otherwise, or a line break. */
    private static final Pattern NOT_A_NAME = Pattern.compile("[:*_`\\[\\]\\\\<>#›\\p{Cntrl}]");

    /** The screen the game was last seen on, by go_to_screen or which_screen: a walk looks near it first. */
    private static volatile String lastSeen;

    record Try(@Describe("the screen the input is on, as the map names it: Home, or its constant HOME") String screen,
               @Describe(value = "a picture cut on that screen, clicked at its best match", optional = true)
               String picture,
               @Describe(value = "notches to scroll over the picture instead of clicking it, for a list or a panel "
                       + "that scrolls: positive up or away, negative down or toward you", optional = true)
               Integer scroll,
               @Describe(value = "a hotkey instead of a picture, as printed on it: Q", optional = true) String key,
               @Describe(value = "the name the screen it opens shows, when it opens one; left out, the picture's "
                       + "name names it", optional = true) String opens,
               @Describe(value = "why it is never clicked (it spends premium currency or real money): recorded "
                       + "as tried, not clicked", optional = true) String never,
               @Describe(value = "the picture proving it locked on this account (a padlock on it): recorded as "
                       + "tried, not clicked", optional = true) String locked,
               @Describe(value = "what it is for: chore, shortcut, choice or info", optional = true) String kind,
               @Describe(value = "the longest the input is given to answer, in milliseconds; 5000 when left out",
                       optional = true) Integer settleMs,
               @Describe(value = "true to act on the user's own desktop, taking their mouse; refused there "
                       + "otherwise", optional = true) Boolean desktop) {
    }

    record Goal(@Describe("the screen to go to, as the map names it: Campaign, or its constant CAMPAIGN") String screen,
                @Describe(value = "true to act on the user's own desktop, taking their mouse; refused there "
                        + "otherwise", optional = true) Boolean desktop) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("try_input")
                    .describedAs("Discovery's one way to act on a screen: clicks an input cut on it (scrolls over "
                            + "it, or taps a hotkey) and records in the bot's Screens what that did: opened a screen "
                            + "(a new one, owing its marker, with its screenshot), led to one mapped already, changed "
                            + "this one in place, or nothing. Every picture crop_picture cut with a screen is untried "
                            + "until this tries it. A screen opened is finished before the next input of the screen "
                            + "it was opened from: refused while a screen owes its marker, while a screen opened from "
                            + "this one has an input left to try, and while another branch begun is not finished")
                    .takes(Try.class).handledBy(MapTools::tryInput),
            AssistantTool.named("go_to_screen")
                    .describedAs("Walks the game to a screen of the bot's Screens as a run does: finds where it is "
                            + "by the markers, then does each input on the way and waits for each screen; a popup "
                            + "on the way is closed. Says where it ended and why when it did not arrive")
                    .takes(Goal.class).handledBy(MapTools::goToScreen));

    private MapTools() {}

    /** try_input; Studio busy at any read or write of the bot (each on its thread) is said, never thrown. */
    static AgentReply tryInput(Try input, AgentContext context) {
        try {
            return tried(input, context);
        } catch (IllegalStateException busy) {
            return AgentReply.refused("Studio did not answer while reading or writing the bot's Screens: "
                    + busy.getMessage() + " Look where the game is and read the map (read_guide) before trying again.");
        }
    }

    private static AgentReply tried(Try input, AgentContext context) {
        StudioServices services = context.services();
        if (services.projectDir() == null) return AgentReply.refused(Frames.noProject());
        boolean byPicture = input.picture() != null && !input.picture().isBlank();
        boolean byKey = input.key() != null && !input.key().isBlank();
        if (byPicture == byKey) {
            return AgentReply.refused("An input is a picture to click or a hotkey, one of them: the bot can take no "
                    + "other. A button with no picture yet: screenshot, crop_picture it with its screen, then "
                    + "try_input it. Nothing was done.");
        }
        Optional<Key> key = byKey ? Key.named(input.key()) : Optional.empty();
        if (byKey && key.isEmpty()) return AgentReply.refused("\"" + input.key() + "\" is no key.");
        String opens = input.opens() == null || input.opens().isBlank() ? null : input.opens().strip();
        String asked = input.screen() == null ? "" : input.screen().strip();
        if (!isName(asked) || opens != null && !isName(opens)) return AgentReply.refused(plainName());
        boolean never = input.never() != null;
        if (never && input.never().isBlank()) {
            return AgentReply.refused("Say why it is never clicked (it spends gems). Nothing was done.");
        }
        boolean locked = input.locked() != null && !input.locked().isBlank();
        if (never && locked) return AgentReply.refused("An input is never done or locked, not both. Nothing was done.");
        Optional<Kind> kind = Optional.empty();
        if (input.kind() != null && !input.kind().isBlank()) {
            kind = kindNamed(input.kind());
            if (kind.isEmpty()) {
                return AgentReply.refused("No kind \"" + input.kind().strip() + "\": chore, shortcut, choice or info.");
            }
        }
        int notches = input.scroll() == null ? 0 : input.scroll();
        if (input.scroll() != null && (notches == 0 || !byPicture || never || locked)) {
            return AgentReply.refused(notches == 0 ? "Scroll by at least one notch." : !byPicture ? "A scroll is over "
                    + "a picture: the list or panel, cut with its screen." : "A scroll spends nothing and opens "
                    + "nothing: leave never and locked out.");
        }
        if (byKey && opens == null && !never && !locked) {
            return AgentReply.refused("A hotkey names no screen: give opens, the name the screen it opens shows. "
                    + "Nothing was done.");
        }
        int settle = input.settleMs() == null ? SETTLE_MS : input.settleMs();
        ScreenGraph graph = FxCall.call(() -> ScreenBook.read(services));
        Optional<String> found = ScreenBook.find(graph, asked);
        if (found.isEmpty()) {
            return AgentReply.refused("No screen " + asked + " in the bot's Screens" + (graph.screens().isEmpty()
                    ? ": crop_picture the first screen's marker with screen " + asked + " and marker true; that "
                    + "starts the map." : ": try_input adds a screen when an input opens it.") + " Nothing was done.");
        }
        String screen = found.get();
        String name = graph.shown(screen);
        Optional<String> owing = owingMarker(services, graph);
        if (owing.isPresent()) return AgentReply.refused(owes(graph.shown(owing.get())));
        Optional<String> below = graph.unfinishedBelow(screen);
        if (below.isPresent()) {
            String child = graph.shown(below.get());
            List<Input> waiting = graph.untried(below.get());
            String why = !waiting.isEmpty() ? "it still has untried inputs (" + labels(waiting) + ")"
                    : "nothing on it is cut yet: crop_picture every input on it with screen " + child
                    + ", then try each; its close button too";
            return AgentReply.refused("Finish " + child + " first: " + why + ", and it is below " + name
                    + ". A screen opened is finished, every button, tab, toggle and arrow tried and every "
                    + "list scrolled, before the next input of the screen it was opened from. Nothing was done.");
        }
        Optional<String> begun = graph.branchBegun(screen);
        if (begun.isPresent()) {
            return AgentReply.refused("Finish the branch begun at " + graph.shown(begun.get()) + " first: it, or a "
                    + "screen reached from it, still has inputs to try. One branch to its end before the next. "
                    + "Nothing was done.");
        }
        GameScreen on = graph.screens().get(screen);
        String what = byPicture ? input.picture().strip() : null;
        Optional<Input> held = held(on, what, key.orElse(null), notches, graph, screen);
        boolean untried = held.filter(i -> !graph.tried(screen, i)).isPresent();
        if (byPicture && !ActTools.exists(context, what)) {
            if (!untried) return AgentReply.refused(Frames.noPicture(what));
            Optional<String> failed = write(() -> ScreenBook.update(services, screen, s -> replaced(s, held.get(), null)));
            return failed.map(AgentReply::refused).orElseGet(() -> AgentReply.text(what + " is no picture any more "
                    + "(deleted or renamed): taken off " + name + "'s untried inputs. Nothing was clicked."));
        }
        if ((never || locked) && byPicture && !untried) {
            return AgentReply.refused(what + " is not an untried input of " + name + ": crop_picture it with screen "
                    + name + " first. Nothing was done.");
        }
        if (locked && !ActTools.exists(context, input.locked().strip())) {
            return AgentReply.refused(Frames.noPicture(input.locked().strip()));
        }
        String child = opens != null ? opens : byPicture ? nameFrom(what, name) : null;
        if (!never && !locked && notches == 0 && opens == null
                && (!isName(child) || ScreenBook.find(graph, child).isPresent())) {
            return AgentReply.refused(what + " would name the screen it opens \"" + child + "\"" + (isName(child)
                    ? ", a screen the map has" : "") + ": give opens, the name that screen shows. Nothing was done.");
        }
        if (on.marker() != null) {
            Optional<AgentReply> away = ActTools.notOn(context, on.marker().id(), name);
            if (away.isPresent()) return away.get();
        }
        Input base = kinded(made(services, held, what, key.orElse(null), notches), kind);
        Input old = held.orElse(null);
        boolean ask = !graph.untried(screen).isEmpty();
        if (never || locked) {
            Input settled = never ? base.never(input.never().strip())
                    : base.locked(FxCall.call(() -> ScreenBook.picture(services, input.locked().strip())));
            return noted(services, screen, old, settled, never ? "never, " + input.never().strip()
                    : "locked by " + input.locked().strip(), ask);
        }
        ActTools.Entered first = ActTools.enter(context, byPicture ? what : null, notches, key.orElse(null),
                input.desktop(), settle, FrameChange.ANSWER_SHARE);
        ActTools.Entered entered = notches == 0 ? slowScreen(context, first, on.marker()) : first;
        if (entered.after() == null) {
            return entered.reply().isRefused() ? entered.reply() : entered.reply().and(AgentReply.text(
                    "Nothing was written. Look where the game is before trying it again from " + name + "."));
        }
        if (notches != 0) {
            // A list scrolled moves much of the screen and opens nothing: the end shows when nothing moves.
            boolean moved = FrameChange.box(entered.before(), entered.after(), entered.motion()).isPresent();
            AgentReply said = noted(services, screen, old, base.did(moved ? Did.IN_PLACE : Did.NONE),
                    moved ? "changes this screen in place" : "no visible effect", ask && !moved);
            return entered.reply().and(said).and(AgentReply.text(moved ? "Scroll it again the same way until "
                    + "nothing moves (its end), then the other way to its other end; cut, with screen " + name
                    + ", each input it brings into view: they are untried in turn."
                    : "Nothing moved: that end is reached. Its other end too, when not scrolled to yet."));
        }
        if (entered.share() >= SCREEN_SHARE) {
            // Back, close, or another way to a screen mapped already: its marker came into view. A marker on screen
            // before the click proves nothing: a panel opens over it.
            // The screen opens names first, when the map has it; else the first whose marker came into view.
            Optional<String> known = (opens == null ? Optional.<String>empty() : ScreenBook.find(graph, opens)
                    .filter(c -> !c.equals(screen)))
                    .or(() -> graph.screens().keySet().stream().filter(c -> !c.equals(screen))
                            .filter(c -> graph.screens().get(c).marker() != null)
                            .filter(c -> ActTools.shows(context, graph.screens().get(c).marker().id(), entered.after())
                                    && !ActTools.shows(context, graph.screens().get(c).marker().id(),
                                    entered.before()))
                            .findFirst());
            if (known.isPresent()) return entered.reply().and(reached(services, graph, screen, old, base, known.get()));
            return entered.reply().and(opened(context, graph, screen, old, base, child));
        }
        boolean changed = FrameChange.box(entered.before(), entered.after(), entered.motion()).isPresent();
        AgentReply said = noted(services, screen, old, base.did(changed ? Did.IN_PLACE : Did.NONE),
                changed ? "changes this screen in place" : "no visible effect", ask);
        return entered.reply().and(changed ? said.and(AgentReply.text("Cut, with screen " + name + ", each new "
                + "input it shows (another state of a toggle, a tab's buttons): they are untried in turn.")) : said);
    }

    /** {@code base}, which opened a screen the map has not: added below the others, owing its marker. */
    private static AgentReply opened(AgentContext context, ScreenGraph graph, String screen, Input old, Input base,
                                     String child) {
        StudioServices services = context.services();
        String constant = ScreenBook.constantFor(graph, child);
        GameScreen made = GameScreen.named(child).from(Way.of(BotConstants.screen(screen), base));
        Optional<String> failed = write(() -> ScreenBook.update(services, screen, s -> replaced(s, old, base))
                .or(() -> ScreenBook.add(services, constant, made)));
        if (failed.isPresent()) return unwritten(failed.get());
        String grouped;
        try {
            grouped = TagTools.screenTag(services, child).map(tag -> " Its pictures go under the tag " + tag + ".")
                    .orElse("");
        } catch (IllegalStateException e) {
            grouped = "";   // Studio busy: its first cut declares the tag
        }
        String from = graph.shown(screen);
        return AgentReply.text("Opened " + child + ", added to the map as Screens." + constant + ", reached from "
                        + from + " by " + base.label() + "; it owes its marker." + grouped + " Before leaving it, "
                        + "from the screenshot below: crop_picture with screen " + child + " and marker true a part "
                        + "only this screen shows, then with screen " + child + " every input on it (each button, "
                        + "icon, tab, toggle, arrow and list, and its close button); what cannot be cut now, "
                        + "write_notes as an Owed: line in its section. Then try each of them, and finish " + child
                        + " before going back to " + from + " for its next input.")
                .and(LookTools.screenshot(new LookTools.Shot(null, null, null, null, null, null), context));
    }

    /**
     * {@code base} on {@code screen} led to {@code known}, mapped already: a popup over it, its way back, or another
     * way into {@code known}, which names {@code screen} once it is declared below it; a screen reached through
     * {@code known} cannot be named from it, so the input says it leaves.
     */
    private static AgentReply reached(StudioServices services, ScreenGraph graph, String screen, Input old, Input base,
                                      String known) {
        String to = graph.shown(known);
        GameScreen target = graph.screens().get(known);
        if (target.isPopup()) {
            return noted(services, screen, old, base.did(Did.IN_PLACE), "shows the popup " + to, false)
                    .and(AgentReply.text(to + " is a popup: close it before the next input."));
        }
        // A popup's way out is its back, whatever it uncovers: a popup is in no tree and names no screen.
        if (graph.screens().get(screen).isPopup() || graph.parent(screen).filter(known::equals).isPresent()) {
            Optional<String> failed = write(() -> ScreenBook.update(services, screen,
                    s -> replaced(s, old, null).back(base)));
            return failed.map(MapTools::unwritten).orElseGet(() -> AgentReply.text("Noted in " + graph.shown(screen)
                    + ": " + base.label() + " is its way back to " + to + "."));
        }
        List<String> order = List.copyOf(graph.screens().keySet());
        int below = order.indexOf(known);
        boolean nameable = order.indexOf(screen) < below || !graph.dependsOn(screen).contains(known)
                && graph.dependsOn(screen).stream().allMatch(c -> order.indexOf(c) < below);
        if (!nameable) {
            return noted(services, screen, old, base.did(Did.LEAVES), "leaves for " + to + ", which this screen is "
                    + "reached through, so the map cannot name it from here", false);
        }
        boolean already = graph.opens(screen, base).filter(known::equals).isPresent();
        Optional<String> failed = write(() -> (order.indexOf(screen) < below ? Optional.<String>empty()
                : ScreenBook.moveBefore(services, screen, known))
                .or(() -> ScreenBook.update(services, screen, s -> replaced(s, old, base)))
                .or(() -> already ? Optional.empty() : ScreenBook.update(services, known, s -> {
                    List<Way> ways = new ArrayList<>(s.alsoFrom());
                    ways.add(Way.of(BotConstants.screen(screen), base));
                    return s.alsoFrom(ways);
                })));
        if (failed.isPresent()) return unwritten(failed.get());
        return noted(services, screen, base, null, "opens " + to + ", mapped already: a way into it", false);
    }

    /**
     * Writes {@code now} in place of {@code old} on {@code screen} (none: nothing written), then says what the
     * screen still holds; {@code ask} asks, as its last input goes, about inputs never cut.
     */
    private static AgentReply noted(StudioServices services, String screen, Input old, Input now, String did,
                                    boolean ask) {
        if (now != null) {
            Optional<String> failed = write(() -> ScreenBook.update(services, screen, s -> replaced(s, old, now)));
            if (failed.isPresent()) return unwritten(failed.get());
        }
        String what = now != null ? now.label() : old != null ? old.label() : "it";
        ScreenGraph graph;
        try {
            graph = FxCall.call(() -> ScreenBook.read(services));
        } catch (IllegalStateException busy) {
            return AgentReply.text("Noted: tried " + what + ", " + did + ". Studio did not answer when the map was "
                    + "read again: read_guide says what is left.");
        }
        String name = graph.shown(screen);
        List<Input> left = graph.untried(screen);
        return AgentReply.text("Noted in " + name + ": tried " + what + ", " + did + "." + (left.isEmpty()
                ? " Every input cut on it is tried" + graph.shape(screen).map(s -> s == ScreenGraph.Shape.MENU
                ? ": a menu." : ": an activity.").orElse(".")
                + (ask ? " Before leaving " + name + ": was every list and panel on it scrolled to both ends "
                + "(try_input with scroll), every tab opened, every toggle flipped, every arrow paged? Each one "
                + "that was not: crop_picture it with screen " + name + " and try it." : "")
                : " Untried: " + labels(left) + "."));
    }

    /** Where crop_picture notes a cut: a screen of the map, or the one it starts; else why it cannot. */
    record Placing(String constant, String name, boolean starts, boolean popup, String refusal) {
        static Placing refused(String why) {
            return new Placing(null, null, false, false, why);
        }
    }

    /**
     * The screen {@code typed} names for a cut. A marker cut for a screen the map lacks starts it when the map has no
     * screen yet (the first, Home) or when it is a {@code popup}; any other screen is added by try_input.
     */
    static Placing placing(StudioServices services, String typed, boolean marker, boolean popup) {
        if (!isName(typed)) return Placing.refused(plainName());
        ScreenGraph graph = FxCall.call(() -> ScreenBook.read(services));
        Optional<String> found = ScreenBook.find(graph, typed);
        if (found.isPresent()) return new Placing(found.get(), graph.shown(found.get()), false, false, null);
        boolean first = graph.screens().values().stream().allMatch(GameScreen::isPopup);
        if (marker && (popup || first)) {
            return new Placing(ScreenBook.constantFor(graph, typed), typed, true, popup, null);
        }
        return Placing.refused("No screen " + typed + " in the bot's Screens: " + (first ? "the first screen starts "
                + "the map with its marker, marker true." : "try_input adds a screen when an input opens it; a popup "
                + "starts with its marker cut with popup true.") + " Nothing was saved.");
    }

    /**
     * Notes the picture {@code picture}, just cut, on the screen {@code at} names: the screen it starts, its marker,
     * its way back, or an input untried until try_input tries it ({@code sometimes}: one that comes and goes). Says
     * where.
     */
    static String noteCut(StudioServices services, Placing at, String picture, boolean marker, boolean back,
                          boolean sometimes) {
        try {
            return FxCall.call(() -> {
                ImageTemplate cut = ScreenBook.picture(services, picture);
                if (at.starts()) {
                    GameScreen screen = GameScreen.named(at.name()).marker(cut);
                    Optional<String> failed = ScreenBook.add(services, at.constant(), at.popup() ? screen.popup() : screen);
                    return failed.map(why -> " It could not be added to the map: " + why).orElse(" Added "
                            + at.name() + " to the map as Screens." + at.constant() + ", this its marker" + (at.popup()
                            ? "; a popup, closed by Escape until its close button is cut with back true."
                            : ": the map's first screen. Cut every input on it with screen " + at.name() + "."));
                }
                ScreenGraph graph = ScreenBook.read(services);
                GameScreen screen = graph.screens().get(at.constant());
                if (screen == null) return " " + at.name() + " is no longer in the map, so it was not noted.";
                String was = marker && screen.marker() != null && !screen.marker().id().equals(cut.id())
                        ? screen.marker().id() : null;
                Optional<Input> had = screen.inputs().stream()
                        .filter(i -> i.picture() != null && i.picture().id().equals(cut.id())).findFirst();
                if (!marker && !back && had.isPresent() && (!sometimes || had.get().comesAndGoes())) {
                    return " " + at.name() + " has it as an input already.";
                }
                // A way back is no input to try, and an input seen to come and go is marked so in place.
                Optional<String> failed = ScreenBook.update(services, at.constant(), s -> marker ? s.marker(cut)
                        : back ? replaced(s, had.orElse(null), null).back(Input.click(cut))
                        : had.isPresent() ? replaced(s, had.get(), had.get().sometimes())
                        : s.inputs(added(s.inputs(), sometimes ? Input.click(cut).sometimes() : Input.click(cut))));
                if (failed.isPresent()) return " It could not be noted in " + at.name() + ": " + failed.get();
                return " Noted in " + at.name() + (marker ? " as its marker" + (was == null ? "" : ", in place of "
                        + was) + "." : back ? " as its way back." : sometimes ? " as an input that comes and goes: "
                        + "try_input it while it shows." : " as untried: try_input it before leaving " + at.name() + ".");
            });
        } catch (IllegalStateException busy) {
            return " It could not be noted in " + at.name() + ": " + busy.getMessage();
        }
    }

    static AgentReply goToScreen(Goal goal, AgentContext context) {
        StudioServices services = context.services();
        if (services.projectDir() == null) return AgentReply.refused(Frames.noProject());
        ScreenGraph graph;
        try {
            graph = FxCall.call(() -> ScreenBook.read(services));
        } catch (IllegalStateException busy) {
            return AgentReply.refused("Studio did not hand over the bot's Screens: " + busy.getMessage());
        }
        Optional<String> target = ScreenBook.find(graph, goal.screen());
        if (target.isEmpty()) {
            return AgentReply.refused("No screen " + goal.screen() + " in the bot's Screens. Nothing was done.");
        }
        Optional<ToolReach> reach = ToolReach.of(context);
        if (reach.isEmpty()) return AgentReply.refused(Frames.noProject());
        Optional<AgentReply> desktop = ActTools.desktopRefusal(context, reach.get().hands(), goal.desktop());
        if (desktop.isPresent()) return desktop.get();
        ScreenWalk.Result walked = new ScreenWalk(graph, reach.get(), STEP_MS, null).go(target.get(), lastSeen);
        seen(graph, walked.at());
        String path = walked.walked().isEmpty() ? "" : " Walked: " + String.join(" › ",
                walked.walked().stream().map(graph::shown).toList()) + ".";
        return AgentReply.text((walked.arrived() ? "" : "Not there (" + walked.outcome().name().toLowerCase(Locale.ROOT)
                .replace('_', ' ') + "): ") + walked.why() + path);
    }

    /** Which screen of the map {@code frame} shows, as which_screen says it first; empty with no map. */
    static Optional<String> where(AgentContext context, BufferedImage frame) {
        ScreenGraph graph;
        try {
            graph = FxCall.call(() -> ScreenBook.read(context.services()));
        } catch (IllegalStateException busy) {
            return Optional.empty();
        }
        Optional<ToolReach> reach = ToolReach.of(context);
        if (graph.screens().isEmpty() || reach.isEmpty()) return Optional.empty();
        Optional<String> here = new ScreenWalk(graph, reach.get(), STEP_MS, null).whereOn(frame, lastSeen);
        here.ifPresent(c -> seen(graph, c));
        return Optional.of(here.map(c -> "On " + graph.shown(c) + " (Screens." + c + "): its marker shows.")
                .orElse("No screen of the bot's Screens shows: none of their markers is found."));
    }

    private static void seen(ScreenGraph graph, String constant) {
        if (constant != null && graph.screen(constant).filter(s -> !s.isPopup()).isPresent()) lastSeen = constant;
    }

    /**
     * {@code first} read on while it changed less than a screen and the screen's {@code marker} still shows: a
     * screen slow to draw (Garage, a second late) would be noted as a change in place. A change that grew with the
     * marker still in view is drift, not a screen.
     */
    private static ActTools.Entered slowScreen(AgentContext context, ActTools.Entered first, ImageTemplate marker) {
        if (first.after() == null || first.share() >= SCREEN_SHARE || marker == null
                || !ActTools.shows(context, marker.id(), first.after())) {
            return first;
        }
        ActTools.Entered later = ActTools.later(context, first, SLOW_SCREEN_MS, SCREEN_SHARE);
        return later.after() == null || later != first && !ActTools.shows(context, marker.id(), later.after())
                ? later : first;
    }

    /**
     * The input of {@code on} this try is: the key's, the picture's click, or for a scroll the one of those notches,
     * else the picture's untried click, which the scroll then stands for.
     */
    private static Optional<Input> held(GameScreen on, String picture, Key key, int notches, ScreenGraph graph,
                                        String screen) {
        if (key != null) return on.inputs().stream().filter(i -> i.act() == Input.Act.KEY && i.key() == key).findFirst();
        List<Input> same = on.inputs().stream()
                .filter(i -> i.picture() != null && i.picture().id().equalsIgnoreCase(picture)).toList();
        if (notches == 0) return same.stream().filter(i -> i.act() == Input.Act.CLICK).findFirst();
        return same.stream().filter(i -> i.act() == Input.Act.SCROLL && i.notches() == notches).findFirst()
                .or(() -> same.stream().filter(i -> i.act() == Input.Act.CLICK && !graph.tried(screen, i)).findFirst());
    }

    /** The input to record: {@code held} as it is, a click it stood for made a scroll, or a new one. */
    private static Input made(StudioServices services, Optional<Input> held, String picture, Key key, int notches) {
        if (held.isPresent() && (notches == 0 || held.get().act() == Input.Act.SCROLL)) return held.get();
        if (key != null) return Input.key(key);
        Input fresh = FxCall.call(() -> {
            ImageTemplate cut = ScreenBook.picture(services, picture);
            return notches == 0 ? Input.click(cut) : Input.scroll(cut, notches);
        });
        return held.map(Input::kind).map(fresh::kind).orElse(fresh);
    }

    private static Input kinded(Input input, Optional<Kind> kind) {
        return kind.map(input::kind).orElse(input);
    }

    /** {@code screen} with {@code now} where {@code old} was, added when {@code old} is not on it, gone when null. */
    static GameScreen replaced(GameScreen screen, Input old, Input now) {
        List<Input> inputs = new ArrayList<>(screen.inputs());
        int at = old == null ? -1 : inputs.indexOf(old);
        if (at >= 0 && now == null) inputs.remove(at);
        else if (at >= 0) inputs.set(at, now);
        else if (now != null && inputs.stream().noneMatch(now::equals)) inputs.add(now);
        return screen.inputs(inputs);
    }

    private static List<Input> added(List<Input> inputs, Input input) {
        List<Input> all = new ArrayList<>(inputs);
        all.add(input);
        return all;
    }

    private static Optional<Kind> kindNamed(String typed) {
        String id = typed.strip().toUpperCase(Locale.ROOT);
        for (Kind kind : Kind.values()) if (kind.name().equals(id)) return Optional.of(kind);
        return Optional.empty();
    }

    /** A write's failure, Studio's own refusal or its being busy; empty when written. */
    private static Optional<String> write(Supplier<Optional<String>> write) {
        try {
            return FxCall.call(write);
        } catch (IllegalStateException busy) {
            return Optional.of(busy.getMessage());
        }
    }

    private static AgentReply unwritten(String why) {
        return AgentReply.refused("The input was used, but the bot's Screens could not be written: " + why);
    }

    private static String plainName() {
        return "Name screens in plain words on one line, as the game shows them: no colon, no markdown "
                + "(* _ ` [ ] # \\ < > ›).";
    }

    /**
     * The first screen with no marker whose section in the notes gives no reason for it ({@code Owed: marker — every
     * icon here animates}): one with a reason holds no other screen back, and stays owed.
     */
    static Optional<String> owingMarker(StudioServices services, ScreenGraph graph) {
        List<String> excused = new ArrayList<>();
        try {
            GameNotes.sections(services.projectDir()).forEach((name, text) -> {
                if (GameNotes.owed(text).stream().anyMatch(MapTools::markerWithReason)) {
                    excused.add(name.toLowerCase(Locale.ROOT));
                }
            });
        } catch (IllegalArgumentException | UncheckedIOException unread) {
            // No notes to read: no reason is given.
        }
        return graph.screens().keySet().stream().filter(c -> graph.screens().get(c).marker() == null)
                .filter(c -> !excused.contains(graph.shown(c).toLowerCase(Locale.ROOT))).findFirst();
    }

    /** Whether an {@code Owed:} line owes the marker and says why: {@code marker — every icon here animates}. */
    static boolean markerWithReason(String owed) {
        return MARKER_REASON.matcher(owed).matches();
    }

    private static final Pattern MARKER_REASON = Pattern.compile("(?i)marker[ \\t]+[—–-][ \\t]+\\S.*");

    static String owes(String screen) {
        return screen + " still owes its marker: screenshot it, then crop_picture with screen " + screen + " and "
                + "marker true a part only that screen shows; or, when none can be cut, write_notes its section "
                + "with \"Owed: marker — <why>\". Nothing was done.";
    }

    private static String labels(List<Input> inputs) {
        return String.join(", ", inputs.stream().map(Input::label).toList());
    }

    /** Whether {@code name} can name a screen: one line of plain words, no markdown. */
    static boolean isName(String name) {
        return name != null && !name.isEmpty() && !NOT_A_NAME.matcher(name).find();
    }

    /** The screen a picture opens, named from the picture: {@code engineer_home} on Home is {@code Engineer}. */
    static String nameFrom(String picture, String from) {
        String suffix = "_" + from.toLowerCase(Locale.ROOT).replace(' ', '_');
        String base = picture.toLowerCase(Locale.ROOT).endsWith(suffix) && picture.length() > suffix.length()
                ? picture.substring(0, picture.length() - suffix.length()) : picture;
        String words = base.replace('_', ' ').strip();
        return words.isEmpty() ? picture : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
