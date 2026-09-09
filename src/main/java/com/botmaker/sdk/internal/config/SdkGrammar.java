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
import com.botmaker.plugin.basics.store.ValueGrammar;

import java.util.List;

/**
 * The SDK's eight value types, spelled for a running bot.
 *
 * <p>Declared in {@code META-INF/services/com.botmaker.plugin.basics.store.ValueGrammar}, so a bot with the
 * SDK on its classpath can write {@code Settings.load("target", Rect.class)} and nothing else has to be
 * arranged. A plugin that introduces a value type ships one of these beside it; this class is the worked
 * example the mechanism was designed against.
 *
 * <p><b>The nine JDK types are not here since 2026-09-09</b> — {@code String}, {@code Boolean},
 * {@code Integer}, {@code Double}, {@code Character}, {@code Color}, {@code LocalDate}, {@code LocalTime}
 * and {@code Duration} are read by {@code botmaker-plugin-basics}' {@code BasicsGrammar}, which arrives on
 * every bot's classpath with this jar. That is the mechanism working rather than a change to it: two
 * grammars, disjoint, indexed together by {@code Settings}. Leaving them here as well would be the one thing
 * it refuses — two grammars claiming one type, with a bot's answer decided by jar order.
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
 * the two files describe the same eight types to two different audiences, and both go through
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
