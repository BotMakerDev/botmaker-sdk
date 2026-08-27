package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.catalog.FacadeEntry;
import com.botmaker.plugin.api.catalog.MemberEntry;
import com.botmaker.plugin.api.catalog.MemberId;
import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalog's build gate: <b>a broken entry fails the build, not a menu.</b>
 *
 * <p>Since 2026-08-27 the catalog is not generated at all — {@link SdkPlugin} calls
 * {@code PaletteCatalog.of(Mouse.class, …)} and the members are reflected off those classes. That removes the
 * failure this test was originally written against (a hand-written entry naming a member somebody renamed):
 * members are <em>discovered</em> now, so there is nothing left to go stale, and the class list is javac-checked
 * because it is written as class literals.
 *
 * <p>What is still worth checking is everything reflection cannot decide on its own: that every id names a
 * public member of its own facade, that nothing is offered twice, that no simple name is claimed twice (the
 * editor keys imports on it), that nothing outside {@code com.botmaker.sdk.api} — nothing a bot can write down
 * — reached the palette, and that nothing {@code @Hidden} was offered anyway.
 *
 * <p>And one that is new and load-bearing: {@link PaletteCatalog#problems()} must be <b>empty</b>. Load-time
 * validation degrades rather than throwing — the precedent is {@code ValueCatalog.merge}, because a malformed
 * catalog must never be the reason a project will not open — so in production a duplicated
 * {@code @PaletteDefault} costs one entry and a logged sentence. Here it costs a red build, which is where that
 * mistake should actually be found.
 */
class ApiCatalogTest {

    private static final String API_PACKAGE = "com.botmaker.sdk.api.";

    /** Building it is itself half the test: this is the reflection pass the editor will run. */
    private static PaletteCatalog catalog() {
        return new SdkPlugin().catalog(null);
    }

    @Test
    @DisplayName("the catalog builds and is not empty")
    void catalogBuilds() {
        assertFalse(catalog().isEmpty(),
                "no facade was catalogued: is @Palette missing, or is the class list in SdkPlugin empty?");
    }

    @Test
    @DisplayName("nothing was reported as malformed")
    void noProblems() {
        assertTrue(catalog().problems().isEmpty(),
                "PaletteCatalog.of reported: " + String.join("; ", catalog().problems()));
    }

    @Test
    @DisplayName("every entry resolves to a public member of its own facade")
    void everyEntryResolves() {
        for (FacadeEntry facade : catalog().facades()) {
            for (MemberEntry member : facade.members()) {
                MemberId id = member.id();
                assertSame(facade.type(), id.declaringClass(),
                        id + " is filed under " + facade.qualifiedName());
                Executable resolved = resolve(id);
                assertTrue(resolved != null, id + " names no member of " + facade.qualifiedName());
                assertTrue(Modifier.isPublic(resolved.getModifiers()), id + " is not public");
            }
        }
    }

    @Test
    @DisplayName("only types a bot can write down are catalogued")
    void everyFacadeIsPublicApi() {
        for (FacadeEntry facade : catalog().facades()) {
            assertTrue(Modifier.isPublic(facade.type().getModifiers()),
                    facade.qualifiedName() + " is not public");
            assertTrue(facade.qualifiedName().startsWith(API_PACKAGE),
                    facade.qualifiedName() + " is not under " + API_PACKAGE
                            + "; a bot cannot write that name down, so it cannot be offered");
            assertTrue(facade.type().isAnnotationPresent(Palette.class),
                    facade.qualifiedName() + " is catalogued without carrying @Palette");
        }
    }

    @Test
    @DisplayName("nothing @Hidden reached the palette")
    void nothingHiddenIsOffered() {
        for (FacadeEntry facade : catalog().facades()) {
            assertEquals(!facade.type().isAnnotationPresent(Hidden.class), facade.offered(),
                    facade.qualifiedName() + ": offered disagrees with @Hidden on the type");
            for (MemberEntry member : facade.members()) {
                Executable resolved = resolve(member.id());
                assertTrue(resolved == null || !resolved.isAnnotationPresent(Hidden.class),
                        member.id() + " is @Hidden and was offered anyway");
            }
        }
    }

    @Test
    @DisplayName("nothing is offered twice, and no simple name is claimed twice")
    void noDuplicates() {
        Set<String> simpleNames = new HashSet<>();
        for (FacadeEntry facade : catalog().facades()) {
            assertTrue(simpleNames.add(facade.simpleName()),
                    "two facades share the simple name " + facade.simpleName()
                            + "; the editor keys imports on it");
            Set<MemberId> seen = new HashSet<>();
            for (MemberEntry member : facade.members()) {
                assertTrue(seen.add(member.id()), member.id() + " is offered twice");
            }
        }
    }

    @Test
    @DisplayName("the plugin answers the same catalog for every pin, including none")
    void pluginIgnoresThePin() {
        SdkPlugin plugin = new SdkPlugin();
        PaletteCatalog current = plugin.catalog("1.1.0");
        assertFalse(current.isEmpty(), "an old pin must not empty the palette");
        assertSame(current, plugin.catalog("v1.1.0"));
        assertSame(current, plugin.catalog("0.0.0-SNAPSHOT"),
                "a dev pin is this very jar; refusing it would empty the palette in every dev build");
        assertSame(current, plugin.catalog("9.9.9"),
                "a bot newer than this jar is still offered this build's curation; the narrowing that"
                        + " matters is SdkSurfaceService's intersection against the bot's own jar");
        assertSame(current, plugin.catalog(null),
                "an absent pin must be total too — nothing here reads the pin at all");
    }

    // ----------------------------------------------------------------------------------------- helpers

    /** The member {@code id} names, or {@code null}. Matches on the JVM descriptor, so overloads differ. */
    private static Executable resolve(MemberId id) {
        if (id.isConstructor()) {
            for (Constructor<?> candidate : id.declaringClass().getDeclaredConstructors()) {
                if (MemberId.of(candidate).descriptor().equals(id.descriptor())) return candidate;
            }
            return null;
        }
        for (Method candidate : id.declaringClass().getDeclaredMethods()) {
            if (candidate.getName().equals(id.name()) && MemberId.of(candidate).descriptor().equals(id.descriptor())) {
                return candidate;
            }
        }
        return null;
    }
}
