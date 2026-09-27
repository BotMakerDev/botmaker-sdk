package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which constant a picture is, and the order the picture library asks the host for things in.
 *
 * <p>Nothing here needs a host: finding uses by binding and rewriting the bot are {@code PluginValues}' job
 * and are tested in the editor's own module ({@code ManagedSetsTest}). A recording host stands in.
 */
class TemplateUsesTest {

    @Test
    void aPictureWithNoConstantHasNoUsesToFind() {
        Host host = new Host("ORE");
        host.uses.put("ORE", List.of(use("Main.java", 4)));

        assertTrue(TemplateUses.find(host, "Gold-Ore").isEmpty(), "mixed case and a dash: never a constant");
        assertTrue(TemplateUses.find(host, "gold").isEmpty(), "no Pictures.GOLD is declared");
        assertEquals(1, TemplateUses.find(host, "ore").uses().size());
    }

    @Test
    void declaringAddsTheConstantOnceWithItsPath() {
        Host host = new Host("ORE");

        assertEquals(Optional.empty(), TemplateUses.declare(host, "gold"));
        assertEquals(Optional.empty(), TemplateUses.declare(host, "ore"));
        assertEquals(Optional.empty(), TemplateUses.declare(host, "Gold-Ore"));
        assertEquals(List.of("add GOLD src/main/resources/images/gold.png"), host.calls);
    }

    @Test
    void aRenameRenamesTheConstantThenPointsItAtTheNewFile() {
        Host host = new Host("ORE");

        assertEquals(Optional.empty(), TemplateUses.rename(host, "ore", "iron"));
        assertEquals(List.of("rename ORE IRON"), host.calls);
        assertEquals("src/main/resources/images/iron.png", ((ImageTemplate) host.opened.value()).filePath());
    }

    @Test
    void aRenameTheHostRefusesChangesNothingElse() {
        Host host = new Host("ORE");
        host.refuseRename = "would not compile";

        assertEquals(Optional.of("would not compile"), TemplateUses.rename(host, "ore", "iron"));
        assertEquals(null, host.opened, "the path is not rewritten");
    }

    @Test
    void aRepointDeclaresTheReplacementFirstAndSaysItGuessed() {
        Host host = new Host("ORE");
        host.uses.put("ORE", List.of(use("Main.java", 4)));

        assertEquals(Optional.empty(), TemplateUses.repoint(host, "ore", "gold"));
        assertEquals(List.of("add GOLD src/main/resources/images/gold.png",
                "repoint ORE GOLD " + TemplateUses.repointNote("ore", "gold")), host.calls);
    }

    @Test
    void anUnusedPictureHasNothingToRepointAndItsConstantIsForgotten() {
        Host host = new Host("ORE");

        assertEquals(Optional.empty(), TemplateUses.repoint(host, "ore", "gold"));
        assertEquals(Optional.empty(), TemplateUses.forget(host, "ore"));
        assertEquals(List.of("remove ORE"), host.calls);
    }

    @Test
    void aScanDescribesItselfAsARefusalWould() {
        TemplateUses.Scan scan = new TemplateUses.Scan("ore", List.of(
                use("Main.java", 4), use("Main.java", 9), use("Mining.java", 2)));
        assertEquals("3 uses in 2 files", scan.describe());
        assertEquals("1 use in 1 file", new TemplateUses.Scan("ore", List.of(use("Main.java", 4))).describe());
    }

    @Test
    void theRepointNoteNamesBothPictures() {
        String note = TemplateUses.repointNote("ore", "gold");
        assertTrue(note.contains("\"ore\"") && note.contains("\"gold\""), note);
    }

    private static PluginValues.Use use(String file, int line) {
        return new PluginValues.Use(Path.of("/bot", file), line, "find(Pictures.ORE);");
    }

    /** A host holding one open set, answering from maps and writing down what it was asked to do. */
    private static final class Host implements PluginValues {
        final List<String> members = new ArrayList<>();
        final Map<String, List<Use>> uses = new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();
        String refuseRename;
        TestContexts.Recording opened;

        Host(String... members) {
            this.members.addAll(List.of(members));
        }

        @Override public List<String> ids() { return List.of("pictures"); }
        @Override public Optional<ValueContext> open(String id) { return Optional.empty(); }
        @Override public List<String> members(String id) { return List.copyOf(members); }
        @Override public List<Use> uses(String id, String member) { return uses.getOrDefault(member, List.of()); }

        @Override
        public Optional<ValueContext> open(String id, String member) {
            if (!members.contains(member)) return Optional.empty();
            opened = TestContexts.row(ImageTemplate.class, "new ImageTemplate(\"old.png\")");
            return Optional.of(opened);
        }

        @Override
        public Optional<String> add(String id, String member, Object value) {
            calls.add("add " + member + " " + ((ImageTemplate) value).filePath());
            members.add(member);
            return Optional.empty();
        }

        @Override
        public Optional<String> rename(String id, String member, String newName) {
            if (refuseRename != null) return Optional.of(refuseRename);
            calls.add("rename " + member + " " + newName);
            members.set(members.indexOf(member), newName);
            return Optional.empty();
        }

        @Override
        public Optional<String> repoint(String id, String member, String replacement, String note) {
            calls.add("repoint " + member + " " + replacement + " " + note);
            return Optional.empty();
        }

        @Override
        public Optional<String> remove(String id, String member) {
            calls.add("remove " + member);
            members.remove(member);
            return Optional.empty();
        }
    }
}
