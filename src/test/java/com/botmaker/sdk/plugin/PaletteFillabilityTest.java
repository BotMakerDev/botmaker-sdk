package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.catalog.FacadeEntry;
import com.botmaker.plugin.api.catalog.MemberEntry;
import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.plugin.basics.values.BasicsTypes;
import com.botmaker.sdk.plugin.types.SdkTypes;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every block the palette offers can be filled in: each parameter of each offered member has a type some
 * plugin declares (so the host seeds it with a value, and an editor draws it), an annotation that claims an
 * editor, or a shape the host fills itself.
 *
 * <p>A parameter of any other type is seeded with {@code null} by Studio and shown read-only, so the dropped
 * block either threw on its first run or did nothing: {@code Text}'s {@code OcrOptions} overloads and
 * {@code Time}'s {@code ZoneId} ones did both until 2026-09-29. Hiding one overload is impossible
 * ({@code @Hidden} hides a name), so such a member must take the {@code null}, and is listed below with why.
 */
class PaletteFillabilityTest {

    /**
     * Offered members whose unfillable parameter is read as a default when it arrives as {@code null}. Each entry
     * is {@code Owner#member(parameterType)}.
     */
    private static final Set<String> NULL_MEANS_DEFAULT = Set.of(
            "Text#read(OcrOptions)", "Text#find(OcrOptions)", "Text#findExact(OcrOptions)",
            "Text#findMatching(OcrOptions)", "Text#findFuzzy(OcrOptions)", "Text#findAll(OcrOptions)",
            "Text#readAll(OcrOptions)", "Text#waitFor(OcrOptions)", "Text#waitForGone(OcrOptions)",
            "Time#now(ZoneId)", "Time#setDefaultTimeZone(ZoneId)");

    @Test
    void every_offered_parameter_can_be_filled() {
        Set<Class<?>> declared = new HashSet<>();
        for (PluginType<?> t : BasicsTypes.ALL) declared.add(t.type());
        for (PluginType<?> t : SdkTypes.ALL) declared.add(t.type());

        List<String> unfillable = new ArrayList<>();
        Set<String> excused = new HashSet<>();
        for (FacadeEntry facade : catalog().facades()) {
            if (!facade.offered()) continue;
            Set<String> names = new HashSet<>();
            for (MemberEntry member : facade.members()) names.add(member.id().name());
            for (Method m : facade.type().getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers()) || !Modifier.isStatic(m.getModifiers())) continue;
                if (!names.contains(m.getName())) continue;
                for (Parameter p : m.getParameters()) {
                    if (fillable(p, declared)) continue;
                    String key = facade.type().getSimpleName() + "#" + m.getName() + "("
                            + p.getType().getSimpleName() + ")";
                    if (NULL_MEANS_DEFAULT.contains(key)) excused.add(key);
                    else unfillable.add(key);
                }
            }
        }
        assertTrue(unfillable.isEmpty(), "offered but unfillable: " + unfillable);
        // An entry nothing needs any more (its type got an editor) is removed, so the list stays a list of debts.
        Set<String> stale = new HashSet<>(NULL_MEANS_DEFAULT);
        stale.removeAll(excused);
        assertTrue(stale.isEmpty(), "fillable now, drop from NULL_MEANS_DEFAULT: " + stale);
    }

    private static boolean fillable(Parameter p, Set<Class<?>> declared) {
        Class<?> type = p.getType();
        if (p.getAnnotations().length > 0) return true;                      // @SteamAppId, @ActivityName, …
        if (type.isArray()) type = type.getComponentType();                   // a run: its elements are drawn
        if (declared.contains(type)) return true;
        if (type.isEnum()) return true;                                       // the host's constant dropdown
        return type.isInterface() && type.isAnnotationPresent(FunctionalInterface.class); // a lambda body
    }

    private static PaletteCatalog catalog() {
        String classes = SdkPlugin.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        try (ScanResult scan = new ClassGraph().overrideClasspath(classes).enableAnnotationInfo().scan()) {
            return PaletteCatalog.of(scan.getClassesWithAnnotation(Palette.class).loadClasses()
                    .toArray(Class<?>[]::new));
        }
    }
}
