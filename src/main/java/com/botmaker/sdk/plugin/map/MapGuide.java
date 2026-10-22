package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Input;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

/**
 * What each sign of 🗺 Screens means: the toolbar's ? opens it as a short legend. The tooltips it repeats are kept
 * here too, so a tooltip and the legend never say two things of one button.
 */
final class MapGuide {

    // --- the toolbar ---
    static final String OTHER_WAYS = "Show the dashed arrows: other ways into a screen, and ways back";
    static final String ALL_LINKS = "Draw every arrow in full. Off, only the entrances are, and the arrows in and out "
            + "of the screen under the pointer or picked; the rest are faint";
    static final String FIT = "Zoom to show the whole map";
    static final String ZOOM_OUT = "Zoom out (the wheel zooms about the pointer; a right drag pans)";
    static final String ZOOM_IN = "Zoom in";
    static final String TIDY = "Put every card you moved back where the layout puts it";
    static final String PNG = "Save the whole map as a picture, at full size";
    static final String OPEN = "Open 🗺 Screens, with each screen's inputs and actions";
    static final String GUIDE = "What each sign on the map means";
    static final String OUTLINE = "Show or hide the outline of screens, on the left";
    static final String PANEL = "Show or hide the side panel of the screen picked, on the right";
    static final String FULL_SCREEN = "The window full screen, or back (F11; Esc leaves it too)";

    // --- the outline ---
    static final String FILTER_OWES = "Only screens owing their marker or with nothing cut";
    static final String FILTER_UNTRIED = "Only screens and inputs left to try";
    static final String OPENING_ONLY = "Every input, or only those that open a screen or are not tried yet: an input "
            + "tried that opened nothing is folded away";
    static final String COLLAPSE_ALL = "Shut every line, down to the screens at the top";
    static final String EXPAND_ALL = "Open every line";

    // --- a card's badges ---
    static final String LOCKED = "Every way in is locked on this account";
    static final String FOLDED = "Folded: Unfold in its right-click menu, or double-click it in the overlay's pane";

    // --- the toolbar's ▶▶ ---
    static final String NEXT_UNTRIED = "Pick the next input to try, on the screen Discovery explores next, and show "
            + "it big: ▶ Try then tries it";

    /**
     * A part of the side panel or of the picked input's editors, each with a line always shown under its title saying
     * what it is for. A part folds; those the input's kind needs open at first, the rest folded, and each then stays
     * as it was last left on this computer ({@link PanelParts}).
     */
    enum Part {
        MARKERS("Markers", "What tells this screen apart: pictures that all show on it, pictures it never shows, "
                + "words it reads."),
        INPUTS("Inputs", "Everything pressed on it: buttons, hotkeys, strips, trees. ▶ tries one, ⋯ edits it, a "
                + "click on its box on the board picks it."),
        WAYS_IN("Ways in", "Each input of another screen that opens this one; the first is how it was found."),
        SCREEN_LOOKS("Looks", "Other looks of the whole screen, an event's skin: each shows only while its method "
                + "says so."),
        WAY_BACK("Way back", "What a walk presses to leave it: its own input, else a close button, then Escape."),
        OWES("Owes", "What is left to cut or try here, as Discovery noted it."),
        NOTES("Notes", "Your notes on it: what it spends and earns, its timers, its quirks."),
        PRESSED_BY("Pressed by", "How the run presses it: a click, a double or right click, a hold, a hover."),
        TEXT_FIELD("Text field", "A field the run types into: the words typed, the field cleared first, Enter "
                + "after."),
        DRAG("Drag", "Dragged rather than clicked: onto a picture or into a zone, over a time."),
        BUTTON_LOOKS("Looks", "Other pictures of this button and what each means: Disabled is never pressed, "
                + "Selected tells a tab."),
        COPIES("Copies", "When its picture shows more than once: which copy is pressed, and in what order."),
        WHERE("Where", "Where it is found: at its own place, or beside another picture, or a grid of spots there."),
        GROUP("Strip or tree", "Whether it is one button of a strip that scrolls, or a node of a tree."),
        KEYS("Keys", "The key or combination pressed, and how long it is held."),
        WORDS("Words", "The words it is known by, read in its zone."),
        MEMBERS("Members", "The strip's buttons, in their order along it."),
        STRIP_SCROLL("Scroll", "How the strip turns to bring a member into view: wheel notches or a drag, measured "
                + "once."),
        NODES("Nodes", "The tree's nodes, how they link and where their offsets place them."),
        TREE_SCROLL("Scroll", "How the tree's view moves to reach a node out of view."),
        TRY("Try", "Presses it on the game now and records what it did.");

        private final String title;
        private final String caption;

        Part(String title, String caption) {
            this.title = title;
            this.caption = caption;
        }

        String title() {
            return title;
        }

        String caption() {
            return caption;
        }

        /**
         * Whether it opens at first for {@code input} (null: a part of the screen's panel): a screen's markers and
         * inputs; a button's press and looks, its field or drag when it is one; every part of a hotkey, words, a strip
         * or a tree, which shows only for its own kind; and Try.
         */
        boolean opensFor(Input input) {
            return switch (this) {
                case MARKERS, INPUTS, PRESSED_BY, BUTTON_LOOKS, KEYS, WORDS, MEMBERS, STRIP_SCROLL, NODES,
                     TREE_SCROLL, TRY -> true;
                case TEXT_FIELD -> input instanceof Button button && button.isField();
                case DRAG -> input instanceof Button button && button.isDrag();
                case WAYS_IN, SCREEN_LOOKS, WAY_BACK, OWES, NOTES, COPIES, WHERE, GROUP -> false;
            };
        }

        /** Whether it opens by what the button is, never as it was last left: its field, its drag. */
        boolean followsKind() {
            return this == TEXT_FIELD || this == DRAG;
        }
    }

    // --- the side panel ---
    static final String TRY_NEXT = "Try the next untried input, as the assistant's try_input does";
    static final String CUT_INPUTS = "Draw boxes around more of its buttons: each is noted untried";
    static final String CUT_MARKER = "Draw a box around a part only this screen shows; on a screen with markers, one "
            + "more of them: it shows where all of them are found";
    static final String GO_HERE = "Walk the game here by the map, as Navigate.to does in a run";
    static final String ADD_MARKER = "One more marker from a picture the bot has, or a picture this screen never "
            + "shows where a look-alike does";
    static final String LEADS_TO = "Pick the screen of the map this input opens: one more way into it";
    static final String ON_ITS_OWN = "Pick the screen this one follows by itself, nothing pressed: Home after the "
            + "loading screen, Battle after Entering battle. A walk waits for it, up to the time that screen loads in";
    static final String REMOVE_BACK ="Take its own way back off: a walk then closes it by its close button, else "
            + "Escape";

    // --- the board, live ---
    static final String LIVE = "Draw the game here, live: a click, a drag, the wheel or a key on it goes to the game";
    static final String FREEZE = "Keep the frame shown, lossless, to take a screen's shot or make a new screen of it";
    static final String MISSED = "A frame the latest run saved when a wait timed out, opened frozen";
    static final String RETAKE_FROZEN = "This frame as the screen's shot, while its markers show on it; a frame showing "
            + "one of its looks is that look's shot";
    static final String LOOK_PICK = "Which look of this screen the shot and the inputs show: a look's own inputs only "
            + "on it, its own shot once one was taken while the game showed it";
    static final String NEW_FROM_FROZEN = "A new screen with this frame as its shot, opened by the input last clicked; "
            + "cutting then starts on it, its marker first";
    static final String RECORD_WAY = "The input clicked opened this screen: one more way into it, as try_input records";
    static final String CUT_FROZEN = "Draw pictures on this frame, then say what each is: a marker, a button, its way "
            + "back, a look, a strip's button, a zone, digits or a new image for a picture";
    static final String CUT_IGNORE = "A drag inside a cut is the part of it a run skips: a timer, a count";
    static final String CUT_SAVE = "Name each cut and say what it is; saved as crop_pictures saves it";
    static final String ADD_HOTKEY = "A key this screen answers, noted untried";
    static final String ADD_STRIP = "A new strip of the screen's buttons in no strip or tree, ticked in order, and how "
            + "it scrolls";
    static final String REPEATED = "The screen shows it more than once: a way and a run pick the copy they press";
    static final String CYCLES = "Each press shows its next look, the usual one first again after the last";
    static final String GESTURE = "How a run presses it: a click, a double or right click, a hold of the time beside "
            + "it, or the pointer only moved onto it for what opens on hover";
    static final String KEY_HOLD = "How long the keys stay down; 0 taps them";
    static final String FIELD = "A text box: clicked, then its text typed, one of the bot's Texts. Blank, a body gives "
            + "the text (Navigate.type), and no walk goes through it";
    static final String DRAG = "Pressed, moved onto a picture or into a zone of the bot's Zones, and let go: a card "
            + "onto its slot, a slider, a swipe. A picture not on the screen leaves it unpressed";
    static final String TEXT_MARKER = "Words read inside a zone that tell the screen too, read only once its pictures "
            + "say it may be this one: a line that is the words, whatever its case, a misread letter forgiven in longer "
            + "words";
    static final String TEXT_BUTTON ="Clicked where its words are read inside its zone: a line that is the words, "
            + "whatever its case, a misread letter forgiven in longer words. Cut a zone around the words to make one";
    static final String SPOT = "A spot with no picture of its own, an avatar or a custom upload: pressed beside a "
            + "picture that always shows near it, at the same distance in that picture's sizes, and only when it is "
            + "found. Cells: the cut is a grid of that many equal cells, an inventory's slots";
    static final String READOUT = "Read on the screen picked, as a readout of the cut's name: a body reads it by "
            + "Navigate.read or Navigate.number while the game shows that screen. Names which one it shows: the "
            + "hero's name on a hero's page, read by Navigate.subject";
    static final String PLACE = "Found only where it sits from a picture: the Claim right of a row's title. The copy "
            + "nearest the picture that meets the side is pressed; none, and nothing is";
    static final String WAY_COPY ="The copy of this repeated button the way goes through";
    static final String TRY_HOW = "Tries it as try_input does: which copy, the name of the screen it opens, how long it "
            + "is given, and whether the game stays here after";
    static final String STRIP_MEMBERS = "Its buttons in the strip's order, each looked for anywhere along it and "
            + "scrolled to over another that shows";
    static final String STRIP_SCROLL = "How it is scrolled: the wheel turned some notches, or the pointer dragged; "
            + "none takes the strip off, its buttons looked for at their places";
    static final String MEASURE = "Scrolls it once as try_input does, and writes how far its buttons move and how "
            + "long they glide";
    static final String IN_STRIP = "Puts this button in a strip of the screen, or in a new one";
    static final String IN_TREE = "Puts this button in a tree of the screen, or in a new one, as one of its nodes";
    static final String TREE_NODES = "Its nodes where their offsets place them: drag one beside a node it is linked "
            + "to to place it there, click one to edit its button";
    static final String UNLOCKED_BY = "The nodes whose line leads to it: they unlock it";
    static final String PLACE_NODE = "Where it sits from a node it is linked to, in that node's widths and heights";
    static final String TREE_SCROLL = "How it is scrolled: its view dragged, the wheel turned some notches, or none "
            + "when it fits on the screen";
    static final String MEASURE_TREE = "Measures where each linked pair in view sits, moves the view once and "
            + "measures again, as try_input does over a node";

    /** One sign and what it means; {@code style} draws the sign as a pill of that class, null as plain text. */
    record Entry(String sign, String style, String meaning) {}

    /** A heading and its signs. */
    record Section(String title, List<Entry> entries) {}

    private static final List<Section> SECTIONS = List.of(
            new Section("The toolbar", List.of(
                    new Entry("Find a screen…", null, "Lists the screens whose name, constant, inputs, pictures or "
                            + "notes have the words; ↑ ↓ and Enter or a click zooms to one, Esc fits the map again."),
                    new Entry("Other ways", null, OTHER_WAYS + "."),
                    new Entry("⇄ All links", null, ALL_LINKS + "; this computer remembers which."),
                    new Entry("− Fit +", null, FIT + "; the wheel zooms about the pointer, a right drag pans."),
                    new Entry("↶ ↷", null, "Undo and redo, in Studio's own history (Ctrl+Z, Ctrl+Y): a map edit "
                            + "and a code edit are undone in the order they were made."),
                    new Entry("Tidy", null, TIDY + "."),
                    new Entry("▶▶ Next untried", null, NEXT_UNTRIED + "."),
                    new Entry("⤓ PNG", null, PNG + "."),
                    new Entry("☰ Outline ▤ Panel", null, "Show or hide the outline and the side panel; this "
                            + "computer remembers which."),
                    new Entry("⛶", null, FULL_SCREEN + ". ↶ ↷, Tidy, ⤓ PNG, ☰ ▤ and ⛶ are in the 🗺 Screens "
                            + "window, not the overlay's tab."))),
            new Section("The outline", List.of(
                    new Entry("Filter screens and inputs…", null, "Keeps the screens and inputs whose words have the "
                            + "text, and the lines down to them. Under each screen are its inputs, and under an "
                            + "input the screen it opens first; popups and screens not reached sit in groups below."),
                    new Entry("◻ ⌨ ☰ 🌳", null, "A button, a hotkey, a strip, a tree."),
                    new Entry("Owes", null, FILTER_OWES + "."),
                    new Entry("Untried", null, FILTER_UNTRIED + "."),
                    new Entry("⊟ Collapse all ⊞ Expand all", null, COLLAPSE_ALL + ", or open every "
                            + "line; a line shut stays shut as the map changes."),
                    new Entry("All inputs | Inputs that open a screen", null, OPENING_ONLY + "."),
                    new Entry("Recent", null, "The " + Outline.RECENT_COUNT + " screens whose shot was taken last, "
                            + "the newest first; a click picks the screen as its line below does."),
                    new Entry("a click", null, "Picks the screen and shows it big in the Screen tab; an input's line "
                            + "picks that input too; a right-click opens its ⋯ menu."))),
            new Section("The Screen tab", List.of(
                    new Entry("Graph | Screen", null, "The map, or the picked screen's shot as big as the tab."),
                    new Entry("a box", null, "Picks its input: the panel shows it first, with the screens it leads "
                            + "to (→ picks one) and Leads to…; ✕ picks none. Under it, what it does (information "
                            + "only, comes and goes, never, locked), a button's looks and copies, a hotkey's key, "
                            + "and ▶ Try: " + TRY_HOW + "."),
                    new Entry("Repeated", null, REPEATED + "; each way it leads by says its copy: " + WAY_COPY
                            + "."),
                    new Entry("Cycles", null, CYCLES + "."),
                    new Entry("Strip…", null, IN_STRIP + "."),
                    new Entry("Members", null, "A strip's: " + STRIP_MEMBERS + "."),
                    new Entry("Scroll", null, "A strip's: " + STRIP_SCROLL + ". ▶ Scroll and measure: " + MEASURE
                            + ". A tree's: " + TREE_SCROLL + ". ▶ Scroll and measure: " + MEASURE_TREE + "."),
                    new Entry("Tree…", null, IN_TREE + "."),
                    new Entry("Nodes", null, "A tree's: " + TREE_NODES + ". Unlocked by…: " + UNLOCKED_BY
                            + ". Place…: " + PLACE_NODE + "."),
                    new Entry("Leads to…", null, LEADS_TO + ", as try_input records an input that led to a screen "
                            + "mapped already."),
                    new Entry("Fit", null, "Draws the shot as wide as the tab; the wheel zooms, a drag moves it."),
                    new Entry("● Live", null, LIVE + ", as the click, key and scroll tools give it. The line under "
                            + "the bar names the screen it shows, by its markers, which the graph and the outline "
                            + "light; its inputs are boxed."),
                    new Entry("Record: A · input → B", null, RECORD_WAY + "."),
                    new Entry("❄ Freeze", null, FREEZE + ": Retake its shot — " + RETAKE_FROZEN + " — or New "
                            + "screen: " + NEW_FROM_FROZEN + ". A frame of no screen of the map offers New screen "
                            + "first: name it, then cut."),
                    new Entry("Missed frames", null, MISSED + ", to make it a screen's shot or a new screen."),
                    new Entry("✂ Cut", null, CUT_FROZEN + ". ▢ ⬭ ✎ pick the shape (✎: click its corners, then "
                            + "its first corner again or ✓ Close shape); ▨ Skip a part: " + CUT_IGNORE + "; a click "
                            + "on a cut (its number, for a free shape) picks it: a drag moves it, its corners resize "
                            + "it, Delete takes it off; ✓ Save: " + CUT_SAVE + ". A refused cut stays to fix."),
                    new Entry("▶ Launch", null, "Starts the game the bot launches, when it is not up."))),
            new Section("A card", List.of(
                    new Entry("3/5", null, "The ring: how many of the screen's inputs were tried, out of all."),
                    new Entry("owes its marker", "screen-pill-owes", "Nothing tells this screen yet: cut its marker, "
                            + "a part only it shows. \"nothing cut\": it has a marker but no input."),
                    new Entry("2 untried", "screen-pill-untried", "Inputs are left to try."),
                    new Entry("finished", "screen-pill-finished", "Every input was tried; \"not seen\" counts inputs "
                            + "that come and go and were not there."),
                    new Entry("list", "screen-pill-plain", "Its shape once finished, a popup, or a screen reached "
                            + "only at times or locked; such a card is drawn dashed."),
                    new Entry("Build", "screen-pill-stage", "The stage its notes say it reached."),
                    new Entry("🔒", null, LOCKED + "."),
                    new Entry("⊕", null, FOLDED + "."),
                    new Entry("thick outline", null, "The screen a run is on now; a thin one, the screens it came "
                            + "through. A pulsing card is being tried."),
                    new Entry("click", null, "Picks the screen and shows it big in the Screen tab, its panel on the "
                            + "right."),
                    new Entry("drag", null, "Moves a card; it keeps that place in the bot's Screens. Its right-click "
                            + "menu folds what is below it (in the overlay's pane, a double-click)."))),
            new Section("Arrows", List.of(
                    new Entry("solid", null, "How each screen was found: its parent's input that opens it."),
                    new Entry("dashed", null, "Another way into a screen, or a way back; Other ways hides them."),
                    new Entry("click", null, "An arrow shows the picture it is clicked by; a right-click says when "
                            + "its way is open."))),
            new Section("The panel", List.of(
                    new Entry("▶ Try", null, "Clicks one input on the game and records what it did."),
                    new Entry("▶ Try next", null, TRY_NEXT + "."),
                    new Entry("✂ Cut inputs", null, CUT_INPUTS + ", on the board: the frame frozen there, the game "
                            + "frozen now, or the screen's shot. Its ▾ cuts over the game window instead."),
                    new Entry("✂ Cut marker", null, CUT_MARKER + ". Markers lists them, and the pictures it never "
                            + "shows; ✕ takes one off."),
                    new Entry("＋ Marker from a picture…", null, ADD_MARKER + "."),
                    new Entry("parts", null, "Each part says under its title what it is for, and folds by its "
                            + "title; the parts an input's kind needs open at first, and each stays as you left it."),
                    new Entry("＋ Hotkey…", null, "In ⋯ Screen: " + ADD_HOTKEY + "."),
                    new Entry("＋ Strip… ＋ Tree…", null, "In ⋯ Screen: " + ADD_STRIP + "; a tree's nodes the same, "
                            + "linked and placed in its editor after."),
                    new Entry("Ways in", null, "Each screen and input it is reached from; its ⋯ says when the way is "
                            + "open, or takes it off. The first is how it was found. ＋ After a screen, on its "
                            + "own…: " + ON_ITS_OWN + "."),
                    new Entry("Looks", null, "The looks the screen has; each one's ⋯ names the method it shows only "
                            + "while."),
                    new Entry("Way back ✕", null, REMOVE_BACK + "; the input it was stays, untried."),
                    new Entry("🧭 Go here", null, GO_HERE + "."),
                    new Entry("⋯ Screen", null, "Rename; Popup, for a screen the game opens on its own over any "
                            + "other; Start screen, the one the game opens on (its loading screen); A panel of the "
                            + "screen its way in is on, shown inside it; The next screen shows within…, how long a "
                            + "walk waits on it for the screen after it; retake the "
                            + "shot; add pictures the bot has; a new screen from here; not a screen, when its way in "
                            + "only changes the screen above; delete; ＋ Hotkey, ＋ Strip, ＋ Tree. A card's "
                            + "right-click opens the same menu, with Fold."),
                    new Entry("the shot", null, "On the Screen tab, big: click a box to pick its "
                            + "input. A picture shows ← to go back to its screen. Each box is where its "
                            + "picture is on the shot; a dashed one is away from its picture's place (placed again "
                            + "while the game shows the screen, it gets a copy of its own there); one not on the shot "
                            + "is listed under it, and its row says not on shot."))),
            new Section("An input's ⋯", List.of(
                    new Entry("Result", null, "What it did: untried, tried (opening nothing), leaves for a screen "
                            + "reached otherwise, or opens a new one."),
                    new Entry("Information only", null, "It only tells: Discovery tries it last."),
                    new Entry("Comes and goes", null, "It is not always there: a try that does not see it is not a "
                            + "failure."),
                    new Entry("Only there while", null, "A method of the bot that says when it is there."),
                    new Entry("Never…", null, "Never clicked, and why: it spends gems or real money."),
                    new Entry("Locked by…", null, "A picture proving it locked, a padlock on it."),
                    new Entry("Use as the way back", null, "A walk leaves the screen by this input."),
                    new Entry("Remove from this screen", null, "Takes the input off the screen; its picture "
                            + "stays."))));

    private MapGuide() {}

    /** The legend's sections, in the order it shows them. */
    static List<Section> sections() {
        return SECTIONS;
    }

    /** Opens the legend beside {@code owner}. */
    static Stage open(StudioServices services, Window owner) {
        VBox all = new VBox(14);
        all.setPadding(new Insets(14));
        for (Section section : SECTIONS) {
            Label heading = new Label(section.title());
            heading.getStyleClass().add("screen-panel-heading");
            VBox lines = new VBox(6);
            for (Entry entry : section.entries()) {
                Label sign = new Label(entry.sign());
                if (entry.style() != null) sign.getStyleClass().addAll("screen-pill", entry.style());
                else sign.getStyleClass().add("screen-guide-sign");
                HBox signBox = new HBox(sign);
                signBox.setMinWidth(130);
                signBox.setPrefWidth(130);
                signBox.setAlignment(Pos.TOP_LEFT);
                Label meaning = new Label(entry.meaning());
                meaning.setWrapText(true);
                meaning.setMinHeight(Region.USE_PREF_SIZE);
                HBox.setHgrow(meaning, Priority.ALWAYS);
                lines.getChildren().add(new HBox(10, signBox, meaning));
            }
            all.getChildren().addAll(heading, lines);
        }
        ScrollPane scroller = new ScrollPane(all);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().addAll("screen-map", "screen-panel-scroller");
        scroller.getStylesheets().add(ScreenCanvas.SHEET);
        return Modals.window(services, owner, Modals.Frame.modeless("🗺 Screens guide", 560, 640, 380, 300),
                scroller);
    }
}
