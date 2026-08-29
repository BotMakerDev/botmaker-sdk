package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.util.Debug;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A running bot's own {@code activities.json}, read off the classpath.
 *
 * <p>The sibling of {@link ProjectDefaults}, which does the same job for {@code botmaker-project.properties},
 * and it keeps that class's discipline exactly: a missing file, a missing key and a value that will not parse
 * are all ordinary states with an answer, never an exception. <b>A bot does not fail to start because of its
 * own configuration file</b> — the rule the generated {@code Activities} class used to state in its javadoc,
 * and which has to survive the move from generated fields to a runtime read or the move is not safe to make.
 *
 * <h2>Why this reads a tree and not the authoring model</h2>
 *
 * <p>{@code com.botmaker.sdk.authoring} has records for all of this already, and they are unreachable from
 * here: {@code VariableModel} names {@code ValueChoice}, {@code Range}, {@code Visibility} and
 * {@code ParameterGroup} in its own components, and {@code botmaker-studio-api} is an {@code optional}
 * dependency — deliberately not on a generated bot's classpath. Loading one of those records in a bot is
 * {@code NoClassDefFoundError}. So this walks the JSON as a tree, over field names that are the record
 * components' own names and change only when they do.
 *
 * <p><b>What is not duplicated is the part that would actually hurt.</b> Turning stored text into a typed
 * value stays with {@code WireText}, which every codec in {@code SdkValueTypes} already parses through and
 * which names no contract type. So the editor and the bot agree about what {@code "3s500ms"} means by
 * construction; what is written twice is only the walk to find it.
 *
 * <h2>Read once</h2>
 *
 * <p>{@link #current()} parses on first use and holds the result for the life of the process, because a bot's
 * configuration cannot change while it runs — the editor writes the file and the bot is restarted. Tests and
 * the flow loader use {@link #of} instead, which parses whatever it is handed and caches nothing.
 */
public final class ProjectData {

    /** Where a project's model lives on a bot's classpath — {@code src/main/resources/activities.json}. */
    public static final String RESOURCE = "/activities.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ProjectData EMPTY = new ProjectData(MAPPER.createObjectNode());

    private static ProjectData current;

    private final JsonNode root;

    private ProjectData(JsonNode root) {
        this.root = root;
    }

    // ---- loading ----------------------------------------------------------------------------------------

    /** This bot's own model, parsed once. Never {@code null}, and empty when there is nothing to read. */
    public static synchronized ProjectData current() {
        if (current == null) current = load(RESOURCE);
        return current;
    }

    /** Test seam: forget the cached model so the next {@link #current()} reads again. */
    static synchronized void forget() {
        current = null;
    }

    /**
     * The model at {@code resource} on the classpath, or an empty one.
     *
     * <p>The two failures are told apart on purpose. A <b>missing</b> resource is silent: an empty project
     * has no model, and a bot that has never had an activity added is not misconfigured. A resource that
     * exists and will not parse says so once, because that is a real mistake somebody can act on — and it
     * still yields an empty model rather than throwing, since a bot that refuses to start tells its user far
     * less than one that starts and reports empty configuration.
     */
    public static ProjectData load(String resource) {
        try (InputStream in = ProjectData.class.getResourceAsStream(resource)) {
            if (in == null) return EMPTY;
            return new ProjectData(MAPPER.readTree(in));
        } catch (Exception e) {
            Debug.error("[config] " + resource + " could not be read (" + e.getMessage()
                    + "); running with no configuration");
            return EMPTY;
        }
    }

    /** The model in {@code json}, or an empty one — the seam a test and the flow loader read through. */
    public static ProjectData of(String json) {
        if (json == null || json.isBlank()) return EMPTY;
        try {
            return new ProjectData(MAPPER.readTree(json));
        } catch (Exception e) {
            Debug.error("[config] model could not be parsed (" + e.getMessage() + ")");
            return EMPTY;
        }
    }

    /** A model with nothing in it — every lookup below answers its own fallback. */
    public static ProjectData empty() {
        return EMPTY;
    }

    // ---- activities -------------------------------------------------------------------------------------

    /**
     * Whether the named activity is switched on, defaulting to {@code false}.
     *
     * <p>{@code false} rather than {@code true}: an activity nothing knows about should not run. The
     * generated {@code Activities} class defaulted the same way and for the same reason.
     */
    public boolean enabled(String activity) {
        return activityNamed(activity).path("enabled").asBoolean(false);
    }

    /** The outcomes the named activity declares, without the implicit one. Empty when it has none. */
    public List<String> outcomes(String activity) {
        return strings(activityNamed(activity).path("outcomes"));
    }

    /** Every activity's name, in the order the file lists them. */
    public List<String> activities() {
        List<String> names = new ArrayList<>();
        for (JsonNode activity : root.path("activities")) {
            String name = activity.path("name").asText("");
            if (!name.isEmpty()) names.add(name);
        }
        return List.copyOf(names);
    }

    /** Whether the named activity goes home before running, and whether it checks for popups first. */
    public boolean goHome(String activity) {
        return activityNamed(activity).path("goHome").asBoolean(false);
    }

    public boolean popupCheck(String activity) {
        return activityNamed(activity).path("popupCheck").asBoolean(false);
    }

    private JsonNode activityNamed(String activity) {
        if (activity == null) return MAPPER.missingNode();
        for (JsonNode candidate : root.path("activities")) {
            if (activity.equals(candidate.path("name").asText(null))) return candidate;
        }
        return MAPPER.missingNode();
    }

    // ---- variables --------------------------------------------------------------------------------------

    /**
     * The stored text of the named variable's first value, or {@code ""}.
     *
     * <p>Stored <em>text</em>, because that is what the file holds and what {@code WireText} takes. Every
     * typed reader in {@code com.botmaker.sdk.api.config.Wire} is this plus one call.
     */
    public String value(String variable) {
        List<String> values = values(variable);
        return values.isEmpty() ? "" : values.get(0);
    }

    /** Every stored value of the named variable — one for a plain value, several for a list. */
    public List<String> values(String variable) {
        if (variable == null) return List.of();
        for (JsonNode candidate : root.path("variables")) {
            if (variable.equals(candidate.path("name").asText(null))) {
                return strings(candidate.path("value"));
            }
        }
        return List.of();
    }

    /** Whether the file declares a variable by this name at all — the question {@code ""} cannot answer. */
    public boolean declares(String variable) {
        if (variable == null) return false;
        for (JsonNode candidate : root.path("variables")) {
            if (variable.equals(candidate.path("name").asText(null))) return true;
        }
        return false;
    }

    /** Every variable's name, in the order the file lists them. */
    public List<String> variables() {
        List<String> names = new ArrayList<>();
        for (JsonNode variable : root.path("variables")) {
            String name = variable.path("name").asText("");
            if (!name.isEmpty()) names.add(name);
        }
        return List.copyOf(names);
    }

    // ---- flow -------------------------------------------------------------------------------------------

    /** The drawn flow, as the file holds it — read by the flow loader, which owns what it means. */
    public JsonNode flow() {
        return root.path("flow");
    }

    /** Whether this model holds nothing at all. */
    public boolean isEmpty() {
        return activities().isEmpty() && variables().isEmpty();
    }

    private static List<String> strings(JsonNode array) {
        if (array == null || !array.isArray()) return List.of();
        List<String> out = new ArrayList<>(array.size());
        for (JsonNode element : array) out.add(element.asText(""));
        return List.copyOf(out);
    }
}
