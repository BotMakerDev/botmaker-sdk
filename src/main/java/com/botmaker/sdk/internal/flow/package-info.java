/**
 * The walk over a {@link com.botmaker.sdk.api.flow.FlowGraph}: run a node's activity, read its outcome,
 * follow the edge, mind the step budget.
 *
 * <p><b>Not versioned surface.</b> The graph is the surface and it is where the generated table points;
 * {@code FlowWalker} is what reads it, and a bot never calls the walker itself.
 */
@Internal("the generated table names FlowGraph; the walk over it is plumbing")
package com.botmaker.sdk.internal.flow;

import com.botmaker.plugin.api.meta.Internal;
