package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.StudioPlugin;
import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.sdk.api.authoring.SdkVersion;
import com.botmaker.sdk.internal.plugin.catalog.Catalogs;

/**
 * The BotMaker SDK, as a Studio plugin.
 *
 * <p>This is plugin #1 and the only one that ships today, and it is deliberately <b>an ordinary
 * implementation of {@link StudioPlugin} with no back door</b>: no {@code instanceof SdkPlugin} branch in
 * the host, no package-private hook, no second interface. What the SDK gets that a third-party plugin does
 * not is a set of <em>privileges</em> — it is always loaded, it owns the primary slot editors, and Studio's
 * own pom declares the dependency — never a wider API. One implementor proves little about a contract; an
 * implementor that cannot cheat proves rather more.
 *
 * <h2>Why the version is an argument</h2>
 *
 * <p>{@link #catalog(String)} takes the version <em>the bot pins</em>, not this jar's. A bot compiles
 * against the SDK it names in its pom, which may be older than the one the editor bundles, so the palette
 * has to answer for that jar. This is the same rule {@code Authoring} enforces by taking an
 * {@link SdkVersion} first — stated here as a parameter for the same reason.
 *
 * <h2>Where this class may live, and where it may not</h2>
 *
 * <p>Under {@code com.botmaker.sdk.plugin}, never {@code com.botmaker.sdk.api} — a bot cannot write this
 * name down, and nothing under {@code api} may reference a {@code com.botmaker.plugin.api} type. That
 * invariant is what makes the contract's {@code <optional>true</optional>} scope safe: the class is in the
 * jar and cannot link on a bot's classpath, exactly like an SLF4J binding, and no bot ever reaches it.
 */
public final class SdkPlugin implements StudioPlugin {

    /** The stable identifier the host files this plugin's contributions under. */
    public static final String ID = "com.botmaker.sdk";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "BotMaker SDK";
    }

    /**
     * The palette for the SDK version {@code pinnedVersion} names.
     *
     * <p>Total, and empty on anything unrecognised — a pin this jar has never heard of (a bot newer than the
     * editor), a malformed pin, or a version released before catalogs existed. Empty means <em>uncurated</em>
     * and the host widens rather than empties; see {@link Catalogs}.
     *
     * <p>A blank, absent or {@code -SNAPSHOT} pin is <em>this very jar</em>, not an unknown version — the
     * rule {@link SdkVersion#ofPin} exists to state, and the reason a dev-run editor gets a curated palette
     * rather than an empty one.
     */
    @Override
    public PaletteCatalog catalog(String pinnedVersion) {
        return SdkVersion.ofPin(pinnedVersion).map(Catalogs::forVersion).orElse(PaletteCatalog.empty());
    }
}
