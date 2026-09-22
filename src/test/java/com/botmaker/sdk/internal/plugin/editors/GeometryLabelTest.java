package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The geometry editors' label: the value in, the text on the collapsed pill out.
 *
 * <p>Inherited from Studio's {@code CoordinatePickerLabelTest}, which guarded the same property until these
 * editors moved here on 2026-08-27. The number on the pill is what a user reads, and getting it wrong shows
 * one coordinate while the bot runs another.
 *
 * <h2>It asserted a parser until 2026-09-22, and the parser is what went</h2>
 *
 * <p>Every case here took a {@code String} — {@code "new Point(10, 20)"} — because {@code ValueContext}
 * handed an editor source text and this editor read the numbers out of it. Four of the cases were about
 * nothing else: a missing argument defaulting to zero, extra arguments ignored, the constructed type not
 * checked, Java integer literals read leniently. All four described <b>how this plugin's copy of a Java
 * reader behaved</b>, and there were three such copies in the repository disagreeing with each other.
 *
 * <p>A value crosses as a value now. The editor asks {@link ValueContext#value(Class)} and the host, which
 * owns the one grammar, answers with a {@link Point} or with nothing. So the questions worth asking here
 * are the two that are still this plugin's: <b>a value labels as its numbers</b>, and <b>an expression the
 * host could not decode labels as the author's own text</b> rather than being overwritten with a default.
 * What a half-written constructor decodes to is the host's question and is asserted where the grammar is.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class GeometryLabelTest {

    /** A slot holding {@code value}, which is what the host hands an editor once it has decoded one. */
    private static ValueContext holding(Object value) {
        return TestContexts.typedSlot(value.getClass().getName(), "").withValue(value);
    }

    /** A slot holding an expression nothing decoded — a variable, a call, something hand-written. */
    private static ValueContext unreadable(String typeName, String source) {
        return TestContexts.typedSlot(typeName, source);
    }

    // --- a value labels as its numbers ---

    @Test
    void a_point_is_read_back_as_its_coordinates() {
        assertEquals("10, 20", GeometryEditors.pointLabel(holding(new Point(10, 20))));
    }

    @Test
    void a_rect_is_read_back_as_its_origin_and_size() {
        assertEquals("10, 20  640×480", GeometryEditors.rectLabel(holding(new Rect(10, 20, 640, 480))));
    }

    @Test
    void a_size_is_read_back_as_its_two_dimensions() {
        assertEquals("640 × 480", GeometryEditors.sizeLabel(holding(new Size(640, 480))));
    }

    @Test
    void negative_coordinates_survive_the_round_trip() {
        assertEquals("-1920, -50", GeometryEditors.pointLabel(holding(new Point(-1920, -50))),
                "a left-hand or upper monitor has negative screen coordinates");
        assertEquals("-1920, 0  100×100",
                GeometryEditors.rectLabel(holding(new Rect(-1920, 0, 100, 100))));
    }

    @Test
    void the_origin_is_a_value_like_any_other() {
        assertEquals("0, 0", GeometryEditors.pointLabel(holding(new Point(0, 0))));
        assertEquals("0, 0  0×0", GeometryEditors.rectLabel(holding(new Rect(0, 0, 0, 0))));
    }

    // --- an expression nobody decoded is shown as written ---

    /**
     * The case this editor must never get wrong.
     *
     * <p>{@code target.center()} is a point the bot computes; there is no value to put on a pill and no
     * default that would be honest. Showing the author's own text says <em>this is what is in your file</em>,
     * where a {@code 0, 0} would claim a coordinate they never set — and the pill is read-only there, so the
     * next redraw cannot write the claim back into the file.
     */
    @Test
    void an_expression_the_host_could_not_decode_is_shown_as_the_author_wrote_it() {
        assertEquals("target.center()",
                GeometryEditors.pointLabel(unreadable(Point.class.getName(), "target.center()")));
        assertEquals("ORIGIN",
                GeometryEditors.pointLabel(unreadable(Point.class.getName(), "ORIGIN")));
        assertEquals("bounds()",
                GeometryEditors.rectLabel(unreadable(Rect.class.getName(), "bounds()")));
    }

    /** Nothing written yet: the placeholder, which is what a freshly inserted block shows. */
    @Test
    void an_empty_slot_shows_its_placeholder() {
        assertEquals("Choose point…", GeometryEditors.pointLabel(unreadable(Point.class.getName(), "")));
        assertEquals("Choose region…", GeometryEditors.rectLabel(unreadable(Rect.class.getName(), "")));
        assertEquals("Choose size…", GeometryEditors.sizeLabel(unreadable(Size.class.getName(), "")));
    }
}
