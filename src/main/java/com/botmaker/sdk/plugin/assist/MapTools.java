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
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Variant;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenWalk;
import com.botmaker.sdk.internal.vision.FrameChange;
import com.botmaker.sdk.plugin.types.ScreenTypes;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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
    /** How long a change over the whole screen that hid its marker is read on for the marker to come back. */
    static final int FLASH_MS = 1_500;
    /** How often the screen is read meanwhile. */
    private static final int FLASH_POLL_MS = 250;
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
               @Describe(value = STAY, optional = true) Boolean stay,
               @Describe(value = "true to act on the user's own desktop, taking their mouse; refused there "
                       + "otherwise", optional = true) Boolean desktop) {
    }

    record Tries(@Describe("the screen the inputs are on, as the map names it: Home, or its constant HOME") String screen,
                 @Describe("pictures cut on that screen, clicked in this order, each at its best match") List<String> pictures,
                 @Describe(value = "the longest each is given to answer, in milliseconds; 5000 when left out",
                         optional = true) Integer settleMs,
                 @Describe(value = STAY, optional = true) Boolean stay,
                 @Describe(value = "true to act on the user's own desktop, taking their mouse; refused there "
                         + "otherwise", optional = true) Boolean desktop) {
    }

    private static final String STAY = "true stays on the screen when this leaves it finished; left out, the game is "
            + "walked back up to the nearest screen with inputs left to try";

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
                            + "its way in is recorded on its parent as changing it in place. Refused once anything "
                            + "is reached from it")
                    .takes(Mistake.class).handledBy(MapTools::notAScreen),
            AssistantTool.named("place_pictures")
                    .describedAs("crop_picture's map half for pictures the bot has already: notes them on a screen "
                            + "of the bot's Screens as its marker, its way back, or inputs untried until try_input "
                            + "tries them, without cutting them again. A marker starts the screen as crop_picture's "
                            + "does")
                    .takes(Places.class).handledBy(MapTools::placePictures),
            AssistantTool.named("try_input")
                    .describedAs("Discovery's one way to act on a screen: clicks an input cut on it (scrolls over "
                            + "it, or taps a hotkey) and records in the bot's Screens what that did: opened a screen "
                            + "(a new one, owing its marker, with its screenshot), led to one mapped already, changed "
                            + "this one in place, or nothing. Every picture crop_picture cut with a screen is untried "
                            + "until this tries it. A screen opened is finished before the next input of the screen "
                            + "it was opened from: refused while a screen owes its marker, while a screen opened from "
                            + "this one has an input left to try, and while another branch begun is not finished. "
                            + "Once nothing on the screen is left to try, it goes back up by itself (stay keeps it)")
                    .takes(Try.class).handledBy(MapTools::tryInput),
            AssistantTool.named("try_inputs")
                    .describedAs("try_input for several pictures of one screen in one call, in order: each is "
                            + "clicked and recorded as try_input does, and the call stops at the first that leaves "
                            + "the screen (opens one, or leads to one mapped already) or is refused. For the plain "
                            + "buttons of a screen; scrolls, hotkeys, never and locked go through try_input")
                    .takes(Tries.class).handledBy(MapTools::tryInputs),
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

    /**
     * try_inputs: each picture as try_input tries it, staying on the screen, until one is refused or leaves it (it is
     * recorded as anything but a change in place or no effect); then the walk up, once, when the screen is finished.
     */
    static AgentReply tryInputs(Tries tries, AgentContext context) {
        List<String> pictures = tries.pictures() == null ? List.of()
                : tries.pictures().stream().filter(p -> p != null && !p.isBlank()).map(String::strip).toList();
        if (pictures.isEmpty()) return AgentReply.refused("Give the pictures to try, in order. Nothing was done.");
        AgentReply all = null;
        for (int i = 0; i < pictures.size(); i++) {
            AgentReply one = tryInput(new Try(tries.screen(), pictures.get(i), null, null, null, null, null, null,
                    tries.settleMs(), true, tries.desktop()), context);
            if (one.isRefused()) {
                if (all == null) return one;
                return all.and(AgentReply.text("Stopped before " + pictures.get(i) + ": " + one.parts().getFirst()
                        .text() + " Not tried: " + String.join(", ", pictures.subList(i, pictures.size())) + "."));
            }
            all = all == null ? one : all.and(one);
            if (!stayed(context, tries.screen(), pictures.get(i))) {
                return i + 1 == pictures.size() ? all : all.and(AgentReply.text("Stopped: " + pictures.get(i)
                        + " left the screen. Not tried: " + String.join(", ", pictures.subList(i + 1, pictures.size()))
                        + "."));
            }
        }
        Optional<String> screen = readScreen(context, tries.screen());
        return screen.isEmpty() ? all : up(context, all, screen.get(), new Try(tries.screen(), null, null, null, null,
                null, null, null, null, tries.stay(), tries.desktop()));
    }

    /** Whether {@code picture}, just tried on {@code typed}, is recorded as keeping the game there. */
    private static boolean stayed(AgentContext context, String typed, String picture) {
        try {
            ScreenGraph graph = FxCall.call(() -> ScreenBook.read(context.services()));
            return ScreenBook.find(graph, typed).map(c -> graph.screens().get(c).allInputs().stream()
                    .filter(i -> i.act() == Input.Act.CLICK && i.picture() != null
                            && i.picture().id().equalsIgnoreCase(picture))
                    .anyMatch(i -> i.did() == Did.IN_PLACE || i.did() == Did.NONE)).orElse(false);
        } catch (IllegalStateException busy) {
            return false;
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
        Optional<String> owing = owingMarker(graph);
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
            // Left on a screen below it, finished with nothing to try (a rules panel): walked back to it first.
            if (away.isPresent() && !walkedTo(context, graph, screen, input.desktop())) return away.get();
        }
        Input base = kinded(made(services, held, what, key.orElse(null), notches), kind);
        Input old = held.orElse(null);
        // A button with looks leads where the look clicked says: the way it opens names that look.
        State look = base.variants().isEmpty() || what == null ? null : base.looks().stream()
                .filter(v -> v.picture().id().equalsIgnoreCase(what)).map(Variant::state).findFirst().orElse(null);
        boolean ask = !graph.untried(screen).isEmpty();
        if (never || locked) {
            Input settled = never ? base.never(input.never().strip())
                    : base.locked(FxCall.call(() -> ScreenBook.picture(services, input.locked().strip())));
            return up(context, noted(services, screen, old, settled, never ? "never, " + input.never().strip()
                    : "locked by " + input.locked().strip(), ask), screen, input);
        }
        ActTools.Entered first = ActTools.enter(context, screen, byPicture ? what : null, notches, key.orElse(null),
                input.desktop(), settle, FrameChange.ANSWER_SHARE);
        ActTools.Entered slow = notches == 0 ? slowScreen(context, first, on.marker()) : first;
        ActTools.Entered entered = notches == 0 ? flash(context, slow, on.marker()) : slow;
        boolean flashed = entered != slow;    // its marker hidden a moment, then back: this screen, redrawn
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
        if (!flashed && entered.share() >= SCREEN_SHARE) {
            // Back, close, or another way to a screen mapped already: its marker came into view. A marker on screen
            // before the click proves nothing: a panel opens over it.
            // The screen opens names first, when the map has it and its marker shows; else the first whose marker
            // came into view.
            Optional<String> named = opens == null ? Optional.empty() : ScreenBook.find(graph, opens)
                    .filter(c -> !c.equals(screen));
            if (named.isPresent()) {
                GameScreen to = graph.screens().get(named.get());
                if (to.marker() != null && !ActTools.shows(context, to.marker().id(), entered.after())) {
                    // Taken at its word, a fight's start panel was recorded as the fight (2026-10-15).
                    return entered.reply().and(AgentReply.text("Nothing was written: " + graph.shown(named.get())
                            + "'s marker " + to.marker().id() + " does not show, so this is not "
                            + graph.shown(named.get()) + ". Look at it (describe); give opens the name it shows, "
                            + "and try " + what + " again from " + name + "."));
                }
            }
            Optional<String> known = named
                    .or(() -> graph.screens().keySet().stream().filter(c -> !c.equals(screen))
                            .filter(c -> graph.screens().get(c).marker() != null)
                            .filter(c -> ActTools.shows(context, graph.screens().get(c).marker().id(), entered.after())
                                    && !ActTools.shows(context, graph.screens().get(c).marker().id(),
                                    entered.before()))
                            .findFirst());
            if (known.isPresent()) {
                return entered.reply().and(reached(services, graph, screen, old, base, look, known.get()));
            }
            return entered.reply().and(opened(context, graph, screen, old, base, look, child, entered.after()));
        }
        boolean changed = FrameChange.box(entered.before(), entered.after(), entered.motion()).isPresent();
        AgentReply said = noted(services, screen, old, base.did(changed ? Did.IN_PLACE : Did.NONE),
                changed ? "changes this screen in place" : "no visible effect", ask);
        return up(context, entered.reply().and(changed ? said.and(AgentReply.text("Cut, with screen " + name
                + ", each new input it shows (another state of a toggle, a tab's buttons): they are untried in turn."))
                : said), screen, input);
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
     * {@code said}, and when the try left {@code screen} finished, the game walked back up to the nearest screen above
     * it with work left ({@link #upTo}), checked by that screen's marker; {@code input}'s {@code stay} keeps it.
     */
    private static AgentReply up(AgentContext context, AgentReply said, String screen, Try input) {
        if (said.isRefused() || Boolean.TRUE.equals(input.stay())) return said;
        ScreenGraph graph;
        try {
            graph = FxCall.call(() -> ScreenBook.read(context.services()));
        } catch (IllegalStateException busy) {
            return said;
        }
        Optional<String> to = upTo(graph, screen);
        Optional<ToolReach> reach = ToolReach.of(context);
        if (to.isEmpty() || reach.isEmpty()
                || ActTools.desktopRefusal(context, reach.get().hands(), input.desktop()).isPresent()) {
            return said;
        }
        ScreenWalk.Result walked = new ScreenWalk(graph, reach.get(), STEP_MS, null, close(context)).go(to.get(),
                screen);
        seen(graph, walked.at());
        if (walked.arrived()) retakeShot(context, reach.get(), walked.at());
        String back = graph.shown(to.get());
        String left = graph.shown(screen);
        return said.and(AgentReply.text(walked.arrived()
                ? "Nothing left to try on " + left + ": went back up to " + back + ". Anything on " + left + " not cut "
                + "yet (a tab's buttons, a list's far end): go_to_screen " + left + ", cut it with screen " + left
                + " and try it."
                : "Nothing left to try on " + left + ", but the way up to " + back + " failed: " + walked.why()
                + " Look where the game is (which_screen)."));
    }

    /**
     * Where the game goes once {@code screen} is finished: up its parents to the first with work left on or below it,
     * else the top one. Empty while it has work left, and for a popup or the top screen.
     */
    static Optional<String> upTo(ScreenGraph graph, String screen) {
        if (workLeft(graph, screen) || graph.screen(screen).filter(GameScreen::isPopup).isPresent()) {
            return Optional.empty();
        }
        Set<String> seen = new HashSet<>(Set.of(screen));
        Optional<String> up = graph.parent(screen);
        while (up.isPresent() && !workLeft(graph, up.get()) && graph.parent(up.get()).isPresent()) {
            if (!seen.add(up.get())) return Optional.empty();    // a map whose ways in loop has no top to go to
            up = graph.parent(up.get());
        }
        return up.filter(c -> !c.equals(screen));
    }

    /** Whether {@code constant}, or a screen below it, still has an input to try. */
    private static boolean workLeft(ScreenGraph graph, String constant) {
        return graph.unfinished(constant) || graph.unfinishedBelow(constant).isPresent();
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
        Way way = Way.of(BotConstants.screen(screen), via);
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
        Optional<String> failed = write(() -> ScreenBook.update(services, screen, s -> replaced(s, old, base))
                .or(() -> ScreenBook.add(services, constant, made)));
        if (failed.isPresent()) return unwritten(failed.get());
        ScreenShots.save(services.resourcesDir(), constant, frame);
        String from = graph.shown(screen);
        return AgentReply.text("Opened " + child + ", added to the map as Screens." + constant + ", reached from "
                        + from + " by " + base.label() + "; it owes its marker. 🖼 Manage Pictures groups the "
                        + "pictures it names under it. Before leaving it, "
                        + "from the screenshot below: crop_picture with screen " + child + " and marker true a part "
                        + "only this screen shows, then with screen " + child + " every input on it (each button, "
                        + "icon, tab, toggle, arrow and list, and its close button); what cannot be cut now, "
                        + "write_notes with screen " + child + " and owes. Then try each of them, and finish " + child
                        + " before going back to " + from + " for its next input.")
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
        boolean already = graph.leads(screen, base).stream()
                .anyMatch(lead -> lead.state() == look && lead.to().equals(known));
        Optional<String> failed = write(() -> (order.indexOf(screen) < below ? Optional.<String>empty()
                : ScreenBook.moveBefore(services, screen, known))
                .or(() -> ScreenBook.update(services, screen, s -> replaced(s, old, base)))
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
        return noteCut(services, at, picture, marker, back, sometimes, Look.NONE);
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
    static String noteCut(StudioServices services, Placing at, String picture, boolean marker, boolean back,
                          boolean sometimes, Look look) {
        if (look.variantOf() != null) return noteVariant(services, at, picture, look);
        if (look.screenLook() != null) return noteScreenLook(services, at, picture, marker, sometimes, look);
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
                Optional<String> failed = ScreenBook.update(services, at.constant(), s -> marker ? s.marker(cut)
                        : back ? replaced(s, had.orElse(null), null).back(Input.click(cut))
                        : had.isPresent() ? replaced(s, had.get(), brought(had.get().sometimes(), look))
                        : s.inputs(added(s.inputs(), sometimes ? brought(Input.click(cut).sometimes(), look)
                        : Input.click(cut))));
                if (failed.isPresent()) return " It could not be noted in " + at.name() + ": " + failed.get();
                return " Noted in " + at.name() + (marker ? " as its marker" + (was == null ? "" : ", in place of "
                        + was) + "." : back ? " as its way back." : sometimes ? " as an input that comes and goes: "
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
     * an input only it has (one that comes and goes as {@code sometimes} says). The look is added when new.
     */
    private static String noteScreenLook(StudioServices services, Placing at, String picture, boolean marker,
                                         boolean sometimes, Look look) {
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
                ScreenVariant now = marker ? was.marker(cut) : was.inputs(added(was.inputs(), input));
                Optional<String> failed = ScreenBook.update(services, at.constant(), s -> s.withVariant(now));
                if (failed.isPresent()) return " It could not be noted in " + at.name() + ": " + failed.get();
                return " Noted in " + at.name() + "'s look " + was.name() + (marker ? " as what tells it apart."
                        : " as an input only it has, untried: try_input it while " + was.name() + " shows.");
            });
        } catch (IllegalStateException busy) {
            return " It could not be noted in " + at.name() + ": " + busy.getMessage();
        }
    }

    /** not_a_screen: the screen removed, its entrance on its parent recorded as {@link Did#IN_PLACE}. */
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
            Optional<String> parent = graph.parent(constant);
            Optional<Input> entrance = parent.isPresent() && screen.from() != null
                    ? Optional.ofNullable(screen.from().via()) : Optional.empty();
            if (parent.isEmpty() && !screen.isPopup()) {
                // Its way in not written: the untried input on another screen whose picture names it.
                for (String c : graph.screens().keySet()) {
                    Optional<Input> named = graph.untried(c).stream().filter(i -> i.picture() != null
                            && nameFrom(i.picture().id(), graph.shown(c)).equals(screen.name())).findFirst();
                    if (named.isPresent() && !c.equals(constant)) {
                        parent = Optional.of(c);
                        entrance = named;
                        break;
                    }
                }
            }
            if (screen.isPopup() || parent.isEmpty() || entrance.isEmpty()) {
                return AgentReply.refused(graph.shown(constant) + " was not opened by an input: nothing to take back.");
            }
            String from = parent.get();
            List<String> after = graph.screens().keySet().stream()
                    .filter(c -> !c.equals(constant) && graph.dependsOn(c).contains(constant)).toList();
            if (!after.isEmpty()) {
                return AgentReply.refused(String.join(", ", after.stream().map(graph::shown).toList())
                        + " is reached from " + graph.shown(constant) + ": it stays.");
            }
            Input via = entrance.get();
            Optional<String> failed = FxCall.call(() -> ScreenBook.remove(services, constant)
                    .or(() -> ScreenBook.update(services, from, s -> s.allInputs().stream().filter(via::sameAs)
                            .findFirst().map(held -> replaced(s, held, held.did(Did.IN_PLACE)))
                            .orElseGet(() -> s.inputs(added(s.inputs(), via.did(Did.IN_PLACE)))))));
            if (failed.isPresent()) return unwritten(failed.get());
            ScreenShots.remove(services.resourcesDir(), constant);
            return AgentReply.text(graph.shown(constant) + " is off the map; " + via.label() + " is noted in "
                    + graph.shown(from) + " as changing it in place.");
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
                return AgentReply.refused("The bot has no picture " + String.join(", ", missing) + "; crop_picture "
                        + "cuts a new one. Nothing was noted.");
            }
            StringBuilder said = new StringBuilder();
            if (places.marker() != null) {
                Placing at = placing(services, screen, true, popup);
                if (at.refusal() != null) return AgentReply.refused(at.refusal());
                said.append(places.marker()).append(':').append(noteCut(services, at, places.marker(), true, false,
                        false));
            }
            Placing at = placing(services, screen, false, false);
            if (at.refusal() != null) return said.isEmpty() ? AgentReply.refused(at.refusal())
                    : AgentReply.text(said + " Nothing else was noted: " + at.refusal());
            List<String> noted = new ArrayList<>();
            Optional<String> failed = noteAll(services, at, places.back(), inputs, sometimes, noted);
            if (failed.isPresent()) {
                return said.isEmpty() ? unwritten(failed.get())
                        : AgentReply.text(said + " Nothing else was noted: " + failed.get());
            }
            return AgentReply.text((said + noted(at.name(), noted, !inputs.isEmpty() || !sometimes.isEmpty())).strip());
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
        if (walked.arrived()) retakeShot(context, reach.get(), walked.at());
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
                        .map(ScreenVariant::name).toList()) + "): a new look is cut with screenLook and marker true.");
    }

    /**
     * {@code constant}'s shot taken again, over the one it had: a walk that arrived closed what was over it, while a shot
     * kept only when missing may show a panel opened over the screen before that panel was mapped (Market over Tavern,
     * 2026-10-15).
     */
    private static void retakeShot(AgentContext context, ToolReach reach, String constant) {
        reach.look().ifPresent(frame -> ScreenShots.save(context.services().resourcesDir(), constant, frame));
    }

    private static void seen(ScreenGraph graph, String constant) {
        if (constant != null && graph.screen(constant).filter(s -> !s.isPopup()).isPresent()) lastSeen = constant;
    }

    /**
     * {@code entered}, or a later look when the screen's {@code marker} comes back within {@link #FLASH_MS}: a change
     * over the whole screen that hid it a moment (a rarity raised in Artifacts flashed, 2026-10-15) changed the screen
     * in place and opened none.
     */
    private static ActTools.Entered flash(AgentContext context, ActTools.Entered entered, ImageTemplate marker) {
        if (entered.after() == null || entered.share() < SCREEN_SHARE || marker == null
                || ActTools.shows(context, marker.id(), entered.after())) {
            return entered;
        }
        Optional<ToolReach> reach = ToolReach.of(context);
        if (reach.isEmpty()) return entered;
        return ToolTiming.timed(ToolTiming.Phase.SETTLE, () -> {
            long deadline = reach.get().nowMs() + FLASH_MS;
            while (reach.get().nowMs() < deadline && !Thread.currentThread().isInterrupted()) {
                reach.get().pause(FLASH_POLL_MS);
                Optional<BufferedImage> now = reach.get().look();
                if (now.isPresent() && ActTools.shows(context, marker.id(), now.get())) {
                    return ActTools.again(context, entered, now.get());
                }
            }
            return entered;
        });
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
        List<Input> all = on.allInputs();
        if (key != null) return all.stream().filter(i -> i.act() == Input.Act.KEY && i.key() == key).findFirst();
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
        if (at >= 0 && now == null) inputs.remove(at);
        else if (at >= 0) inputs.set(at, now);
        else if (now != null && inputs.stream().noneMatch(now::equals)) inputs.add(now);
        return screen.inputs(inputs);
    }

    /** {@code screen}'s input clicking or scrolling {@code picture}, if it has one. */
    private static Optional<Input> input(GameScreen screen, ImageTemplate picture) {
        return screen.inputs().stream().filter(i -> i.picture() != null && i.picture().id().equals(picture.id()))
                .findFirst();
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
     * The first screen with no marker that does not say why it owes one ({@code marker — every icon here animates}
     * among its {@link GameScreen#owes}): one with a reason holds no other screen back, and stays owed.
     */
    static Optional<String> owingMarker(ScreenGraph graph) {
        return graph.screens().keySet().stream().filter(c -> graph.screens().get(c).marker() == null)
                .filter(c -> graph.screens().get(c).owes().stream().noneMatch(MapTools::markerWithReason))
                .findFirst();
    }

    /** Whether a thing owed is the marker, with why: {@code marker — every icon here animates}. */
    static boolean markerWithReason(String owed) {
        return MARKER_REASON.matcher(owed).matches();
    }

    private static final Pattern MARKER_REASON = Pattern.compile("(?i)marker[ \\t]+[—–-][ \\t]+\\S.*");

    static String owes(String screen) {
        return screen + " still owes its marker: screenshot it, then crop_picture with screen " + screen + " and "
                + "marker true a part only that screen shows; or, when none can be cut, write_notes with screen "
                + screen + " and owes \"marker — <why>\". Nothing was done.";
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
