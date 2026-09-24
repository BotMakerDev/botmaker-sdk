package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.slot.SlotEditor;
import com.botmaker.plugin.api.toolbar.ToolbarGroup;
import com.botmaker.plugin.api.toolbar.ToolbarItem;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.launch.Game;
import com.botmaker.sdk.api.interaction.MouseButton;
import com.botmaker.sdk.api.vision.ColorMatch;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.ImageTemplateGroup;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Matches;
import com.botmaker.sdk.api.vision.Precision;
import com.botmaker.sdk.api.vision.TextMatch;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
     * The fourteen types <b>this plugin</b> declares, in declaration order, which is the order a "what type
     * is this variable" dropdown offers them in after plugin-basics' nine. Written out rather than derived
     * from {@code SdkTypes.ALL}, because a test that reads its expectation from its subject asserts nothing.
     *
     * <p>They were persisted ids — {@code IMAGE_TEMPLATE}, {@code POINT} — until 2026-09-22. A type's
     * identity is its Java class now, which is what a {@code @Param} field is declared as, so there is no
     * second name to keep in step with the first.
     *
     * <p>The last six are declarable but not editable: their fresh form is a call the bot re-evaluates, so
     * they answer {@code freshCall()} where the first eight answer {@code fresh()}.
     */
    private static final List<Class<?>> DECLARED_TYPES = List.of(
            ImageTemplate.class, Precision.class, Point.class, Rect.class, Size.class,
            Direction.class, Key.class, MouseButton.class,
            CaptureSource.class, ImageTemplateGroup.class, MatchResult.class, Matches.class,
            ColorMatch.class, TextMatch.class);

    private final SdkPlugin plugin = new SdkPlugin();

    @Test
    void the_plugin_identifies_itself_by_the_id_a_registry_entry_would_claim() {
        assertEquals("com.botmaker.sdk", plugin.id());
        assertEquals(SdkPlugin.ID, plugin.id());
        assertFalse(plugin.displayName().isBlank());
    }

    /**
     * A project with no {@code Sdk.java} can be given one: the flow and the capture source share the
     * {@code Sdk} holder, each typed with what its method returns and starting as the template's does; the
     * pictures are an open set, an empty {@code Pictures} class.
     */
    @Test
    void each_managed_value_says_where_the_host_creates_it() {
        var byId = new java.util.HashMap<String, com.botmaker.plugin.api.source.ManagedValue>();
        plugin.managedValues().forEach(v -> byId.put(v.id(), v));
        var flow = byId.get(SdkPlugin.FLOW);
        assertEquals(SdkPlugin.SDK_HOLDER, flow.holder());
        assertEquals(com.botmaker.sdk.api.flow.Flow.class, flow.valueType());
        assertEquals(com.botmaker.sdk.api.flow.Flow.NONE, flow.initial());
        var capture = byId.get(SdkPlugin.CAPTURE);
        assertEquals(SdkPlugin.SDK_HOLDER, capture.holder());
        assertEquals(CaptureSource.class, capture.valueType());
        assertEquals(CaptureSource.desktop().getClass(), capture.initial().getClass());
        var pictures = byId.get(SdkPlugin.PICTURES);
        assertEquals("Pictures", pictures.holder());
        assertNull(pictures.valueType());
    }

    /** The palette is the host's to discover from {@code @Palette}; {@link ApiCatalogTest} checks what it finds. */
    @Test
    void the_plugin_builds_no_palette_by_hand() {
        assertTrue(plugin.catalog().isEmpty());
    }

    @Test
    void every_type_is_declared_once_and_in_the_order_the_dropdown_shows() {
        assertEquals(DECLARED_TYPES, plugin.types().stream().map(PluginType::type).toList());
    }

    /**
     * Every declared type says what a fresh one is — as a value, or as an expression, never as neither.
     *
     * <p>This is what a type picker needs before it can offer a type at all: the host writes a declaration
     * with an initializer, so a type answering nothing would produce a field with no value and a menu entry
     * that does nothing.
     */
    @Test
    void every_declared_type_says_what_a_fresh_one_is() {
        for (PluginType<?> type : plugin.types()) {
            String name = type.type().getName();
            Object fresh = type.fresh();
            if (fresh != null) {
                assertTrue(type.type().isInstance(fresh), name + " answered a fresh value of another type");
                assertNull(type.freshCall(),
                        name + " answered both a value and a call; the host would not know which");
            } else {
                java.lang.reflect.Method call = type.freshCall();
                assertNotNull(call, name + " answers neither a fresh value nor a fresh call");
                assertTrue(java.lang.reflect.Modifier.isStatic(call.getModifiers())
                                && java.lang.reflect.Modifier.isPublic(call.getModifiers()),
                        name + "'s fresh call must be public static: " + call);
                assertEquals(0, call.getParameterCount(), name + "'s fresh call takes arguments: " + call);
                assertEquals(type.type(), call.getReturnType(), name + "'s fresh call returns another type");
            }
        }
    }

    /**
     * {@code build(components(fresh()))} is {@code fresh()} — the law every {@code ComponentType} owes.
     *
     * <p>It is the check the deleted id-uniqueness one could never make. A codec pair where only the writer
     * had a caller is exactly how {@code literal}/{@code valueOfLiteral} drifted until a {@code java.awt.Color}
     * came back rewritten; asking a type to put its own fresh value back together catches that on the build.
     *
     * <p><b>Compared by components rather than by {@code equals}</b>, because not every type this plugin
     * owns has value equality and requiring it would be the wrong demand: {@code ImageTemplate} holds an
     * OpenCV {@code Mat} and is {@code AutoCloseable}, so two of them naming one picture are not
     * interchangeable and an {@code equals} claiming they are would be worse than none. The component list
     * is what the host actually writes and reads back, so comparing it catches every drift that can reach a
     * user's file — a part in the wrong position, a part lost — and demands nothing of the type beyond the
     * two methods it already declares.
     */
    @Test
    void every_composite_type_round_trips_its_fresh_value() {
        for (PluginType<?> type : plugin.types()) {
            if (!(type instanceof ComponentType<?> composite)) continue;
            Object fresh = type.fresh();
            if (fresh == null) continue;       // a seeded type has no value to take apart
            List<Object> parts = composite.componentsOf(fresh);
            assertEquals(parts, composite.componentsOf(composite.build(parts)),
                    type.type().getName() + " did not survive being taken apart and put back together");
        }
    }

    // the_parameters_section_is_one_group_filed_under_the_blank_id stood here until 2026-09-22, over
    // plugin.parameters(""). The group, the surface that read it and the store behind it are all deleted:
    // nothing ever declared a row, so the reading half had nothing to read. A parameter is a @Param field in
    // the bot's own Java, and the Parameters window's sections are the bot's own classes.

    // every_source_seed_names_a_type_and_an_expression stood here until 2026-09-22, over
    // plugin.sourceSeeds(). A seed said two things -- this type is declarable, and here is a fresh one as
    // Java text javac never looked at -- and both are now types(): the first by being in the list, the
    // second by fresh() or freshCall(). The two tests above are what it became.

    /**
     * The eight buttons, their sections and their order within them.
     *
     * <p>The order values are asserted rather than only the sequence, because a bar assembled from two
     * plugins interleaves by order and ties break on the plugin id — so a wrong number here moves a button
     * on a bar this test cannot see. {@code activity-flow} takes {@link ToolbarGroup#AUTHORING} at 10, which
     * is the slot Studio's own 🔀 Flow button vacated when the graph editor moved here on 2026-09-11.
     *
     * <p>The last two are {@link ToolbarGroup#OVERLAY}: they are drawn on the overlay editor's own row rather
     * than on the main bar, and their subject is the window the HUD is drawn over. Recording is not among
     * them: it is the host's, and this plugin takes part through {@code @Records} and {@code recordedValues()}.
     */
    @Test
    void the_toolbar_contributes_eight_items_in_their_groups_and_orders() {
        List<ToolbarItem> items = plugin.toolbarItems();

        assertEquals(List.of("pilot", "capture-templates", "manage-templates",
                        "activity-flow", "capture-source", "project-setup",
                        "point-here", "picture-here"),
                items.stream().map(ToolbarItem::id).toList());

        assertEquals(List.of(ToolbarGroup.RUN, ToolbarGroup.TOOLS, ToolbarGroup.TOOLS,
                        ToolbarGroup.AUTHORING, ToolbarGroup.PROJECT, ToolbarGroup.PROJECT,
                        ToolbarGroup.OVERLAY, ToolbarGroup.OVERLAY),
                items.stream().map(ToolbarItem::group).toList());

        assertEquals(List.of(10, 20, 30, 10, 50, 40, 10, 20),
                items.stream().map(ToolbarItem::order).toList());

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
            ask(editor, "a value with no call site",
                    () -> editor.matches(TestContexts.row(null, "")), failures);
            ask(editor, "a typed slot",
                    () -> editor.matches(TestContexts.typedSlot(String.class, "\"\"")), failures);
            ask(editor, "a call site",
                    () -> editor.matches(TestContexts.slot(
                            TestContexts.method(Game.class, "launchSteam", String.class), 0, "\"\"")), failures);
            ask(editor, "an unresolved call",
                    () -> editor.matches(TestContexts.slot(null, 0, "\"\"")), failures);
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
