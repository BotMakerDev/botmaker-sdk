package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.SlotContext;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.TypeRef;
import com.botmaker.plugin.api.ValueContext;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The geometry editors' label round-trip: source in, the text on the collapsed pill out.
 *
 * <p>Inherited, deliberately, from Studio's {@code CoordinatePickerLabelTest}, which guarded the same
 * property until these editors moved here on 2026-08-27. Every case below was one of its assertions, because
 * the move must not quietly change what a user reads: the number on the pill is read back out of the
 * {@code new Point(…)} the last pick wrote, and getting it wrong shows one coordinate while the bot runs
 * another.
 *
 * <p>What did change is <b>how</b> it is read. The old picker had a JDT {@code ClassInstanceCreation} and
 * could ask whether the node was a constructor; the contract hands over source text and no syntax tree
 * (rule 3), so the same question is now asked of the string. These cases are what pins the two readings to
 * the same answers — and no JavaFX toolkit is needed to ask them, which the old test did need.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class GeometryLabelTest {

    /** A slot holding {@code source}, with nothing else an editor could reach for. */
    private static ValueContext slot(String source) {
        return new SlotContext() {
            @Override public String currentSource() { return source; }

            @Override public String enclosingClass() { return null; }

            @Override public String enclosingMethod() { return null; }

            @Override public int argIndex() { return -1; }

            @Override public void replaceWith(String javaExpression, String... importsNeeded) {}

            @Override public TypeRef type() {
                return new TypeRef() {
                    @Override public String simpleName() { return ""; }

                    @Override public String qualifiedName() { return ""; }
                };
            }

            @Override public List<String> value() { return List.of(source); }

            @Override public void set(List<String> value) {}

            @Override public StudioServices services() { return null; }
        };
    }

    private static String point(String source) {
        return GeometryEditors.pointLabel(slot(source));
    }

    private static String rect(String source) {
        return GeometryEditors.rectLabel(slot(source));
    }

    // --- The round-trip ---

    @Test
    void a_point_constructor_is_read_back_as_its_coordinates() {
        assertEquals("10, 20", point("new Point(10, 20)"));
    }

    @Test
    void a_rect_constructor_is_read_back_as_its_origin_and_size() {
        assertEquals("10, 20  640×480", rect("new Rect(10, 20, 640, 480)"));
    }

    @Test
    void negative_coordinates_survive_the_round_trip() {
        assertEquals("-1920, -50", point("new Point(-1920, -50)"),
                "a left-hand or upper monitor has negative screen coordinates");
        assertEquals("-1920, 0  100×100", rect("new Rect(-1920, 0, 100, 100)"));
    }

    /** A half-written constructor is what a freshly inserted block looks like before the user picks. */
    @Test
    void a_missing_argument_defaults_to_zero_rather_than_failing() {
        assertEquals("10, 0", point("new Point(10)"));
        assertEquals("0, 0", point("new Point()"));
        assertEquals("10, 20  0×0", rect("new Rect(10, 20)"));
        assertEquals("0, 0  0×0", rect("new Rect()"));
    }

    /** Extra arguments are ignored rather than shifting the read — the first N positions are the contract. */
    @Test
    void only_the_arguments_the_type_has_are_read() {
        assertEquals("1, 2", point("new Point(1, 2, 3)"));
        assertEquals("1, 2  3×4", rect("new Rect(1, 2, 3, 4, 5)"));
    }

    /**
     * A slot filled with a variable, a call or a computed expression is shown verbatim: an editor is not the
     * only way to fill one, and rewriting a user's {@code target.center()} into "0, 0" would be a lie about
     * what the bot does.
     */
    @Test
    void a_non_constructor_expression_is_shown_verbatim() {
        assertEquals("origin", point("origin"));
        assertEquals("bounds", rect("bounds"));
        assertEquals("target.center()", point("target.center()"));
    }

    /**
     * The type being constructed is not checked. Which editor a slot gets was decided from its declared type
     * one layer up, so this reads positionally and a second check here would be dead code.
     */
    @Test
    void the_type_being_constructed_is_not_checked() {
        assertEquals("1, 2", point("new Rect(1, 2, 3, 4)"));
    }

    @Test
    void an_empty_slot_asks_to_be_filled_rather_than_showing_zeroes() {
        assertEquals("Choose point…", point(""));
        assertEquals("Choose region…", rect("   "));
    }

    // --- Reading the numbers ---

    /**
     * The values come out of source, so they are Java literals rather than plain digits: a {@code long}
     * suffix and digit separators are things a person actually writes, and neither may cost the user their
     * label in the middle of rendering a block.
     */
    @Test
    void java_integer_literals_are_read_leniently() {
        assertEquals("100, 20", point("new Point(100L, 20)"));
        assertEquals("1000, 20", point("new Point(1_000, 20)"));
        assertEquals("new Point(abc, 20)", point("new Point(abc, 20)"),
                "text that is not a number at all is not silently read as zero");
    }

    /**
     * A comma inside a string literal is not an argument separator. Unreachable for geometry today, but
     * {@code Slots.arguments} is what every editor in this package reads its value with, and the editors that
     * follow do take strings.
     */
    @Test
    void a_comma_inside_a_string_literal_does_not_split_the_arguments() {
        assertEquals(List.of("\"a, b\"", "2"), Slots.arguments("Foo.of(\"a, b\", 2)"));
        assertEquals(List.of("bar(1, 2)", "3"), Slots.arguments("Foo.of(bar(1, 2), 3)"));
    }
}
