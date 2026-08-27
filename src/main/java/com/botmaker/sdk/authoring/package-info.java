/**
 * Reading and writing a bot project's {@code activities.json}, and the model records that describe it.
 *
 * <p><b>Not versioned surface.</b> A bot never writes {@code ProjectModel} down — the only callers are Studio
 * and the SDK's own generator — so this package sits outside {@code com.botmaker.sdk.api} by the boundary rule
 * (<em>can a bot write the name down?</em>) and is marked {@link com.botmaker.plugin.api.meta.Internal}
 * accordingly. It moved here from {@code api.authoring} on 2026-08-27 for that reason and one other: its
 * records name {@code com.botmaker.plugin.api.value} types in their components, and nothing under
 * {@code api.*} may reference a {@code com.botmaker.plugin.api} type.
 *
 * <p>Its consumers are the host and the generator, both of which resolve it from the jar the project pins, so
 * a change here is an ordinary cross-module release rather than an API break.
 */
@Internal("the authoring model is read by Studio and the generator, never written down by a bot")
package com.botmaker.sdk.authoring;

import com.botmaker.plugin.api.meta.Internal;
