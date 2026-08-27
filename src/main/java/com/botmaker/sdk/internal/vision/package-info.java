/**
 * Bookkeeping behind the image templates a project ships — what was captured, at what scale, from where.
 *
 * <p><b>Not versioned surface.</b> A bot names {@code ImageTemplate} and the generated {@code Templates}
 * constants; the metadata beside a template on disk is read by the loader and by Studio, never by a bot.
 */
@Internal("read by the template loader and by Studio; a bot names ImageTemplate")
package com.botmaker.sdk.internal.vision;

import com.botmaker.plugin.api.meta.Internal;
