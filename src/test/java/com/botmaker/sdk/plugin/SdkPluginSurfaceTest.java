package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.ParameterGroup;
import com.botmaker.plugin.api.SlotEditor;
import com.botmaker.plugin.api.SourceSeed;
import com.botmaker.plugin.api.ToolbarGroup;
import com.botmaker.plugin.api.ToolbarItem;
import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueType;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every contribution surface {@link SdkPlugin} implements, asserted structurally — the replacement for
 * Studio's {@code PluginHostLoadTest}, which was deleted on 2026-09-02 along with the bundled plugin it
 * tested.
 *
 * <h2>Why a structural test rather than more behaviour</h2>
 *
 * <p>The SDK's plugin half is covered where it computes something — {@code MacroTranslatorTest},
 * {@code GeometryLabelTest}, {@code TemplateUsesTest} and the rest. What nothing covered after the migration
 * is the <b>shape</b>: that the surfaces answer at all, and that they answer the things the host will look
 * for. That gap matters here more than it would elsewhere, because <b>this plugin's characteristic failure
 * is silent</b>. {@code PluginHost.discover} catches a plugin that will not load — correctly; a classpath
 * with no plugin on it is an ordinary state — so a broken surface is an editor with an empty palette, no
 * toolbar buttons and one line on stderr. Nothing fails to compile at any point. That exact failure shipped
 * once already, on 2026-08-28, when a non-transitive {@code optional} toolkit left Studio unable to
 * construct {@code SdkPlugin} at all.
 *
 * <p>So the assertions below are deliberately about identity rather than about quality: the six toolbar ids
 * and where they sit, the seventeen value type ids in registration order, the one parameter section. A
 * button that disappears is a red build here instead of a bug report.
 *
 * <h2>{@code matches} is called and {@code create} is not</h2>
 *
 * <p>Building a {@code Node} needs a live JavaFX toolkit, which this suite does not start. A
 * <em>predicate</em> needs nothing — it has the slot's type and its call site to answer with — so every
 * editor's {@code matches} is run against a recording context that throws from
 * {@code StudioServices}. A predicate reaching for the theme is doing what a headless host cannot support,
 * and it is the thing {@code botmaker-cli}'s {@code editors} check exists to catch — except that check
 * <b>skips</b> whenever JavaFX is absent from the classpath it builds, which it is: {@code SlotEditor.create}
 * returns a {@code javafx.scene.Node}, and this module's JavaFX is {@code optional} and therefore off any
 * {@code runtime}-scoped classpath. Here the jars are present because this is the SDK's own build.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SdkPluginSurfaceTest {

    /**
     * The seventeen ids, <b>in registration order</b>, which is the order a "what type is this variable"
     * dropdown offers them in. Written out rather than derived from the catalog, because a test that reads
     * its expectation from its subject asserts nothing.
     */
    private static final List<String> VALUE_TYPE_IDS = List.of(
            "TEXT", "YES_NO", "WHOLE_NUMBER", "DECIMAL_NUMBER", "CHARACTER", "COLOR", "DATE", "TIME_OF_DAY",
            "DURATION", "IMAGE_TEMPLATE", "PRECISION", "POINT", "RECT", "SIZE", "DIRECTION", "KEY",
            "MOUSE_BUTTON");

    private final SdkPlugin plugin = new SdkPlugin();

    @Test
    void the_plugin_identifies_itself_by_the_id_a_registry_entry_would_claim() {
        assertEquals("com.botmaker.sdk", plugin.id());
        assertEquals(SdkPlugin.ID, plugin.id());
        assertFalse(plugin.displayName().isBlank());
    }

    @Test
    void the_palette_builds_with_no_problems_and_is_the_same_object_on_a_second_ask() {
        assertTrue(plugin.catalog("").problems().isEmpty(), () -> plugin.catalog("").problems().toString());
        assertFalse(plugin.catalog("").facades().isEmpty());

        // AbstractStudioPlugin memoises, and it matters: reflecting 52 facades happens while a project is
        // opening, so a second ask must not do it again.
        assertSame(plugin.catalog(""), plugin.catalog("1.1.2"));
    }

    @Test
    void every_value_type_is_registered_once_and_in_the_order_the_dropdown_shows() {
        ValueCatalog catalog = plugin.valueTypes();
        assertEquals(VALUE_TYPE_IDS, catalog.types().stream().map(ValueType::id).toList());
        for (String id : VALUE_TYPE_IDS) {
            assertTrue(catalog.knows(id), id);
            assertTrue(catalog.codec(id).isPresent(), id);
        }
    }

    @Test
    void the_parameters_section_is_one_group_filed_under_the_blank_id() {
        List<ParameterGroup> groups = plugin.parameters("");
        assertEquals(1, groups.size());

        // The blank id is the whole of the migration: a variable written before groups existed carries no
        // group, reads back as blank, and is therefore this plugin's.
        ParameterGroup group = groups.getFirst();
        assertEquals(ParameterGroup.DEFAULT_ID, group.id());
        assertEquals("Parameters", group.className());
        assertFalse(group.categories().isEmpty());
    }

    @Test
    void every_source_seed_names_a_type_and_an_expression() {
        List<SourceSeed> seeds = plugin.sourceSeeds();
        assertFalse(seeds.isEmpty());
        for (SourceSeed seed : seeds) {
            assertFalse(seed.typeName().isBlank(), seed::toString);
            assertNotNull(seed.expression(), seed::toString);
        }
    }

    /**
     * The six buttons, their sections and their order within them.
     *
     * <p>The order values are asserted rather than only the sequence, because a bar assembled from two
     * plugins interleaves by order and ties break on the plugin id — so a wrong number here moves a button
     * on a bar this test cannot see.
     */
    @Test
    void the_toolbar_contributes_six_items_in_their_groups_and_orders() {
        List<ToolbarItem> items = plugin.toolbarItems();

        assertEquals(List.of("pilot", "capture-templates", "record-macro", "manage-templates",
                "capture-targets", "project-setup"), items.stream().map(ToolbarItem::id).toList());

        assertEquals(List.of(ToolbarGroup.RUN, ToolbarGroup.TOOLS, ToolbarGroup.TOOLS, ToolbarGroup.TOOLS,
                ToolbarGroup.PROJECT, ToolbarGroup.PROJECT), items.stream().map(ToolbarItem::group).toList());

        assertEquals(List.of(10, 20, 25, 30, 50, 40), items.stream().map(ToolbarItem::order).toList());

        for (ToolbarItem item : items) {
            assertNotNull(item.label().get(), item::id);
            assertFalse(item.label().get().isBlank(), item::id);
            // A toolbar button is a glyph and two words; the tooltip is where the rest lives, and the
            // contract asks for one.
            assertNotNull(item.tooltip(), item::id);
            assertFalse(item.tooltip().isBlank(), item::id);
            assertNotNull(item.onClick(), item::id);
            assertNotNull(item.enabledWhen(), item::id);
        }
    }

    /** {@link ToolbarGroup#STUDIO} is the host's own section and an item in it is refused by name. */
    @Test
    void no_toolbar_item_claims_the_hosts_own_section() {
        assertTrue(plugin.toolbarItems().stream().noneMatch(i -> i.group() == ToolbarGroup.STUDIO));
    }

    /**
     * Every editor's predicate, over the three shapes of context a host actually hands one: a Parameters
     * row, a typed slot, and a slot known only by its call site.
     *
     * <p>It asserts <b>no throw</b> and nothing else. Which editor claims which value is
     * {@code ColorEditorTest}'s business and its siblings'; what is untested anywhere else is that asking
     * the question at all is safe — and a predicate that throws leaves a Parameters row with no widget in
     * it and no explanation, which reads as the host being broken.
     */
    @Test
    void every_slot_editors_predicate_answers_without_throwing() {
        List<SlotEditor> editors = plugin.slotEditors();
        assertFalse(editors.isEmpty());

        List<String> failures = new ArrayList<>();
        for (SlotEditor editor : editors) {
            ask(editor, "a Parameters row", () -> editor.matches(TestContexts.row("TEXT", "")), failures);
            ask(editor, "a typed slot",
                    () -> editor.matches(TestContexts.typedSlot("java.lang.String", "\"\"")), failures);
            ask(editor, "a call site",
                    () -> editor.matches(TestContexts.slot("Game", "steam", 0, "\"\"")), failures);
        }
        if (!failures.isEmpty()) fail(String.join("\n", failures));
    }

    private static void ask(SlotEditor editor, String shape, Runnable call, List<String> failures) {
        try {
            call.run();
        } catch (RuntimeException | LinkageError e) {
            failures.add(editor.getClass().getName() + " threw on " + shape + ": " + e);
        }
    }
}
