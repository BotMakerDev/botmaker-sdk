package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Modals;
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
    static final String FOLDED = "Folded: double-click to unfold";

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
    static final String RETAKE_FROZEN = "This frame as the screen's shot, while its markers show on it";
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
    static final String ADD_TREE = "A new tree of the screen's buttons in no strip or tree, ticked in order, and how "
            + "it scrolls";
    static final String REPEATED = "The screen shows it more than once: a way and a run pick the copy they press";
    static final String CYCLES = "Each press shows its next look, the usual one first again after the last";
    static final String GESTURE = "How a run presses it: a click, a double or right click, a hold of the time beside "
            + "it, or the pointer only moved onto it for what opens on hover";
    static final String KEY_HOLD = "How long the keys stay down; 0 taps them";
    static final String WAY_COPY = "The copy of this repeated button the way goes through";
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
                    new Entry("− Fit +", null, FIT + "; the wheel zooms about the pointer, a right drag pans."),
                    new Entry("↶ ↷", null, "Undo and redo, in Studio's own history (Ctrl+Z, Ctrl+Y): a map edit "
                            + "and a code edit are undone in the order they were made."),
                    new Entry("Tidy", null, TIDY + "."),
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
                    new Entry("drag", null, "Moves a card; it keeps that place in the bot's Screens. Double-click "
                            + "folds what is below it."))),
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
                    new Entry("＋ Hotkey…", null, ADD_HOTKEY + "."),
                    new Entry("＋ Strip… ＋ Tree…", null, ADD_STRIP + "; a tree's nodes the same, linked and placed in "
                            + "its editor after."),
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
                            + "only changes the screen above; delete. A card's right-click opens the same menu."),
                    new Entry("the shot", null, "Click it to see it big in the Screen tab; click a box to pick its "
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
