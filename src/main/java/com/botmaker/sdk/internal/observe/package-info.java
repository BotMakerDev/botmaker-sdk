/**
 * The telemetry a running bot reports — clicks, matches, swipes, the surface it acted on — and the IPC that
 * carries them to Studio.
 *
 * <p><b>Not versioned surface.</b> The wire format is a private contract between a bot's SDK and the Studio
 * watching it, and both ends of it ship together in the pair the user has installed.
 */
@Internal("a private wire between a running bot and the Studio watching it")
package com.botmaker.sdk.internal.observe;

import com.botmaker.plugin.api.meta.Internal;
