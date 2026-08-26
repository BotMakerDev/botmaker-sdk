package com.botmaker.sdk.internal.plugin.catalog;

import com.botmaker.plugin.api.catalog.PaletteCatalog;

/**
 * What SDK 1.2.0 offers Studio's palette: <b>everything 1.1.0 offered, and nothing more.</b>
 *
 * <p>That is a finding rather than a placeholder. 1.2.0's whole addition is
 * {@code com.botmaker.sdk.api.authoring} — the project model and the {@code Authoring} facade — and none of
 * it is palette surface: a bot never calls it. The editor does, on the bot's behalf, which is exactly the
 * distinction the catalog exists to draw.
 *
 * <p>The class is still worth having. A version with no catalog class at all means <em>uncurated</em> and
 * opens every menu; a version whose catalog is its predecessor's means <em>curated, and unchanged</em>. And
 * this is the file the next release writes its deltas on:
 *
 * <pre>{@code
 * return V1_2_0.build().toBuilder()
 *         .facade(Mouse.class, Category.INTERACTION)
 *             .<Point, Duration>add(Mouse::hover)
 *         .build();
 * }</pre>
 *
 * <p>Frozen on release, like {@link V1_1_0} — see its class comment for why an edit here is a removal.
 */
final class V1_2_0 {

    private V1_2_0() {
    }

    static PaletteCatalog build() {
        return V1_1_0.build();
    }
}
