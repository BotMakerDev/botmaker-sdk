package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.value.ValueForm;
import com.botmaker.sdk.authoring.ActivityModel;
import com.botmaker.sdk.authoring.FlowEdgeModel;
import com.botmaker.sdk.authoring.FlowModel;
import com.botmaker.sdk.authoring.FlowNodeModel;
import com.botmaker.sdk.authoring.ProjectModel;
import com.botmaker.sdk.authoring.VariableModel;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.util.List;
import java.util.Map;

/**
 * How Jackson binds the authoring model records, kept out of the records themselves.
 *
 * <p>The records live in {@link com.botmaker.sdk.authoring} and this class stays anyway, which is the part
 * worth knowing. They were in the plugin contract from 2026-08-31 to 2026-09-07, and the commit that put
 * them there added {@code jackson-annotations} to the contract's pom — a library in that pom is imposed on
 * every plugin that ever compiles against it, so the marks were pulled back out into this class within the
 * day. The records are the SDK's again and could carry the annotations now; keeping them here is the same
 * rule stated the other way round, that <b>a record describing a file's shape should not name the library
 * that happens to read it</b>. A second reader binding these records with its own mapper is still possible.
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
     * {@link VariableModel}'s derived answers, and the one component whose stored name is not its own.
     *
     * <p>The component is {@code form} and the file says {@code type}. The shape axis went on 2026-09-20 and
     * the files did not, so the name on disk stays what every project already wrote; naming it on the
     * accessor renames the property in both directions, which is what a record's implicit creator reads. It
     * replaced a {@code fromWire} creator that existed to settle a question the shapes asked and a
     * {@link ValueForm} does not — see {@code VariableModel}'s own note.
     */
    abstract static class VariableMixin {

        @JsonProperty("type") abstract ValueForm form();

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
