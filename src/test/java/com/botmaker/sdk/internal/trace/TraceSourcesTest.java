package com.botmaker.sdk.internal.trace;

import com.botmaker.sdk.api.bot.ActivityContext;
import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.bot.Watchdog;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.capture.Window;
import com.botmaker.sdk.api.emulator.Emulator;
import com.botmaker.sdk.api.emulator.Emulators;
import com.botmaker.sdk.api.interaction.Keyboard;
import com.botmaker.sdk.api.interaction.Mouse;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.api.launch.Game;
import com.botmaker.sdk.api.launch.LaunchTarget;
import com.botmaker.sdk.api.launch.Target;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.api.util.TraceSource;
import com.botmaker.sdk.api.vision.ImageClicker;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageWaiter;
import com.botmaker.sdk.api.vision.Pixel;
import com.botmaker.sdk.api.vision.Text;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.session.SessionBootstrap;
import com.botmaker.shared.Diag;
import com.botmaker.shared.ipc.TelemetryEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A debug line's source is the class that wrote it, so no SDK line spells its own {@code [Name]}. The table is
 * the console's promise: each class resolves to the prefix its lines were written with by hand before
 * 2026-09-29, so a run reads exactly as it did.
 */
class TraceSourcesTest {

    @TraceSource("Farming")
    private static final class Named {
        static String fromLambda() {
            Supplier<String> inside = TraceSources::caller;
            return inside.get();
        }
    }

    private final List<TelemetryEvent.Log> lines = new ArrayList<>();

    @AfterEach
    void restore() {
        Diag.setSink(null);
        Debug.enable();
    }

    @Test
    void eachSdkClassIsTracedUnderThePrefixItUsedToWrite() {
        Map<Class<?>, String> before = new LinkedHashMap<>();
        before.put(ActivityContext.class, "Activity");
        before.put(Bot.class, "Bot");
        before.put(PopupGuard.class, "Popup");
        before.put(Watchdog.class, "Watchdog");
        before.put(Source.class, "Source");
        before.put(Window.class, "Window");
        before.put(Emulator.class, "Emulator");
        before.put(Emulators.class, "Emulator");
        before.put(Keyboard.class, "Keyboard");
        before.put(Mouse.class, "Mouse");
        before.put(Wait.class, "Wait");
        before.put(Game.class, "Game");
        before.put(LaunchTarget.class, "Target");
        before.put(Target.class, "Target");
        before.put(ImageFinder.class, "Vision");
        before.put(ImageClicker.class, "Vision");
        before.put(ImageWaiter.class, "Vision");
        before.put(Pixel.class, "Vision");
        before.put(Text.class, "Vision");
        before.put(NamedWindow.class, "Source");
        before.put(SessionBootstrap.class, "Session");

        before.forEach((type, prefix) -> assertEquals(prefix, TraceSources.of(type), type.getName()));
    }

    @Test
    void aNestedClassOrALambdaIsTracedUnderItsTopLevelClass() {
        assertEquals("TraceSourcesTest", TraceSources.of(Named.class.getNestHost()));
        assertEquals("TraceSourcesTest", Named.fromLambda(), "the annotation is read off the top-level class");
        assertEquals("Target", TraceSources.of(LaunchTarget.Steam.class));
    }

    @Test
    void aBotsOwnLineIsPrintedUnderItsClassAndAnExplicitPrefixWins() {
        Debug.enable();
        Diag.setSink(lines::add);

        Debug.log("hello");
        Debug.log("[Custom] mine");
        Debug.error("oops");

        assertEquals(List.of("TraceSourcesTest", "Custom", "TraceSourcesTest"),
                lines.stream().map(TelemetryEvent.Log::source).toList());
        assertEquals("hello", lines.getFirst().text());
        assertEquals(TelemetryEvent.Log.ERROR, lines.get(2).level());
    }

    /** The host filters by the class and method that wrote a line; a lambda counts as the method it sits in. */
    @Test
    void aLineCarriesTheClassAndMethodThatWroteIt() {
        Debug.enable();
        Diag.setSink(lines::add);

        Debug.log("direct");
        Runnable inLambda = () -> Debug.log("from a lambda");
        inLambda.run();

        assertEquals(List.of(TraceSourcesTest.class.getName(), TraceSourcesTest.class.getName()),
                lines.stream().map(TelemetryEvent.Log::writerClass).toList());
        assertEquals(List.of("aLineCarriesTheClassAndMethodThatWroteIt", "aLineCarriesTheClassAndMethodThatWroteIt"),
                lines.stream().map(TelemetryEvent.Log::writerMethod).toList());
    }
}
