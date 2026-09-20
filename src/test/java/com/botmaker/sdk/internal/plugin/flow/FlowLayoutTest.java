package com.botmaker.sdk.internal.plugin.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gitignored half of a flow: where each card sits.
 *
 * <p>The claims worth testing here are the ones a user meets. A layout comes back as it was written. A
 * project that has never been opened reads as "lay it out for me" rather than as an error. And a card that
 * left the canvas leaves the file, because a sidecar that only grows is a sidecar nobody cleans up.
 */
class FlowLayoutTest {

    @Test
    void aLayoutComesBackAsItWasWritten(@TempDir Path resources) throws IOException {
        Map<String, FlowLayout.Spot> spots = new LinkedHashMap<>();
        spots.put("Collect", new FlowLayout.Spot(40, 120));
        spots.put("Rest", new FlowLayout.Spot(320.5, 80.25));
        FlowLayout.write(resources, new FlowLayout.Layout(spots, false));

        FlowLayout.Layout read = FlowLayout.read(resources);
        assertEquals(spots, read.spots());
        assertEquals(false, read.goHomeByDefault());
        assertEquals(320.5, read.spot("Rest").x());
    }

    /** A fresh clone has no sidecar — gitignored — and that is the ordinary state, not a broken one. */
    @Test
    void aProjectWithNoSidecarReadsAsNothingPlaced(@TempDir Path resources) {
        FlowLayout.Layout read = FlowLayout.read(resources);
        assertTrue(read.spots().isEmpty());
        assertNull(read.spot("Collect"));
        // New cards go home first until somebody says otherwise: the safe default, and the one a bot with no
        // sidecar has always had.
        assertTrue(read.goHomeByDefault());
    }

    @Test
    void aCardThatLeftTheCanvasLeavesTheFile(@TempDir Path resources) throws IOException {
        FlowLayout.write(resources, new FlowLayout.Layout(
                Map.of("Collect", new FlowLayout.Spot(0, 0), "Rest", new FlowLayout.Spot(10, 10)), true));
        FlowLayout.write(resources, new FlowLayout.Layout(
                Map.of("Collect", new FlowLayout.Spot(0, 0)), true));

        assertEquals(1, FlowLayout.read(resources).spots().size());
        assertNull(FlowLayout.read(resources).spot("Rest"));
    }

    /** It lands where both templates' {@code .gitignore} says it does, which is the whole point of it. */
    @Test
    void itLandsAtThePathTheTemplatesIgnore(@TempDir Path resources) throws IOException {
        FlowLayout.write(resources, FlowLayout.Layout.NONE);

        Path expected = resources.resolve("plugins").resolve("com.botmaker").resolve("sdk")
                .resolve("flow-layout.json");
        assertTrue(Files.isRegularFile(expected), expected.toString());
        assertTrue(FlowLayout.IGNORED_PATH.endsWith("plugins/com.botmaker/sdk/flow-layout.json"),
                FlowLayout.IGNORED_PATH);
    }
}
