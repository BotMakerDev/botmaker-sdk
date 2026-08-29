package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.catalog.ScaffoldCatalog;
import com.botmaker.plugin.api.catalog.ScaffoldEntry;
import com.botmaker.plugin.api.catalog.ScaffoldPlan;
import com.botmaker.plugin.api.scaffold.Seeding;
import com.botmaker.sdk.authoring.ActivityModel;
import com.botmaker.sdk.authoring.Authoring;
import com.botmaker.sdk.authoring.ProjectModel;
import com.botmaker.sdk.authoring.SdkVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SDK's seeds, as a plugin contributes them.
 *
 * <p>Most of what a seed has to get right is checked by {@code javac}: the three classes compile in this
 * module's own build, which is the entire argument for a seed being real source rather than a template.
 * What is left is everything javac cannot see, and it is all here.
 *
 * <p><b>The one that would otherwise be silent is {@link #everySeedsSourceReachedTheJar}.</b> A seed's class
 * compiling proves nothing about whether its {@code .java} was copied into {@code target/classes} — that is
 * a line in the pom, and a build misconfigured that way fails quietly: the plugin catalogues three seeds and
 * can write none of them, at the moment a user creates a project.
 */
class SdkPluginSeedsTest {

    private static final String PIN = SdkVersion.latest().toString();

    private static ScaffoldCatalog catalog() {
        return new SdkPlugin().scaffold(PIN);
    }

    // ---- the catalog ------------------------------------------------------------------------------------

    @Test
    void everySeedsSourceReachedTheJar() {
        ScaffoldCatalog seeds = catalog();

        assertEquals(List.of(), seeds.problems(),
                "a seed with no .java beside its .class is reported here and nowhere else — check the "
                        + "<resources> block in the pom");
        assertEquals(3, seeds.seeds().size());
        for (ScaffoldEntry seed : seeds.seeds()) {
            assertTrue(seed.source().contains("class " + seed.templateName()),
                    () -> seed.templateName() + "'s source does not look like its own file");
        }
    }

    @Test
    void theSeedsAreTheThreeAGameBotIsMadeOf() {
        assertEquals(List.of("GoHome", "Popups", "ActivityTemplate"),
                catalog().seeds().stream().map(ScaffoldEntry::templateName).toList());
    }

    @Test
    void onlyTheActivityTemplateIsRenamedAndOnlyItHasAHole() {
        // GoHome and Popups are one file each with a fixed name; the activity template is one shape a project
        // has many of. That asymmetry is the reason Seeding carries a name at all.
        for (ScaffoldEntry seed : catalog().seeds()) {
            boolean isTemplate = seed.templateName().equals("ActivityTemplate");
            assertEquals(isTemplate, seed.renamesType(), seed.templateName() + ".renamesType()");
            assertEquals(isTemplate ? 1 : 0, seed.enums().size(), seed.templateName() + " holes");
        }
    }

    @Test
    void everySeedHandsItsRunMethodToTheUser() {
        // @Editable is the inverse mark: the signature is the plugin's, the body is never the plugin's again.
        // All three seeds exist to be filled in, so all three must say so.
        for (ScaffoldEntry seed : catalog().seeds()) {
            assertTrue(seed.isEditable("run"), seed.templateName() + " must hand over run()");
            assertFalse(seed.isEditable("isEnabled"),
                    seed.templateName() + "'s isEnabled() is managed, not the user's");
        }
    }

    @Test
    void theActivityTemplateSpellsItsHoleTheWaySeedingsFillsIt() {
        ScaffoldEntry template = catalog().seeds().stream()
                .filter(s -> s.templateName().equals("ActivityTemplate")).findFirst().orElseThrow();

        assertEquals("outcomes", template.enums().get(0).key());
        assertEquals("Outcome", template.enums().get(0).enumName());
    }

    // ---- the seedings -----------------------------------------------------------------------------------

    @Test
    void aProjectWithNoModelWantsNoSeeds(@TempDir Path dir) {
        // Not an error and not an empty project's misfortune: a project with nothing in it wants nothing.
        assertEquals(Map.of(), new SdkPlugin().seedings(PIN, dir));
        assertEquals(Map.of(), new SdkPlugin().seedings(PIN, null));
    }

    @Test
    void oneFilePerActivityPlusTheTwoFixedOnes(@TempDir Path dir) throws IOException {
        write(dir, ProjectModel.of(List.of(ActivityModel.of("Mining"), ActivityModel.of("Fishing")),
                List.of()));

        Map<String, List<Seeding>> seedings = new SdkPlugin().seedings(PIN, dir);

        assertEquals(3, seedings.size());
        assertEquals(1, seedings.get("src/main/java/{package}/GoHome.java").size());
        assertEquals(1, seedings.get("src/main/java/{package}/Popups.java").size());
        assertEquals(List.of("Mining", "Fishing"),
                seedings.get("src/main/java/{package}/activities/{name}.java").stream()
                        .map(Seeding::name).toList());
    }

    @Test
    void anActivitysOutcomesFillTheHoleWithTheImplicitOneFirst(@TempDir Path dir) throws IOException {
        write(dir, ProjectModel.of(
                List.of(ActivityModel.of("Mining").withOutcomes(List.of("BAG_FULL", "NO_ORE"))), List.of()));

        Seeding mining = new SdkPlugin().seedings(PIN, dir)
                .get("src/main/java/{package}/activities/{name}.java").get(0);

        // allOutcomes(), not outcomes(): NEXT is the constant a stub returns and a wire with no outcome
        // names, so it has to lead. DISABLED is absent — an activity cannot report never having run.
        assertEquals(List.of("NEXT", "BAG_FULL", "NO_ORE"), mining.valuesFor("outcomes"));
    }

    @Test
    void theKeyIsTheActivitysIdAndSurvivesARename(@TempDir Path dir) throws IOException {
        ActivityModel mining = ActivityModel.of("Mining").withNewId();
        write(dir, ProjectModel.of(List.of(mining), List.of()));
        String before = activityKey(dir);

        write(dir, ProjectModel.of(List.of(mining.withName("Smelting")), List.of()));

        assertEquals(before, activityKey(dir),
                "the key must not move when the name does — that is the whole reason it exists");
        assertEquals("Smelting", new SdkPlugin().seedings(PIN, dir)
                .get("src/main/java/{package}/activities/{name}.java").get(0).name());
    }

    @Test
    void anActivityWithNoIdOfItsOwnKeysOnItsName(@TempDir Path dir) throws IOException {
        // A project written before ids existed. It degrades to what it always did — a rename orphans the old
        // stub — rather than to anything worse, and never churns: the id is the name every time it is read.
        write(dir, ProjectModel.of(List.of(ActivityModel.of("Mining")), List.of()));

        assertEquals("sdk:activity:Mining", activityKey(dir));
        assertEquals(activityKey(dir), activityKey(dir));
    }

    // ---- the two halves agree ---------------------------------------------------------------------------

    @Test
    void theCatalogAndTheSeedingsCrossWithNoProblems(@TempDir Path dir) throws IOException {
        // The keys seedings answers under are read off the seeds' own @Scaffold, so a path edited on one side
        // moves both. ScaffoldPlan is what would notice if they ever stopped agreeing.
        write(dir, ProjectModel.of(
                List.of(ActivityModel.of("Mining").withOutcomes(List.of("BAG_FULL")),
                        ActivityModel.of("Fishing")),
                List.of()));
        SdkPlugin plugin = new SdkPlugin();

        ScaffoldPlan plan = ScaffoldPlan.of(plugin.scaffold(PIN), "com.mybot",
                plugin.seedings(PIN, dir));

        assertEquals(List.of(), plan.problems());
        assertEquals(List.of("src/main/java/com/mybot/GoHome.java",
                        "src/main/java/com/mybot/Popups.java",
                        "src/main/java/com/mybot/activities/Mining.java",
                        "src/main/java/com/mybot/activities/Fishing.java"),
                plan.files().stream().map(ScaffoldPlan.PlannedFile::path).toList());

        ScaffoldPlan.PlannedFile mining = plan.at("src/main/java/com/mybot/activities/Mining.java");
        assertNotNull(mining);
        assertEquals("Mining", mining.typeName());
        assertEquals(List.of("NEXT", "BAG_FULL"),
                mining.constantsFor(mining.seed().enums().get(0)));
    }

    @Test
    void anActivityNamedSomethingJavaCannotBeIsRefusedByThePlanRatherThanWritten(@TempDir Path dir)
            throws IOException {
        // The editor refuses these at the dialog, so this is the second line of defence — and it has to be a
        // reported problem rather than a throw, because it runs while a project is opening.
        write(dir, ProjectModel.of(List.of(ActivityModel.of("class")), List.of()));
        SdkPlugin plugin = new SdkPlugin();

        ScaffoldPlan plan = ScaffoldPlan.of(plugin.scaffold(PIN), "com.mybot", plugin.seedings(PIN, dir));

        assertFalse(plan.problems().isEmpty());
        assertTrue(plan.problems().stream().anyMatch(p -> p.contains("class")), plan.problems().toString());
        assertEquals(2, plan.files().size(), "the two fixed seeds are unaffected by one bad activity");
    }

    private static String activityKey(Path dir) {
        return new SdkPlugin().seedings(PIN, dir)
                .get("src/main/java/{package}/activities/{name}.java").get(0).key();
    }

    private static void write(Path dir, ProjectModel model) throws IOException {
        Path resources = Files.createDirectories(dir.resolve("src/main/resources"));
        Authoring.writeModel(SdkVersion.latest(), resources, model, 3);
    }
}
