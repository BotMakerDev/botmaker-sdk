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
import com.botmaker.sdk.api.screens.ScreenList;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Variant;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.Markers;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenWalk;
import com.botmaker.sdk.internal.vision.FrameChange;
import com.botmaker.sdk.plugin.types.ScreenTypes;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Discovery's map, kept by a tool rather than by the assistant's memory (the user's call, 2026-10-14: mapping
 * Firestone, the assistant clicked through a dozen screens, cut nothing and wrote nothing until asked; then cut a
 * screen's buttons and left without trying one). The map is the bot's {@code Screens} class ({@link ScreenBook}):
 * every input cut on a screen with crop_pictures is untried until try_input clicks it (or scrolls over it, a list's),
 * and try_input records what it did — opened a screen (a new constant, owing its marker), led to one mapped already
 * (a way into it, or this one's way back), changed this one in place, or nothing. A screen opened is finished before
 * the next input of the one it was opened from: the tool refuses a screen while one reached from it has an untried
 * input, and any screen while one owes its marker ({@link ScreenGraph}). go_to_screen walks the map as a run does.
 */
final class MapTools {

    /** The share of the still screen an input must change to have opened a screen: Firestone's side panels. */
    static final double SCREEN_SHARE = 0.15;
    /** How much longer a small change is read on while the screen's marker still shows: Garage drew a second late. */
    static final int SLOW_SCREEN_MS = 2_500;
    /** How long a change over the whole screen that hid its marker is read on for the marker to come back. */
    static final int FLASH_MS = 1_500;
    /** How often the screen is read meanwhile. */
    private static final int FLASH_POLL_MS = 250;
    /** How long each step of a walk is given for the next screen's marker, as {@code Navigate.to} gives it. */
    private static final long STEP_MS = ScreenWalk.STEP_MS;

    /** What a screen's name cannot hold: a line break, a tab, any control character. */
    private static final Pattern NOT_A_NAME = Pattern.compile("\\p{Cntrl}");

    /** The screen the game was last seen on, by go_to_screen or which_screen: a walk looks near it first. */
    private static volatile String lastSeen;

    /** One input try_input tries: a picture to click or scroll over, or a hotkey. */
    record Step(@Describe(value = "a picture cut on that screen, clicked at its best match", optional = true)
                String picture,
                @Describe(value = "notches to scroll over the picture instead of clicking it, for a list or a panel "
                        + "that scrolls: positive up or away, negative down or toward you", optional = true)
                Integer scroll,
                @Describe(value = "which copy of a picture the screen shows more than once, from 1 in reading order: "
                        + "each copy is an input of its own; left out, its best match", optional = true)
                Integer match,
                @Describe(value = "a hotkey instead of a picture, as printed on it: Q", optional = true) String key,
                @Describe(value = "the name the screen it opens shows, when it opens one; left out, the picture's "
                        + "name names it, and a hotkey's is tried again with it when it opened one", optional = true)
                String opens,
                @Describe(value = "why it is never clicked (it spends premium currency or real money): recorded "
                        + "as tried, not clicked", optional = true) String never,
                @Describe(value = "the picture proving it locked on this account (a padlock on it): recorded as "
                        + "tried, not clicked", optional = true) String locked,
                @Describe(value = "what it is for", optional = true) Kind kind) {
    }

    record Try(@Describe("the screen the inputs are on, as the map names it: Home, or its constant HOME") String screen,
               @Describe("the inputs, tried in this order; the call stops at the first that leaves the screen (opens "
                       + "one, leads to one mapped already, or shows a popup) or is refused. One input is a list of "
                       + "one") List<Step> inputs,
               @Describe(value = "the longest each is given to answer, in milliseconds; 5000 when left out",
                       optional = true) Integer settleMs,
               @Describe(value = STAY, optional = true) Boolean stay,
               @Describe(value = "true to act on the user's own desktop, taking their mouse; refused there "
                       + "otherwise", optional = true) Boolean desktop) {
    }

    /** One input of a try_input call, with the call's screen and settings: what {@link #tried} tries. */
    private record One(String screen, String picture, Integer scroll, Integer match, String key, String opens,
                       String never, String locked, Kind kind, Integer settleMs, Boolean stay, Boolean desktop) {
        static One of(Try call, Step step, Boolean stay) {
            return new One(call.screen(), step.picture(), step.scroll(), step.match(), step.key(), step.opens(),
                    step.never(), step.locked(), step.kind(), call.settleMs(), stay, call.desktop());
        }

        /** The input that walks the game up from {@code call}'s screen once every input of it was tried. */
        static One after(Try call) {
            return new One(call.screen(), null, null, null, null, null, null, null, null, null, call.stay(),
                    call.desktop());
        }

        String label() {
            return picture != null && !picture.isBlank() ? picture.strip() + (match == null ? "" : " #" + match)
                    + (scroll == null ? "" : " " + scroll) : key == null ? "?" : key.strip();
        }
    }

    private static final String STAY = "true stays on the screen when this leaves it finished; left out, the game is "
            + "walked to the next screen to explore";

    record Goal(@Describe("the screen to go to, as the map names it: Campaign, or its constant CAMPAIGN") String screen,
                @Describe(value = "true to act on the user's own desktop, taking their mouse; refused there "
                        + "otherwise", optional = true) Boolean desktop) {
    }

    record Places(@Describe("the screen they are on, as the map names it, or a new one its marker starts") String screen,
                  @Describe(value = "pictures the bot has, each noted as an untried input of that screen",
                          optional = true) List<String> pictures,
                  @Describe(value = "a picture the bot has that is that screen's marker, a part no other screen shows",
                          optional = true) String marker,
                  @Describe(value = "a picture the bot has that is that screen's close button, its way back",
                          optional = true) String back,
                  @Describe(value = "true, with marker, when the screen is a popup the game opens on its own",
                          optional = true) Boolean popup,
                  @Describe(value = "pictures the bot has that show only at times on that screen (a Claim when "
                          + "loot is ready)", optional = true) List<String> sometimes) {
    }

    record Mistake(@Describe("the screen try_input added that is no screen of its own, as the map names it") String screen) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("not_a_screen")
                    .describedAs("Takes back a screen try_input added when the input only changed the screen it was "
                            + "on (a whole redraw: a stage changed, a fight restarted): the screen leaves the map and "
                            + "each way into it is recorded on the screen it leaves as changing that one in place. "
                            + "Refused once anything is reached from it")
                    .takes(Mistake.class).handledBy(MapTools::notAScreen),
            AssistantTool.named("place_pictures")
                    .describedAs("crop_pictures' map half for pictures the bot has already: notes them on a screen "
                            + "of the bot's Screens as its marker, its way back, or inputs untried until try_input "
                            + "tries them, without cutting them again. A marker starts the screen as a cut one does")
                    .takes(Places.class).handledBy(MapTools::placePictures),
            AssistantTool.named("try_input")
                    .describedAs("Discovery's one way to act on a screen: clicks inputs cut on it (scrolls over "
                            + "one, or taps a hotkey) and records in the bot's Screens what each did: opened a screen "
                            + "(a new one, owing its marker, with its screenshot), led to one mapped already, changed "
                            + "this one in place, or nothing. Every picture crop_pictures cut with a screen is untried "
                            + "until this tries it. Breadth first: a screen opened gets its marker and inputs cut, "
                            + "then its parent's inputs are tried on; refused while a screen owes its marker. Once "
                            + "nothing on the screen is left to try, it walks to the next screen to explore, the "
                            + "shallowest with inputs left (stay keeps it)")
                    .takes(Try.class).handledBy(MapTools::tryInput),
            AssistantTool.named("go_to_screen")
                    .describedAs("Walks the game to a screen of the bot's Screens as a run does: finds where it is "
                            + "by the markers, then does each input on the way and waits for each screen; a popup "
                            + "on the way is closed. Says where it ended and why when it did not arrive")
                    .takes(Goal.class).handledBy(MapTools::goToScreen));

    private MapTools() {}

    /** What one try did: its reply, and whether the game is still on the screen tried, for the next input. */
    private record Tried(AgentReply reply, boolean stays) {
        static Tried left(AgentReply reply) {
            return new Tried(reply, false);
        }

        static Tried refused(String why) {
            return left(AgentReply.refused(why));
        }
    }

    /**
     * try_input; Studio busy at any read or write of the bot (each on its thread) is said, never thrown. One input is
     * tried as it is, walking up when it finished the screen; several each stay on the screen, until one is refused
     * or leaves it (it opened a screen, led to one mapped already or showed a popup), then the walk up, once, when
     * the screen is finished.
     */
    static AgentReply tryInput(Try call, AgentContext context) {
        List<Step> steps = call.inputs() == null ? List.of() : call.inputs();
        if (steps.isEmpty()) return AgentReply.refused("Give the inputs to try, in order. Nothing was done.");
        if (steps.size() == 1) return attempt(One.of(call, steps.getFirst(), call.stay()), context).reply();
        AgentReply all = null;
        for (int i = 0; i < steps.size(); i++) {
            One one = One.of(call, steps.get(i), true);
            Tried tried = attempt(one, context);
            if (tried.reply().isRefused()) {
                if (all == null) return tried.reply();
                return all.and(AgentReply.text("Stopped before " + one.label() + ": " + tried.reply().parts()
                        .getFirst().text() + " Not tried: " + labels(call, steps.subList(i, steps.size())) + "."));
            }
            all = all == null ? tried.reply() : all.and(tried.reply());
            if (!tried.stays()) {
                return i + 1 == steps.size() ? all : all.and(AgentReply.text("Stopped: " + one.label()
                        + " left the screen, or covered it. Not tried: " + labels(call, steps.subList(i + 1,
                        steps.size())) + "."));
            }
        }
        Optional<String> screen = readScreen(context, call.screen());
        return screen.isEmpty() ? all : up(context, all, screen.get(), One.after(call));
    }

    private static String labels(Try call, List<Step> steps) {
        return String.join(", ", steps.stream().map(s -> One.of(call, s, true).label()).toList());
    }

    private static Tried attempt(One input, AgentContext context) {
        try {
            return tried(input, context);
        } catch (IllegalStateException busy) {
            return Tried.refused("Studio did not answer while reading or writing the bot's Screens: "
                    + busy.getMessage() + " Look where the game is and read the map (read_guide) before trying again.");
        }
    }

    /** The constant {@code typed} names in the map; empty when none or Studio is busy. */
    private static Optional<String> readScreen(AgentContext context, String typed) {
        try {
            return ScreenBook.find(FxCall.call(() -> ScreenBook.read(context.services())), typed);
        } catch (IllegalStateException busy) {
            return Optional.empty();
        }
    }

    private static Tried tried(One input, AgentContext context) {
        StudioServices services = context.services();
        if (services.projectDir() == null) return Tried.refused(Frames.noProject());
        boolean byPicture = input.picture() != null && !input.picture().isBlank();
        boolean byKey = input.key() != null && !input.key().isBlank();
        if (byPicture == byKey) {
            return Tried.refused("An input is a picture to click or a hotkey, one of them: the bot can take no "
                    + "other. A button with no picture yet: screenshot, crop_pictures it with its screen, then "
                    + "try_input it. Nothing was done.");
        }
        Optional<Key> key = byKey ? Key.named(input.key()) : Optional.empty();
        if (byKey && key.isEmpty()) return Tried.refused("\"" + input.key() + "\" is no key.");
        String opens = input.opens() == null || input.opens().isBlank() ? null : input.opens().strip();
        String asked = input.screen() == null ? "" : input.screen().strip();
        if (!isName(asked) || opens != null && !isName(opens)) return Tried.refused(plainName());
        boolean never = input.never() != null;
        if (never && input.never().isBlank()) {
            return Tried.refused("Say why it is never clicked (it spends gems). Nothing was done.");
        }
        boolean locked = input.locked() != null && !input.locked().isBlank();
        if (never && locked) return Tried.refused("An input is never done or locked, not both. Nothing was done.");
        Optional<Kind> kind = Optional.ofNullable(input.kind());
        int notches = input.scroll() == null ? 0 : input.scroll();
        if (input.scroll() != null && (notches == 0 || !byPicture || never || locked)) {
            return Tried.refused(notches == 0 ? "Scroll by at least one notch." : !byPicture ? "A scroll is over "
                    + "a picture: the list or panel, cut with its screen." : "A scroll spends nothing and opens "
                    + "nothing: leave never and locked out.");
        }
        int match = input.match() == null ? 0 : input.match();
        if (input.match() != null && (match < 1 || !byPicture)) {
            return Tried.refused(!byPicture ? "A hotkey has no copies: leave match out." : "match counts the "
                    + "picture's copies from 1, the first in reading order. Nothing was done.");
        }
        int settle = input.settleMs() == null ? ActTools.SETTLE_MS : input.settleMs();
        ScreenGraph graph = FxCall.call(() -> ScreenBook.read(services));
        Optional<String> found = ScreenBook.find(graph, asked);
        if (found.isEmpty()) {
            return Tried.refused("No screen " + asked + " in the bot's Screens" + (graph.screens().isEmpty()
                    ? ": crop_pictures the first screen's marker with screen " + asked + ", its box marker set; that "
                    + "starts the map." : ": try_input adds a screen when an input opens it.") + " Nothing was done.");
        }
        String screen = found.get();
        String name = graph.shown(screen);
        Optional<String> owing = graph.owingMarker();
        if (owing.isPresent()) return Tried.refused(owes(graph.shown(owing.get())));
        GameScreen on = graph.screens().get(screen);
        String what = byPicture ? input.picture().strip() : null;
        Optional<Input> held = held(on, what, key.orElse(null), notches, match, graph, screen);
        boolean untried = held.filter(i -> !graph.tried(screen, i)).isPresent();
        if (byPicture && !ActTools.exists(context, what)) {
            if (!untried) return Tried.refused(Frames.noPicture(what));
            Optional<String> failed = write(() -> ScreenBook.update(services, screen, s -> replaced(s, held.get(), null)));
            // Nothing clicked: the game is where it was.
            return new Tried(failed.map(AgentReply::refused).orElseGet(() -> AgentReply.text(what + " is no "
                    + "picture any more (deleted or renamed): taken off " + name + "'s untried inputs. Nothing was "
                    + "clicked.")), true);
        }
        if ((never || locked) && byPicture && !untried) {
            return Tried.refused(what + " is not an untried input of " + name + ": crop_pictures it with screen "
                    + name + " first. Nothing was done.");
        }
        if (locked && !ActTools.exists(context, input.locked().strip())) {
            return Tried.refused(Frames.noPicture(input.locked().strip()));
        }
        // A hotkey names nothing: when it opens a screen, it is tried again with opens.
        String child = opens != null ? opens : byPicture ? nameFrom(what, name) : null;
        if (byPicture && !never && !locked && notches == 0 && opens == null
                && (!isName(child) || ScreenBook.find(graph, child).isPresent())) {
            return Tried.refused(what + " would name the screen it opens \"" + child + "\"" + (isName(child)
                    ? ", a screen the map has" : "") + ": give opens, the name that screen shows. Nothing was done.");
        }
        Optional<AgentReply> away = notOn(context, graph, screen);
        // Left on a screen below it, finished with nothing to try (a rules panel): walked back to it first.
        if (away.isPresent() && !walkedTo(context, graph, screen, input.desktop())) return Tried.left(away.get());
        if (match > 0 && held.isPresent() && held.get().nth() == 0) {
            // The picture cut once for all its copies becomes one untried input per copy a run finds: none is left
            // out of Discovery, each with its own result to come.
            int count = ToolReach.of(context).flatMap(reach -> reach.look().map(frame -> reach.copies(held.get(),
                    on.listOf(held.get()).orElse(null), frame).size())).orElse(0);
            if (count < match) {
                return Tried.left(AgentReply.text(what + " shows " + (count == 0 ? "nowhere" : count + (count == 1
                        ? " time" : " times")) + " at a run's score, so it has no copy " + match
                        + "; nothing was clicked."));
            }
            Optional<String> failed = write(() -> ScreenBook.update(services, screen,
                    s -> copies(s, held.get(), count)));
            return failed.isPresent() ? Tried.refused(failed.get()) : tried(input, context);
        }
        Input made =kinded(made(services, held, what, key.orElse(null), notches), kind);
        // A copy tried by its place among the picture's copies is recorded as that copy.
        Input base = match > 0 && made.nth() != match ? made.nth(match) : made;
        Input old = held.orElse(null);
        // A button with looks leads where the look clicked says: the way it opens names that look, and its usual
        // look none (a way taken whatever the button looks).
        State look = what == null ? null : base.variants().stream()
                .filter(v -> v.picture().id().equalsIgnoreCase(what)).map(Variant::state).findFirst().orElse(null);
        boolean ask = !graph.untried(screen).isEmpty();
        if (never || locked) {
            Input settled = never ? base.never(input.never().strip())
                    : base.locked(FxCall.call(() -> ScreenBook.picture(services, input.locked().strip())));
            return new Tried(up(context, noted(services, screen, old, settled, never ? "never, " + input.never()
                    .strip() : "locked by " + input.locked().strip(), ask), screen, input), true);
        }
        Optional<ScreenList> list = notches == 0 ? on.listOf(base) : Optional.empty();
        if (list.isPresent()) {
            // A member of a list out of view is scrolled to first, as a run scrolls to it.
            ToolReach.of(context).ifPresent(reach -> reach.look()
                    .ifPresent(frame -> reach.inView(base, list.get(), frame)));
        }
        ActTools.Entered first = ActTools.enter(context, screen, byPicture ? what : null, notches, base,
                list.orElse(null), key.orElse(null), input.desktop(), settle, FrameChange.ANSWER_SHARE);
        Markers onMarkers = Markers.of(on);
        ActTools.Entered slow = notches == 0 ? slowScreen(context, first, onMarkers) : first;
        ActTools.Entered entered = notches == 0 ? flash(context, slow, onMarkers) : slow;
        boolean flashed = entered != slow;    // its markers hidden a moment, then back: this screen, redrawn
        if (entered.after() == null && notches != 0 && !entered.reply().isRefused() && onMarkers.any()
                && Frames.look(context).filter(f -> ActTools.shows(context, onMarkers, f)).isPresent()) {
            // The screen is still there: what scrolled is the picture itself, carried off by the list it was on.
            return Tried.refused("The scrolled picture " + what + " moved off its place (the list carried it), so "
                    + "nothing was scrolled. Scroll over a picture that stays put while the list moves (its frame, "
                    + "its title), cut with screen " + name + ".");
        }
        if (entered.after() == null) {
            return Tried.left(entered.reply().isRefused() ? entered.reply() : entered.reply().and(AgentReply.text(
                    "Nothing was written. Look where the game is before trying it again from " + name + ".")));
        }
        if (notches != 0) {
            // A list scrolled moves much of the screen and opens nothing: the end shows when nothing moves.
            boolean moved = FrameChange.box(entered.before(), entered.after(), entered.motion()).isPresent();
            AgentReply said = entered.reply().and(noted(services, screen, old, base.did(moved ? Did.IN_PLACE
                    : Did.NONE), moved ? "changes this screen in place" : "no visible effect", ask && !moved));
            if (moved) {
                return new Tried(said.and(AgentReply.text("Scroll it again the same way until nothing moves (its "
                        + "end), then the other way to its other end; cut, with screen " + name + ", each input it "
                        + "brings into view: they are untried in turn.")), true);
            }
            // An end reached may be the screen's last input: then it is finished, as a click's would be.
            return new Tried(up(context, said.and(AgentReply.text("Nothing moved: that end is reached. Its other end "
                    + "too, when not scrolled to yet.")), screen, input), true);
        }
        if (!flashed && entered.share() >= SCREEN_SHARE) {
            // The screen opens names first, when the map has it and its marker shows.
            Optional<String> named = opens == null ? Optional.empty() : ScreenBook.find(graph, opens)
                    .filter(c -> !c.equals(screen));
            if (named.isPresent()) {
                GameScreen to = graph.screens().get(named.get());
                if (to.hasMarker() && !ActTools.shows(context, Markers.of(to), entered.after())) {
                    // Taken at its word, a fight's start panel was recorded as the fight (2026-10-15).
                    return Tried.left(entered.reply().and(AgentReply.text("Nothing was written: "
                            + graph.shown(named.get()) + "'s marker " + Markers.of(to).ids() + " does not show, so this "
                            + "is not " + graph.shown(named.get()) + ". Look at it (describe); give opens the name it "
                            + "shows, and try " + base.label() + " again from " + name + ".")));
                }
            }
            // A name the map lacks, given as opens, is a screen opened; else where the markers say the game is.
            boolean anew = opens != null && ScreenBook.find(graph, opens).isEmpty();
            Optional<String> known = anew ? Optional.empty() : named.or(() -> landed(context, graph, screen, entered));
            if (known.isPresent()) {
                AgentReply said = entered.reply().and(reached(services, graph, screen, old, base, look, known.get()));
                // A popup shows over the screen; anything else left it, maybe finished.
                return Tried.left(graph.screens().get(known.get()).isPopup() ? said : up(context, said, screen, input));
            }
            if (child == null) {
                return Tried.left(entered.reply().and(AgentReply.text("Nothing was written: " + base.label()
                        + " opened a screen the map does not have, and a hotkey names none. Look at it (describe), "
                        + "go back to " + name + " and try it again with opens, the name it shows.")));
            }
            return Tried.left(entered.reply().and(opened(context, graph, screen, old, base, look, child,
                    entered.after())));
        }
        boolean changed = FrameChange.box(entered.before(), entered.after(), entered.motion()).isPresent();
        AgentReply said = noted(services, screen, old, base.did(changed ? Did.IN_PLACE : Did.NONE),
                changed ? "changes this screen in place" : "no visible effect", ask);
        return new Tried(up(context, entered.reply().and(changed ? said.and(AgentReply.text("Cut, with screen " + name
                + ", each new input it shows (another state of a toggle, a tab's buttons): they are untried in turn."))
                : said), screen, input), true);
    }

    /**
     * Why the game is not on {@code screen}, read on a fresh look: its marker does not show, or a screen opened from
     * it covers it (Info luck over Tavern, both markers in view, is not Tavern). Empty when it is, and for a screen
     * with no marker yet.
     */
    private static Optional<AgentReply> notOn(AgentContext context, ScreenGraph graph, String screen) {
        GameScreen on = graph.screens().get(screen);
        if (!on.hasMarker()) return Optional.empty();
        String name = graph.shown(screen);
        Optional<ToolReach> reach = ToolReach.of(context);
        if (reach.isEmpty()) {
            // No reach to read the whole set by: each marker looked for alone, the first missing one said.
            return on.markers().stream().map(m -> ActTools.notOn(context, m.id(), name)).flatMap(Optional::stream)
                    .findFirst();
        }
        Optional<BufferedImage> frame = reach.get().look();
        if (frame.isEmpty()) return Optional.of(AgentReply.refused(Frames.noScreen()));
        if (!ActTools.shows(context, Markers.of(on), frame.get())) {
            return Optional.of(AgentReply.refused("The game is not on " + name + ": its marker " + Markers.of(on).ids()
                    + (on.absent().isEmpty() ? "" : ", without " + String.join(", ", on.absent().stream()
                    .map(ImageTemplate::id).toList())) + ", is not on the screen. Go back to " + name + " first."));
        }
        // A popup shows over the tree's screens, which whereOn answers with: its own marker is the proof.
        if (on.isPopup()) return Optional.empty();
        Optional<String> here = new ScreenWalk(graph, reach.get(), STEP_MS, null).whereOn(frame.get(), screen);
        if (here.isEmpty() || here.get().equals(screen)) return Optional.empty();
        return Optional.of(AgentReply.refused("The game is not on " + name + ": " + graph.shown(here.get())
                + " shows over it. Go back to " + name + " first."));
    }

    /**
     * The screen of the map other than {@code screen} that {@code entered} left the game on: a popup that came into
     * view, else the one the markers say ({@link ScreenWalk#whereOn}), so a panel closed to the screen under it, whose
     * marker showed under the panel too, is that screen and no new one. Empty while the markers still say
     * {@code screen}, or none shows.
     */
    private static Optional<String> landed(AgentContext context, ScreenGraph graph, String screen,
                                           ActTools.Entered entered) {
        Optional<ToolReach> reach = ToolReach.of(context);
        if (reach.isEmpty()) return Optional.empty();
        ScreenWalk walk = new ScreenWalk(graph, reach.get(), STEP_MS, null);
        Optional<String> popup = walk.popupOn(entered.after()).filter(c -> !c.equals(screen)
                && !ActTools.shows(context, Markers.of(graph.screens().get(c)), entered.before()));
        if (popup.isPresent()) return popup;
        return walk.whereOn(entered.after(), screen).filter(c -> !c.equals(screen));
    }

    /** Walks the game to {@code screen} by the map, as go_to_screen does; whether it got there. */
    private static boolean walkedTo(AgentContext context, ScreenGraph graph, String screen, Boolean desktop) {
        Optional<ToolReach> reach = ToolReach.of(context);
        if (reach.isEmpty() || ActTools.desktopRefusal(context, reach.get().hands(), desktop).isPresent()) {
            return false;
        }
        ScreenWalk.Result walked = new ScreenWalk(graph, reach.get(), STEP_MS, null, close(context)).go(screen, null);
        seen(graph, walked.at());
        return walked.arrived();
    }

    /**
     * {@code said}, and when the try left {@code screen} finished, the game walked to the screen Discovery explores next
     * ({@link ScreenGraph#nextToExplore}: the shallowest with work left), checked by that screen's marker;
     * {@code input}'s {@code stay} keeps it. A popup's inputs are tried while it shows, so it walks nowhere.
     */
    private static AgentReply up(AgentContext context, AgentReply said, String screen, One input) {
        if (said.isRefused() || Boolean.TRUE.equals(input.stay())) return said;
        ScreenGraph graph;
        try {
            graph = FxCall.call(() -> ScreenBook.read(context.services()));
        } catch (IllegalStateException busy) {
            return said;
        }
        boolean stays = graph.unfinished(screen) || graph.screen(screen).filter(GameScreen::isPopup).isPresent();
        Optional<String> to = stays ? Optional.empty()
                : graph.nextToExplore().filter(c -> !c.equals(screen));
        Optional<ToolReach> reach = ToolReach.of(context);
        if (to.isEmpty() || reach.isEmpty()
                || ActTools.desktopRefusal(context, reach.get().hands(), input.desktop()).isPresent()) {
            return said;
        }
        ScreenWalk.Result walked = new ScreenWalk(graph, reach.get(), STEP_MS, null, close(context)).go(to.get(),
                screen);
        seen(graph, walked.at());
        if (walked.arrived()) retakeShot(context, reach.get(), graph, walked.at());
        String back = graph.shown(to.get());
        String left = graph.shown(screen);
        return said.and(AgentReply.text(walked.arrived()
                ? "Nothing left to try on " + left + ": went to " + back + ", the next screen to explore (the "
                + "shallowest with inputs left). Anything on " + left + " not cut yet (a tab's buttons, a list's far "
                + "end): go_to_screen " + left + ", cut it with screen " + left + " and try it."
                : "Nothing left to try on " + left + ", but the way to " + back + ", the next screen to explore, "
                + "failed: " + walked.why() + " Look where the game is (which_screen)."));
    }

    /** The game's usual close button, the bot's picture {@value #CLOSE}, as a way back with none tries it first. */
    private static Input close(AgentContext context) {
        if (!ActTools.exists(context, CLOSE)) return null;
        try {
            return FxCall.call(() -> Input.click(ScreenBook.picture(context.services(), CLOSE)));
        } catch (IllegalStateException busy) {
            return null;
        }
    }

    /** {@code via} on {@code screen} as a way in, needing the look {@code look} of its button (null: any). */
    private static Way way(String screen, Input via, State look) {
        Way way = Way.of(BotConstants.screen(screen), ScreenGraph.bare(via));
        return look == null ? way : way.whenLooks(look);
    }

    /** The picture name a way back with no input of its own clicks first, before Escape: Firestone's close_x. */
    static final String CLOSE = "close_x";

    /** {@code base}, which opened a screen the map has not: added below the others, owing its marker. */
    private static AgentReply opened(AgentContext context, ScreenGraph graph, String screen, Input old, Input base,
                                     State look, String child, BufferedImage frame) {
        StudioServices services = context.services();
        String constant = ScreenBook.constantFor(graph, child);
        GameScreen made = GameScreen.named(child).from(way(screen, base, look));
        // Tried again, what it did before goes: the way it opens is what it does now.
        Optional<String> failed = write(() -> ScreenBook.update(services, screen, s -> replaced(s, old, base.did(null)))
                .or(() -> ScreenBook.add(services, constant, made)));
        if (failed.isPresent()) return unwritten(failed.get());
        // A draft: the frame right after the click may be the way there (Firestone's Arena replay kept its VS
        // splash, 2026-10-16). The marker's cut replaces it with the frame the marker was cut from.
        ScreenShots.save(services.resourcesDir(), constant, frame);
        String from = graph.shown(screen);
        return AgentReply.text("Opened " + child + ", added to the map as Screens." + constant + ", reached from "
                        + from + " by " + base.label() + "; it owes its marker. 🖼 Manage Pictures groups the "
                        + "pictures it names under it. Before leaving it, "
                        + "from the screenshot below: crop_pictures with screen " + child + " and marker set a part "
                        + "only this screen shows, then with screen " + child + " every input on it (each button, "
                        + "icon, tab, toggle, arrow and list, and its close button); what cannot be cut now, "
                        + "write_notes with screen " + child + " and owes. Then go_to_screen " + from + " and try its "
                        + "next input: Discovery is breadth first, so " + child + "'s inputs are tried once the "
                        + "screens above it are finished (read_guide names the next screen to explore).")
                .and(LookTools.screenshot(new LookTools.Shot(null, null, null, null, null, null), context));
    }

    /**
     * {@code base} on {@code screen} led to {@code known}, mapped already: a popup over it, its way back, or another
     * way into {@code known}, which names {@code screen} once it is declared below it; a screen reached through
     * {@code known} cannot be named from it, so the input says it leaves.
     */
    private static AgentReply reached(StudioServices services, ScreenGraph graph, String screen, Input old, Input base,
                                      State look, String known) {
        String to = graph.shown(known);
        GameScreen target = graph.screens().get(known);
        if (target.isPopup()) {
            return noted(services, screen, old, base.did(Did.IN_PLACE), "shows the popup " + to, false)
                    .and(AgentReply.text(to + " is a popup: close it before the next input."));
        }
        // A popup's way out is its back, whatever it uncovers: a popup is in no tree and names no screen.
        boolean up = graph.screens().get(screen).isPopup() || graph.parent(screen).filter(known::equals).isPresent();
        Input back = graph.screens().get(screen).back();
        if (up && back != null && !back.sameAs(base)) {
            // A Confirm that also goes back is an input of its own: the close button stays the way back (Rooms,
            // 2026-10-15).
            return noted(services, screen, old, base.did(Did.LEAVES), "leaves for " + to + "; the way back stays "
                    + back.label(), false);
        }
        if (up) {
            Optional<String> failed = write(() -> ScreenBook.update(services, screen,
                    s -> replaced(s, old, null).back(ScreenEdits.untried(base))));
            return failed.map(MapTools::unwritten).orElseGet(() -> noted(services, screen, base, null,
                    "its way back to " + to, false));
        }
        List<String> order = List.copyOf(graph.screens().keySet());
        int below = order.indexOf(known);
        if (graph.dependsOn(screen).contains(known)) {
            return noted(services, screen, old, base.did(Did.LEAVES), "leaves for " + to + ", which this screen is "
                    + "reached through, so the map cannot name it from here", false);
        }
        // The screen and every screen it is reached through declared below known move above it, in their order, so
        // known may name the screen (Squads under Engineer, both mapped after Arena squad, 2026-10-16).
        List<String> moving = order.stream().filter(c -> order.indexOf(c) > below
                && (c.equals(screen) || graph.dependsOn(screen).contains(c))).toList();
        // A way taken whatever the button looks covers each look; one needing a look covers only that look.
        boolean already = graph.leads(screen, base).stream().anyMatch(lead -> lead.to().equals(known)
                && (lead.state() == null || lead.state() == look));
        Optional<String> failed = write(() -> moving.stream()
                .map(c -> ScreenBook.moveBefore(services, c, known)).flatMap(Optional::stream).findFirst()
                .or(() -> ScreenBook.update(services, screen, s -> replaced(s, old, base.did(null))))
                .or(() -> already ? Optional.empty() : ScreenBook.update(services, known, s -> {
                    List<Way> ways = new ArrayList<>(s.alsoFrom());
                    ways.add(way(screen, base, look));
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
                + "that was not: crop_pictures it with screen " + name + " and try it." : "")
                : " Untried: " + ScreenGraph.labels(left) + "."));
    }

    /** Where crop_pictures notes a cut: a screen of the map, or the one it starts; else why it cannot. */
    record Placing(String constant, String name, boolean starts, boolean popup, String refusal) {
        static Placing refused(String why) {
            return new Placing(null, null, false, false, why);
        }
    }

    /**
     * The screen {@code typed} names for a cut. A marker cut for a screen the map lacks starts it when the map has no
     * screen yet (the first, Home) or when it is a {@code popup}; any other screen is added by try_input. A marker
     * cut as a {@code popup}'s for a screen the map has makes it one.
     */
    static Placing placing(StudioServices services, String typed, boolean marker, boolean popup) {
        if (!isName(typed)) return Placing.refused(plainName());
        ScreenGraph graph = FxCall.call(() -> ScreenBook.read(services));
        Optional<String> found = ScreenBook.find(graph, typed);
        if (found.isPresent()) {
            boolean becomes = marker && popup && !graph.screens().get(found.get()).isPopup();
            List<String> after = graph.screens().keySet().stream()
                    .filter(c -> !c.equals(found.get()) && graph.dependsOn(c).contains(found.get())).toList();
            if (becomes && !after.isEmpty()) {
                return Placing.refused(graph.shown(found.get()) + " cannot be a popup: " + String.join(", ",
                        after.stream().map(graph::shown).toList()) + " is reached from it, and a popup opens on its "
                        + "own and leads nowhere. Nothing was saved.");
            }
            return new Placing(found.get(), graph.shown(found.get()), false, becomes, null);
        }
        boolean first = graph.screens().values().stream().allMatch(GameScreen::isPopup);
        if (marker && (popup || first)) {
            return new Placing(ScreenBook.constantFor(graph, typed), typed, true, popup, null);
        }
        return Placing.refused("No screen " + typed + " in the bot's Screens: " + (first ? "the first screen starts "
                + "the map with its marker, marker set." : "try_input adds a screen when an input opens it; a popup "
                + "starts with its marker cut with popup true.") + " Nothing was saved.");
    }

    /**
     * Notes the picture {@code picture}, just cut, on the screen {@code at} names: the screen it starts, its marker,
     * its way back, or an input untried until try_input tries it ({@code sometimes}: one that comes and goes). Says
     * where.
     */
    static String noteCut(StudioServices services, Placing at, String picture, Mark marker, boolean back,
                          boolean sometimes) {
        return noteCut(services, at, picture, marker, back, sometimes, Look.NONE);
    }

    /**
     * What a picture cut is to what tells its screen apart. A screen shows where all its markers are found and none of
     * its absent pictures is: one picture is not always unique (Rarity's title scored 0.80 on Upgrade, tabs share
     * their window, 2026-10-16).
     */
    enum Mark {
        /** One of the markers this call cuts, which together replace the screen's set. */
        SET,
        /** A marker added to the set the screen has. */
        ADD,
        /** A picture the screen never shows, where another screen like it does. */
        ABSENT;

        /** Whether it is a marker, shown, not an absent picture. */
        boolean shown() {
            return this != ABSENT;
        }
    }

    /** {@code list} with {@code cut}, which it holds once. */
    private static List<ImageTemplate> with(List<ImageTemplate> list, ImageTemplate cut) {
        if (list.stream().anyMatch(p -> p.id().equals(cut.id()))) return list;
        List<ImageTemplate> more = new ArrayList<>(list);
        more.add(cut);
        return more;
    }

    /** {@code set} as {@code mark} changes it by {@code cut}: replaced, added to, or given an absent picture. */
    private static Markers marked(Markers set, Mark mark, ImageTemplate cut) {
        return switch (mark) {
            case SET -> new Markers(List.of(cut), set.absent());
            case ADD -> new Markers(with(set.shown(), cut), set.absent());
            case ABSENT -> new Markers(set.shown(), with(set.absent(), cut));
        };
    }

    /** {@code screen}'s markers and absent pictures, read from the map; empty while the map lacks it. */
    static Optional<Markers> markers(StudioServices services, String typed) {
        try {
            ScreenGraph graph = FxCall.call(() -> ScreenBook.read(services));
            return ScreenBook.find(graph, typed).map(c -> Markers.of(graph.screens().get(c)));
        } catch (IllegalStateException busy) {
            return Optional.empty();
        }
    }

    /**
     * After {@code typed}'s markers were written: when another screen's shot shows them too, they are put back as
     * {@code before} and said so, with what would tell the two apart; blank when they tell it alone. Checked once the
     * whole set is written: two pictures that each show elsewhere may show together nowhere else.
     */
    static String unique(StudioServices services, String typed, Markers before) {
        ScreenGraph graph;
        try {
            graph = FxCall.call(() -> ScreenBook.read(services));
        } catch (IllegalStateException busy) {
            return "";
        }
        Optional<String> constant = ScreenBook.find(graph, typed);
        if (constant.isEmpty()) return "";
        Markers now = Markers.of(graph.screens().get(constant.get()));
        if (now.same(before)) return "";
        Optional<MapAudit.Clash> clash = MapAudit.clash(services.resourcesDir(), MapAudit.confidence(services), graph,
                constant.get(), now);
        if (clash.isEmpty()) return "";
        Optional<String> failed = FxCall.call(() -> ScreenBook.update(services, constant.get(),
                s -> s.markers(before.shown()).absent(before.absent())));
        String name = graph.shown(constant.get());
        return " But " + clashSaid(graph, constant.get(), now, clash.get()) + (failed.isPresent() ? " Putting " + name
                + "'s markers back failed: " + failed.get() : " " + name + " keeps " + before.said() + "; the "
                + "pictures stay saved.");
    }

    /** That {@code set} of {@code constant} shows on {@code clash}'s screen too, and what would tell the two apart. */
    static String clashSaid(ScreenGraph graph, String constant, Markers set, MapAudit.Clash clash) {
        String other = graph.shown(clash.screen());
        String name = graph.shown(constant);
        return set.said() + " shows on " + other + "'s shot too, so a run would take " + other + " for " + name
                + ". Add a marker only " + name + " shows (marker add), or " + (clash.apart().isEmpty() ? "one of "
                + other + "'s that " + name + " never shows" : String.join(" or ", clash.apart()) + ", which " + name
                + "'s shot does not show,") + " as absent (marker absent).";
    }

    /**
     * How a cut is noted beyond marker, back and sometimes: another look of the input {@code variantOf} names, and
     * for one that comes and goes, the bot's method saying when it is there, {@code Firestone::eventOn}; and the look
     * of the screen it belongs to, {@code Halloween}, as that look's marker or an input only it has.
     */
    record Look(String variantOf, State state, String when, String screenLook) {
        static final Look NONE = new Look(null, null, null, null);
    }

    /** As above, the cut also noted as {@code look} says. */
    static String noteCut(StudioServices services, Placing at, String picture, Mark mark, boolean back,
                          boolean sometimes, Look look) {
        if (look.variantOf() != null) return noteVariant(services, at, picture, look);
        if (look.screenLook() != null) return noteScreenLook(services, at, picture, mark, sometimes, look);
        boolean marker = mark != null;
        try {
            return FxCall.call(() -> {
                ImageTemplate cut = ScreenBook.picture(services, picture);
                if (at.starts()) {
                    GameScreen screen = GameScreen.named(at.name()).markers(List.of(cut));
                    Optional<String> failed = ScreenBook.add(services, at.constant(), at.popup() ? screen.popup() : screen);
                    return failed.map(why -> " It could not be added to the map: " + why).orElse(" Added "
                            + at.name() + " to the map as Screens." + at.constant() + ", this its marker" + (at.popup()
                            ? "; a popup, closed by Escape until its close button is cut with back true."
                            : ": the map's first screen. Cut every input on it with screen " + at.name() + "."));
                }
                ScreenGraph graph = ScreenBook.read(services);
                GameScreen screen = graph.screens().get(at.constant());
                if (screen == null) return " " + at.name() + " is no longer in the map, so it was not noted.";
                Markers set = Markers.of(screen);
                if (mark == Mark.ABSENT && set.shown().stream().anyMatch(p -> p.id().equals(cut.id()))) {
                    return " " + at.name() + " has " + cut.id() + " as a marker: it cannot be one it never shows too.";
                }
                if (mark == Mark.ADD && set.absent().stream().anyMatch(p -> p.id().equals(cut.id()))) {
                    return " " + at.name() + " has " + cut.id() + " as a picture it never shows: edit_map remove_marker "
                            + "it first.";
                }
                Markers now = marker ? marked(set, mark, cut) : set;
                if (marker && now.same(set)) return " " + at.name() + " is told by " + set.said() + " already.";
                // A set replaced by one picture is said: it loses what told the screen apart.
                String was = mark == Mark.SET && set.any() ? set.ids() : null;
                Optional<Input> lookOf = screen.allInputs().stream()
                        .filter(i -> i.variants().stream().anyMatch(v -> v.picture().id().equals(cut.id()))).findFirst();
                if (lookOf.isPresent()) return " " + at.name() + " has it as a look of " + lookOf.get().label() + ".";
                Optional<Input> had = screen.allInputs().stream()
                        .filter(i -> i.picture() != null && i.picture().id().equals(cut.id())).findFirst();
                boolean says = look.when() != null;
                if (!marker && !back && had.isPresent() && (!sometimes || had.get().comesAndGoes() && !says)) {
                    return " " + at.name() + " has it as an input already.";
                }
                // A way back is no input to try, and an input seen to come and go is marked so in place.
                Optional<String> failed = ScreenBook.update(services, at.constant(), s -> marker
                        ? at.popup() ? s.markers(now.shown()).absent(now.absent()).popup()
                        : s.markers(now.shown()).absent(now.absent())
                        : back ? replaced(s, had.orElse(null), null).back(Input.click(cut))
                        : had.isPresent() ? replaced(s, had.get(), brought(had.get().sometimes(), look))
                        : s.inputs(added(s.inputs(), sometimes ? brought(Input.click(cut).sometimes(), look)
                        : Input.click(cut))));
                if (failed.isPresent()) return " It could not be noted in " + at.name() + ": " + failed.get();
                String popup = at.popup() ? "; a popup now, apart from the map's tree, closed on the way" : "";
                return " Noted in " + at.name() + (mark == Mark.SET ? " as its marker" + (was == null || was.equals(
                        cut.id()) ? "" : ", in place of " + was + " (marker add adds to the set instead)") + popup + "."
                        : mark == Mark.ADD ? " as one of its markers, told by " + now.said() + popup + "."
                        : mark == Mark.ABSENT ? " as a picture it never shows, told by " + now.said() + "."
                        : back ? " as its way back." : sometimes ? " as an input that comes and goes: "
                        + "try_input it while it shows." : " as untried: try_input it before leaving " + at.name() + ".");
            });
        } catch (IllegalStateException busy) {
            return " It could not be noted in " + at.name() + ": " + busy.getMessage();
        }
    }

    /** {@code input} with the method saying when it is there, as {@code look} names it; none keeps what it had. */
    private static Input brought(Input input, Look look) {
        return look.when() == null ? input : input.when(ScreenTypes.condition(look.when()));
    }

    /** {@code picture}, just cut, noted as another look of the input {@code look} names on the screen {@code at}. */
    private static String noteVariant(StudioServices services, Placing at, String picture, Look look) {
        if (at.starts()) return " " + at.name() + " is not in the map yet, so it has no input to give a look to.";
        try {
            return FxCall.call(() -> {
                ImageTemplate cut = ScreenBook.picture(services, picture);
                GameScreen screen = ScreenBook.read(services).screens().get(at.constant());
                if (screen == null) return " " + at.name() + " is no longer in the map, so it was not noted.";
                Optional<Input> of = screen.allInputs().stream().filter(i -> i.looks().stream()
                        .anyMatch(v -> v.picture().id().equalsIgnoreCase(look.variantOf().strip()))).findFirst();
                if (of.isEmpty()) {
                    return " " + at.name() + " has no input " + look.variantOf() + ": cut that button with screen "
                            + at.name() + " first, then this as its look.";
                }
                Input now = of.get().withVariant(Variant.of(cut, look.state()));
                Optional<String> failed = ScreenBook.update(services, at.constant(), s -> replaced(s, of.get(), now));
                if (failed.isPresent()) return " It could not be noted in " + at.name() + ": " + failed.get();
                String label = look.state().id();
                return " Noted in " + at.name() + " as the " + label + " look of " + of.get().label() + ": one input, "
                        + "found by any of its looks" + (look.state() == State.DISABLED ? ", never pressed while it "
                        + "looks so." : ". When this look leads elsewhere, try_input it by this picture: the way it "
                        + "opens names the look.");
            });
        } catch (IllegalStateException busy) {
            return " It could not be noted in " + at.name() + ": " + busy.getMessage();
        }
    }

    /**
     * {@code picture}, just cut, noted on the look {@code look} names of the screen {@code at}: that look's marker, or
     * an input only it has (one that comes and goes as {@code sometimes} says). The look is added when new. With a
     * marker, the method {@code look} names says when the screen looks so: {@code Firestone::halloween}.
     */
    private static String noteScreenLook(StudioServices services, Placing at, String picture, Mark mark,
                                         boolean sometimes, Look look) {
        boolean marker = mark != null;
        if (at.starts()) return " " + at.name() + " is not in the map yet: cut its own marker first, then its looks.";
        String name = look.screenLook().strip();
        try {
            return FxCall.call(() -> {
                ImageTemplate cut = ScreenBook.picture(services, picture);
                GameScreen screen = ScreenBook.read(services).screens().get(at.constant());
                if (screen == null) return " " + at.name() + " is no longer in the map, so it was not noted.";
                ScreenVariant was = screen.variants().stream().filter(v -> v.name().equalsIgnoreCase(name)).findFirst()
                        .orElse(ScreenVariant.named(name));
                Input input = sometimes ? brought(Input.click(cut).sometimes(), look) : Input.click(cut);
                Markers set = marker ? marked(Markers.of(was), mark, cut) : Markers.of(was);
                ScreenVariant told = was.markers(set.shown()).absent(set.absent());
                ScreenVariant marked = look.when() == null ? told : told.when(ScreenTypes.condition(look.when()));
                ScreenVariant now = marker ? marked : was.inputs(added(was.inputs(), input));
                Optional<String> failed = ScreenBook.update(services, at.constant(), s -> s.withVariant(now));
                if (failed.isPresent()) return " It could not be noted in " + at.name() + ": " + failed.get();
                return " Noted in " + at.name() + "'s look " + was.name() + (marker ? " as what tells it apart, "
                        + set.said() + (look.when() == null ? "." : ", shown while " + look.when() + " answers true.")
                        : " as an input only it has, untried: try_input it while " + was.name() + " shows.");
            });
        } catch (IllegalStateException busy) {
            return " It could not be noted in " + at.name() + ": " + busy.getMessage();
        }
    }

    /**
     * not_a_screen: the screen removed, and each way into it ({@code from} and {@code alsoFrom}) recorded on the screen
     * it leaves as {@link Did#IN_PLACE}: one left as a way would bring the screen back as soon as it is tried again.
     */
    static AgentReply notAScreen(Mistake mistake, AgentContext context) {
        StudioServices services = context.services();
        if (services == null || services.projectDir() == null) return AgentReply.refused(Frames.noProject());
        try {
            ScreenGraph graph = FxCall.call(() -> ScreenBook.read(services));
            Optional<String> found = mistake.screen() == null ? Optional.empty()
                    : ScreenBook.find(graph, mistake.screen().strip());
            if (found.isEmpty()) return AgentReply.refused("No screen " + mistake.screen() + " in the bot's Screens.");
            String constant = found.get();
            GameScreen screen = graph.screens().get(constant);
            record Entrance(String from, Input via) {}
            List<Way> ways = new ArrayList<>();
            if (screen.from() != null) ways.add(screen.from());
            ways.addAll(screen.alsoFrom());
            List<Entrance> entrances = new ArrayList<>();
            for (Way way : ways) {
                Optional<String> from = graph.constantOf(way.from()).filter(c -> !c.equals(constant));
                if (way.via() != null && from.isPresent()) entrances.add(new Entrance(from.get(), way.via()));
            }
            if (screen.isPopup() || entrances.isEmpty()) {
                return AgentReply.refused(graph.shown(constant) + " was not opened by an input: nothing to take back.");
            }
            List<String> after = graph.screens().keySet().stream()
                    .filter(c -> !c.equals(constant) && graph.dependsOn(c).contains(constant)).toList();
            if (!after.isEmpty()) {
                return AgentReply.refused(String.join(", ", after.stream().map(graph::shown).toList())
                        + " is reached from " + graph.shown(constant) + ": it stays.");
            }
            Optional<String> failed = FxCall.call(() -> {
                Optional<String> refused = ScreenBook.remove(services, constant);
                for (Entrance in : entrances) {
                    if (refused.isPresent()) break;
                    refused = ScreenBook.update(services, in.from(), s -> s.allInputs().stream()
                            .filter(in.via()::sameAs).findFirst()
                            .map(held -> replaced(s, held, held.did(Did.IN_PLACE)))
                            .orElseGet(() -> s.inputs(added(s.inputs(), ScreenGraph.bare(in.via()).did(Did.IN_PLACE)))));
                }
                return refused;
            });
            if (failed.isPresent()) return unwritten(failed.get());
            ScreenShots.remove(services.resourcesDir(), constant);
            return AgentReply.text(graph.shown(constant) + " is off the map; " + String.join(", ", entrances.stream()
                    .map(in -> in.via().label() + " is noted in " + graph.shown(in.from())).toList())
                    + " as changing it in place.");
        } catch (IllegalStateException busy) {
            return AgentReply.refused("Studio did not hand over the bot's Screens: " + busy.getMessage());
        }
    }

    /**
     * Notes {@code back}, {@code inputs} and {@code sometimes}, pictures the bot has, on the screen {@code at} names, in
     * one write of it: a write per picture took 11 s for 25 (2026-10-15). Each one noted is said in {@code noted}.
     * Empty, or why the screen could not be written.
     */
    static Optional<String> noteAll(StudioServices services, Placing at, String back, List<String> inputs,
                                    List<String> sometimes, List<String> noted) {
        if (back == null && inputs.isEmpty() && sometimes.isEmpty()) return Optional.empty();
        return FxCall.call(() -> ScreenBook.update(services, at.constant(), s -> {
            GameScreen now = s;
            if (back != null) {
                ImageTemplate cut = ScreenBook.picture(services, back);
                now = replaced(now, input(now, cut).orElse(null), null).back(Input.click(cut));
                noted.add(back + ": its way back");
            }
            for (String picture : inputs) {
                ImageTemplate cut = ScreenBook.picture(services, picture);
                if (input(now, cut).isPresent()) noted.add(picture + ": an input already");
                else {
                    now = now.inputs(added(now.inputs(), Input.click(cut)));
                    noted.add(picture + ": untried");
                }
            }
            for (String picture : sometimes) {
                ImageTemplate cut = ScreenBook.picture(services, picture);
                Optional<Input> had = input(now, cut);
                now = had.isPresent() ? replaced(now, had.get(), had.get().sometimes())
                        : now.inputs(added(now.inputs(), Input.click(cut).sometimes()));
                noted.add(picture + ": comes and goes");
            }
            return now;
        }));
    }

    /** What {@link #noteAll} noted on {@code name}, said on a line of its own; empty when nothing was. */
    static String noted(String name, List<String> noted, boolean inputs) {
        return noted.isEmpty() ? "" : "\nNoted in " + name + ": " + String.join("; ", noted) + "."
                + (inputs ? " try_input each untried one before leaving " + name + "; one that comes and goes while it "
                + "shows." : "");
    }

    /** place_pictures: the marker first, which may start the screen, then the way back, then the inputs. */
    static AgentReply placePictures(Places places, AgentContext context) {
        StudioServices services = context.services();
        if (services == null || services.projectDir() == null) return AgentReply.refused(Frames.noProject());
        if (places.screen() == null || places.screen().isBlank()) return AgentReply.refused("Give the screen.");
        String screen = places.screen().strip();
        boolean popup = Boolean.TRUE.equals(places.popup());
        if (popup && places.marker() == null) return AgentReply.refused("popup goes with marker.");
        List<String> inputs = places.pictures() == null ? List.of() : places.pictures();
        List<String> sometimes = places.sometimes() == null ? List.of() : places.sometimes();
        List<String> all = new ArrayList<>(inputs);
        all.addAll(sometimes);
        if (places.marker() != null) all.add(places.marker());
        if (places.back() != null) all.add(places.back());
        if (all.isEmpty()) return AgentReply.refused("Give a marker, a way back, pictures or sometimes.");
        try {
            List<String> missing = FxCall.call(() -> all.stream().filter(p -> !ScreenBook.exists(services, p)).toList());
            if (!missing.isEmpty()) {
                return AgentReply.refused("The bot has no picture " + String.join(", ", missing) + "; crop_pictures "
                        + "cuts a new one. Nothing was noted.");
            }
            // Each checked on the game first: one found away from its place goes on as a copy cut where it sits.
            ScreenFit.Fitted fitted = ScreenFit.fit(context, screen, all,
                    places.marker() == null ? Set.of() : Set.of(places.marker()), Set.copyOf(sometimes));
            if (!fitted.missing().isEmpty()) {
                return AgentReply.refused(String.join(", ", fitted.missing()) + (fitted.missing().size() == 1
                        ? " is" : " are") + " not on " + screen + " now: go there, or cut what it shows with "
                        + "crop_pictures. Nothing was noted.");
            }
            Optional<String> uncut = fitted.save(context);
            if (uncut.isPresent()) return AgentReply.refused("A copy was not cut: " + uncut.get() + " Nothing was noted.");
            Map<String, String> as = new HashMap<>();
            for (int i = 0; i < all.size(); i++) as.put(all.get(i), fitted.pictures().get(i));
            String marker = places.marker() == null ? null : as.get(places.marker());
            String back = places.back() == null ? null : as.get(places.back());
            List<String> plain = inputs.stream().map(as::get).toList();
            List<String> comes = sometimes.stream().map(as::get).toList();
            StringBuilder said = new StringBuilder();
            if (marker != null) {
                Placing at = placing(services, screen, true, popup);
                if (at.refusal() != null) return AgentReply.refused(at.refusal());
                Optional<Markers> before = markers(services, screen);
                said.append(marker).append(':').append(noteCut(services, at, marker, Mark.SET, false, false));
                String clash = before.map(b -> unique(services, screen, b)).orElse("");
                said.append(clash);
                if (clash.isEmpty()) {
                    said.append(markerShot(context, screen, fitted.frame() != null ? fitted.frame()
                            : Frames.look(context).orElse(null)));
                }
            }
            Placing at = placing(services, screen, false, false);
            if (at.refusal() != null) return said.isEmpty() ? AgentReply.refused(at.refusal())
                    : AgentReply.text(said + " Nothing else was noted: " + at.refusal());
            List<String> noted = new ArrayList<>();
            Optional<String> failed = noteAll(services, at, back, plain, comes, noted);
            if (failed.isPresent()) {
                return said.isEmpty() ? unwritten(failed.get())
                        : AgentReply.text(said + " Nothing else was noted: " + failed.get());
            }
            return AgentReply.text((said + noted(at.name(), noted, !plain.isEmpty() || !comes.isEmpty())
                    + fitted.said()).strip());
        } catch (IllegalStateException busy) {
            return AgentReply.refused("Studio did not hand over the bot's Screens: " + busy.getMessage());
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
        ScreenWalk.Result walked = new ScreenWalk(graph, reach.get(), STEP_MS, null, close(context)).go(target.get(),
                lastSeen);
        seen(graph, walked.at());
        if (walked.arrived()) retakeShot(context, reach.get(), graph, walked.at());
        String path =walked.walked().isEmpty() ? "" : " Walked: " + String.join(" › ",
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
        ScreenWalk walk = new ScreenWalk(graph, reach.get(), STEP_MS, null);
        Optional<String> here = walk.whereOn(frame, lastSeen);
        here.ifPresent(c -> seen(graph, c));
        Optional<String> over = walk.popupOn(frame);
        // A screen mapped before shots were kept gets one the first time it is seen with nothing over it.
        if (over.isEmpty()) here.ifPresent(c -> ScreenShots.saveIfMissing(context.services().resourcesDir(), c, frame));
        String popup = over.map(c -> "The popup " + graph.shown(c) + " (Screens." + c + ") shows over "
                + "it: go_to_screen closes it on the way.").orElse("");
        return Optional.of(here.map(c -> "On " + graph.shown(c) + " (Screens." + c + "): its marker shows."
                        + looks(graph, c, walk, frame) + (popup.isEmpty() ? "" : " " + popup))
                .orElse(popup.isEmpty() ? "No screen of the bot's Screens shows: none of their markers is found."
                        : popup.replace(" over it", "")));
    }

    /**
     * Which of {@code constant}'s looks {@code frame} shows, as a sentence: the look whose marker matches, or, when the
     * screen has looks and none matches, that this may be a new one to cut. Blank for a screen with no looks.
     */
    private static String looks(ScreenGraph graph, String constant, ScreenWalk walk, BufferedImage frame) {
        GameScreen screen = graph.screens().get(constant);
        if (screen.variants().isEmpty()) return "";
        return walk.lookOn(constant, frame).map(v -> " It looks as " + v.name() + ".")
                .orElse(" None of its looks' markers shows (" + String.join(", ", screen.variants().stream()
                        .map(ScreenVariant::name).toList()) + "): a new look is cut with screenLook and marker set.");
    }

    /**
     * {@code constant}'s shot taken again, over the one it had: a walk that arrived closed what was over it, while a shot
     * kept only when missing may show a panel opened over the screen before that panel was mapped (Market over Tavern,
     * 2026-10-15).
     */
    private static void retakeShot(AgentContext context, ToolReach reach, ScreenGraph graph, String constant) {
        Path resources = context.services().resourcesDir();
        if (resources == null || RETAKEN.contains(resources.resolve(constant))) return;
        if (graph.screen(constant).filter(GameScreen::hasMarker).isEmpty()) return;
        // Only a frame its markers show on: the walk's last frame may still be the way there.
        Optional<BufferedImage> frame = reach.look().filter(f -> ScreenFit.shows(reach, graph, constant, f));
        // Once a session: each arrival wrote a 1–3 MB shot of an animated game, a new file the next Project History
        // snapshot hashed, compressed and kept, 2–3 s of every write and most of a 63 MB history (2026-10-16).
        if (frame.isPresent() && RETAKEN.add(resources.resolve(constant))) ScreenShots.save(resources, constant, frame.get());
    }

    /**
     * After {@code typed}'s markers are written, the whole set: {@code frame} becomes its shot when it shows them, said
     * as a sentence; blank otherwise (no frame, the screen is not in the map). The shot a screen got when it opened was
     * the frame right after the click, a draft; and a recut marker means the screen changed.
     */
    static String markerShot(AgentContext context, String typed, BufferedImage frame) {
        Optional<ToolReach> reach = ToolReach.of(context);
        Path resources = context.services().resourcesDir();
        if (frame == null || reach.isEmpty() || resources == null) return "";
        ScreenGraph graph;
        try {
            graph = FxCall.call(() -> ScreenBook.read(context.services()));
        } catch (IllegalStateException busy) {
            return "";
        }
        Optional<String> constant = ScreenBook.find(graph, typed);
        if (constant.isEmpty() || !graph.screens().get(constant.get()).hasMarker()
                || !ScreenFit.shows(reach.get(), graph, constant.get(), frame)) {
            return "";
        }
        ScreenShots.save(resources, constant.get(), frame);
        RETAKEN.add(resources.resolve(constant.get()));
        return " " + graph.shown(constant.get()) + "'s shot is this frame now.";
    }

    /** The shots a walk has taken again since the plugin loaded, by file. */
    private static final Set<Path> RETAKEN = ConcurrentHashMap.newKeySet();

    private static void seen(ScreenGraph graph, String constant) {
        if (constant != null && graph.screen(constant).filter(s -> !s.isPopup()).isPresent()) lastSeen = constant;
    }

    /**
     * {@code entered}, or a later look when the screen's {@code markers} come back within {@link #FLASH_MS}: a change
     * over the whole screen that hid them a moment (a rarity raised in Artifacts flashed, 2026-10-15) changed the
     * screen in place and opened none.
     */
    private static ActTools.Entered flash(AgentContext context, ActTools.Entered entered, Markers markers) {
        if (entered.after() == null || entered.share() < SCREEN_SHARE || !markers.any()
                || ActTools.shows(context, markers, entered.after())) {
            return entered;
        }
        Optional<ToolReach> reach = ToolReach.of(context);
        if (reach.isEmpty()) return entered;
        return ToolTiming.timed(ToolTiming.Phase.SETTLE, () -> {
            long deadline = reach.get().nowMs() + FLASH_MS;
            while (reach.get().nowMs() < deadline && !Thread.currentThread().isInterrupted()) {
                reach.get().pause(FLASH_POLL_MS);
                Optional<BufferedImage> now = reach.get().look();
                if (now.isPresent() && ActTools.shows(context, markers, now.get())) {
                    return ActTools.again(context, entered, now.get());
                }
            }
            return entered;
        });
    }

    /**
     * {@code first} read on while it changed less than a screen and the screen's {@code markers} still show: a
     * screen slow to draw (Garage, a second late) would be noted as a change in place. A change that grew with the
     * markers still in view is drift, not a screen.
     */
    private static ActTools.Entered slowScreen(AgentContext context, ActTools.Entered first, Markers markers) {
        if (first.after() == null || first.share() >= SCREEN_SHARE || !markers.any()
                || !ActTools.shows(context, markers, first.after())) {
            return first;
        }
        ActTools.Entered later = ActTools.later(context, first, SLOW_SCREEN_MS, SCREEN_SHARE);
        return later.after() == null || later != first && !ActTools.shows(context, markers, later.after())
                ? later : first;
    }

    /**
     * The input of {@code on} this try is: the key's, the picture's click, or for a scroll the one of those notches,
     * else the picture's untried click, which the scroll then stands for. A copy {@code match} (from 1; 0 for the best
     * match) is that copy's input, else the picture's untried input cut once for all its copies, which the copy then
     * stands for.
     */
    private static Optional<Input> held(GameScreen on, String picture, Key key, int notches, int match,
                                        ScreenGraph graph, String screen) {
        if (key != null) {
            return on.allInputs().stream().filter(i -> i.act() == Input.Act.KEY && i.key() == key).findFirst();
        }
        Optional<Input> copy = held(on.allInputs().stream().filter(i -> i.nth() == match).toList(), picture,
                notches, graph, screen);
        return match == 0 || copy.isPresent() ? copy : held(on.allInputs().stream()
                .filter(i -> i.nth() == 0 && !i.every() && !graph.tried(screen, i)).toList(), picture, notches,
                graph, screen);
    }

    /** {@link #held(GameScreen, String, Key, int, int, ScreenGraph, String)} of a picture among {@code all}. */
    private static Optional<Input> held(List<Input> all, String picture, int notches, ScreenGraph graph,
                                        String screen) {
        List<Input> same = all.stream()
                .filter(i -> i.picture() != null && i.picture().id().equalsIgnoreCase(picture)).toList();
        // A button's other look is that button: clicking its badge copy tries the same input.
        if (notches == 0) {
            return same.stream().filter(i -> i.act() == Input.Act.CLICK).findFirst()
                    .or(() -> all.stream().filter(i -> i.act() == Input.Act.CLICK)
                            .filter(i -> i.variants().stream().anyMatch(v -> v.picture().id().equalsIgnoreCase(picture)))
                            .findFirst());
        }
        return same.stream().filter(i -> i.act() == Input.Act.SCROLL && i.notches() == notches).findFirst()
                .or(() -> same.stream().filter(i -> i.act() == Input.Act.CLICK && !graph.tried(screen, i)).findFirst());
    }

    /**
     * The input to record: {@code held} as it is, a click it stood for made a scroll (what was said of the click kept:
     * its kind, looks, coming and going), or a new one.
     */
    private static Input made(StudioServices services, Optional<Input> held, String picture, Key key, int notches) {
        if (held.isPresent() && (notches == 0 || held.get().act() == Input.Act.SCROLL)) return held.get();
        if (key != null) return Input.key(key);
        ImageTemplate cut = FxCall.call(() -> ScreenBook.picture(services, picture));
        if (notches == 0) return Input.click(cut);
        return held.map(click -> new Input(Input.Act.SCROLL, cut, null, notches, null, click.kind(), null,
                click.comesAndGoes(), null, click.variants(), click.when(), click.nth(), click.every()))
                .orElse(Input.scroll(cut, notches));
    }

    private static Input kinded(Input input, Optional<Kind> kind) {
        return kind.map(input::kind).orElse(input);
    }

    /** {@code screen} with {@code once}, an input of a picture shown {@code count} times, as one input per copy. */
    private static GameScreen copies(GameScreen screen, Input once, int count) {
        GameScreen now = replaced(screen, once, once.nth(1));
        for (int n = 2; n <= count; n++) now = replaced(now, null, once.nth(n));
        return now;
    }

    /** {@code screen} with {@code now} where {@code old} was, added when {@code old} is not on it, gone when null. */
    static GameScreen replaced(GameScreen screen, Input old, Input now) {
        // An input of one of the screen's looks is changed in that look.
        for (ScreenVariant look : screen.variants()) {
            int in = old == null ? -1 : look.inputs().indexOf(old);
            if (in < 0) continue;
            List<Input> theirs = new ArrayList<>(look.inputs());
            if (now == null) theirs.remove(in);
            else theirs.set(in, now);
            return screen.withVariant(look.inputs(theirs));
        }
        List<Input> inputs = new ArrayList<>(screen.inputs());
        int at = old == null ? -1 : inputs.indexOf(old);
        if (at >= 0 && now == null) {
            inputs.remove(at);
            // An input taken off is off its list too.
            if (old.picture() != null) return screen.inputs(inputs)
                    .lists(ScreenGraph.withoutMember(screen.lists(), old.picture().id()));
        } else if (at >= 0) inputs.set(at, now);
        else if (now != null && inputs.stream().noneMatch(now::equals)) inputs.add(now);
        return screen.inputs(inputs);
    }

    /** {@code screen}'s input clicking or scrolling {@code picture}, its looks' too, if it has one. */
    private static Optional<Input> input(GameScreen screen, ImageTemplate picture) {
        return screen.allInputs().stream().filter(i -> i.picture() != null && i.picture().id().equals(picture.id()))
                .findFirst();
    }

    private static List<Input> added(List<Input> inputs, Input input) {
        List<Input> all = new ArrayList<>(inputs);
        all.add(input);
        return all;
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
        return "Name screens in plain words on one line, as the game shows them.";
    }

    static String owes(String screen) {
        return screen + " still owes its marker: screenshot it, then crop_pictures with screen " + screen + " and "
                + "marker set a part only that screen shows; or, when none can be cut, write_notes with screen "
                + screen + " and owes \"marker — <why>\". Nothing was done.";
    }

    /** Whether {@code name} can name a screen: one line of words. */
    static boolean isName(String name) {
        return name != null && !name.isEmpty() && !NOT_A_NAME.matcher(name).find();
    }

    /**
     * Whether try_input wants {@code opens} to try {@code input} of {@code screen}: a hotkey, whose name names nothing,
     * or a click whose picture names no new screen ({@code close_x}, or a screen the map has).
     */
    static boolean asksWhatItOpens(ScreenGraph graph, String screen, Input input) {
        if (input.act() == Input.Act.SCROLL || input.never() != null || input.lock() != null) return false;
        if (input.picture() == null) return true;
        String child = nameFrom(input.picture().id(), graph.shown(screen));
        return !isName(child) || ScreenBook.find(graph, child).isPresent();
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
