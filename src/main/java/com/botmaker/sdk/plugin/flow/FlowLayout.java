package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.basics.store.PluginData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.botmaker.sdk.plugin.SdkPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where each card sits on the Activity Flow canvas — and the only part of a flow that is <b>not</b> in the
 * bot's own Java.
 *
 * <h2>Why this is a sidecar, and a gitignored one</h2>
 *
 * <p>Everything else the flow editor writes goes into {@code Sdk.flow()}, because it is what the bot does:
 * which activities exist, how they are wired, which are switched on, what stops a run. A card's position is
 * not. Nothing reads it but this editor, two people laying the same flow out differently are not disagreeing
 * about anything, and above all <b>dragging a node must not show up in {@code git diff}</b> — a value file
 * that changes every time somebody looks at the canvas is one nobody can review.
 *
 * <p>So positions go to {@code src/main/resources/plugins/com.botmaker/sdk/flow-layout.json}, which both bot
 * templates gitignore, alongside {@code settings.json} and for the same reason. A clone with no sidecar is
 * the ordinary case rather than a broken one: the editor lays the flow out itself when it finds no position
 * for anything, exactly as it always did for a flow that had never been opened.
 *
 * <p>The positions are keyed by <b>activity name</b>, which is the name the canvas draws and the edges route
 * on. A rename therefore loses one card's position, and that is the right trade: the alternative is a second
 * identity to keep in step, in the one file that is allowed to be wrong.
 *
 * <p>One thing that is not a position rides along: whether a newly added card starts with its "go home
 * first" tick on. It is a preference about the <em>editor</em> and not about the flow — no existing activity
 * changes when it is flipped, and nothing at runtime reads it — so it belongs on this side of the line with
 * the positions rather than in the bot's source.
 */
public final class FlowLayout {

    /** The sidecar's name within this plugin's own project folder; {@code .json} is {@link PluginData}'s. */
    public static final String FILE = "flow-layout";

    /**
     * The sidecar's path inside a project, as both templates' {@code .gitignore} spells it. Here so that the
     * one place that writes the file and the documentation of where it lands cannot drift apart.
     */
    public static final String IGNORED_PATH = "src/main/resources/plugins/com.botmaker/sdk/flow-layout.json";

    private FlowLayout() {}

    /** One card's place on the canvas, in unscaled canvas coordinates. */
    public record Spot(double x, double y) {
    }

    /**
     * What the sidecar holds.
     *
     * @param spots           each card's place, keyed by activity name; empty means "lay it out for me"
     * @param goHomeByDefault whether a newly added card starts with its ⌂ tick on
     */
    public record Layout(Map<String, Spot> spots, boolean goHomeByDefault) {

        /** No sidecar: nothing placed, and new cards go home first. */
        public static final Layout NONE = new Layout(Map.of(), true);

        public Layout {
            spots = spots == null ? Map.of() : Map.copyOf(spots);
        }

        /** Where {@code activity}'s card was left, or null. */
        public Spot spot(String activity) {
            return spots.get(activity);
        }
    }

    /**
     * The saved layout — {@link Layout#NONE} when there is no sidecar, which is the state a fresh clone and
     * a flow nobody has opened are both in.
     *
     * <p>Never throws. {@link PluginData} reads an absent, unreadable or unparseable file as empty, and a
     * layout is the one thing in this editor that is genuinely better lost than reported.
     */
    public static Layout read(Path resourcesDir) {
        if (resourcesDir == null) return Layout.NONE;
        JsonNode root = PluginData.of(resourcesDir, SdkPlugin.ID).read(FILE);
        Map<String, Spot> spots = new LinkedHashMap<>();
        root.path(SPOTS).fields().forEachRemaining(entry -> {
            JsonNode spot = entry.getValue();
            if (spot == null || !spot.isObject()) return;
            spots.put(entry.getKey(), new Spot(spot.path("x").asDouble(), spot.path("y").asDouble()));
        });
        return new Layout(spots, root.path(GO_HOME).asBoolean(true));
    }

    /**
     * Stores {@code layout}, replacing whatever was there.
     *
     * <p>Replacing rather than merging: the canvas holds every card there is, so a name missing from it is a
     * card that no longer exists and keeping its position would grow the file forever.
     *
     * <p>Throws, so the caller may say the layout was not saved. It is written <em>after</em> the flow
     * itself, and a failure here leaves the flow written and one screenful of positions lost.
     */
    public static void write(Path resourcesDir, Layout layout) throws IOException {
        if (resourcesDir == null) return;
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put(GO_HOME, layout.goHomeByDefault());
        ObjectNode spots = root.putObject(SPOTS);
        layout.spots().forEach((activity, spot) -> {
            ObjectNode node = spots.putObject(activity);
            node.put("x", spot.x());
            node.put("y", spot.y());
        });
        PluginData.of(resourcesDir, SdkPlugin.ID).write(FILE, root);
    }

    private static final String SPOTS = "spots";
    private static final String GO_HOME = "goHomeByDefault";
}
