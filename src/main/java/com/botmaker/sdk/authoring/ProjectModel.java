package com.botmaker.sdk.authoring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Everything a bot project declares about itself: its activities, its variables, its flow, and the editor
 * chrome that hangs off them. This is {@code activities.json}, as a value.
 *
 * <h2>One file, one owner</h2>
 *
 * <p>Some of what is here the generator reads and emits from — the activities, the variables, the wiring.
 * The rest ({@link VariableModel#bounds()}, {@link VariableModel#visibility()}, {@link #presets()},
 * {@link FlowNodeModel#x()}) it stores and never interprets: it is an editor's business, not a bot's.
 *
 * <p>Splitting the file along that line was considered and rejected. Two files, each authoritative for half
 * of one model, is how two authors get created — and the whole point of the inversion is that the file
 * describing a bot has exactly one. So the SDK owns all of it, and the fields it does not understand travel
 * through it untouched.
 *
 * <h2>Plain data</h2>
 *
 * <p>Nothing here validates. A flow may name an activity that does not exist, a preset may list one that was
 * deleted, a variable's value may sit outside its own bounds. That is on purpose: a model is the file, and a
 * file can say anything. Refusing belongs where a user is watching — in an editor, or in the generator when
 * it is asked to emit something it genuinely cannot.
 *
 * @param activities      the activity definitions
 * @param variables       every configured value the bot reads, project-wide
 * @param flow            the wiring; empty means "no chosen chain, use declaration order"
 * @param presets         named on/off selections of activities, saved by the user
 * @param goHomeByDefault whether a newly added activity starts with {@link ActivityModel#goHome()} ticked;
 *                        boxed for the same reason as that field — absent must mean {@code true}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectModel(List<ActivityModel> activities, List<VariableModel> variables, FlowModel flow,
                           List<PresetModel> presets, Boolean goHomeByDefault) {

    /** The file this model is stored in, relative to the project's resources directory. */
    public static final String FILE_NAME = "activities.json";

    public ProjectModel {
        activities = activities == null ? List.of() : List.copyOf(activities);
        variables = variables == null ? List.of() : List.copyOf(variables);
        flow = flow == null ? FlowModel.empty() : flow;
        presets = presets == null ? List.of() : List.copyOf(presets);
        if (goHomeByDefault == null) goHomeByDefault = Boolean.TRUE;
    }

    /** Nothing declared — how a freshly created empty project reads. */
    public static ProjectModel empty() {
        return of(List.of(), List.of());
    }

    /**
     * A fresh model over {@code activities} and {@code variables}, with no flow and no presets.
     *
     * <p>For <em>building</em> one — a new project, a test. Never for editing an existing one: that is what
     * the {@code with…} methods are for. A static factory says "this is a new model" at the call site; a
     * two-argument constructor says nothing, and reads as "the model, with these two things changed" — which
     * is how a save came to drop the flow, the presets and every value at once.
     */
    public static ProjectModel of(List<ActivityModel> activities, List<VariableModel> variables) {
        return new ProjectModel(activities, variables, FlowModel.empty(), List.of(), Boolean.TRUE);
    }

    /** True when there is nothing to generate from. */
    @JsonIgnore
    public boolean isEmpty() {
        return activities.isEmpty() && variables.isEmpty();
    }

    public ProjectModel withActivities(List<ActivityModel> newActivities) {
        return new ProjectModel(newActivities, variables, flow, presets, goHomeByDefault);
    }

    public ProjectModel withVariables(List<VariableModel> newVariables) {
        return new ProjectModel(activities, newVariables, flow, presets, goHomeByDefault);
    }

    public ProjectModel withFlow(FlowModel newFlow) {
        return new ProjectModel(activities, variables, newFlow, presets, goHomeByDefault);
    }

    public ProjectModel withPresets(List<PresetModel> newPresets) {
        return new ProjectModel(activities, variables, flow, newPresets, goHomeByDefault);
    }

    public ProjectModel withGoHomeByDefault(boolean newDefault) {
        return new ProjectModel(activities, variables, flow, presets, newDefault);
    }

    /** A copy with each activity's enable flag set from {@code preset} (in it → on, else off). */
    public ProjectModel applyPreset(PresetModel preset) {
        return withActivities(activities.stream().map(a -> a.withEnabled(preset.enables(a.name()))).toList());
    }

    // ---- queries ----------------------------------------------------------------------------------------
    //
    // Derived answers, not stored ones. They live on the model because the generator and an editor both need
    // them and must not answer them differently: which activities a run reaches decides both what the
    // registry instantiates and what the canvas greys out, and which names are taken decides both what
    // compiles and what a dialog refuses.

    /**
     * The activities a run can actually reach — everything reachable from the {@link #flow()}'s start when
     * one is wired, else plain declaration order. Orphans (placed but unreachable) are excluded.
     *
     * <p>An orphan is excluded from the run order and is an activity in every other sense: it keeps its
     * enable flag ({@link #activityFlags()} spans orphans) and its stub, because wiring it up is one drag
     * away.
     */
    @JsonIgnore
    public List<ActivityModel> orderedActivities() {
        if (flow.isEmpty()) return activities;
        java.util.Map<String, ActivityModel> byName = new java.util.LinkedHashMap<>();
        for (ActivityModel a : activities) byName.put(a.name(), a);
        List<ActivityModel> ordered = new java.util.ArrayList<>();
        for (String name : flow.reachable(activities.stream().map(ActivityModel::name).toList())) {
            ActivityModel a = byName.get(name);
            if (a != null) ordered.add(a);
        }
        return ordered;
    }

    /**
     * Every referenceable generated field, in generation order: each activity's enable flag, then the
     * project's variables. The names here are exactly the generated field names — but <b>not</b> the class
     * they are declared on, which since the two files split is two answers and no longer one constant.
     */
    @JsonIgnore
    public List<VariableModel> allVariables() {
        List<VariableModel> all = new java.util.ArrayList<>(activityFlags());
        all.addAll(variables);
        return all;
    }

    /**
     * One {@code boolean} per activity — the fields the generated {@code Activities} holds, and the whole of
     * what it holds. Every activity, not only the reachable ones: an orphan keeps its flag, because its
     * stub's {@code isEnabled()} names the field either way.
     */
    @JsonIgnore
    public List<VariableModel> activityFlags() {
        return activities.stream().map(ActivityModel::enabledVariable).toList();
    }

    /**
     * The variables whoever runs the bot is offered, grouped under their tag headings and in declaration
     * order within each — so a runner shows "Mining" and "General" rather than one flat list.
     */
    @JsonIgnore
    public java.util.Map<String, List<VariableModel>> sharedVariables() {
        java.util.Map<String, List<VariableModel>> byTag = new java.util.LinkedHashMap<>();
        for (VariableModel v : variables) {
            if (v.isPublic()) byTag.computeIfAbsent(v.tagOrGeneral(), t -> new java.util.ArrayList<>()).add(v);
        }
        return byTag;
    }

    /**
     * Whether {@code name} is already taken, ignoring {@code except} (the element being renamed, or null).
     *
     * <p><b>One namespace, one check</b> — still one, now that the fields are declared on two classes. The
     * original reason was javac's: both became fields of {@code Activities}, so an activity called
     * {@code Mining} and a variable called {@code Mining} were one field declared twice. Split across
     * {@code Activities} and {@code Parameters} that particular collision compiles, and the check stays,
     * because a second reason took over: which class a <em>name</em> is a field of has no answer when the
     * name belongs to both, and the qualifier an editor writes into source would be a coin toss.
     *
     * <p>Case-insensitive, because the generated stubs are named after activities and a case-insensitive
     * filesystem cannot tell {@code Mining.java} from {@code mining.java}.
     */
    public boolean nameClash(String name, String except) {
        if (name == null || name.isBlank()) return false;
        String candidate = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (except != null && candidate.equals(except.trim().toLowerCase(java.util.Locale.ROOT))) return false;
        for (ActivityModel a : activities) {
            if (a.name().toLowerCase(java.util.Locale.ROOT).equals(candidate)) return true;
        }
        for (VariableModel v : variables) {
            if (v.name().toLowerCase(java.util.Locale.ROOT).equals(candidate)) return true;
        }
        return false;
    }
}
