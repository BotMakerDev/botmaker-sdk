package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.ParameterGroup;
import com.botmaker.plugin.api.StudioPlugin;
import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.sdk.internal.authoring.SdkValueTypes;
import com.botmaker.sdk.internal.authoring.SourceEmitter;
import com.botmaker.sdk.internal.plugin.catalog.Catalog;

import java.util.List;

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
 * <h2>Why the version is still an argument, though this plugin ignores it</h2>
 *
 * <p>{@link #catalog(String)} takes the version <em>the bot pins</em>, not this jar's, and until 2026-08-26
 * the SDK answered it from a per-version class. It no longer does: there is one catalog, generated from the
 * annotations on the facades in <em>this</em> build, and the pin is not consulted.
 *
 * <p>The rule it used to serve is unchanged, and is met somewhere better. What an older pin may be offered
 * is this catalog <b>intersected with the bot's own resolved jar</b>, which {@code SdkSurfaceService}
 * already computes from bytecode — so a member this build added is still absent from an older bot, because
 * that bot's jar does not contain it. A frozen class per version could only restate, by hand, what the jar
 * already says; and it had to be edited whenever a member was deleted, which made it untruthful about the
 * past exactly when it mattered.
 *
 * <p>The parameter stays on the contract regardless. It is not the SDK's to remove — another plugin may
 * ship per-version curation and needs somewhere to read the pin from — and a surface that narrows to fit
 * its only implementor is the back door this class exists to refuse.
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

    /**
     * Built once. Every entry resolves a {@code Method} reflectively, which is worth doing exactly once in
     * the editor and never at all on a bot's classpath, where this class is not loaded.
     */
    private static final PaletteCatalog CATALOG = Catalog.build();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "BotMaker SDK";
    }

    /**
     * This build's palette, whatever {@code pinnedVersion} says — see the class comment for why the
     * parameter survives an implementation that does not read it.
     *
     * <p>Total: every pin, including a malformed one, a blank one and one naming a version newer than this
     * jar, gets the same answer. Nothing here can be empty, so nothing here can widen the host's menus by
     * accident; narrowing to a pin is {@code SdkSurfaceService}'s intersection against the bot's own jar.
     */
    @Override
    public PaletteCatalog catalog(String pinnedVersion) {
        return CATALOG;
    }

    /**
     * The seventeen types a project variable could hold before there was a registry to hold them in.
     *
     * <p>They are registered through the same builder any plugin uses, and their ids are the constant names
     * of the enum they used to be, so every project ever written keeps its meaning. That is the whole test
     * of the surface: the vocabulary that was hard-coded into the host is now contributed by a plugin, and
     * it had to give up nothing to become contributable.
     */
    @Override
    public ValueCatalog valueTypes() {
        return SdkValueTypes.CATALOG;
    }

    /**
     * One section, {@code Parameters} — the class every bot has always had, now declared rather than assumed.
     *
     * <p>Its id is blank ({@link ParameterGroup#DEFAULT_ID}), which is the whole of the migration: a variable
     * in a project written before groups existed carries no group, reads back as blank, and is therefore this
     * plugin's. A second plugin declares {@code ParameterGroup.of("discord", "DiscordParameters")} and gets
     * its own section, its own file and its own namespace.
     *
     * <p>Total in the pin, like {@link #catalog(String)}: the class has existed in every SDK there has been.
     */
    @Override
    public List<ParameterGroup> parameters(String pinnedVersion) {
        return List.of(SourceEmitter.SDK_PARAMETERS);
    }
}
