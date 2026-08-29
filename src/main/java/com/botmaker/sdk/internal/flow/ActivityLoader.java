package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Activity;
import com.botmaker.sdk.api.util.Debug;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds one instance of each activity the project's own {@code activities.json} names.
 *
 * <p>It is what replaced the generated {@code ActivityRegistry} — a file holding one typed
 * {@code public static final Mining MINING = new Mining();} per activity, plus an {@code ALL} list over them,
 * rewritten on every change to the flow. Every line of it followed from the model, which is the rule for what
 * may not be generated: a file whose contents are a function of project data is data.
 *
 * <h2>How a name becomes a class</h2>
 *
 * <p>By convention, and deliberately by convention rather than by a manifest. A generated activity has always
 * been {@code <project package>.activities.<Name>} — that is where the editor writes the stub and where it
 * expects to find it again — so the package of the class that starts the bot plus the name in the file is
 * already enough. A manifest would be a second statement of the same fact, written by somebody, kept in step
 * by somebody, and wrong the first time it was not.
 *
 * <p>The anchor is a {@code Class<?>} rather than a package string because the bot's entry point is the one
 * thing that can name itself without knowing what it is called: {@code FlowGraph.run(Main.class, …)} keeps
 * working through a rename of the class, the package, or both.
 *
 * <h2>Why constructing them is the point</h2>
 *
 * <p>{@link Activity}'s constructor registers the instance by name, which is what makes
 * {@code Activity.disable("Mining")} resolve. The old registry's {@code ALL} field existed for exactly that
 * side effect. So this constructs every activity the file names, reachable from the start node or not — an
 * orphan that never runs can still be enabled by name from inside another activity's body.
 *
 * <h2>Failure</h2>
 *
 * <p>Best-effort, like everything else that reads a bot's own configuration. A name with no class behind it
 * — renamed by hand, deleted, never written — is one line on the console and one node the flow does not
 * have; the run continues without it. Refusing to start would turn a stale entry in a JSON file into a bot
 * that cannot run at all.
 */
public final class ActivityLoader {

    /** Where a generated activity's class sits, relative to the project's base package. */
    private static final String SUBPACKAGE = ".activities.";

    private ActivityLoader() {}

    /**
     * One instance per name that resolves, keyed by {@link Activity#name()}.
     *
     * @param anchor a class in the project's base package — the bot's entry point
     * @param names  the activity names the project's model declares, in file order
     */
    public static Map<String, Activity<?>> load(Class<?> anchor, List<String> names) {
        Map<String, Activity<?>> out = new LinkedHashMap<>();
        if (anchor == null || names == null) return Map.of();

        String basePackage = anchor.getPackageName();
        for (String name : names) {
            Activity<?> activity = construct(basePackage, name, anchor.getClassLoader());
            // Keyed by the activity's own name(), not by the name in the file: an activity that passed a
            // different name to Activity(String) registered itself under that one, and the flow's wires are
            // matched against the registry, never against the file.
            if (activity != null) out.put(activity.name(), activity);
        }
        return Map.copyOf(out);
    }

    private static Activity<?> construct(String basePackage, String name, ClassLoader loader) {
        if (name == null || name.isBlank()) return null;
        String fqn = basePackage.isEmpty() ? name : basePackage + SUBPACKAGE + name;
        try {
            Class<?> type = Class.forName(fqn, true, loader);
            if (!Activity.class.isAssignableFrom(type)) {
                Debug.error("[Flow] " + fqn + " is not an Activity — skipping '" + name + "'");
                return null;
            }
            return (Activity<?>) type.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            Debug.error("[Flow] no class " + fqn + " for activity '" + name
                    + "' — it is in this project's configuration but not in its source. Skipping it.");
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // A no-argument constructor that is absent, private, or throws. Naming the cause matters here:
            // a constructor that threw looks exactly like a missing file from the flow's point of view.
            Debug.error("[Flow] could not construct " + fqn + " (" + e + ") — skipping '" + name + "'");
            return null;
        }
    }
}
