package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.palette.Fills;
import com.botmaker.plugin.api.slot.SlotEditor;
import com.botmaker.plugin.api.source.ManagedMethod;
import com.botmaker.plugin.api.source.ManagedOpenSet;
import com.botmaker.plugin.api.source.ManagedParam;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.toolbar.ToolbarGroup;
import com.botmaker.plugin.api.toolbar.ToolbarItem;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.EditableType;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.plugin.api.value.Ref;
import com.botmaker.plugin.toolkit.testing.PluginTests;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.input.KeySequence;
import com.botmaker.sdk.api.input.MouseButton;
import com.botmaker.sdk.api.text.OcrOptions;
import com.botmaker.sdk.api.text.TextMatch;
import com.botmaker.sdk.api.vision.ColorMatch;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.ImageTemplateGroup;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Matches;
import com.botmaker.sdk.api.vision.Precision;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.launch.Game;
import com.botmaker.sdk.plugin.editors.SdkEditors;
import com.botmaker.sdk.plugin.overlay.SdkOverlay;
import com.botmaker.sdk.plugin.run.SdkRunOverlay;
import com.botmaker.sdk.plugin.types.CaptureTypes;
import com.botmaker.sdk.plugin.types.FlowTypes;
import com.botmaker.sdk.plugin.types.ScreenTypes;
import com.botmaker.sdk.plugin.types.SdkTypes;
import com.botmaker.sdk.plugin.types.SettingsTypes;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
     * The sixteen types <b>this plugin</b> declares, in declaration order, which is the order a "what type
     * is this variable" dropdown offers them in after plugin-basics' thirteen. Written out rather than derived
     * from {@code SdkTypes.ALL}, because a test that reads its expectation from its subject asserts nothing.
     *
     * <p>They were persisted ids — {@code IMAGE_TEMPLATE}, {@code POINT} — until 2026-09-22. A type's
     * identity is its Java class now, which is what a {@code @Param} field is declared as, so there is no
     * second name to keep in step with the first.
     *
     * <p>The last four are declarable but not editable: their fresh form is a call the bot re-evaluates, so
     * they answer {@code freshCall()} where the first twelve answer {@code fresh()}.
     */
    private static final List<Class<?>> DECLARED_TYPES = List.of(
            ImageTemplate.class, Precision.class, OcrOptions.class, com.botmaker.sdk.api.text.DigitSet.class,
            com.botmaker.sdk.api.text.Words.class,
            Point.class, Rect.class, Size.class,
            com.botmaker.sdk.api.geometry.Zone.class,
            Key.class, MouseButton.class, Combo.class,
            KeySequence.class, CaptureSource.class, ImageTemplateGroup.class, com.botmaker.sdk.api.bot.Launcher.class,
            com.botmaker.sdk.api.bot.LaunchChoice.class, com.botmaker.sdk.api.flow.Activity.class, com.botmaker.sdk.api.bot.Outcome.class);

    /**
     * The four vision results, each declared by the host off its {@code @Fills} method: what the pill says
     * names the type, never the call, so no label says "last" (it also draws {@code ImageFinder.find(…)}).
     */
    @Test
    void the_vision_results_are_filled_by_their_api_method() throws NoSuchMethodException {
        Map<Method, String> filled = Map.of(
                com.botmaker.sdk.api.vision.Vision.class.getMethod("lastMatch"), "Picture match",
                Matches.class.getMethod("none"), "Pictures found",
                com.botmaker.sdk.api.vision.Vision.class.getMethod("lastColorMatch"), "Colour match",
                com.botmaker.sdk.api.text.Text.class.getMethod("lastMatch"), "Text found");
        filled.forEach((method, label) -> {
            assertEquals(label, method.getAnnotation(Fills.class).label(), method::toString);
            assertFalse(label.toLowerCase(java.util.Locale.ROOT).contains("last"), label);
        });
        assertEquals(Set.of(MatchResult.class, Matches.class, ColorMatch.class, TextMatch.class),
                filled.keySet().stream().map(Method::getReturnType).collect(java.util.stream.Collectors.toSet()));
    }

    private final SdkPlugin plugin = new SdkPlugin();

    /** The host knows the plugin by its pom: the property names this class, the coordinate is its id. */
    @Test
    void the_pom_names_this_plugin_and_the_name_studio_shows() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<botmaker.plugin>" + SdkPlugin.class.getName() + "</botmaker.plugin>"),
                "pom.xml's botmaker.plugin must name " + SdkPlugin.class.getName());
        assertTrue(pom.contains("<name>BotMaker SDK</name>"), "pom.xml's <name> is what Studio shows");
        assertTrue(pom.contains("<properties>keep</properties>"),
                "flatten must keep properties, or the jar's pom loses botmaker.plugin");
    }

    /**
     * A project with no {@code Sdk.java} can be given one: the flow and the capture source share the
     * {@code Sdk} holder, each typed with what its method returns and starting as the template's does; the
     * pictures are an open set, an empty {@code Pictures} class.
     */
    @Test
    void each_managed_value_says_where_the_host_creates_it() {
        var byId = new java.util.HashMap<SdkValue.Id, com.botmaker.plugin.api.source.ManagedValue<?>>();
        for (SdkValue.Id id : SdkValue.Id.values()) {
            plugin.managedValues().stream().filter(v -> v.id().equals(ManagedValue.idOf(id))).findFirst()
                    .ifPresent(v -> byId.put(id, v));
        }
        var flows = byId.get(SdkValue.Id.FLOWS);
        assertEquals(SdkValue.class.getName(), flows.marker());
        assertEquals("Flows", flows.holder());
        assertInstanceOf(ManagedOpenSet.OfConstants.class, flows);
        assertEquals(com.botmaker.sdk.api.flow.Flow.class, flows.type());
        var run = byId.get(SdkValue.Id.RUN);
        assertEquals("Sdk", run.holder());
        assertEquals(com.botmaker.sdk.api.flow.Run.class, run.type());
        assertEquals(com.botmaker.sdk.api.flow.Run.NONE, assertInstanceOf(ManagedMethod.class, run).initial());
        var layout = byId.get(SdkValue.Id.FLOW_LAYOUT);
        assertEquals("Sdk", layout.holder());
        assertEquals(com.botmaker.sdk.api.flow.FlowLayout.class, layout.type());
        assertEquals(com.botmaker.sdk.api.flow.FlowLayout.NONE,
                assertInstanceOf(ManagedMethod.class, layout).initial());
        var capture = byId.get(SdkValue.Id.CAPTURE);
        assertEquals("Sdk", capture.holder());
        assertEquals(CaptureSource.class, capture.type());
        assertEquals(CaptureSource.desktop().getClass(),
                assertInstanceOf(ManagedParam.class, capture).initial().getClass());
        var settings = byId.get(SdkValue.Id.SETTINGS);
        assertEquals(com.botmaker.sdk.api.bot.BotSettings.class, settings.type());
        var pictures = byId.get(SdkValue.Id.PICTURES);
        assertEquals("Pictures", pictures.holder());
        assertInstanceOf(ManagedOpenSet.OfConstants.class, pictures);
        assertEquals(com.botmaker.sdk.api.vision.ImageTemplate.class, pictures.type());
        assertEquals("Activities", byId.get(SdkValue.Id.ACTIVITIES).holder());
        assertEquals(com.botmaker.sdk.api.bot.Outcome.class, byId.get(SdkValue.Id.ACTIVITIES).type());
        ManagedOpenSet.EnumGroups<?> activities = assertInstanceOf(ManagedOpenSet.EnumGroups.class,
                byId.get(SdkValue.Id.ACTIVITIES), "one enum of outcomes per activity");
        assertEquals("Collect", activities
                .byGroup(com.botmaker.sdk.api.flow.Activity.class, "Collect").orElseThrow().name());
        assertEquals("Zones", byId.get(SdkValue.Id.ZONES).holder());
        assertEquals(com.botmaker.sdk.api.geometry.Zone.class, byId.get(SdkValue.Id.ZONES).type());
        assertEquals("Digits", byId.get(SdkValue.Id.DIGITS).holder());
        assertEquals(com.botmaker.sdk.api.text.DigitSet.class, byId.get(SdkValue.Id.DIGITS).type());
        assertEquals("Texts", byId.get(SdkValue.Id.TEXTS).holder());
        assertEquals(com.botmaker.sdk.api.text.Words.class, byId.get(SdkValue.Id.TEXTS).type());
        // One declaration per constant, and nothing else.
        assertEquals(SdkValue.Id.values().length, byId.size());
        assertEquals(SdkValue.Id.values().length, plugin.managedValues().size());
    }

    /**
     * What a host checks when it loads this plugin, and that each holder lists every declaration it holds: a field
     * left out of its holder's method is declared nowhere.
     */
    @Test
    void a_host_can_load_the_plugin_and_every_holder_lists_every_field() {
        PluginTests.assertDeclared(SdkPlugin.class);
        for (Class<?> holder : List.of(SdkTypes.class, FlowTypes.class, ScreenTypes.class, CaptureTypes.class,
                SettingsTypes.class, SdkValues.class, SdkEditors.class, SdkToolbarItems.class, SdkRunOverlay.class,
                SdkOverlay.class, SdkAssistant.class)) {
            PluginTests.assertListsEveryField(holder);
        }
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
     * The nine buttons, their sections and their order within them. ⚙ Bot Settings joined on 2026-09-27, when
     * the settings became this plugin's managed value.
     *
     * <p>The order values are asserted rather than only the sequence, because a bar assembled from two
     * plugins interleaves by order and ties break on the plugin id — so a wrong number here moves a button
     * on a bar this test cannot see. {@code activity-flow} takes {@link ToolbarGroup#AUTHORING} at 10, which
     * is the slot Studio's own 🔀 Flow button vacated when the graph editor moved here on 2026-09-11; 🗺 Screens
     * ({@code screen-map}) follows it at 20 (2026-10-14).
     *
     * <p>The last two are {@link ToolbarGroup#OVERLAY}: they are drawn on the overlay editor's own row rather
     * than on the main bar, and their subject is the window the HUD is drawn over.     */
    @Test
    void the_toolbar_contributes_eleven_items_in_their_groups_and_orders() {
        List<ToolbarItem> items = plugin.toolbarItems();

        assertEquals(List.of("pilot", "capture-templates", "manage-templates",
                        "activity-flow", "screen-map", "capture-source", "bot-settings", "game", "project-setup",
                        "point-here", "picture-here"),
                items.stream().map(ToolbarItem::id).toList());

        assertEquals(List.of(ToolbarGroup.RUN, ToolbarGroup.TOOLS, ToolbarGroup.TOOLS,
                        ToolbarGroup.AUTHORING, ToolbarGroup.AUTHORING, ToolbarGroup.PROJECT, ToolbarGroup.PROJECT,
                        ToolbarGroup.PROJECT, ToolbarGroup.PROJECT, ToolbarGroup.OVERLAY, ToolbarGroup.OVERLAY),
                items.stream().map(ToolbarItem::group).toList());

        assertEquals(List.of(10, 20, 30, 10, 20, 50, 60, 30, 40, 10, 20),
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
                            TestContexts.voidCall((Ref.Void1<String>) Game::launchSteam), 0, "\"\"")), failures);
            ask(editor, "an unresolved call",
                    () -> editor.matches(TestContexts.slot(null, 0, "\"\"")), failures);
        }
        if (!failures.isEmpty()) fail(String.join("\n", failures));
    }

    /**
     * Every SDK type draws itself (6f, 6d, 6e) as a real picker — so `plugin validate`'s pickers check has
     * nothing to refuse here. The four vision results are the host's pills ({@code @Fills}).
     */
    @Test
    void every_sdk_type_draws_itself() {
        List<String> plain = new ArrayList<>();
        for (PluginType<?> type : plugin.types()) {
            if (!(type instanceof EditableType<?>)) plain.add(type.type().getSimpleName());
        }
        assertEquals(List.of(), plain);
        assertEquals(19, plugin.types().size(), "Direction went on 2026-10-01; Activity and Outcome came 2026-10-02;"
                + " Spot, Zone and DigitSet 2026-10-11; Launcher 2026-10-13; Spot went 2026-10-15; the four"
                + " vision results became @Fills 2026-10-19; LaunchChoice came 2026-10-19; Words 2026-10-22");
    }

    /**
     * Basics draws the JDK types and the SDK only its own (2026-09-27): no SDK editor claims a Duration.
     * Color is the one recorded exception, drawn here as an alternative, not tested here.
     */
    @Test
    void no_sdk_editor_claims_a_duration() {
        var slot = TestContexts.typedSlot(java.time.Duration.class, "java.time.Duration.ofMillis(1000L)");
        for (SlotEditor editor : plugin.slotEditors()) {
            assertFalse(editor.matches(slot), () -> editor + " claims a Duration slot");
        }
    }

    private static void ask(SlotEditor editor, String shape, Runnable call, List<String> failures) {
        try {
            call.run();
        } catch (RuntimeException | LinkageError e) {
            failures.add(editor.getClass().getName() + " threw on " + shape + ": " + e);
        }
    }
}
