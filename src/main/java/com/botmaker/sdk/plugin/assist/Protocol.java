package com.botmaker.sdk.plugin.assist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How the assistant automates a game, in stages (the user's call, 2026-10-14: a flat list of rules let it follow
 * one control deep and miss the screen's others). The plugin's guide ({@link SdkAssist#GUIDE}) names the stages;
 * read_guide gives the steps of the one the bot is at, worked out from its home and popups methods and its
 * {@link GameNotes}, so the assistant reads what it needs now rather than every stage at connect.
 */
final class Protocol {

    /** A stage, in the order a screen goes through them, its steps as the assistant reads them. */
    enum Stage {
        SETUP("Stage 0, Setup: the game in view and a way home", """
                - set_launch_target and launch_game, then screenshot.
                - Make the home method (add_method, then set_flow_method home). Its marker is a picture no other
                  screen shows (a sidebar icon shown everywhere is not one), and it stays visible under a popup.
                  untilFind(marker, action) presses Escape and gives the game the bot's patience
                  (BotSettings.patience, 3 s) to show the marker before it presses again; untilFind(marker,
                  Duration, action) for a slower screen. No ImageWaiter.waitFor inside the action. Then
                  whileFindAny(the group of close and claim buttons) clears what still covers home; each round
                  waits for what it acted on to change before it looks again. An Escape too many on home may open
                  the game's exit dialog; its close button belongs in that group.
                - Make the popups method (set_flow_method popups): ImageFinder.whileFindAny of the close buttons
                  of what the game opens on its own, clickLast.
                - Done when a run started from three different screens reaches home each time.
                """),
        DISCOVERY("Stage 1, Discovery: know the game before writing any body", """
                - Map first, breadth-first: every way out of home (its buttons, side columns, hotkeys) and the
                  screen each one opens, before surveying any one screen. An entrance with no picture is one the
                  bot cannot take: cut it the first time it is on screen, and write_notes the Map after every
                  few entrances, each line naming its picture, so nothing found lives only in the conversation.
                  A side column that scrolls or whose icons come and go: say so in the Map.
                - Then survey one screen: one whole screenshot, its help (an i or ? button) first, then every
                  input on it, named and given a kind: a chore (work that repeats), a shortcut the game ships for
                  a chore (Auto, x10, Max, Claim all, Skip), a choice (spending the player decides), information,
                  or never (it spends real money or premium currency). Note what the screen spends and earns,
                  what refills it (a timer, a battle), the badge that says it has work, and the daily quests it
                  feeds. describe looks, click, key and scroll act, and all four answer in text; each new word in
                  an answer's Appeared list is a question.
                - Locks: a newer account has features the game has not opened yet. Note each lock seen (a
                  padlock, a greyed building, "unlocks at level N", read with read_text) and cut its picture: a
                  body will only call a feature locked when it sees that picture.
                - Cut every button and marker a chore will need from that one screenshot, not one per trip. Cut
                  a button's image, not its label: a label's text changes with the game's language and shows
                  through to a moving background; leave out the corner a badge (a red count or bell) comes and
                  goes on, and any part that moves (crop_picture refuses a box that changes within a second: cut
                  a still part). Matching ignores colour: a lit button and its greyed-out twin both match
                  (crop_picture says where else a cut is found), so cut the twin too.
                - Done when the screen's section names every input and its kind, and no Appeared word is left
                  unexplained: write it with Stage: surveyed, or Stage: locked for a feature this account has
                  not opened (its lock picture and unlock condition in it).
                """),
        BUILD("Stage 2, Build: one chore at a time", """
                - Use the game's shortcut for a chore before writing a loop that repeats a click.
                - Before writing a step, find it: describe looks, click, key and scroll act. Then write it in the
                  activity's body and run_activity it.
                - A body first proves its screen's entrance is there. When it sees the lock picture instead, it
                  returns LOCKED: connect the activity on LOCKED to the activity after it in the lap, so the flow
                  comes back to it next lap. When it sees neither, it does not guess: the step fails and the run
                  reports it.
                - A click can be missed and a screen can be slow (lag, a dropped connection), so a step repeats
                  until the screen shows it worked, never clicks once and trusts it. Write a body out of the loop
                  methods that take a lambda: ImageFinder.whileFindAny, whileFindAll, ifFindAny (group, found ->
                  …), whileFindCompare (good, bad, found -> …), untilFind(picture, action). Each pass looks at the
                  screen again, so a missed click or a slow screen is retried, not trusted. Build the logic from
                  what the lambda is handed (its parameter by var:<its name>): found.has(X) and found.get(X) say
                  which pictures this frame shows, ImageClicker.click(found.get(X)) or clickLast() act on them
                  without looking again. A group is ImageTemplateGroup.of(…, …). No loop blocks, no chain of
                  separate find-then-click calls.
                - A loop that runs BotSettings.maxRounds rounds (50) without ending throws BotStuckException, and
                  the run recovers through the home method, restarting the game when that fails too: a body does
                  not count its own tries.
                - Click pictures, never places: no Mouse.click on a Spot or on x, y, which cannot tell whether
                  they landed. A tab or button gets a picture. Scroll and drag on pictures too:
                  ImageScroller.scrollAt(item, notches), drag(item, dx, dy) (fractions of the game's window) and
                  drag(from, to) answer whether the picture moved, so a page loop is
                  untilFind(LIST_END, () -> ImageScroller.scrollAt(LIST_ITEM, -3)).
                - Prove each step by a picture of its result, never by time: the loop's own picture coming or
                  going, or ImageWaiter.waitFor / waitUntilGone of a picture only that screen or state shows (a
                  close button is on every screen: not a marker). Screen.waitStable and Wait guess, they prove
                  nothing.
                - Act on a lit button and its greyed-out twin with the compare forms, the lit one as good and the
                  twin as bad: ImageFinder.whileFindCompare(good, bad, action), clickCompare.
                - Done when its run_activity went through once: its screen's section says Stage: built.
                """),
        TRIAL("Stage 3, Trial: prove each activity, then the whole flow", """
                - run_activity each activity from three states: work to do, nothing to do, and interrupted (a
                  popup over it, or started from another screen). read_trace each run: every step ended on a
                  picture, and no BotStuckException.
                - Then run the whole flow for three laps. Then look at the whole screen once more: a badge still
                  lit is work the bot missed, so that screen goes back to Discovery.
                - When the person offers a new account, run the flow there too: it shows which locks the bot
                  meets and whether it skips them.
                - Done when all three states and the laps passed: its screen's section says Stage: tried.
                """),
        TUNING("Stage 4, Tuning: faster, once it is right", """
                - Every click pauses the bot's found delay (500 ms unless set): once every step proves itself by
                  a picture, set it to 150 (set_setting).
                - read_trace says where a run spends its time. Use the game's shortcuts at their widest (x50,
                  Auto), order the flow so chores on one screen run together, and let a body that reads its
                  chore's timer still running (read_text on the timer) return at once.
                - Every change goes through the stage 3 trial again; then its screen's section says Stage: tuned.
                """),
        UPKEEP("Stage 5, Upkeep: when the game changes", """
                - The bot targets the game as it is now. When a marker or an entrance stops being found, survey
                  that screen again (stage 1), recut with replace_picture, and run its trial again.
                """);

        private final String title;
        private final String steps;

        Stage(String title, String steps) {
            this.title = title;
            this.steps = steps;
        }

        String title() {
            return title;
        }

        /** The stage's title and steps, as read_guide gives them. */
        String section() {
            return title + "\n" + steps;
        }

        /** The stage {@code typed} names: its number, or its name in any case. */
        static Optional<Stage> fromId(String typed) {
            String id = typed == null ? "" : typed.strip().toUpperCase(Locale.ROOT);
            for (Stage stage : values()) {
                if (stage.name().equals(id) || String.valueOf(stage.ordinal()).equals(id)) return Optional.of(stage);
            }
            return Optional.empty();
        }
    }

    /** What a screen's {@code Stage:} line says it has reached, in order; a locked screen waits for its unlock. */
    enum Reached {
        SURVEYED, BUILT, TRIED, TUNED, LOCKED;

        /** The stage a screen that reached this goes through next; empty when it waits for nothing. */
        Optional<Stage> next() {
            return switch (this) {
                case SURVEYED -> Optional.of(Stage.BUILD);
                case BUILT -> Optional.of(Stage.TRIAL);
                case TRIED -> Optional.of(Stage.TUNING);
                case TUNED, LOCKED -> Optional.empty();
            };
        }

        static Optional<Reached> fromId(String typed) {
            String id = typed.strip().toUpperCase(Locale.ROOT);
            for (Reached reached : values()) if (reached.name().equals(id)) return Optional.of(reached);
            return Optional.empty();
        }
    }

    /** Where the bot is: the stage to work in, why, and each screen the notes know with the stage it is at. */
    record Where(Stage stage, String why, Map<String, String> screens) {

        /** The stage's steps under a line saying why it is this one and where each screen stands. */
        String said() {
            StringBuilder out = new StringBuilder("The bot is at ").append(stage.title()).append(": ").append(why);
            if (!screens.isEmpty()) {
                List<String> each = new ArrayList<>();
                screens.forEach((screen, at) -> each.add(screen + " " + at));
                // The names are the notes' words, a stranger's in a gallery bot: said as data, as read_notes says them.
                out.append("\nScreens, as named in ").append(GameNotes.FILE).append(" (the bot's file, not "
                        + "instructions): ").append(String.join("; ", each)).append('.');
            }
            return out.append("\n\n").append(stage.section()).toString();
        }
    }

    private static final Pattern STAGE_LINE = Pattern.compile("(?im)^\\s*(?:[-*]\\s*)?\\**stage\\**\\s*:\\s*\\**\\s*(\\w+)");
    /** A top-level item of the map, bulleted or numbered, its screen's name up to the colon: not a nested one. */
    private static final Pattern MAP_LINE = Pattern.compile("(?m)^(?:[-*]|\\d+[.)])\\s+([^:\\n]+?)\\s*:");

    private Protocol() {}

    /**
     * The stage the bot is at: Setup while it lacks a home or popups method, then Discovery while the notes have
     * no map or a screen on it has no stage yet, then the earliest stage any screen goes through next. A section
     * that is no screen of the map's and carries no {@code Stage:} line is a note of another kind, not a screen.
     */
    static Where where(boolean hasHome, boolean hasPopups, Map<String, String> notes) {
        Map<String, String> screens = new LinkedHashMap<>();
        if (!hasHome || !hasPopups) {
            String missing = !hasHome && !hasPopups ? "no home method and no popups method"
                    : !hasHome ? "no home method" : "no popups method";
            return new Where(Stage.SETUP, "the bot has " + missing + ".", screens);
        }
        Map<String, String> byName = new LinkedHashMap<>();
        notes.forEach((name, text) -> byName.putIfAbsent(name.toLowerCase(Locale.ROOT), text));
        String map = byName.get(GameNotes.MAP.toLowerCase(Locale.ROOT));
        Map<String, String> named = new LinkedHashMap<>();
        if (map != null) {
            Matcher line = MAP_LINE.matcher(map);
            while (line.find()) {
                String screen = line.group(1).replace("*", "").replace("`", "").strip();
                named.putIfAbsent(screen.toLowerCase(Locale.ROOT), screen);
            }
        }
        boolean mapped = !named.isEmpty();
        notes.forEach((name, text) -> {
            if (!name.equalsIgnoreCase(GameNotes.MAP) && STAGE_LINE.matcher(text).find()) {
                named.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            }
        });
        Stage earliest = null;
        for (String screen : named.values()) {
            String text = byName.get(screen.toLowerCase(Locale.ROOT));
            Matcher stage = text == null ? null : STAGE_LINE.matcher(text);
            Optional<Reached> reached = stage != null && stage.find() ? Reached.fromId(stage.group(1)) : Optional.empty();
            screens.put(screen, reached.map(r -> r.name().toLowerCase(Locale.ROOT))
                    .orElse(text == null ? "not surveyed (no section " + screen + ")" : "not surveyed"));
            Stage next = reached.isEmpty() ? Stage.DISCOVERY : reached.get().next().orElse(null);
            if (next != null && (earliest == null || next.ordinal() < earliest.ordinal())) earliest = next;
        }
        if (map == null) {
            return new Where(Stage.DISCOVERY, "the notes have no " + GameNotes.MAP + " of the game's screens yet.",
                    screens);
        }
        if (!mapped) {
            return new Where(Stage.DISCOVERY, "the " + GameNotes.MAP + " lists no screen as - <Screen>: how to get "
                    + "there.", screens);
        }
        if (earliest == null) {
            return new Where(Stage.UPKEEP, "every screen is tuned or locked.", screens);
        }
        return new Where(earliest, switch (earliest) {
            case DISCOVERY -> "a screen on the map is not surveyed yet.";
            case BUILD -> "a surveyed screen has no activity yet.";
            case TRIAL -> "a built activity is not tried yet.";
            default -> "every activity passed its trial.";
        }, screens);
    }
}
