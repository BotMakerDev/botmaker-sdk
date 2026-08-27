/**
 * Defaults a generated project starts from, before its own {@code Parameters} overwrite them.
 *
 * <p><b>Not versioned surface.</b> A bot reads its settings through {@code BotSettings}; these are the
 * values behind that, not a name it writes.
 */
@Internal("the values behind BotSettings, which is the name a bot actually writes")
package com.botmaker.sdk.internal.config;

import com.botmaker.plugin.api.meta.Internal;
