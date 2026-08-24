package com.botmaker.sdk.internal.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The values a bot's Parameters screen stored, read once from {@code /activities.json} on the classpath.
 *
 * <p>Studio used to write this loader into every generated {@code Activities} class as a text block — the
 * same twenty lines of Jackson in every project, differing in nothing. It lives here for the same reason the
 * flow walk does: it is not a fact about anybody's bot.
 *
 * <h2>Everything is a list of strings</h2>
 *
 * <p>Whatever its type, a value is stored as a <b>list of strings</b> — one entry for an ordinary variable,
 * one per item for a {@code List of …} one — so the file has one reader and one writer rather than a case per
 * type. Turning that text back into a typed value is {@link com.botmaker.sdk.api.config.Wire}'s job; this
 * class only answers what the file said.
 *
 * <p>An activity's on/off tick is stored the same way, as the one-entry list {@code ["true"]}, which is why
 * the two sections of the file collapse into one map here.
 *
 * <h2>It never fails</h2>
 *
 * <p>A missing file, unreadable JSON or a missing key all yield "nothing stored", and the caller falls back
 * to the type's default. <b>A bot never fails to start because of its own configuration file</b> — the user
 * who broke it is not at a debugger, they are looking at a bot that will not run.
 */
public final class ConfigStore {

    /** Where Studio writes the file. Studio's own {@code ActivitiesConfig.FILE_NAME} names the same one. */
    public static final String RESOURCE = "/activities.json";

    private static final Map<String, List<String>> VALUES = load(RESOURCE);

    private ConfigStore() {}

    /** The first entry stored under {@code name}, or {@code ""} — never {@code null}. */
    public static String one(String name) {
        List<String> stored = VALUES.get(name);
        return stored == null || stored.isEmpty() ? "" : stored.getFirst();
    }

    /** Every entry stored under {@code name}, or an empty list. */
    public static List<String> all(String name) {
        List<String> stored = VALUES.get(name);
        return stored == null ? List.of() : stored;
    }

    /**
     * Reads {@code resource} off the classpath. Package-private rather than private so a test can point it at
     * a fixture; the running bot only ever reads {@link #RESOURCE}.
     */
    static Map<String, List<String>> load(String resource) {
        Map<String, List<String>> values = new HashMap<>();
        try (InputStream in = ConfigStore.class.getResourceAsStream(resource)) {
            if (in == null) return values;
            JsonNode root = new ObjectMapper().readTree(in);
            for (JsonNode activity : root.path("activities")) {
                values.put(activity.path("name").asText(), List.of(activity.path("enabled").asText("false")));
            }
            for (JsonNode variable : root.path("variables")) {
                List<String> items = new ArrayList<>();
                for (JsonNode item : variable.path("value")) items.add(item.asText(""));
                values.put(variable.path("name").asText(), List.copyOf(items));
            }
        } catch (Exception e) {
            // Degrade rather than crash: the defaults are a working bot, an exception here is not.
            System.err.println("Activities: could not load " + resource + " (" + e.getMessage()
                    + "); using defaults.");
        }
        return values;
    }
}
