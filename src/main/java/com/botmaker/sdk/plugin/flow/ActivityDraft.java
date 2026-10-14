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
 * whether it is enabled, and where its card sits. Mutable and observable — the node card and the side panel
 * both bind to the same draft, so a change in one is visible in the other immediately. On save it splits in
 * two: a {@link Flow.Step} for its {@code Flows} constant, and a position for {@code Sdk.flowLayout()}.
 *
 * <p>A sub-flow card is a draft too, named by its label ({@code ▸ DAILY}, {@link #isSubflow}): its outcomes are
 * the exits of the flow it runs, read when the canvas opens, and it has no body (2026-10-12).
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
     * The outcomes this activity can report — its enum's constants, as labels, {@link Arrow#NEXT} among them
     * only while the enum holds it. Observable because the card grows one output port per outcome —
     * adding one in the side panel has to put a port on the card immediately, or there is nothing to drag a
     * wire from.
     */
    private final ObservableList<String> outcomes = FXCollections.observableArrayList();

    /** Run the bot's home method ({@code Run.home}) before this activity. On by default; see the card's tick. */
    private final BooleanProperty goHome = new SimpleBooleanProperty(true);

    /** Let the person running the bot switch this activity off for their machine ({@code .switchable()}). Off by default. */
    private final BooleanProperty runnerSwitch = new SimpleBooleanProperty(false);

    private double x;
    private double y;

    /**
     * The activity's work, as the method reference it is written as — {@code Collect::body}.
     *
     * <p><b>This is the draft's identity.</b> Renaming the card on the canvas must not read as a delete plus
     * a create, and the method reference javac resolves is the same token that links the card to the code —
     * one fact, checked by the compiler, instead of a second key kept in step by hand.
     *
     * <p>Blank for a card added on the canvas whose method has not been written yet, which is an ordinary
     * way to work: it is written as {@code ActivityBody.none()}, the flow walks through the card, and the card
     * does nothing until the "Runs" box names a method.
     *
     * <p>Not a property, because nothing may observe it and nothing may bind to it.
     */
    private String body;

    private List<String> brokenExits = List.of();

    public ActivityDraft(String name, String description, boolean enabled, List<String> outcomes,
                         boolean goHome, double x, double y) {
        this(name, description, enabled, outcomes, goHome, x, y, "");
    }

    public ActivityDraft(String name, String description, boolean enabled, List<String> outcomes,
                         boolean goHome, double x, double y, String body) {
        this.body = body == null ? "" : body;
        this.name.set(name);
        this.description.set(description == null ? "" : description);
        this.enabled.set(enabled);
        this.outcomes.setAll(outcomes);
        this.goHome.set(goHome);
        this.x = x;
        this.y = y;
    }

    /**
     * A draft of an activity of the stored flow, placed at {@code (x, y)} — carrying its body reference.
     * {@code exits} are its enum's outcomes ({@link Board#exitsOf}).
     */
    public static ActivityDraft of(Flow.Step step, List<String> exits, double x, double y) {
        ActivityDraft draft = new ActivityDraft(step.label(), step.description(), step.enabled(), exits,
                step.goHome(), x, y, FlowTypes.sourceOf(step.body()));
        draft.runnerSwitch.set(step.runnerSwitch());
        return draft;
    }

    /**
     * The value this draft currently describes, <b>with the body reference it came in with</b>, going to
     * {@code whenOff} when switched off: the canvas's {@link Arrow#DISABLED} arrow is the caller's to read. A
     * sub-flow card has no body.
     *
     * <p>Carrying the body through is the whole reason {@link #body} is a field. Rebuilding the activity
     * from the draft's visible fields alone would hand back one whose work was blank, which is how a rename
     * on the canvas would come to unwire a card from the code behind it.
     */
    public Flow.Step toStep(Flow.Node whenOff) {
        return new Flow.Step(FlowNames.node(name.get()), isSubflow() ? null : FlowTypes.body(body), description.get(),
                enabled.get(), goHome.get(), runnerSwitch.get(), whenOff);
    }

    /** The flow this card runs, {@code DAILY}, when it is a sub-flow card ({@code ▸ DAILY}); else null. */
    public String subflow() {
        return FlowNames.subflowOf(name.get());
    }

    /** Whether this is a sub-flow card: its ports are its flow's exits, read off that flow, never typed. */
    public boolean isSubflow() {
        return subflow() != null;
    }

    /**
     * The ports of a sub-flow card that its flow no longer ends on, each still left by a wire — drawn broken
     * until the wire is moved or removed. Empty for an activity.
     */
    public List<String> brokenExits() {
        return brokenExits;
    }

    /** Marks {@code exits} as {@link #brokenExits}; they stay among the card's outcomes, so their wires do. */
    public void setBrokenExits(List<String> exits) {
        this.brokenExits = exits == null ? List.of() : List.copyOf(exits);
    }

    /** Every outcome this activity can report, in its enum's order, each once. */
    public List<String> allOutcomes() {
        List<String> all = new ArrayList<>(outcomes.size());
        for (String o : outcomes) {
            if (Arrow.DISABLED.equals(o)) continue; // a port, never an Outcome constant
            if (!all.contains(o)) all.add(o);
        }
        return all;
    }

    /**
     * Every outcome this activity's card has a port for: {@link #allOutcomes()}, then
     * {@link Arrow#DISABLED} last.
     *
     * <p>This is the list the canvas draws ports from <em>and</em> the list it prunes wires against, which is
     * what stops a {@code DISABLED} wire from being deleted the moment it is drawn.
     */
    public List<String> flowPorts() {
        List<String> ports = new ArrayList<>(allOutcomes());
        ports.add(Arrow.DISABLED);
        return ports;
    }

    public StringProperty nameProperty() { return name; }
    public StringProperty descriptionProperty() { return description; }
    public BooleanProperty enabledProperty() { return enabled; }
    public BooleanProperty goHomeProperty() { return goHome; }
    public BooleanProperty runnerSwitchProperty() { return runnerSwitch; }
    public ObservableList<String> outcomes() { return outcomes; }

    public String name() { return name.get(); }
    public String description() { return description.get(); }
    public boolean enabled() { return enabled.get(); }
    public boolean goHome() { return goHome.get(); }

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
