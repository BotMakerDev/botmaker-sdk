package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Verdict;
import com.botmaker.sdk.plugin.types.ScreenTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An input's verdict as the assistant's tools say it (2026-11-09): {@code free}, {@code spends:Progress::gems},
 * {@code never:why} or {@code none}. A run exploring on its own presses a free input, presses one that spends only
 * while it may spend that fact, and never presses one never done.
 */
public final class Verdicts {

    /** What a verdict is given as, said once for every tool that takes one. */
    public static final String SAY = "free (nothing spent), spends:<fact> (Progress::gems, a fact add_fact made), "
            + "never:<why> (premium currency, real money) or none";

    /** {@code text} read as a verdict, or why it is none. */
    public record Read(Verdict verdict, String refused) {
        static Read refused(String why) {
            return new Read(null, why);
        }
    }

    private Verdicts() {}

    /** {@code text} as a verdict; a fact that spends is one of the bot's facts. */
    public static Read read(StudioServices services, String text) {
        String said = text == null ? "" : text.strip();
        int colon = said.indexOf(':');
        String kind = (colon < 0 ? said : said.substring(0, colon)).strip().toLowerCase(Locale.ROOT);
        String rest = colon < 0 ? "" : said.substring(colon + 1).strip();
        return switch (kind) {
            case "free" -> rest.isEmpty() ? new Read(Verdict.FREE, null) : Read.refused("free takes nothing after it.");
            case "none" -> rest.isEmpty() ? new Read(Verdict.NONE, null) : Read.refused("none takes nothing after it.");
            case "never" -> rest.isEmpty() ? Read.refused("Say why it is never pressed: never:spends gems.")
                    : new Read(Verdict.never(rest), null);
            case "spends" -> spends(services, rest);
            default -> Read.refused("No verdict \"" + said + "\": " + SAY + ".");
        };
    }

    private static Read spends(StudioServices services, String fact) {
        if (!ScreenTypes.isMethod(fact)) return Read.refused("spends names a fact: spends:Progress::gems.");
        String group = fact.substring(0, fact.indexOf("::"));
        String name = fact.substring(fact.indexOf("::") + 2);
        boolean known = services.pluginValues().runParameters().stream()
                .anyMatch(p -> p.group().equals(group) && p.name().equals(name));
        if (!known) return Read.refused("No fact " + fact + "; add it first (add_fact).");
        return new Read(new Verdict.Spends(ScreenTypes.fact(fact)), null);
    }

    /** What a hand form offers, as {@link #read} reads it: free, spending each of the bot's facts, never, none. */
    public static List<String> offered(StudioServices services) {
        List<String> offered = new ArrayList<>(List.of(NONE, "free"));
        if (services != null && services.pluginValues() != null) {
            services.pluginValues().runParameters().forEach(p -> offered.add("spends:" + p.group() + "::" + p.name()));
        }
        offered.add("never:spends premium currency");
        return offered;
    }

    /** {@code said} as a tool takes it: the form's {@link #NONE} and a blank are no verdict given. */
    public static String given(String said) {
        return said == null || said.isBlank() || NONE.equals(said) ? null : said.strip();
    }

    /** What a hand form shows for no verdict. */
    public static final String NONE = "none";

    /** {@code input}'s verdict as {@link #read} reads it back. */
    public static String said(Input input) {
        return switch (input.verdict()) {
            case Verdict.Free ignored -> "free";
            case Verdict.Spends ignored -> "spends:" + ScreenTypes.spentOf(input);
            case Verdict.Never(String why) -> "never:" + why;
            case Verdict.None ignored -> "none";
        };
    }
}
