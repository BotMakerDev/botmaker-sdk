package com.botmaker.sdk.api.plan;

/**
 * What a run runs: a {@code Flow} or a {@link Plan}, and nothing else. They never contain each other. Not sealed only
 * because Java seals across packages in a named module alone, and the SDK has none.
 */
public interface Routine {}
