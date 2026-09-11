package com.botmaker.sdk.internal.plugin.flow;

import com.botmaker.sdk.authoring.ActivityModel;
import com.botmaker.sdk.authoring.FlowEdgeModel;
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
 * the preset bar all bind to the same draft, so a change in one is visible in the others immediately. It is
 * converted back to an immutable {@link ActivityModel} plus a
 * {@link com.botmaker.sdk.authoring.FlowNodeModel} only on save.
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
     * {@link FlowEdgeModel#NEXT_OUTCOME}. Observable because the card grows one output port per outcome —
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
     * The activity's stable identity, carried through this draft untouched and never shown.
     *
     * <p><b>It is here because renaming happens here.</b> The name is a {@link StringProperty} the side panel
     * edits in place, so a draft that did not carry the id would hand back a model whose identity was the new
     * name — and anything reconciling activities by identity would see one deleted and another created.
     *
     * <p>Not a property, because nothing may observe it and nothing may bind to it. Blank means "this draft
     * was built before ids existed"; {@link ActivityModel} resolves that to the name.
     */
    private final String id;

    public ActivityDraft(String name, String description, boolean enabled, List<String> outcomes,
                         boolean goHome, boolean popupCheck, double x, double y) {
        this(name, description, enabled, outcomes, goHome, popupCheck, x, y, null);
    }

    public ActivityDraft(String name, String description, boolean enabled, List<String> outcomes,
                         boolean goHome, boolean popupCheck, double x, double y, String id) {
        this.id = id;
        this.name.set(name);
        this.description.set(description == null ? "" : description);
        this.enabled.set(enabled);
        this.outcomes.setAll(outcomes);
        this.goHome.set(goHome);
        this.popupCheck.set(popupCheck);
        this.x = x;
        this.y = y;
    }

    /** A draft of an existing activity, placed at {@code (x, y)} — carrying its id. */
    public static ActivityDraft of(ActivityModel model, double x, double y) {
        return new ActivityDraft(model.name(), model.description(), model.enabled(), model.outcomes(),
                model.goHome(), model.popupCheck(), x, y, model.id());
    }

    /**
     * The immutable model this draft currently describes, <b>with the identity it came in with</b>.
     *
     * <p>The last argument is the whole reason {@link #id} exists on this class. Rebuilding the model from
     * the draft's visible fields alone makes a rename indistinguishable from a delete plus a create, because
     * the name is the only identity left.
     */
    public ActivityModel toModel() {
        return new ActivityModel(name.get(), enabled.get(), description.get(), List.copyOf(outcomes),
                goHome.get(), popupCheck.get(), id);
    }

    /**
     * Every constant of this activity's generated {@code Outcome} enum: the implicit default first, then the
     * declared ones. Mirrors {@link ActivityModel#allOutcomes()}.
     */
    public List<String> allOutcomes() {
        List<String> all = new ArrayList<>(outcomes.size() + 1);
        all.add(FlowEdgeModel.NEXT_OUTCOME);
        for (String o : outcomes) {
            if (FlowEdgeModel.DISABLED_OUTCOME.equals(o)) continue; // a port, never an Outcome constant
            if (!all.contains(o)) all.add(o);
        }
        return all;
    }

    /**
     * Every outcome this activity's card has a port for: {@link #allOutcomes()}, then
     * {@link FlowEdgeModel#DISABLED_OUTCOME} last. Mirrors {@link ActivityModel#flowPorts()}.
     *
     * <p>This is the list the canvas draws ports from <em>and</em> the list it prunes wires against, which is
     * what stops a {@code DISABLED} wire from being deleted the moment it is drawn.
     */
    public List<String> flowPorts() {
        List<String> ports = new ArrayList<>(allOutcomes());
        ports.add(FlowEdgeModel.DISABLED_OUTCOME);
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

    public void moveTo(double newX, double newY) {
        this.x = newX;
        this.y = newY;
    }

    @Override
    public String toString() {
        return name.get();
    }
}
