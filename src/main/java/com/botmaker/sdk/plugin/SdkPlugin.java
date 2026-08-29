package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.ParameterGroup;
import com.botmaker.plugin.api.SlotEditor;
import com.botmaker.plugin.api.StudioPlugin;
import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.plugin.api.catalog.ScaffoldCatalog;
import com.botmaker.plugin.api.scaffold.Scaffold;
import com.botmaker.plugin.api.scaffold.Seeding;
import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.toolkit.AbstractStudioPlugin;
import com.botmaker.sdk.authoring.ActivityModel;
import com.botmaker.sdk.authoring.Authoring;
import com.botmaker.sdk.authoring.ProjectModel;
import com.botmaker.sdk.authoring.SdkVersion;
import com.botmaker.sdk.internal.authoring.SdkValueTypes;
import com.botmaker.sdk.internal.authoring.SourceEmitter;
import com.botmaker.sdk.internal.plugin.editors.SdkEditors;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The BotMaker SDK, as a Studio plugin.
 *
 * <p>This is plugin #1 and the only one that ships today, and it is deliberately <b>an ordinary
 * implementation of {@link StudioPlugin} with no back door</b>: no {@code instanceof SdkPlugin} branch in
 * the host, no package-private hook, no second interface. What the SDK gets that a third-party plugin does
 * not is a set of <em>privileges</em> — it is always loaded, it owns the primary slot editors, and Studio's
 * own pom declares the dependency — never a wider API. One implementor proves little about a contract; an
 * implementor that cannot cheat proves rather more.
 *
 * <h2>Why the version is still an argument, though this plugin ignores it</h2>
 *
 * <p>{@link #catalog(String)} takes the version <em>the bot pins</em>, not this jar's, and until 2026-08-26
 * the SDK answered it from a per-version class. It no longer does: there is one catalog, reflected off the
 * facades in <em>this</em> build, and the pin is not consulted.
 *
 * <p>The rule it used to serve is unchanged, and is met somewhere better. What an older pin may be offered
 * is this catalog <b>intersected with the bot's own resolved jar</b>, which {@code SdkSurfaceService}
 * already computes from bytecode — so a member this build added is still absent from an older bot, because
 * that bot's jar does not contain it. A frozen class per version could only restate, by hand, what the jar
 * already says; and it had to be edited whenever a member was deleted, which made it untruthful about the
 * past exactly when it mattered.
 *
 * <p>The parameter stays on the contract regardless. It is not the SDK's to remove — another plugin may
 * ship per-version curation and needs somewhere to read the pin from — and a surface that narrows to fit
 * its only implementor is the back door this class exists to refuse.
 *
 * <h2>Where this class may live, and where it may not</h2>
 *
 * <p>Under {@code com.botmaker.sdk.plugin}, never {@code com.botmaker.sdk.api} — a bot cannot write this
 * name down, and nothing under {@code api} may reference a {@code com.botmaker.plugin.api} type. That
 * invariant is what makes the contract's {@code <optional>true</optional>} scope safe: the class is in the
 * jar and cannot link on a bot's classpath, exactly like an SLF4J binding, and no bot ever reaches it.
 */
public final class SdkPlugin extends AbstractStudioPlugin {

    /** The stable identifier the host files this plugin's contributions under. */
    public static final String ID = "com.botmaker.sdk";

    public SdkPlugin() {
        super(ID, "BotMaker SDK");
    }

    /**
     * Built once, by reflection over the facades named here. Every member is <em>discovered</em> rather than
     * named — {@code @Hidden}, {@code @PaletteDefault} and {@code @PaletteLabel} travel with the member they
     * annotate — so nothing in this list can go stale except a class that no longer exists, and that is a
     * javac error because these are class literals. Reflection runs exactly once in the editor and never at
     * all on a bot's classpath, where this class is not loaded.
     *
     * <p>The order here is the order the menus fall back to when two facades share a {@code @Palette} order;
     * {@code PaletteCatalog.of} sorts by that order first, so this list is documentation rather than policy.
     *
     * <p>It is a method rather than a {@code static final} field so that the reflection happens the first time
     * the palette is <em>asked for</em> rather than when {@code ServiceLoader} constructs this class, which
     * the host does while opening a project. {@link AbstractStudioPlugin} caches the answer.
     */
    @Override
    protected PaletteCatalog buildCatalog() {
        return PaletteCatalog.of(
            com.botmaker.sdk.api.interaction.Mouse.class,
            com.botmaker.sdk.api.interaction.Keyboard.class,
            com.botmaker.sdk.api.interaction.Wait.class,
            com.botmaker.sdk.api.vision.ImageFinder.class,
            com.botmaker.sdk.api.vision.ImageClicker.class,
            com.botmaker.sdk.api.vision.ImageWaiter.class,
            com.botmaker.sdk.api.vision.Pixel.class,
            com.botmaker.sdk.api.vision.Text.class,
            com.botmaker.sdk.api.vision.Vision.class,
            com.botmaker.sdk.api.bot.BotSettings.class,
            com.botmaker.sdk.api.util.Debug.class,
            com.botmaker.sdk.api.bot.Session.class,
            com.botmaker.sdk.api.bot.Bot.class,
            com.botmaker.sdk.api.bot.Watchdog.class,
            com.botmaker.sdk.api.bot.PopupGuard.class,
            com.botmaker.sdk.api.bot.Activity.class,
            com.botmaker.sdk.api.config.Wire.class,
            com.botmaker.sdk.api.launch.Game.class,
            com.botmaker.sdk.api.launch.Target.class,
            com.botmaker.sdk.api.emulator.Emulators.class,
            com.botmaker.sdk.api.capture.Source.class,
            com.botmaker.sdk.api.capture.Window.class,
            com.botmaker.sdk.api.util.Time.class,
            com.botmaker.sdk.api.util.BotMaker.class,
            com.botmaker.sdk.api.geometry.Point.class,
            com.botmaker.sdk.api.geometry.Rect.class,
            com.botmaker.sdk.api.geometry.Size.class,
            com.botmaker.sdk.api.bot.BotStuckException.class,
            com.botmaker.sdk.api.bot.StartMode.class,
            com.botmaker.sdk.api.capture.CaptureSource.class,
            com.botmaker.sdk.api.geometry.Direction.class,
            com.botmaker.sdk.api.emulator.Emulator.class,
            com.botmaker.sdk.api.emulator.EmulatorRef.class,
            com.botmaker.sdk.api.emulator.EmulatorSource.class,
            com.botmaker.sdk.api.interaction.Key.class,
            com.botmaker.sdk.api.interaction.MouseButton.class,
            com.botmaker.sdk.api.launch.LaunchTarget.class,
            com.botmaker.sdk.api.vision.ColorMatch.class,
            com.botmaker.sdk.api.vision.ImageTemplate.class,
            com.botmaker.sdk.api.vision.ImageTemplateGroup.class,
            com.botmaker.sdk.api.vision.Matches.class,
            com.botmaker.sdk.api.vision.MatchResult.class,
            com.botmaker.sdk.api.vision.Precision.class,
            com.botmaker.sdk.api.vision.TextMatch.class,
            com.botmaker.sdk.api.vision.OcrOptions.class,
            com.botmaker.sdk.api.vision.OcrLanguage.class,
            com.botmaker.sdk.api.vision.TextResult.class,
            com.botmaker.sdk.api.flow.FlowGraph.class,
            com.botmaker.sdk.api.flow.PopupCheck.class,
            com.botmaker.sdk.api.flow.Recovery.class,
            com.botmaker.sdk.api.meta.Since.class,
            com.botmaker.sdk.api.meta.ReplacedBy.class,
            com.botmaker.sdk.api.meta.Replaces.class);
    }

    /**
     * The editors for this plugin's own types — a region dragged on screen instead of
     * {@code new Rect(12, 40, 300, 80)}, and the rest of {@link SdkEditors}.
     *
     * <p>They were the host's until 2026-08-27, which was the last place Studio still had to know what an SDK
     * type looked like. They are reached exactly as a third-party plugin's would be: the host asks every
     * plugin in turn, after its own editors and before its JDK fallbacks. Nothing here is privileged.
     *
     * <p>The classes behind this list touch JavaFX and the plugin widget toolkit, both
     * {@code <optional>true</optional>} in this module's pom, so they are in the jar and never linked on a
     * bot's classpath — the same arrangement that makes the contract dependency safe.
     */
    @Override
    protected List<SlotEditor> buildSlotEditors() {
        return SdkEditors.ALL;
    }

    /**
     * The seventeen types a project variable could hold before there was a registry to hold them in.
     *
     * <p>They are registered through the same builder any plugin uses, and their ids are the constant names
     * of the enum they used to be, so every project ever written keeps its meaning. That is the whole test
     * of the surface: the vocabulary that was hard-coded into the host is now contributed by a plugin, and
     * it had to give up nothing to become contributable.
     */
    @Override
    protected ValueCatalog buildValueTypes() {
        return SdkValueTypes.CATALOG;
    }

    /**
     * One section, {@code Parameters} — the class every bot has always had, now declared rather than assumed.
     *
     * <p>Its id is blank ({@link ParameterGroup#DEFAULT_ID}), which is the whole of the migration: a variable
     * in a project written before groups existed carries no group, reads back as blank, and is therefore this
     * plugin's. A second plugin declares {@code ParameterGroup.of("discord", "DiscordParameters")} and gets
     * its own section, its own file and its own namespace.
     *
     * <p>Total in the pin, like {@link #catalog(String)}: the class has existed in every SDK there has been.
     */
    @Override
    protected List<ParameterGroup> buildParameters() {
        return List.of(SourceEmitter.SDK_PARAMETERS);
    }

    /**
     * The three files a game bot is made of, as seeds.
     *
     * <p>Real compiling classes in this module, named here as class literals — so a seed that is renamed and
     * not re-catalogued is a javac error, and a seed whose <em>source</em> failed to reach the jar is one
     * line in {@code problems()} rather than a silent inability to write it. The pom's {@code <resources>}
     * block is what puts the source there; {@code SdkPluginSeedsTest} is what makes a mistake in it red.
     *
     * <p><b>The entry point is not here, and its absence is the interesting part.</b> It was the fourth seed
     * until 2026-08-29. It is the file that wires the plugins together —
     * {@code PopupGuard.install(Popups.INSTANCE::execute)} beside {@code FlowGraph.run(…)} — and a second
     * plugin's own installation belongs in it too, which no plugin can write on another's behalf. It is also
     * named for the project, which this plugin has no way to know: {@code seedings} is handed a directory,
     * and at creation time there is nothing in it to read. Both point the same way, and it is the argument
     * that already made {@code pom.xml} the host's: only the thing that knows the whole plugin set can
     * compose the file that names them all.
     *
     * <p>Total in the pin, like {@link #catalog(String)}: all three have existed in every SDK that ever
     * generated a game bot.
     */
    @Override
    public ScaffoldCatalog scaffold(String pinnedVersion) {
        return SEEDS;
    }

    /**
     * Built once and held, for the reason {@link #buildCatalog()} is lazy: this reads three source files out
     * of the jar, and the host asks for it while a project is opening.
     */
    private static final ScaffoldCatalog SEEDS = ScaffoldCatalog.of(
            com.botmaker.sdk.internal.plugin.seeds.GoHome.class,
            com.botmaker.sdk.internal.plugin.seeds.Popups.class,
            com.botmaker.sdk.internal.plugin.seeds.ActivityTemplate.class);

    /**
     * How many files this project wants of each, and what goes in them — read from the project's own
     * {@code activities.json}, which is this plugin's file and nobody else's.
     *
     * <p>{@code GoHome} and {@code Popups} are one each and only for a game bot, which is a project whose
     * model has a flow or an activity in it; an empty project wants neither. The activity template is one per
     * activity, carrying that activity's outcomes into its {@code Outcome} enum.
     *
     * <p><b>The key is the activity's id, never its name.</b> That is the whole reason {@code ActivityModel}
     * has an id: the name is what a rename changes, so a host keying on it would orphan the stub the user
     * wrote their {@code run()} body into and hand them an empty one. An activity in a project written
     * before ids existed reports its name as its id, so a rename there degrades to what it always did rather
     * than to something worse.
     *
     * <p>Best-effort, like every read of a project's own file: an unreadable or absent model means this
     * project wants no seeds, which is exactly true of a project that has none.
     */
    @Override
    public Map<String, List<Seeding>> seedings(String pinnedVersion, Path projectDir) {
        ProjectModel model = readModel(pinnedVersion, projectDir);
        if (model == null || model.activities().isEmpty()) return Map.of();

        Map<String, List<Seeding>> out = new LinkedHashMap<>();
        out.put(pathOf(com.botmaker.sdk.internal.plugin.seeds.GoHome.class),
                List.of(new Seeding("sdk:gohome", "GoHome")));
        out.put(pathOf(com.botmaker.sdk.internal.plugin.seeds.Popups.class),
                List.of(new Seeding("sdk:popups", "Popups")));

        List<Seeding> activities = new ArrayList<>(model.activities().size());
        for (ActivityModel activity : model.activities()) {
            activities.add(new Seeding("sdk:activity:" + activity.id(), activity.name(),
                    Map.of("outcomes", activity.allOutcomes())));
        }
        out.put(pathOf(com.botmaker.sdk.internal.plugin.seeds.ActivityTemplate.class), activities);
        // unmodifiableMap, not Map.copyOf: the latter is explicitly unordered and would throw away the
        // LinkedHashMap above. ScaffoldPlan drives off the catalog's order rather than this map's, so
        // nothing downstream depends on it — but a plugin handing over a map whose order it just discarded
        // is a trap for the next reader, and the activities list inside it is ordered and does matter.
        return Collections.unmodifiableMap(out);
    }

    /**
     * The unresolved {@code @Scaffold.path()} of one seed — the key {@link #seedings} answers under.
     *
     * <p>Read off the annotation rather than written out again as a string, so the two halves of this
     * contribution cannot drift: a path edited on the seed moves its seedings with it, and a path this
     * plugin misspelled would be a key no seed claims, which {@code ScaffoldPlan} reports.
     */
    private static String pathOf(Class<?> seed) {
        Scaffold scaffold = seed.getAnnotation(Scaffold.class);
        return scaffold == null ? "" : scaffold.path().trim();
    }

    /** The project's stored model, or {@code null} when there is nothing readable to seed from. */
    private static ProjectModel readModel(String pinnedVersion, Path projectDir) {
        if (projectDir == null) return null;
        try {
            SdkVersion version = SdkVersion.ofPin(pinnedVersion).orElseGet(SdkVersion::latest);
            return Authoring.readModel(version, projectDir.resolve("src/main/resources"));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
