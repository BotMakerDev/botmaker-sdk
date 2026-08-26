package com.botmaker.sdk.internal.plugin.catalog;

import com.botmaker.plugin.api.catalog.FacadeEntry;
import com.botmaker.plugin.api.catalog.MemberEntry;
import com.botmaker.plugin.api.catalog.MemberId;
import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.sdk.api.authoring.SdkVersion;
import com.botmaker.sdk.plugin.SdkPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalogs' build gate: <b>a broken entry fails the build, not a menu.</b>
 *
 * <p>Most of what could go wrong is already a compile error — an entry is a method reference, so a renamed
 * or deleted member breaks {@code V1_1_0.java} itself. What javac cannot check is what happens after
 * {@code SerializedLambda} resolves the reference, and that is what this covers: that the id it produces
 * names a member that really exists and is really public, that the member is filed under its own declaring
 * class, that nothing is offered twice, and that nothing outside {@code com.botmaker.sdk.api} — nothing a
 * bot cannot write down — reached the palette.
 */
class ApiCatalogTest {

    private static final String API_PACKAGE = "com.botmaker.sdk.api.";

    /** Forcing every catalog to build is itself half the test: an entry that cannot resolve throws here. */
    private static List<PaletteCatalog> everyCatalog() {
        List<PaletteCatalog> built = new ArrayList<>();
        for (SdkVersion version : SdkVersion.all()) {
            built.add(Catalogs.forVersion(version));
        }
        return built;
    }

    @Test
    @DisplayName("every known version has a catalog, and every catalog builds")
    void everyVersionIsCatalogued() {
        for (SdkVersion version : SdkVersion.all()) {
            PaletteCatalog catalog = Catalogs.forVersion(version);
            assertFalse(catalog.isEmpty(), version + " has no catalog: add a class for it in this package");
        }
    }

    @Test
    @DisplayName("every entry resolves to a public member of its own facade")
    void everyEntryResolves() {
        for (PaletteCatalog catalog : everyCatalog()) {
            for (FacadeEntry facade : catalog.facades()) {
                for (MemberEntry member : facade.members()) {
                    MemberId id = member.id();
                    assertSame(facade.type(), id.declaringClass(),
                            id + " is filed under " + facade.qualifiedName());
                    Method resolved = resolve(id);
                    assertTrue(resolved != null, id + " names no method of " + facade.qualifiedName());
                    assertTrue(Modifier.isPublic(resolved.getModifiers()), id + " is not public");
                }
            }
        }
    }

    @Test
    @DisplayName("only types a bot can write down are catalogued")
    void everyFacadeIsPublicApi() {
        for (PaletteCatalog catalog : everyCatalog()) {
            for (FacadeEntry facade : catalog.facades()) {
                assertTrue(Modifier.isPublic(facade.type().getModifiers()),
                        facade.qualifiedName() + " is not public");
                assertTrue(facade.qualifiedName().startsWith(API_PACKAGE),
                        facade.qualifiedName() + " is not under " + API_PACKAGE
                                + "; a bot cannot write that name down, so it cannot be offered");
            }
        }
    }

    @Test
    @DisplayName("nothing is offered twice, and no simple name is claimed twice")
    void noDuplicates() {
        for (PaletteCatalog catalog : everyCatalog()) {
            Set<String> simpleNames = new HashSet<>();
            for (FacadeEntry facade : catalog.facades()) {
                assertTrue(simpleNames.add(facade.simpleName()),
                        "two facades share the simple name " + facade.simpleName()
                                + "; the editor keys imports on it");
                Set<MemberId> seen = new HashSet<>();
                for (MemberEntry member : facade.members()) {
                    assertTrue(seen.add(member.id()), member.id() + " is offered twice");
                }
            }
        }
    }

    @Test
    @DisplayName("a later version's catalog contains everything the previous one offered")
    void versionsOnlyGrow() {
        List<SdkVersion> versions = SdkVersion.all();
        for (int i = 1; i < versions.size(); i++) {
            PaletteCatalog older = Catalogs.forVersion(versions.get(i - 1));
            PaletteCatalog newer = Catalogs.forVersion(versions.get(i));
            for (FacadeEntry facade : older.facades()) {
                for (MemberEntry member : facade.members()) {
                    assertTrue(newer.facade(facade.type())
                                    .filter(f -> f.members().contains(member))
                                    .isPresent(),
                            versions.get(i) + " dropped " + member.id() + "; if that is deliberate the"
                                    + " deprecation window has to say so, and release.sh has to be told");
                }
            }
        }
    }

    @Test
    @DisplayName("the plugin answers for the version the bot pins, not for this jar")
    void pluginAnswersPerPin() {
        SdkPlugin plugin = new SdkPlugin();
        assertEquals(Catalogs.forVersion(SdkVersion.V1_1_0), plugin.catalog("1.1.0"));
        assertEquals(Catalogs.forVersion(SdkVersion.V1_1_0), plugin.catalog("v1.1.0"));
        assertEquals(Catalogs.forVersion(SdkVersion.latest()), plugin.catalog("0.0.0-SNAPSHOT"),
                "a dev pin is this very jar; refusing it would empty the palette in every dev build");
        assertTrue(plugin.catalog("9.9.9").isEmpty(),
                "a bot newer than this jar is uncurated, and the editor widens rather than empties");
        assertEquals(Catalogs.forVersion(SdkVersion.latest()), plugin.catalog(null),
                "an absent pin is SdkVersion.ofPin's blank case — this jar, not an unknown version");
    }

    // ----------------------------------------------------------------------------------------- helpers

    /** The method {@code id} names, or {@code null}. Matches on the JVM descriptor, so overloads differ. */
    private static Method resolve(MemberId id) {
        for (Method candidate : id.declaringClass().getDeclaredMethods()) {
            if (candidate.getName().equals(id.name()) && descriptorOf(candidate).equals(id.descriptor())) {
                return candidate;
            }
        }
        return null;
    }

    private static String descriptorOf(Method method) {
        StringBuilder out = new StringBuilder("(");
        for (Class<?> parameter : method.getParameterTypes()) {
            out.append(typeOf(parameter));
        }
        return out.append(')').append(typeOf(method.getReturnType())).toString();
    }

    private static String typeOf(Class<?> type) {
        if (type.isArray()) return "[" + typeOf(type.getComponentType());
        if (!type.isPrimitive()) return "L" + type.getName().replace('.', '/') + ";";
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        return "D";
    }
}
