package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.sdk.api.vision.ColorMatch;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Matches;
import com.botmaker.sdk.api.vision.TextMatch;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

import java.util.Map;

/**
 * The "picker" for the four vision results — {@code MatchResult}, {@code Matches}, {@code ColorMatch},
 * {@code TextMatch} — which is a pill saying, in plain words, what the bot fills in.
 *
 * <p>There is nothing here anyone configures: {@code Vision.lastMatch()} means <em>the match the bot found a
 * moment ago</em>, and any value typed in would be one the bot never sees. So the honest control is a value
 * that explains itself (the maintainer's call, 2026-09-27), with the Java shown in its tooltip, as written.
 * No Reset: the contract gives an editor no way to write a call, and the host writes the fresh call itself
 * when the declaration is made.
 */
public final class ResultEditors {

    private static final Map<Class<?>, String> WORDS = Map.of(
            MatchResult.class, "Last picture match",
            Matches.class, "Pictures found",
            ColorMatch.class, "Last colour match",
            TextMatch.class, "Last text read");

    private ResultEditors() {}

    public static Node pill(ValueContext ctx, Class<?> type) {
        Label pill = Pills.value(words(type), words(type));
        pill.setTooltip(new Tooltip(help(type, ctx.source())));
        return pill;
    }

    /** What the pill says for {@code type}; its simple name for a type this class does not know. */
    public static String words(Class<?> type) {
        return WORDS.getOrDefault(type, type.getSimpleName());
    }

    /** The tooltip: the Java as written, when there is any, then what the bot does with it. */
    public static String help(Class<?> type, String source) {
        String explanation = "the bot fills this in while it runs (" + words(type).toLowerCase()
                + "), so there is nothing to set here.";
        if (source == null || source.isBlank()) {
            return "T" + explanation.substring(1);
        }
        return source + "\n\nWritten as a call: " + explanation;
    }
}
