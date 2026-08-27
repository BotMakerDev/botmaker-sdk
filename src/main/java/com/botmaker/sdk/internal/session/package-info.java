/**
 * Attaching a running bot to the private display session Studio launched it into.
 *
 * <p><b>Not versioned surface.</b> The bot's own source never mentions a session — the generated entry point
 * bootstraps it, and {@code Source.current()} is the name the bot writes.
 */
@Internal("the generated entry point attaches the session; a bot's own code never names it")
package com.botmaker.sdk.internal.session;

import com.botmaker.plugin.api.meta.Internal;
