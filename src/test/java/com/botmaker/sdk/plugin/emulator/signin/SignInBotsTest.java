// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.signin;

import com.botmaker.plugin.api.run.SideRun;
import com.botmaker.plugin.api.run.SideRuns;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignInBotsTest {

    @TempDir
    Path dir;

    private final List<String> asked = new ArrayList<>();

    private SideRuns host(Path copy) {
        return new SideRuns() {
            @Override
            public SideRun start(Path bot, Map<String, String> properties, Map<String, String> environment) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Path catalogCopy(String repo) throws IOException {
                asked.add(repo);
                if (copy == null) throw new IOException("not released yet");
                return copy;
            }
        };
    }

    @Test
    void theHostsCatalogCopyOfTheSignInBot() throws Exception {
        Path copy = dir.resolve("copy");

        assertEquals(copy, SignInBots.of(() -> host(copy), null).get());
        assertEquals(List.of(SignInBots.REPO), asked);
    }

    @Test
    void noCopyRefusesWithTheHostsWhy() {
        IOException e = assertThrows(IOException.class, () -> SignInBots.of(() -> host(null), "").get());

        assertEquals("not released yet", e.getMessage());
    }

    @Test
    void aHostWithNoCatalogSaysSo() {
        SideRuns none = SideRuns.NONE;

        IOException e = assertThrows(IOException.class, () -> SignInBots.of(() -> none, null).get());

        assertTrue(e.getMessage().contains("catalog"), e.getMessage());
    }

    @Test
    void aDevCheckoutIsRunInstead() throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");

        assertEquals(dir, SignInBots.of(() -> host(null), dir.toString()).get());
        assertTrue(asked.isEmpty(), "the catalog is not asked");
    }

    @Test
    void aDevFolderWithNoBotIsRefused() {
        IOException e = assertThrows(IOException.class,
                () -> SignInBots.of(() -> host(dir), dir.resolve("none").toString()).get());

        assertTrue(e.getMessage().contains(SignInBots.DEV_FOLDER), e.getMessage());
    }
}
