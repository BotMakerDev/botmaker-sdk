package com.botmaker.sdk.api.flow;

import com.botmaker.plugin.api.palette.Facade;
import com.botmaker.sdk.api.meta.Since;

/**
 * What to do before a node's activity runs, to be sure it starts from a screen it recognises.
 *
 * <p>Only one thing so far, and that is deliberate: the generated driver's per-activity choice was a
 * {@code boolean goHome}, and the set it stood for was always going to grow (go home, restart the game, do
 * nothing). Naming it now costs one enum and spares an API break later.
 *
 * <p>Whatever "home" means is the bot's own: the walker is handed the project's {@code goHome} step, the same
 * one {@link com.botmaker.sdk.api.bot.Bot#start(Runnable, Runnable)} recovers with.
 */
@Since("1.1.0")
@Facade(category = "flow", categoryLabel = "Flow", role = "VALUE", order = 106)
public enum Recovery {

    /** Run the activity from wherever the previous one left off. */
    NONE,

    /** Run the bot's {@code goHome} step first — but only once the node's activity is known to be active. */
    GO_HOME
}
