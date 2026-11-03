package com.botmaker.sdk.internal.trace;

import com.botmaker.plugin.api.TraceLine;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.internal.launch.LaunchTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A debug line's source is the simple name of the top-level class that wrote it, and nothing else: no SDK line
 * spells its own {@code [Name]}, and since 2026-09-30 no annotation renames a class.
 */
class TraceSourcesTest {

    private static final class Nested {
        static String fromLambda() {
            Supplier<String> inside = TraceSources::caller;
            return inside.get();
        }
    }

    @AfterEach
    void restore() {
        RecordingChannel.stop();
    }

    @Test
    void aClassIsTracedUnderItsOwnSimpleName() {
        assertEquals("ImageFinder", TraceSources.of(ImageFinder.class));
        assertEquals("LaunchTarget", TraceSources.of(LaunchTarget.class));
    }

    @Test
    void aNestedClassOrALambdaIsTracedUnderItsTopLevelClass() {
        assertEquals("TraceSourcesTest", TraceSources.of(Nested.class));
        assertEquals("TraceSourcesTest", Nested.fromLambda());
        assertEquals("LaunchTarget", TraceSources.of(LaunchTarget.Steam.class));
    }

    @Test
    void aBotsOwnLineIsPrintedUnderItsClassAndAnExplicitPrefixWins() {
        List<TraceLine> lines = RecordingChannel.start(true).lines;

        Debug.log("hello");
        Debug.log("[Custom] mine");
        Debug.error("oops");

        assertEquals(List.of("TraceSourcesTest", "Custom", "TraceSourcesTest"),
                lines.stream().map(TraceLine::source).toList());
        assertEquals("hello", lines.getFirst().text());
        assertEquals(TraceLine.Level.ERROR, lines.get(2).level());
    }

    /** An error reaches the trace with debugging off; a debug line does not. */
    @Test
    void anErrorIsTracedOnAQuietRun() {
        List<TraceLine> lines = RecordingChannel.start(false).lines;

        Debug.log("chatter");
        Debug.error("crashed");

        assertEquals(List.of("crashed"), lines.stream().map(TraceLine::text).toList());
    }

    /** The host filters by the class and method that wrote a line; a lambda counts as the method it sits in. */
    @Test
    void aLineCarriesTheClassAndMethodThatWroteIt() {
        List<TraceLine> lines = RecordingChannel.start(true).lines;

        Debug.log("direct");
        Runnable inLambda = () -> Debug.log("from a lambda");
        inLambda.run();

        assertEquals(List.of(TraceSourcesTest.class.getName(), TraceSourcesTest.class.getName()),
                lines.stream().map(TraceLine::writerClass).toList());
        assertEquals(List.of("aLineCarriesTheClassAndMethodThatWroteIt", "aLineCarriesTheClassAndMethodThatWroteIt"),
                lines.stream().map(TraceLine::writerMethod).toList());
    }
}
