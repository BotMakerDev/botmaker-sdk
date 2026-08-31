package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.ValueContext;
import com.botmaker.plugin.toolkit.Editors;
import com.botmaker.plugin.api.authoring.ActivityModel;
import com.botmaker.plugin.api.authoring.ProjectModel;
import com.botmaker.sdk.authoring.Authoring;
import com.botmaker.sdk.authoring.SdkVersion;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The two editors over the names that tie a bot's code to its Activity Flow canvas.
 *
 * <p>Both values are a {@code String}, and both name something the user drew somewhere else:
 * {@code Activities.define("Mining", …)} names an activity of the open project, and {@code ctx.outcome("BAG_FULL")}
 * names one of the outcomes declared on the canvas. Nothing about the type says either — which is what
 * {@link CallSites} is for — and typing them by hand is the one mistake the platform cannot catch for the
 * user: a name that matches nothing is not an error anywhere, it is an activity that never runs and an
 * outcome nothing is wired to.
 *
 * <p><b>The list is read from {@code activities.json}, not from a running bot.</b> The canvas is the source
 * of truth and it is a file, so {@link Authoring#readModel} is the whole implementation. It is read when the
 * dropdown is opened rather than when the block is drawn, so an activity added in the flow window a moment
 * ago is offered without reopening anything.
 *
 * <p><b>Both boxes stay typeable</b>, and that is deliberate rather than a concession. Writing the body
 * before drawing the activity is an ordinary way to work, and an editor that could only pick from what
 * already exists would make it unsayable.
 */
public final class ActivityEditors {

    private ActivityEditors() {}

    /** The activity named by {@code Activities.define("…", body)} — the project's activities, as drawn. */
    public static Node activityName(ValueContext ctx) {
        return Editors.choiceSlot(ctx, () -> activityNames(ctx), "Activity name");
    }

    /**
     * The outcome named by {@code ctx.outcome("…")} — every outcome the project declares.
     *
     * <p><b>Every one, not this activity's own</b>, and the difference is worth stating because the narrower
     * answer is the one a reader expects. An editor is told the call it sits in ({@code outcome}, on
     * {@code ActivityContext}) and no more: the {@code Activities.define("Mining", …)} it is nested inside is
     * two levels up in a syntax tree the plugin never sees. So the honest set is the union, offered with
     * duplicates collapsed. The cost is an outcome from a different activity appearing in the list; what it
     * buys is that the common case — the outcome the user just added on the canvas — is one click rather
     * than typed from memory, and a name typed anyway is still accepted.
     *
     * <p>{@code done()} is not in the list: it is the outcome every activity has without declaring one, and
     * it is spelled by calling that method rather than by naming it here.
     */
    public static Node outcomeName(ValueContext ctx) {
        return Editors.choiceSlot(ctx, () -> outcomeNames(ctx), "Outcome name");
    }

    private static List<String> activityNames(ValueContext ctx) {
        List<String> names = new ArrayList<>();
        for (ActivityModel activity : model(ctx).activities()) {
            if (activity.name() != null && !activity.name().isBlank()) names.add(activity.name());
        }
        return names;
    }

    private static List<String> outcomeNames(ValueContext ctx) {
        Set<String> names = new LinkedHashSet<>();
        for (ActivityModel activity : model(ctx).activities()) {
            for (String outcome : activity.outcomes()) {
                if (outcome != null && !outcome.isBlank()) names.add(outcome);
            }
        }
        return List.copyOf(names);
    }

    /**
     * The open project's model, or an empty one.
     *
     * <p>Rule 2 of the toolkit, applied to reading a file: a project with no {@code activities.json} yet, one
     * whose file cannot be parsed, and one whose directory the host could not name all have to produce a
     * dropdown with nothing in it rather than an editor that throws while it is being built.
     */
    private static ProjectModel model(ValueContext ctx) {
        try {
            return Authoring.readModel(SdkVersion.latest(), ctx.services().resourcesDir());
        } catch (Exception unreadable) {
            return ProjectModel.empty();
        }
    }
}
