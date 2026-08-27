/**
 * The {@link com.botmaker.sdk.api.capture.CaptureSource} implementations — a desktop, a monitor, a named
 * window, a cropped region, a private display session.
 *
 * <p><b>Not versioned surface</b>, and the original worked example of the rule. These moved out of
 * {@code api.*} in 1.1.0 precisely because a bot only ever <em>receives</em> one, from
 * {@code Window.named(…)} or {@code Source.current()}; it never writes the name down. Being uncatalogued by
 * construction is how the palette stops offering them.
 */
@Internal("a bot receives a CaptureSource from a factory; it never names the implementation")
package com.botmaker.sdk.internal.capture;

import com.botmaker.plugin.api.meta.Internal;
