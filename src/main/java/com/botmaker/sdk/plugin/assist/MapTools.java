package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.internal.vision.FrameChange;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Discovery's map, kept by a tool rather than by the assistant's memory (the user's call, 2026-10-14: mapping
 * Firestone, the assistant clicked through a dozen screens, cut nothing and wrote nothing until asked; then cut a
 * screen's buttons and left without trying one). A screen is explored across, never down: every input cut on it
 * with crop_picture is untried until try_input clicks it, and try_input records what it did (opened a screen, changed
 * this one in place, did nothing). A screen whose inputs all open screens is a menu; any other is an activity. The
 * tool refuses a screen while one it was reached from has an untried input, and any screen while one owes its
 * marker. read_guide keeps Discovery open while anything is owed or untried ({@link Protocol}).
 */
final class MapTools {

    /** The share of the still screen an input must change to have opened a screen: Firestone's side panels. */
    static final double SCREEN_SHARE = 0.15;
    static final String HOME = "home";
    /** How long an input is given to answer when the call says nothing: as long as a click's. */
    static final int SETTLE_MS = 5000;
    private static final int DEEPEST = 32;

    /** What a screen's name cannot hold: the notes' own syntax, or markdown its heading would read otherwise. */
    private static final Pattern NOT_A_NAME = Pattern.compile("[:*_`\\[\\]\\\\<>#›\\p{Cntrl}]");

    record Input(@Describe("the screen the input is on, as its section names it: Home, Upgrades") String screen,
                 @Describe(value = "a picture cut on that screen, clicked at its best match", optional = true)
                 String picture,
                 @Describe(value = "a hotkey instead of a picture, as printed on it: Q", optional = true) String key,
                 @Describe(value = "the name the screen it opens shows, when it opens one; left out, the picture's "
                         + "name names it", optional = true) String opens,
                 @Describe(value = "why it is never clicked (it spends premium currency or real money): recorded "
                         + "as tried, not clicked", optional = true) String never,
                 @Describe(value = "the longest the input is given to answer, in milliseconds; 5000 when left out",
                         optional = true) Integer settleMs,
                 @Describe(value = "true to act on the user's own desktop, taking their mouse; refused there "
                         + "otherwise", optional = true) Boolean desktop) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("try_input")
                    .describedAs("Discovery's one way to act on a screen: clicks an input cut on it (or taps a "
                            + "hotkey) and records in its section what that did: opened a screen (its Map line and "
                            + "its own section, owing a marker, with its screenshot), changed this one in place, or "
                            + "nothing. Every picture crop_picture cut with a screen is untried until this tries "
                            + "it. Each screen across, then down: refused while a screen owes its marker, while a "
                            + "screen this one was reached from has an input left to try, and while another branch "
                            + "begun is not finished")
                    .takes(Input.class).handledBy(MapTools::tryInput));

    private MapTools() {}

    static AgentReply tryInput(Input input, AgentContext context) {
        Path project = context.services().projectDir();
        if (project == null) return AgentReply.refused(Frames.noProject());
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
        if (!isName(asked) || opens != null && (!isName(opens) || opens.equalsIgnoreCase(GameNotes.MAP))) {
            return AgentReply.refused("Name screens in plain words on one line, as the notes call them: no colon, "
                    + "no markdown (* _ ` [ ] # \\ < > ›).");
        }
        boolean never = input.never() != null;
        if (never && input.never().isBlank()) {
            return AgentReply.refused("Say why it is never clicked (it spends gems). Nothing was done.");
        }
        if (byKey && opens == null && !never) {
            return AgentReply.refused("A hotkey names no screen: give opens, the name the screen it opens shows. "
                    + "Nothing was done.");
        }
        int settle = input.settleMs() == null ? SETTLE_MS : input.settleMs();
        Map<String, String> notes;
        try {
            notes = GameNotes.sections(project);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            return AgentReply.refused(e.getMessage());
        }
        Optional<String> found = named(notes, asked);
        if (found.isEmpty()) {
            return AgentReply.refused("No section " + asked + " in " + GameNotes.FILE + (asked.equalsIgnoreCase(HOME)
                    ? ": write_notes Home with its Marker: line first, then cut its entrances with screen Home."
                    : ": try_input opens a screen's section when an input opens it.") + " Nothing was done.");
        }
        String screen = found.get();
        Optional<String> owing = owingMarker(notes);
        if (owing.isPresent()) return AgentReply.refused(owes(owing.get()));
        Optional<String> before = unfinishedAbove(notes, screen);
        if (before.isPresent()) {
            List<String> waiting = GameNotes.untried(notes.get(before.get()));
            return AgentReply.refused("Finish " + before.get() + " first: " + (waiting.isEmpty() ? "nothing on it is "
                    + "tried yet" : "it still has untried inputs (" + String.join(", ", waiting) + ")") + ", and "
                    + screen + " was reached from it. Every input of a screen before going down any. Nothing was "
                    + "done.");
        }
        Optional<String> begun = branchBegun(notes, screen);
        if (begun.isPresent()) {
            return AgentReply.refused("Finish the branch begun at " + begun.get() + " first: it, or a screen reached "
                    + "from it, still has inputs to try. One branch to its end before the next. Nothing was done.");
        }
        String what = byPicture ? input.picture().strip() : "key " + key.get().label();
        List<String> left = GameNotes.untried(notes.get(screen));
        if (byPicture && !ActTools.exists(context, what)) {
            if (!left.contains(what)) return AgentReply.refused(Frames.noPicture(what));
            GameNotes.write(project, screen, GameNotes.withUntriedLine(notes.get(screen),
                    left.stream().filter(name -> !name.equals(what)).toList()));
            return AgentReply.text(what + " is no picture any more (deleted or renamed): taken off " + screen
                    + "'s untried inputs. Nothing was clicked.");
        }
        if (never && byPicture && !left.contains(what)) {
            return AgentReply.refused(what + " is not an untried input of " + screen + ": crop_picture it with screen "
                    + screen + " first. Nothing was done.");
        }
        String child = opens != null ? opens : nameFrom(what, screen);
        if (!never && (!isName(child) || child.equalsIgnoreCase(screen) || child.equalsIgnoreCase(GameNotes.MAP)
                || child.equalsIgnoreCase(HOME))) {
            return AgentReply.refused(what + " would name the screen it opens \"" + child + "\": give opens, the name "
                    + "that screen shows. Nothing was done.");
        }
        Optional<String> marker = GameNotes.marker(notes.get(screen));
        if (marker.isPresent()) {
            Optional<AgentReply> away = ActTools.notOn(context, marker.get(), screen);
            if (away.isPresent()) return away.get();
        }
        if (never) return AgentReply.text(tried(project, screen, what, GameNotes.NEVER
                + input.never().strip()));
        ActTools.Entered entered = ActTools.enter(context, byPicture ? input.picture() : null, key.orElse(null),
                input.desktop(), settle, FrameChange.ANSWER_SHARE);
        if (entered.after() == null) {
            return entered.reply().isRefused() ? entered.reply() : entered.reply().and(AgentReply.text(
                    "Nothing was written. Look where the game is before trying it again from " + screen + "."));
        }
        try {
            if (entered.share() >= SCREEN_SHARE) {
                // Back, close, or another way to a screen mapped already: its marker came into view, and its Map
                // line stays. A marker on screen before the click proves nothing: a panel opens over it.
                Optional<String> known = notes.entrySet().stream()
                        .filter(e -> !e.getKey().equals(screen) && !e.getKey().equalsIgnoreCase(GameNotes.MAP))
                        .filter(e -> GameNotes.marker(e.getValue()).filter(m -> ActTools.shows(context, m, entered.after())
                                && !ActTools.shows(context, m, entered.before())).isPresent())
                        .map(Map.Entry::getKey).findFirst();
                if (known.isPresent()) {
                    return entered.reply().and(AgentReply.text(tried(project, screen, what,
                            GameNotes.OPENS + known.get()) + " " + known.get() + " was mapped already: its marker came into view."));
                }
                String how = screen + " › " + entered.how();
                String said = recorded(project, child, how);
                String spelled = spelled(GameNotes.sections(project), child);
                tried(project, screen, what, GameNotes.OPENS + spelled);
                String grouped;
                try {
                    grouped = TagTools.screenTag(context.services(), spelled)
                            .map(tag -> " Its pictures go under the tag " + tag + ".").orElse("");
                } catch (IllegalStateException e) {
                    grouped = "";   // Studio busy: its first cut declares the tag
                }
                return entered.reply()
                        .and(AgentReply.text(said + grouped + " Before leaving it, from the screenshot below: crop_picture "
                                + "with screen " + spelled + " and marker true a part only this screen shows, then "
                                + "with screen " + spelled + " every input on it (each button, tab and toggle); what "
                                + "cannot be cut now, write_notes as an Owed: line in its section. Then go back to "
                                + screen + " and try its next input."))
                        .and(LookTools.screenshot(new LookTools.Shot(null, null, null, null, null, null), context));
            }
            boolean changed = FrameChange.box(entered.before(), entered.after(), entered.motion()).isPresent();
            String said = tried(project, screen, what, changed ? GameNotes.IN_PLACE : GameNotes.NO_EFFECT);
            return entered.reply().and(AgentReply.text(said + (changed ? " Cut, with screen " + screen + ", each new "
                    + "input it shows (another state of a toggle, a tab's buttons): they are untried in turn." : "")));
        } catch (IllegalArgumentException | UncheckedIOException e) {
            return entered.reply().and(AgentReply.refused("The input was used, but the notes could not be written: "
                    + e.getMessage()));
        }
    }

    /**
     * Writes {@code screen}'s line in the Map, reached by {@code how}, and opens its section owing a marker when it
     * has none: a section written earlier keeps its text. Says what it wrote.
     */
    static String recorded(Path project, String asked, String how) {
        Map<String, String> notes = GameNotes.sections(project);
        String map = named(notes, GameNotes.MAP).map(notes::get).orElse("");
        // The name as the notes spell it already, so the Map's line and the section stay one screen.
        String screen = GameNotes.mapName(map, asked).or(() -> named(notes, asked)).orElse(asked);
        // The section first: written alone, it still holds the gate; a Map line alone would not.
        boolean opened = named(notes, screen).isEmpty();
        if (opened) GameNotes.write(project, screen, "Entrance: " + how + "\n" + GameNotes.OWED_MARKER);
        // A section there already keeps its Map line when it has one: this is another way in, not its entrance.
        if (opened || GameNotes.mapName(map, screen).isEmpty()) {
            GameNotes.write(project, named(notes, GameNotes.MAP).orElse(GameNotes.MAP),
                    GameNotes.withMapLine(map, screen, how));
        }
        return "Opened " + screen + ", mapped as " + how + (opened ? ", its section owing a marker."
                : "; its section was there already.");
    }

    /** Records in {@code screen}'s section that {@code what} was tried and did {@code did}. Says what it wrote. */
    static String tried(Path project, String screen, String what, String did) {
        String text = GameNotes.sections(project).get(screen);
        if (text == null) throw new IllegalArgumentException("its section " + screen + " is gone.");
        String now = GameNotes.withTried(text, what, did);
        GameNotes.write(project, screen, now);
        List<String> left = GameNotes.untried(now);
        return "Noted in " + screen + ": tried " + what + ", " + did + "." + (left.isEmpty()
                ? " Every input cut on it is tried: " + GameNotes.shapeOf(now).map(s -> "a " + s + ".").orElse("")
                : " Untried: " + String.join(", ", left) + ".");
    }

    /**
     * Notes the picture {@code picture}, just cut, in {@code section}: its marker, paying the debt, or an input
     * untried until try_input tries it. Says where; the section is the one {@link #section} found.
     */
    static String noteCut(Path project, String section, String picture, boolean marker) {
        String text = GameNotes.sections(project).get(section);
        if (text == null) throw new IllegalArgumentException("its section " + section + " is gone.");
        String was = marker ? GameNotes.marker(text).filter(old -> !old.equals(picture)).orElse(null) : null;
        GameNotes.write(project, section, marker ? GameNotes.withMarker(text, picture)
                : GameNotes.withUntried(text, picture));
        return " Noted in " + section + (marker ? " as its marker" + (was == null ? "" : ", in place of " + was) + "."
                : " as untried: try_input it before leaving " + section + ".");
    }

    /** Whether {@code name} can name a screen: one line of plain words, none of the notes' own syntax. */
    static boolean isName(String name) {
        return !name.isEmpty() && !NOT_A_NAME.matcher(name).find();
    }

    /** {@code screen}'s section as the notes spell it, or why there is none, for crop_picture to refuse first. */
    static Optional<String> section(Path project, String screen) {
        return named(GameNotes.sections(project), screen);
    }

    /** The first section, not the Map, still owing its marker with no reason given. */
    static Optional<String> owingMarker(Map<String, String> notes) {
        return notes.entrySet().stream()
                .filter(e -> !e.getKey().equalsIgnoreCase(GameNotes.MAP) && GameNotes.owesMarker(e.getValue()))
                .map(Map.Entry::getKey).findFirst();
    }

    static String owes(String screen) {
        return screen + " still owes its marker: screenshot it, then crop_picture with screen " + screen + " and "
                + "marker true a part only that screen shows; or, when none can be cut yet, write_notes its section "
                + "with \"" + GameNotes.OWED_MARKER + " — <why>\". Nothing was done.";
    }

    /**
     * The nearest screen {@code screen} was reached from, through the Entrance lines, still unfinished. A game's
     * screens are a shallow tree of many branches (the user's call, 2026-10-14): each screen's every input is tried
     * before going down (breadth on one screen), then one branch is followed down at a time (depth across them).
     */
    static Optional<String> unfinishedAbove(Map<String, String> notes, String screen) {
        Set<String> seen = new HashSet<>();
        String at = screen;
        while (seen.add(at.toLowerCase(Locale.ROOT)) && seen.size() < DEEPEST) {
            Optional<String> from = Optional.ofNullable(notes.get(at)).flatMap(GameNotes::entranceFrom)
                    .flatMap(name -> named(notes, name));
            if (from.isEmpty() || seen.contains(from.get().toLowerCase(Locale.ROOT))) return Optional.empty();
            at = from.get();
            if (unfinished(notes.get(at))) return Optional.of(at);
        }
        return Optional.empty();
    }

    /**
     * A screen begun (something on it tried) whose branch is not finished, off {@code screen}'s own path from Home:
     * its branch is finished first, so one branch is explored to its end before the next is begun.
     */
    static Optional<String> branchBegun(Map<String, String> notes, String screen) {
        Set<String> above = new HashSet<>();
        String at = screen;
        while (above.add(at.toLowerCase(Locale.ROOT)) && above.size() < DEEPEST) {
            Optional<String> from = Optional.ofNullable(notes.get(at)).flatMap(GameNotes::entranceFrom)
                    .flatMap(name -> named(notes, name));
            if (from.isEmpty()) break;
            at = from.get();
        }
        return notes.entrySet().stream()
                .filter(e -> !above.contains(e.getKey().toLowerCase(Locale.ROOT)))
                .filter(e -> depth(notes, e.getKey()).isPresent() && GameNotes.triedAny(e.getValue()))
                .filter(e -> !branchDone(notes, e.getKey(), new HashSet<>()))
                .map(Map.Entry::getKey).findFirst();
    }

    /** Whether {@code screen} and every screen reached from it have every input tried. */
    private static boolean branchDone(Map<String, String> notes, String screen, Set<String> seen) {
        if (!seen.add(screen.toLowerCase(Locale.ROOT)) || seen.size() > DEEPEST * DEEPEST) return true;
        if (unfinished(notes.get(screen))) return false;
        return notes.entrySet().stream()
                .filter(e -> GameNotes.entranceFrom(e.getValue()).filter(from -> from.equalsIgnoreCase(screen)).isPresent())
                .allMatch(e -> branchDone(notes, e.getKey(), seen));
    }

    /**
     * Whether a screen on the tree still has inputs to try: an untried one, or none tried yet (no Shape: line). A
     * screen with no input at all says so with a Shape: line written by hand.
     */
    static boolean unfinished(String text) {
        return !GameNotes.untried(text).isEmpty() || !GameNotes.hasShape(text);
    }

    /** How many Entrance lines lead from Home to {@code screen}; empty when the chain never reaches Home. */
    static OptionalInt depth(Map<String, String> notes, String screen) {
        Set<String> seen = new HashSet<>();
        String at = screen;
        for (int depth = 0; depth < DEEPEST && seen.add(at.toLowerCase(Locale.ROOT)); depth++) {
            if (at.equalsIgnoreCase(HOME)) return OptionalInt.of(depth);
            Optional<String> from = Optional.ofNullable(notes.get(at)).flatMap(GameNotes::entranceFrom);
            if (from.isEmpty()) return OptionalInt.empty();
            at = named(notes, from.get()).orElse(from.get());
        }
        return OptionalInt.empty();
    }

    /** Every {@code Owed:} line and every untried input of every section but the Map, as {@code Screen: what}. */
    static List<String> owed(Map<String, String> notes) {
        List<String> owed = new ArrayList<>();
        notes.forEach((name, text) -> {
            if (name.equalsIgnoreCase(GameNotes.MAP)) return;
            for (String what : GameNotes.owed(text)) owed.add(name + ": " + what);
            List<String> untried = GameNotes.untried(text);
            if (!untried.isEmpty()) owed.add(name + ": untried " + String.join(", ", untried));
            else if (depth(notes, name).isPresent() && unfinished(text)) owed.add(name + ": no input tried yet");
        });
        return owed;
    }

    /** The screen a picture opens, named from the picture: {@code engineer_home} on Home is {@code Engineer}. */
    static String nameFrom(String picture, String from) {
        String suffix = "_" + from.toLowerCase(Locale.ROOT).replace(' ', '_');
        String base = picture.toLowerCase(Locale.ROOT).endsWith(suffix) && picture.length() > suffix.length()
                ? picture.substring(0, picture.length() - suffix.length()) : picture;
        String words = base.replace('_', ' ').strip();
        return words.isEmpty() ? picture : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** The section of {@code notes} named {@code name} in any case, as the notes spell it. */
    private static Optional<String> named(Map<String, String> notes, String name) {
        return notes.keySet().stream().filter(k -> k.equalsIgnoreCase(name)).findFirst();
    }

    /** {@code screen} as the notes spell it, the Map's line first, else as given. */
    private static String spelled(Map<String, String> notes, String screen) {
        return named(notes, GameNotes.MAP).flatMap(map -> GameNotes.mapName(notes.get(map), screen))
                .or(() -> named(notes, screen)).orElse(screen);
    }
}
