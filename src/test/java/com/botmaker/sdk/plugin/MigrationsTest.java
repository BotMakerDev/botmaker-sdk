// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.plugin.types.SdkTypes;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SDK's own {@code @Migrate} rules. The host never runs one: it substitutes a site's parts into the body. So the
 * body is checked here by calling it as Java would, and the rule's text by what the host reads in the sources jar.
 */
class MigrationsTest {

    @Test
    void anInstanceBecomesTheAndroidPlaceOfItsName() {
        // The review-focus bot: Instance.named("MEmu_1") opens as Place.android("MEmu_1").
        assertEquals(Place.android("MEmu_1"), Migrations.instance("MEmu_1"));
        assertEquals(Place.android(""), Migrations.instance(""), "an instance naming none is the place naming none");
    }

    @Test
    void theRuleIsATypeRuleOverInstanceInTheSources() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/botmaker/sdk/plugin/Migrations.java"));
        assertTrue(source.contains("@Migrate(value = Instance.class, kind = Migrate.Kind.TYPE)"), source);
    }

    @Test
    @SuppressWarnings("removal")
    void theOldTypeIsStillDeclaredSoTheRuleCanReadALeftoverValue() throws ReflectiveOperationException {
        Constructor<SdkTypes> made = SdkTypes.class.getDeclaredConstructor();
        made.setAccessible(true);
        List<PluginType<?>> declared = made.newInstance().types();
        assertTrue(declared.contains(SdkTypes.INSTANCE) && declared.contains(SdkTypes.PLACE), declared::toString);
    }
}
