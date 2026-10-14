package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.sdk.api.input.Key;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovery's map, kept by a tool rather than by the assistant's memory (the user's call, 2026-10-14: mapping
 * Firestone, the assistant clicked through a dozen screens, cut nothing and wrote nothing until asked, though the
 * guide said to). map_screen goes through one entrance, a picture or a hotkey, never a bare point, writes the
 * Map's line and opens the screen's section owing its marker; crop_picture with the screen pays that debt; and the
 * next map_screen refuses while any screen still owes one. read_guide keeps Discovery open while an {@code Owed:}
 * line is left ({@link Protocol}).
 */
final class MapTools {

    /** The share of the screen an entrance must change to have opened a screen: Firestone's side panels are a third. */
    static final double SCREEN_SHARE = 0.15;
    static final String HOME = "home";
    static final String OWED_MARKER = "Owed: marker";

    /** A line saying the screen still owes its marker, with no reason given. */
    private static final Pattern OWES_MARKER =
            Pattern.compile("(?im)^[ \\t]*(?:[-*][ \\t]*)?owed[ \\t]*:[ \\t]*marker[ \\t]*\\.?[ \\t]*$");
    /** Any line owing the marker, a reason given after a dash or not, with its line break: cutting a marker pays it. */
    private static final Pattern MARKER_DEBT = Pattern.compile(
            "(?im)^[ \\t]*(?:[-*][ \\t]*)?owed[ \\t]*:[ \\t]*marker(?:[ \\t]*\\.?[ \\t]*|[ \\t]+[—–-][ \\t].*)$\\R?");
    /** A thing the notes owe, said on its own line: a picture to cut, a fact to find. */
    static final Pattern OWED = Pattern.compile("(?im)^[ \\t]*(?:[-*][ \\t]*)?owed[ \\t]*:[ \\t]*(.+?)[ \\t]*$");
    /** What an {@code Owed:} line says when nothing is owed. */
    private static final Pattern NOTHING = Pattern.compile("(?i)(?:none|nothing|n/?a|[—–-])\\.?");
    private static final Pattern MARKER_LINE =
            Pattern.compile("(?im)^[ \\t]*(?:[-*][ \\t]*)?marker[ \\t]*:[ \\t]*(.*?)[ \\t]*(?:\\R|$)");
    private static final Pattern ENTRANCE_LINE = Pattern.compile("(?im)^[ \\t]*(?:[-*][ \\t]*)?entrance[ \\t]*:.*$");
    private static final Pattern CUT_LINE = Pattern.compile("(?im)^([ \\t]*(?:[-*][ \\t]*)?cut[ \\t]*:)(.*)$");
    /** The entrance a Map line said before it was mapped again: {@code home › click x.} */
    private static final Pattern OLD_ENTRANCE = Pattern.compile("^[^›\\n]+›[ \\t]*(?:click|key)[ \\t]+[^ \\t.]+\\.?[ \\t]*");
    /** What a screen's name cannot hold: the notes' own syntax, or markdown its heading would read otherwise. */
    private static final Pattern NOT_A_NAME = Pattern.compile("[:*_`\\[\\]\\\\<>#\\p{Cntrl}]");

    record Entrance(@Describe("the screen it opens, as the Map will name it: Engineer") String screen,
                    @Describe(value = "the screen the entrance is on, as the Map names it; home when left out",
                            optional = true) String from,
                    @Describe(value = "the entrance's picture, clicked at its best match", optional = true)
                    String picture,
                    @Describe(value = "a hotkey instead of a picture, as printed on it: Q", optional = true) String key,
                    @Describe(value = "the longest the screen is given to open, in milliseconds; 10000 when left out",
                            optional = true) Integer settleMs,
                    @Describe(value = "true to act on the user's own desktop, taking their mouse; refused there "
                            + "otherwise", optional = true) Boolean desktop) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("map_screen")
                    .describedAs("Discovery's way into a screen: clicks the entrance's picture or taps its hotkey, "
                            + "waits for a new screen, writes its line in the Map and opens its section owing a "
                            + "marker, and answers with the screen's screenshot to cut from. Refused while a screen "
                            + "mapped before still owes its marker: crop_picture with screen and marker pays it")
                    .takes(Entrance.class).handledBy(MapTools::mapScreen));

    private MapTools() {}

    static AgentReply mapScreen(Entrance entrance, AgentContext context) {
        Path project = context.services().projectDir();
        if (project == null) return AgentReply.refused(Frames.noProject());
        String screen = entrance.screen() == null ? "" : entrance.screen().strip();
        String from = entrance.from() == null || entrance.from().isBlank() ? HOME : entrance.from().strip();
        if (!isName(screen) || screen.equalsIgnoreCase(GameNotes.MAP) || screen.equalsIgnoreCase(HOME)
                || !isName(from)) {
            return AgentReply.refused("Name the screen, and the one the entrance is on, in plain words on one line, "
                    + "as the Map will call them: no colon, no markdown (* _ ` [ ] # \\ < >).");
        }
        boolean byPicture = entrance.picture() != null && !entrance.picture().isBlank();
        boolean byKey = entrance.key() != null && !entrance.key().isBlank();
        if (byPicture == byKey) {
            return AgentReply.refused("An entrance is a picture to click or a hotkey, one of them: the bot can take "
                    + "no other. A button with no picture yet: screenshot, crop_picture it, then map_screen with "
                    + "its name. Nothing was done.");
        }
        Optional<Key> key = byKey ? ActTools.key(entrance.key()) : Optional.empty();
        if (byKey && key.isEmpty()) return AgentReply.refused("\"" + entrance.key() + "\" is no key.");
        int settle = entrance.settleMs() == null ? ActTools.LONGEST_SETTLE_MS : entrance.settleMs();
        Map<String, String> notes;
        try {
            notes = GameNotes.sections(project);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            return AgentReply.refused(e.getMessage());
        }
        Optional<String> owing = owingMarker(notes);
        if (owing.isPresent()) return AgentReply.refused(owes(owing.get()));
        if (!from.equalsIgnoreCase(HOME) && named(notes, from).isEmpty() && !onMap(notes, from)) {
            return AgentReply.refused("map_screen " + from + " first: the notes have no line or section for it.");
        }
        ActTools.Entered entered = ActTools.enter(context, byPicture ? entrance.picture() : null, key.orElse(null),
                entrance.desktop(), settle, SCREEN_SHARE);
        if (entered.after() == null) {
            return entered.reply().isRefused() ? entered.reply() : entered.reply().and(AgentReply.text(
                    "Nothing was written. Look where the game is before going back to " + from + " to map it again."));
        }
        double share = entered.share();
        if (share < SCREEN_SHARE) {
            return entered.reply().and(AgentReply.text("Only " + Math.round(share * 100) + "% of the screen "
                    + "changed: that opened no screen, so nothing was written. A popup over a screen is part of "
                    + "that screen's survey; a screen slow to draw, map it again with a longer settleMs."));
        }
        String how = (from.equalsIgnoreCase(HOME) ? HOME : spelled(notes, from)) + " › " + entered.how();
        String said;
        try {
            said = recorded(project, screen, how);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            return entered.reply().and(AgentReply.refused("The screen opened, but the notes could not be written: "
                    + e.getMessage()));
        }
        return entered.reply()
                .and(AgentReply.text(said + " Before the next map_screen, from the screenshot below: crop_picture "
                        + "with screen " + screen + " and marker true a part only this screen shows; with screen "
                        + screen + " every button a chore here plainly needs (a Claim, an Upgrade, a Train, a tab); "
                        + "what cannot be cut now, write_notes as an Owed: line in its section. Then go back to "
                        + from + "."))
                .and(LookTools.screenshot(new LookTools.Shot(null, null, null, null, null, null), context));
    }

    /**
     * Writes {@code screen}'s line in the Map, reached by {@code how}, and opens its section owing a marker when it
     * has none: a section written earlier keeps its text. Says what it wrote.
     */
    static String recorded(Path project, String asked, String how) {
        Map<String, String> notes = GameNotes.sections(project);
        String map = named(notes, GameNotes.MAP).map(notes::get).orElse("");
        // The name as the notes spell it already, so the Map's line and the section stay one screen.
        String screen = mapName(map, asked).or(() -> named(notes, asked)).orElse(asked);
        // The section first: written alone, it still holds the gate; a Map line alone would not.
        boolean opened = named(notes, screen).isEmpty();
        if (opened) GameNotes.write(project, screen, "Entrance: " + how + "\n" + OWED_MARKER);
        GameNotes.write(project, named(notes, GameNotes.MAP).orElse(GameNotes.MAP), withMapLine(map, screen, how));
        return "Mapped " + screen + " as " + how + (opened ? ", its section owing a marker."
                : "; its section was there already.");
    }

    /**
     * Notes the picture {@code picture}, just cut, in {@code screen}'s section: its marker, paying the debt, or one
     * of its cuts. Says where; the section is the one {@link #section} found.
     */
    static String noteCut(Path project, String section, String picture, boolean marker) {
        String text = GameNotes.sections(project).get(section);
        if (text == null) throw new IllegalArgumentException("its section " + section + " is gone.");
        Matcher old = MARKER_LINE.matcher(text);
        String was = marker && old.find() && !old.group(1).isBlank() && !old.group(1).equals(picture)
                ? old.group(1) : null;
        GameNotes.write(project, section, marker ? withMarker(text, picture) : withCut(text, picture));
        return " Noted in " + section + (marker ? " as its marker" + (was == null ? "" : ", in place of " + was) + "."
                : "'s cuts.");
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
                .filter(e -> !e.getKey().equalsIgnoreCase(GameNotes.MAP) && OWES_MARKER.matcher(e.getValue()).find())
                .map(Map.Entry::getKey).findFirst();
    }

    static String owes(String screen) {
        return screen + " still owes its marker: screenshot it, then crop_picture with screen " + screen + " and "
                + "marker true a part only that screen shows; or, when none can be cut yet, write_notes its section "
                + "with \"" + OWED_MARKER + " — <why>\". Nothing was done.";
    }

    /** Every {@code Owed:} line of every section but the Map, as {@code Screen: what}. */
    static List<String> owed(Map<String, String> notes) {
        List<String> owed = new ArrayList<>();
        notes.forEach((name, text) -> {
            if (name.equalsIgnoreCase(GameNotes.MAP)) return;
            Matcher line = OWED.matcher(text);
            while (line.find()) if (!NOTHING.matcher(line.group(1)).matches()) owed.add(name + ": " + line.group(1));
        });
        return owed;
    }

    /** The Map with {@code screen}'s top-level line saying {@code how}, what it said after that kept. */
    static String withMapLine(String map, String screen, String how) {
        List<String> lines = new ArrayList<>(map.isEmpty() ? List.of() : map.lines().toList());
        for (int i = 0; i < lines.size(); i++) {
            Matcher item = Protocol.MAP_LINE.matcher(lines.get(i));
            if (item.lookingAt() && clean(item.group(1)).equalsIgnoreCase(screen)) {
                String bullet = lines.get(i).substring(0, item.start(1));
                String rest = lines.get(i).substring(item.end()).replaceFirst("^[*`]+", "").strip();
                rest = OLD_ENTRANCE.matcher(rest).replaceFirst("").strip();
                lines.set(i, bullet + screen + ": " + how + (rest.isEmpty() ? "" : ". " + rest));
                return String.join("\n", lines);
            }
        }
        lines.add("- " + screen + ": " + how);
        return String.join("\n", lines).strip();
    }

    /** {@code text} with {@code picture} as its marker: any other Marker line and any marker debt gone. */
    static String withMarker(String text, String picture) {
        String rest = MARKER_DEBT.matcher(MARKER_LINE.matcher(text).replaceAll("")).replaceAll("").strip();
        String line = "Marker: " + picture;
        Matcher entrance = ENTRANCE_LINE.matcher(rest);
        if (entrance.find()) {
            return (rest.substring(0, entrance.end()) + "\n" + line + rest.substring(entrance.end())).strip();
        }
        return (line + "\n" + rest).strip();
    }

    /** {@code text} with {@code picture} added to its {@code Cut:} line, which it gains when it had none. */
    static String withCut(String text, String picture) {
        Matcher cut = CUT_LINE.matcher(text);
        if (!cut.find()) return (text.strip() + "\nCut: " + picture).strip();
        List<String> names = new ArrayList<>();
        for (String name : cut.group(2).split(",")) if (!name.isBlank()) names.add(name.strip());
        if (names.contains(picture)) return text;
        names.add(picture);
        return text.substring(0, cut.start()) + cut.group(1) + " " + String.join(", ", names) + text.substring(cut.end());
    }

    /** The section of {@code notes} named {@code name} in any case, as the notes spell it. */
    private static Optional<String> named(Map<String, String> notes, String name) {
        return notes.keySet().stream().filter(k -> k.equalsIgnoreCase(name)).findFirst();
    }

    /** {@code screen} as the notes spell it, the Map's line first, else as given. */
    private static String spelled(Map<String, String> notes, String screen) {
        return named(notes, GameNotes.MAP).flatMap(map -> mapName(notes.get(map), screen))
                .or(() -> named(notes, screen)).orElse(screen);
    }

    /** Whether the Map has a top-level line for {@code screen}. */
    private static boolean onMap(Map<String, String> notes, String screen) {
        return named(notes, GameNotes.MAP).flatMap(map -> mapName(notes.get(map), screen)).isPresent();
    }

    /** {@code screen} as the Map's top-level line spells it, when it has one. */
    private static Optional<String> mapName(String map, String screen) {
        Matcher item = Protocol.MAP_LINE.matcher(map);
        while (item.find()) {
            String name = clean(item.group(1));
            if (name.equalsIgnoreCase(screen)) return Optional.of(name);
        }
        return Optional.empty();
    }

    private static String clean(String name) {
        return name.replace("*", "").replace("`", "").strip();
    }
}
