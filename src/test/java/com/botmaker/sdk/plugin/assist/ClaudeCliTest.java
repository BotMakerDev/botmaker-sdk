// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Claude's CLI as the crew's client: the user's own claude.ai sign-in, headless and lean, never an API key. */
class ClaudeCliTest {

    private static String after(List<String> command, String flag) {
        return command.get(command.indexOf(flag) + 1);
    }

    @Test
    void aWorkerIsLeanHeadlessAndAlwaysNamesItsModel() {
        for (WorkerRole role : List.of(WorkerRole.READER, WorkerRole.EVALUATOR)) {
            List<String> command = new ClaudeCli().command("rank it", role, Path.of("/run/worker-mcp.json"));

            assertEquals(List.of("claude", "-p", "rank it"), command.subList(0, 3));
            assertEquals(role.model(), after(command, "--model"));
            assertEquals("json", after(command, "--output-format"));
            assertEquals("", after(command, "--setting-sources"));
            assertEquals("", after(command, "--tools"));
            assertEquals("/run/worker-mcp.json", after(command, "--mcp-config"));
            assertTrue(command.containsAll(List.of("--disable-slash-commands", "--no-session-persistence",
                    "--strict-mcp-config")), command.toString());
            assertFalse(command.contains("--bare"), "--bare reads only an API key");
            assertTrue(after(command, "--allowedTools").startsWith("mcp__botmaker__botmaker_sdk_explore"));
        }
    }

    @Test
    void onlyAClaudeAiSignInIsAvailable() {
        assertTrue(ClaudeCli.byClaudeAi("{\"loggedIn\": true, \"authMethod\": \"claude.ai\"}"));
        assertFalse(ClaudeCli.byClaudeAi("{\"loggedIn\": true, \"authMethod\": \"api_key\"}"));
        assertFalse(ClaudeCli.byClaudeAi("{\"loggedIn\": false, \"authMethod\": \"claude.ai\"}"));
        assertFalse(ClaudeCli.byClaudeAi("Not logged in"));
    }

    @Test
    void availableAsksAuthStatusAndAFailureIsNotAvailable() {
        List<List<String>> asked = new ArrayList<>();
        ClaudeCli signedIn = new ClaudeCli("claude", command -> {
            asked.add(command);
            return Optional.of("{\"loggedIn\": true, \"authMethod\": \"claude.ai\"}");
        });

        assertTrue(signedIn.available());
        assertEquals(List.of(List.of("claude", "auth", "status")), asked);
        assertFalse(new ClaudeCli("claude", command -> Optional.empty()).available());
    }
}
