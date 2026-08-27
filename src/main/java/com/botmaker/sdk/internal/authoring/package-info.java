/**
 * The generator: {@code ProjectModel} in, a bot's {@code .java} files out.
 *
 * <p>{@code SourceEmitter} builds the text, {@code LiteralWriter} turns a stored value into a Java literal,
 * {@code ProjectWriter} renders every file before committing any of them, and {@code ValueJson} is the
 * {@code activities.json} parser the contract deliberately does not carry.
 *
 * <p><b>Not versioned surface.</b> A bot receives the output; it never calls the writer.
 */
@Internal("a bot receives generated source, it never calls the generator")
package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.meta.Internal;
