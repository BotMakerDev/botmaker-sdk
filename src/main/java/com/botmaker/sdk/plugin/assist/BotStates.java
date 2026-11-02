package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.screens.ButtonState;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.flow.BotConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a look of a button can mean, for 🗺 Screens and the assistant: the SDK's normal, selected and disabled, and
 * the bot's own states in its {@code States} (2026-11-02). A state named that the bot does not hold yet is added to
 * its enum before the look naming it is written.
 */
public final class BotStates {

    private BotStates() {}

    private static final ManagedSet<ButtonState> SET = ManagedSet.of(SdkValues.STATES);

    /** A constant's name the way a bot's enum holds one: {@code MAXED}, {@code TWO_STARS}. */
    private static final Pattern CONSTANT = Pattern.compile("[A-Z][A-Z0-9_]*");

    /** The SDK's meanings a look can be given, in the order a picker lists them. */
    private static final List<ButtonState> BUILT_IN = List.of(State.NORMAL, State.SELECTED, State.DISABLED);

    /** Every state a look can be given: the SDK's, then the bot's in its file's order. */
    public static List<ButtonState> offered(PluginValues values) {
        List<ButtonState> offered = new ArrayList<>(BUILT_IN);
        if (values == null) return offered;
        for (Member member : SET.members(values)) {
            // Only the two enums a run reads: a state in another is one parse could not read back.
            if (member.grouped() && (ButtonState.BLOCKS.equals(member.group())
                    || ButtonState.PRESSES.equals(member.group()))) {
                offered.add(BotConstants.state(member.group(), member.name()));
            }
        }
        return offered;
    }

    /**
     * The state {@code text} names: an SDK one by its id ({@code disabled}), or one of the bot's as
     * {@code Blocks.MAXED} or {@code Presses.BADGE}, any case; empty for anything else.
     */
    public static Optional<ButtonState> parse(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String said = text.strip();
        for (ButtonState state : BUILT_IN) {
            if (state.id().equalsIgnoreCase(said) || state.displayName().equalsIgnoreCase(said)) {
                return Optional.of(state);
            }
        }
        int dot = said.indexOf('.');
        if (dot < 0) return Optional.empty();
        String group = said.substring(0, dot);
        String name = said.substring(dot + 1).replace(' ', '_').toUpperCase(Locale.ROOT);
        String held = group.equalsIgnoreCase(ButtonState.BLOCKS) ? ButtonState.BLOCKS
                : group.equalsIgnoreCase(ButtonState.PRESSES) ? ButtonState.PRESSES : null;
        if (held == null || !CONSTANT.matcher(name).matches()) return Optional.empty();
        return Optional.of(BotConstants.state(held, name));
    }

    /** How a picker shows {@code state}: the SDK's by its name, the bot's as {@code Blocks.MAXED}. */
    public static String label(ButtonState state) {
        return state instanceof State ? state.displayName() : state.id();
    }

    /**
     * Adds {@code state} to the bot's {@code States} when it is the bot's and the bot does not hold it yet: its enum
     * too when that is missing. Empty when it is held now; else why it could not be added.
     */
    public static Optional<String> declare(PluginValues values, ButtonState state) {
        if (!(state instanceof BotConstants.StateConstant constant)) return Optional.empty();
        if (values == null) return Optional.of("No project is open to add " + constant.id() + " to.");
        Member member = Member.of(constant.group(), constant.name());
        if (SET.contains(values, member)) return Optional.empty();
        Optional<String> missing = values.create(SET.value());
        if (missing.isPresent()) return missing;
        if (!SET.groups(values).contains(constant.group())) {
            return SET.addGroup(values, constant.group(), List.of(constant.name()));
        }
        return SET.add(values, member, constant);
    }
}
