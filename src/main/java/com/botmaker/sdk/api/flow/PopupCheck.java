package com.botmaker.sdk.api.flow;

import com.botmaker.plugin.api.palette.Facade;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.meta.Since;

/**
 * Whether the {@link PopupGuard} runs while one node's activity does.
 *
 * <p>A closed set rather than the {@code boolean} the generated driver used to write, for the reason the
 * repo's conventions give: a flow table is read by a human, and {@code PopupCheck.OFF} says at the call site
 * what {@code false} in the third argument position does not.
 *
 * <p>It is carried by <em>every</em> node, not only the ones that opt out, because
 * {@link PopupGuard#enabled(boolean)} is process-global: a node that said nothing would inherit whatever the
 * node before it left set.
 */
@Since("1.1.0")
@Facade(category = "flow", categoryLabel = "Flow", role = "VALUE", order = 105)
public enum PopupCheck {

    /** Check for popups between this node's steps — the default for a new activity. */
    ON,

    /** Leave popups alone while this node runs. */
    OFF
}
