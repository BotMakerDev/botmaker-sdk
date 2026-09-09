package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.MouseButton;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.Precision;
import com.botmaker.sdk.authoring.WireText;
import com.botmaker.shared.config.ValueGrammar;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * The SDK's seventeen value types, spelled for a running bot.
 *
 * <p>Declared in {@code META-INF/services/com.botmaker.shared.config.ValueGrammar}, so a bot that has the SDK
 * on its classpath can write {@code Settings.load("wait", Duration.class)} and nothing else has to be
 * arranged. A plugin that introduces a value type ships one of these beside it; this class is the worked
 * example the mechanism was designed against.
 *
 * <h2>Every reader is one {@code WireText} call, and that is the point</h2>
 *
 * <p>{@code WireText} is the SDK's grammar and it already has two readers — the editor, through
 * {@code SdkValueTypes}' {@code ValueCodec}s, and a running bot. Writing the parsers a second time here is
 * the exact drift the design refuses: the two halves would then disagree about what {@code "3s500ms"} means,
 * and the disagreement would be invisible until a bot behaved differently from what the Parameters window
 * showed. So every line below names a {@code WireText} method and adds nothing.
 *
 * <h2>What is not here</h2>
 *
 * <p><b>No contract type.</b> {@code ValueType}, {@code ValueCatalog} and {@code ValueCodec} live in
 * {@code com.botmaker.plugin.api.value}, which is <em>not on a bot's classpath</em> — the SDK declares
 * {@code botmaker-studio-api} {@code provided} deliberately. Naming one here would make this class
 * unloadable in exactly the process it exists for. {@code SdkValueTypes} names them and is editor-side only;
 * the two files describe the same seventeen types to two different audiences, and both go through
 * {@code WireText}, which is what keeps them one vocabulary rather than two.
 *
 * <p><b>No fallback of its own invention.</b> Each fallback below is what the corresponding
 * {@code WireText} reader already answers for text it cannot read, so <i>a bot never fails to start because
 * of its own configuration file</i> means the same thing whichever way the value is reached.
 */
public final class SdkGrammar implements ValueGrammar {

    /** The unreadable-image fallback: the same path {@code WireText.template("")} produces. */
    private static final ImageTemplate NO_IMAGE = WireText.template("");

    @Override
    public List<Reader<?>> readers() {
        return List.of(
                new Reader<>(String.class, WireText::text, s -> s, ""),
                new Reader<>(Boolean.class, WireText::flag, String::valueOf, false),
                new Reader<>(Integer.class, WireText::whole, String::valueOf, 0),
                new Reader<>(Double.class, WireText::decimal, String::valueOf, 0.0),
                new Reader<>(Character.class, WireText::letter, String::valueOf, 'a'),
                new Reader<>(Color.class, WireText::color, WireText::spellColor, Color.WHITE),
                new Reader<>(LocalDate.class, WireText::date, LocalDate::toString, WireText.date("")),
                new Reader<>(LocalTime.class, WireText::time, LocalTime::toString, LocalTime.MIDNIGHT),
                new Reader<>(Duration.class, WireText::duration,
                        d -> WireText.spellDuration(d.toMillis()), Duration.ZERO),
                new Reader<>(ImageTemplate.class, WireText::template, ImageTemplate::id, NO_IMAGE),
                new Reader<>(Precision.class, WireText::precision, WireText::spellPrecision,
                        WireText.precision("")),
                new Reader<>(Point.class, WireText::point, WireText::spellPoint, new Point(0, 0)),
                new Reader<>(Size.class, WireText::size, WireText::spellSize, new Size(0, 0)),
                new Reader<>(Rect.class, WireText::area, WireText::spellArea, new Rect(0, 0, 0, 0)),
                new Reader<>(Direction.class, WireText::direction, Direction::name, Direction.values()[0]),
                new Reader<>(Key.class, WireText::key, Key::name, Key.values()[0]),
                new Reader<>(MouseButton.class, WireText::mouseButton, MouseButton::name,
                        MouseButton.values()[0]));
    }
}
