package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.config.ProjectDefaults;
import com.botmaker.shared.Diag;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * What the person running the bot chose for this computer: which of the bot's flows runs, and which of its
 * switchable activities are on.
 *
 * <p>Run properties, because they are facts about this computer rather than the bot: {@value #FLOW}{@code =DAILY}
 * runs {@code Flows.DAILY} instead of the flow {@code Sdk.run()} names, and {@value #ACTIVITY}{@code Rest=off}
 * switches {@code Rest} off where the flow has it {@code .switchable()}. Absent, the bot runs its default flow
 * with every activity as drawn. A switch on an activity the runner may not switch in this flow is ignored, and the
 * debug trace says so.
 */
public final class RunChoices {

    /** The run property naming the {@code Flows} constant to run. */
    public static final String FLOW = "botmaker.flow";

    /** The run properties switching an activity, each this prefix and the activity's enum name. */
    public static final String ACTIVITY = "botmaker.activity.";

    private static final String SOURCE = "Flow";

    private RunChoices() {}

    /** The run property switching {@code activity}: {@code botmaker.activity.Rest}. */
    public static String property(Activity activity) {
        return ACTIVITY + activity.name();
    }

    /**
     * {@code run} as this computer's run properties choose it: its flow, or the {@code Flows} constant they name,
     * with the runner's switches applied.
     *
     * @param values the bot's values classes, as {@code Bot.runFlow} takes them; its {@code Flows} class sits beside
     *               them
     */
    public static Run chosen(Run run, Class<?>... values) {
        return chosen(run, holder(values), System::getProperty);
    }

    /** {@link #chosen(Run, Class[])} over {@code flows}, the bot's {@code Flows} class or null, and {@code property}. */
    static Run chosen(Run run, Class<?> flows, UnaryOperator<String> property) {
        Flow flow = run.flow();
        String name = property.apply(FLOW);
        if (name != null && !name.isBlank()) {
            Flow picked = flow(flows, name.strip());
            if (picked == null) {
                Diag.warn(SOURCE, FLOW + " names " + name.strip() + ", which Flows does not have, so the flow "
                        + "Sdk.run() names runs.");
            } else {
                flow = picked;
                Debug.log("Runs Flows." + name.strip() + ", as this computer chose.");
            }
        }
        return new Run(switched(flow, property), run.home(), run.setup());
    }

    /** {@code flow} with each switchable activity on or off as {@code property} says. */
    static Flow switched(Flow flow, UnaryOperator<String> property) {
        List<Flow.Step> steps = new ArrayList<>();
        boolean changed = false;
        for (Flow.Step step : flow.steps()) {
            String key = property(step.activity());
            String said = property.apply(key);
            Boolean on = ProjectDefaults.parseBoolean(said);
            if (on != null && !step.runnerSwitch()) {
                // A debug line, not a warning: a switch chosen for another flow, where the activity may be switched,
                // is kept for when that flow runs again.
                Debug.log(key + "=" + said.strip() + " is ignored: in this flow " + step.label() + " is not an "
                        + "activity the runner may switch. Tick \"Runner may switch this off\" on its card to allow it.");
            } else if (on != null && on != step.enabled()) {
                Debug.log(step.label() + " is " + (on ? "on" : "off") + ", as this computer chose.");
                step = new Flow.Step(step.activity(), step.body(), step.description(), on, step.goHome(),
                        step.popupCheck(), step.runnerSwitch(), step.whenOff());
                changed = true;
            }
            steps.add(step);
        }
        return changed ? new Flow(steps, flow.edges(), flow.start(), flow.limits()) : flow;
    }

    /**
     * The bot's {@code Flows} class: the one marked {@code @SdkValue(FLOWS)} in the package of one of
     * {@code values}, where Studio writes it beside {@code Sdk.java}. Null when there is none.
     */
    static Class<?> holder(Class<?>... values) {
        if (values == null) return null;
        for (Class<?> type : values) {
            if (type == null) continue;
            try {
                Class<?> found = Class.forName(type.getPackageName() + "." + SdkValues.FLOWS.holder(), true,
                        type.getClassLoader());
                SdkValue mark = found.getAnnotation(SdkValue.class);
                if (mark != null && mark.value() == SdkValue.Id.FLOWS) return found;
            } catch (ClassNotFoundException | LinkageError absent) {
                // No Flows beside this one: look beside the next.
            }
        }
        return null;
    }

    /** The {@code public static} {@link Flow} constant {@code name} of {@code flows}, or null. */
    static Flow flow(Class<?> flows, String name) {
        if (flows == null) return null;
        try {
            Field field = flows.getField(name);
            if (!Modifier.isStatic(field.getModifiers()) || !Flow.class.equals(field.getType())) return null;
            return (Flow) field.get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
            return null;
        }
    }
}
