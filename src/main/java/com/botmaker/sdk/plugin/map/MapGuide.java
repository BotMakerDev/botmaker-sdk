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

    // --- a card's badges ---
    static final String LOCKED = "Every way in is locked on this account";
    static final String FOLDED = "Folded: double-click to unfold";

    // --- the side panel ---
    static final String TRY_NEXT = "Try the next untried input, as the assistant's try_input does";
    static final String CUT_INPUTS = "Draw boxes around more of its buttons: each is noted untried";
    static final String CUT_MARKER = "Draw a box around a part only this screen shows";
    static final String GO_HERE = "Walk the game here by the map, as Navigate.to does in a run";

    /** One sign and what it means; {@code style} draws the sign as a pill of that class, null as plain text. */
    record Entry(String sign, String style, String meaning) {}

    /** A heading and its signs. */
    record Section(String title, List<Entry> entries) {}

    private static final List<Section> SECTIONS = List.of(
            new Section("The toolbar", List.of(
                    new Entry("Find a screen…", null, "Picks the first screen whose name or constant has the words."),
                    new Entry("Other ways", null, OTHER_WAYS + "."),
                    new Entry("− Fit +", null, FIT + "; the wheel zooms about the pointer, a right drag pans."),
                    new Entry("↶ ↷", null, "Undo and redo, in Studio's own history (Ctrl+Z, Ctrl+Y): a map edit "
                            + "and a code edit are undone in the order they were made."),
                    new Entry("Tidy", null, TIDY + "."),
                    new Entry("⤓ PNG", null, PNG + ". ↶ ↷, Tidy and ⤓ PNG are in the 🗺 Screens window, not "
                            + "the overlay's tab."))),
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
                    new Entry("✓2", null, "Inputs that are chores: work the bot does there, not a way on."),
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
                    new Entry("✂ Cut inputs", null, CUT_INPUTS + "."),
                    new Entry("✂ Cut marker", null, CUT_MARKER + "."),
                    new Entry("🧭 Go here", null, GO_HERE + "."),
                    new Entry("⋯ Screen", null, "Rename; Popup, for a screen the game opens on its own; retake the "
                            + "shot; add pictures the bot has; a new screen from here; not a screen, when its way in "
                            + "only changes the screen above; delete. A card's right-click opens the same menu."),
                    new Entry("the shot", null, "Click it to see it big; click a box for what that input opens, "
                            + "else its picture. A picture shows ← to go back to its screen. Each box is where its "
                            + "picture is on the shot; a dashed one is away from its picture's place (placed again "
                            + "while the game shows the screen, it gets a copy of its own there); one not on the shot "
                            + "is listed under it, and its row says not on shot."))),
            new Section("An input's ⋯", List.of(
                    new Entry("Result", null, "What it did: untried, changes the screen in place, no visible effect, "
                            + "leaves the screen, or opens a new one."),
                    new Entry("Kind", null, "What it is for, a chore or a way on."),
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
