/**
 * The SDK's implementation of {@link com.botmaker.plugin.api.StudioPlugin} — this library's other face, as
 * Studio's plugin #1.
 *
 * <p><b>Not versioned surface.</b> Studio reaches these types through the contract's interfaces and a
 * {@code ServiceLoader}, never by name, and a bot never sees them at all: the {@code botmaker-studio-api}
 * dependency is {@code optional} and so never transitive onto a bot's classpath.
 */
@Internal("Studio reaches the plugin through the contract, never by name; a bot never loads it")
package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.meta.Internal;
