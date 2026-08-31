package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.authoring.ActivityModel;
import com.botmaker.plugin.api.authoring.FlowEdgeModel;
import com.botmaker.plugin.api.authoring.FlowModel;
import com.botmaker.plugin.api.authoring.FlowNodeModel;
import com.botmaker.plugin.api.authoring.ProjectModel;
import com.botmaker.plugin.api.authoring.VariableModel;
import com.botmaker.plugin.api.value.Range;
import com.botmaker.plugin.api.value.ValueChoice;
import com.botmaker.plugin.api.value.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.util.List;
import java.util.Map;

/**
 * How Jackson binds the authoring model records, kept out of the records themselves.
 *
 * <p>The records live in {@code com.botmaker.plugin.api.authoring} — the plugin contract — and the contract
 * has exactly one dependency, {@code javafx-controls} at {@code provided}. Adding a library there imposes it
 * on every plugin that ever compiles against the contract, which is why the standing rule is that <b>the
 * contract declares the wire form and whoever owns the file supplies the parser</b>. The records carried
 * {@code @JsonCreator}, {@code @JsonProperty}, {@code @JsonIgnore} and {@code @JsonIgnoreProperties} for one
 * day in August 2026; this class is where those marks went.
 *
 * <p>A Jackson <em>mix-in</em> is a type whose annotations are applied to another type as if they had been
 * written on it. Nothing here is ever instantiated or called: the abstract methods exist only so that
 * Jackson can match them against the record's own by name and parameter types, and the static factories only
 * so that a {@code @JsonCreator} has somewhere to sit.
 *
 * <h2>What is <em>not</em> here</h2>
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} was on all seven records and has no mix-in, because
 * it says nothing a mapper cannot: {@code Authoring}'s already disables
 * {@link com.fasterxml.jackson.databind.DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES}, which is the
 * same statement made once instead of seven times. A reader that binds these records with a mapper of its
 * own must make that setting itself — an unknown key in {@code activities.json} is a field written by a
 * newer editor, and refusing it would stop the project opening.
 */
public final class AuthoringMixins {

    private AuthoringMixins() {}

    /** The mix-ins, as a module, so a mapper picks all of them up in one registration. */
    public static SimpleModule module() {
        SimpleModule m = new SimpleModule("botmaker-authoring");
        m.setMixInAnnotation(ProjectModel.class, ProjectMixin.class);
        m.setMixInAnnotation(ActivityModel.class, ActivityMixin.class);
        m.setMixInAnnotation(VariableModel.class, VariableMixin.class);
        m.setMixInAnnotation(FlowModel.class, FlowMixin.class);
        m.setMixInAnnotation(FlowEdgeModel.class, FlowEdgeMixin.class);
        return m;
    }

    /**
     * {@link ProjectModel}'s derived answers.
     *
     * <p>Every one of these is computed from the components and none of them is stored. {@code isEmpty()}
     * is the one that would actually break without a mark — Jackson reads an {@code isX()} method as the
     * getter of a property called {@code empty} — and the rest are marked for the same reason they were on
     * the record: a reader should not have to know which naming convention makes a method invisible.
     */
    abstract static class ProjectMixin {
        @JsonIgnore abstract boolean isEmpty();
        @JsonIgnore abstract List<ActivityModel> orderedActivities();
        @JsonIgnore abstract List<VariableModel> allVariables();
        @JsonIgnore abstract List<VariableModel> activityFlags();
        @JsonIgnore abstract Map<String, List<VariableModel>> sharedVariables();
        @JsonIgnore abstract List<VariableModel> variablesIn(String groupId);
        @JsonIgnore abstract List<String> variableGroups();
    }

    /** {@link ActivityModel}'s derived answers — the enable flag and the two port lists. */
    abstract static class ActivityMixin {
        @JsonIgnore abstract VariableModel enabledVariable();
        @JsonIgnore abstract List<String> allOutcomes();
        @JsonIgnore abstract List<String> flowPorts();
    }

    /**
     * {@link VariableModel}'s derived answers, and its creator.
     *
     * <p>The creator is not decoration: {@code fromWire} settles a shape a stored file cannot state — an
     * {@code ANY_OF} with no set behind it is an open list, not tick boxes over nothing — so binding
     * straight to the canonical constructor would open old projects wrong.
     */
    abstract static class VariableMixin {

        @JsonCreator
        static VariableModel fromWire(@JsonProperty("name") String name,
                                      @JsonProperty("type") ValueChoice type,
                                      @JsonProperty("value") List<String> value,
                                      @JsonProperty("description") String description,
                                      @JsonProperty("tag") String tag,
                                      @JsonProperty("visibility") Visibility visibility,
                                      @JsonProperty("options") List<String> options,
                                      @JsonProperty("bounds") Range bounds,
                                      @JsonProperty("group") String group) {
            throw new UnsupportedOperationException("mix-in");
        }

        @JsonIgnore abstract String singleValue();
        @JsonIgnore abstract boolean isPublic();
        @JsonIgnore abstract String tagOrGeneral();
        @JsonIgnore abstract String displayLabel();
        @JsonIgnore abstract boolean isIn(String groupId);
    }

    /**
     * {@link FlowModel}'s creator, and the reason it has one.
     *
     * <p>{@code stepDelayMs} is boxed so that a file written before the field existed reads as "take the
     * default" rather than as an explicit zero — which would silently turn every pre-existing flow into a
     * no-pause one. Jackson binds a missing {@code int} to 0 and cannot tell the two apart.
     */
    abstract static class FlowMixin {

        @JsonCreator
        static FlowModel fromWire(@JsonProperty("nodes") List<FlowNodeModel> nodes,
                                  @JsonProperty("edges") List<FlowEdgeModel> edges,
                                  @JsonProperty("start") String start,
                                  @JsonProperty("maxSteps") int maxSteps,
                                  @JsonProperty("stepDelayMs") Integer stepDelayMs) {
            throw new UnsupportedOperationException("mix-in");
        }

        @JsonIgnore abstract boolean isEmpty();
    }

    /** {@link FlowEdgeModel}'s two readings of a blank outcome. */
    abstract static class FlowEdgeMixin {
        @JsonIgnore abstract String outcomeOrNext();
        @JsonIgnore abstract boolean isNext();
    }
}
