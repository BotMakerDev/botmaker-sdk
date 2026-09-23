package com.botmaker.sdk.plugin.flow;

import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.plugin.types.FlowTypes;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;

/**
 * One activity while it is being edited on the Activity Flow canvas: its name, description and outcomes,
 * whether it is enabled, and where its card sits. Mutable and observable — the node card, the side panel and
 * the preset bar all bind to the same draft, so a change in one is visible in the others immediately. On
 * save it splits in two: a {@link Flow.Activity} written into the bot's own Java, and a position written
 * into the gitignored {@link FlowLayout} sidecar.
 *
 * <p><b>It carries no parameters.</b> The Studio class this was ported from held an observable list of the
 * activity's variables, left over from the day values were edited in this dialog's side panel; nothing has
 * read it since that panel became a link to the Parameters window. Dropping it is what leaves this editor
 * with no value vocabulary at all — the whole reason the flow is the one window that moved.
 */
public final class ActivityDraft {

    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty description = new SimpleStringProperty("");
    private final BooleanProperty enabled = new SimpleBooleanProperty();

    /**
     * The named outcomes this activity can report, excluding the implicit
     * {@link Flow.Edge#NEXT}. Observable because the card grows one output port per outcome —
     * adding one in the side panel has to put a port on the card immediately, or there is nothing to drag a
     * wire from.
     */
    private final ObservableList<String> outcomes = FXCollections.observableArrayList();

    /** Run the project's {@code GoHome.run()} before this activity. On by default; see the card's tick. */
    private final BooleanProperty goHome = new SimpleBooleanProperty(true);

    /** Let the popup guard dismiss popups during this activity. On by default; see the card's tick. */
    private final BooleanProperty popupCheck = new SimpleBooleanProperty(true);

    private double x;
    private double y;

    /**
     * The activity's work, as the method reference it is written as — {@code Collect::body}.
     *
     * <p><b>This is the draft's identity, and it replaced a generated id on 2026-09-20.</b> While a flow was
     * JSON, an activity needed a stable key of its own so that renaming the card on the canvas did not read
     * as a delete plus a create; the key was a string nothing else in the project mentioned. The work is now
     * named by a method reference javac resolves, so the identity is the same token that links the card to
     * the code — one fact, checked by the compiler, instead of two kept in step by hand.
     *
     * <p>Blank for a card added on the canvas whose method has not been written yet, which is an ordinary
     * way to work: it is written as {@code ActivityBody.NONE}, the flow walks through the card, and the card
     * does nothing until the "Runs" box names a method.
     *
     * <p>Not a property, because nothing may observe it and nothing may bind to it.
     */
    private String body;

    public ActivityDraft(String name, String description, boolean enabled, List<String> outcomes,
                         boolean goHome, boolean popupCheck, double x, double y) {
        this(name, description, enabled, outcomes, goHome, popupCheck, x, y, "");
    }

    public ActivityDraft(String name, String description, boolean enabled, List<String> outcomes,
                         boolean goHome, boolean popupCheck, double x, double y, String body) {
        this.body = body == null ? "" : body;
        this.name.set(name);
        this.description.set(description == null ? "" : description);
        this.enabled.set(enabled);
        this.outcomes.setAll(outcomes);
        this.goHome.set(goHome);
        this.popupCheck.set(popupCheck);
        this.x = x;
        this.y = y;
    }

    /** A draft of an activity of the stored flow, placed at {@code (x, y)} — carrying its body reference. */
    public static ActivityDraft of(Flow.Activity activity, double x, double y) {
        return new ActivityDraft(activity.name(), activity.description(), activity.enabled(),
                activity.outcomes(), activity.goHome(), activity.popupCheck(), x, y,
                FlowTypes.sourceOf(activity.body()));
    }

    /**
     * The value this draft currently describes, <b>with the body reference it came in with</b>.
     *
     * <p>Carrying the body through is the whole reason {@link #body} is a field. Rebuilding the activity
     * from the draft's visible fields alone would hand back one whose work was blank, which is how a rename
     * on the canvas would come to unwire a card from the code behind it.
     */
    public Flow.Activity toActivity() {
        return new Flow.Activity(FlowTypes.body(body), name.get(), description.get(), enabled.get(),
                goHome.get(), popupCheck.get(), List.copyOf(outcomes));
    }

    /**
     * Every outcome this activity can report: the implicit default first, then the declared ones.
     */
    public List<String> allOutcomes() {
        List<String> all = new ArrayList<>(outcomes.size() + 1);
        all.add(Flow.Edge.NEXT);
        for (String o : outcomes) {
            if (Flow.Edge.DISABLED.equals(o)) continue; // a port, never an Outcome constant
            if (!all.contains(o)) all.add(o);
        }
        return all;
    }

    /**
     * Every outcome this activity's card has a port for: {@link #allOutcomes()}, then
     * {@link Flow.Edge#DISABLED} last.
     *
     * <p>This is the list the canvas draws ports from <em>and</em> the list it prunes wires against, which is
     * what stops a {@code DISABLED} wire from being deleted the moment it is drawn.
     */
    public List<String> flowPorts() {
        List<String> ports = new ArrayList<>(allOutcomes());
        ports.add(Flow.Edge.DISABLED);
        return ports;
    }

    public StringProperty nameProperty() { return name; }
    public StringProperty descriptionProperty() { return description; }
    public BooleanProperty enabledProperty() { return enabled; }
    public BooleanProperty goHomeProperty() { return goHome; }

    public BooleanProperty popupCheckProperty() { return popupCheck; }
    public ObservableList<String> outcomes() { return outcomes; }

    public String name() { return name.get(); }
    public String description() { return description.get(); }
    public boolean enabled() { return enabled.get(); }
    public boolean goHome() { return goHome.get(); }

    public boolean popupCheck() { return popupCheck.get(); }

    public double x() { return x; }
    public double y() { return y; }

    /** The method reference this activity's work is written as, or {@code ""} when none is named yet. */
    public String body() { return body; }

    /** Names the method this activity's work is written as — {@code Collect::body}. */
    public void setBody(String reference) { this.body = reference == null ? "" : reference; }

    public void moveTo(double newX, double newY) {
        this.x = newX;
        this.y = newY;
    }

    @Override
    public String toString() {
        return name.get();
    }
}
