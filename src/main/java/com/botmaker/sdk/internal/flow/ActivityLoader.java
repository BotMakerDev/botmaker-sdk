package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Activity;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.internal.bot.ActivityRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the body of every activity the project's own {@code activities.json} names.
 *
 * <p>It is what replaced the generated {@code ActivityRegistry} — a file holding one typed
 * {@code public static final Mining MINING = new Mining();} per activity, plus an {@code ALL} list over
 * them, rewritten on every change to the flow. Every line of it followed from the model, which is the rule
 * for what may not be generated: a file whose contents are a function of project data is data.
 *
 * <h2>Two ways an activity gets written, and the order they are looked for in</h2>
 *
 * <p><b>A definition first.</b> {@code Activities.define("Mining", ctx -> …)} registers the body under its
 * name, from the bot's own {@code main}, before the flow starts. So by the time this runs the answer is
 * usually already in {@link ActivityRegistry} and there is nothing to find.
 *
 * <p><b>Then a class, by convention.</b> A bot written before 2026-08-29 has a generated
 * {@code <project package>.activities.<Name>} extending {@link Activity}, and that keeps working: nothing
 * having registered the name, this constructs the class, whose constructor registers it. The anchor is a
 * {@code Class<?>} rather than a package string because the bot's entry point is the one thing that can name
 * itself without knowing what it is called — {@code FlowGraph.run(Main.class, …)} survives a rename of the
 * class, the package, or both. A manifest listing class names was refused for the usual reason: it is a
 * second statement of a fact the file already carries, kept in step by somebody, and wrong the first time it
 * is not.
 *
 * <h2>An activity nobody wrote is not an error</h2>
 *
 * <p>It is an activity that does nothing, which the flow already has a word for: it takes its
 * {@code DISABLED} wire, exactly as one switched off in the editor does. That is deliberate and it is what
 * makes drawing a flow before writing its code an ordinary way to work — every card is on the canvas, the
 * run walks through them, and the ones with no body yet fall through. One line on the console says so, once
 * per activity per load, because "my activity never runs" otherwise has no visible cause.
 */
public final class ActivityLoader {

    /** Where a pre-2026-08-29 generated activity's class sits, relative to the project's base package. */
    private static final String SUBPACKAGE = ".activities.";

    private ActivityLoader() {}

    /**
     * The body of each named activity that has one, keyed by name.
     *
     * @param anchor a class in the project's base package — the bot's entry point
     * @param names  the activity names the project's model declares, in file order
     */
    public static Map<String, ActivityRegistry.Runner> load(Class<?> anchor, List<String> names) {
        if (anchor == null || names == null) return Map.of();

        Map<String, ActivityRegistry.Runner> out = new LinkedHashMap<>();
        String basePackage = anchor.getPackageName();
        for (String name : names) {
            ActivityRegistry.Runner runner = ActivityRegistry.get(name);
            if (runner == null) runner = construct(basePackage, name, anchor.getClassLoader());
            if (runner != null) out.put(runner.name(), runner);
        }
        return Map.copyOf(out);
    }

    /**
     * Constructs the legacy activity class for {@code name} and returns what its constructor registered, or
     * {@code null} when there is no such class.
     *
     * <p>The return comes back out of the registry rather than being built from the instance, so there is
     * one place that decides how an {@link Activity} becomes something the flow can run. Keyed by the
     * activity's own {@code name()} and not by the name in the file: an activity that passed a different
     * name to {@code Activity(String)} registered itself under that one.
     */
    private static ActivityRegistry.Runner construct(String basePackage, String name, ClassLoader loader) {
        if (name == null || name.isBlank()) return null;
        String fqn = basePackage.isEmpty() ? name : basePackage + SUBPACKAGE + name;
        try {
            Class<?> type = Class.forName(fqn, true, loader);
            if (!Activity.class.isAssignableFrom(type)) {
                Debug.error("[Flow] " + fqn + " is not an Activity — skipping '" + name + "'");
                return null;
            }
            Activity<?> activity = (Activity<?>) type.getDeclaredConstructor().newInstance();
            return ActivityRegistry.get(activity.name());
        } catch (ClassNotFoundException e) {
            // The ordinary state of an activity drawn on the canvas whose body has not been written yet.
            // Not an error: the flow passes through it and takes its DISABLED wire.
            Debug.log("[Flow] '" + name + "' has no body — no Activities.define(\"" + name + "\", …) and no "
                    + fqn + ". It is on the canvas, so the flow takes its DISABLED wire.");
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // A no-argument constructor that is absent, private, or throws. Naming the cause matters here:
            // a constructor that threw looks exactly like a missing file from the flow's point of view.
            Debug.error("[Flow] could not construct " + fqn + " (" + e + ") — skipping '" + name + "'");
            return null;
        }
    }
}
