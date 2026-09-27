package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.toolkit.Types;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.emulator.EmulatorSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.capture.CurrentSource;
import com.botmaker.sdk.internal.capture.Desktop;
import com.botmaker.sdk.internal.capture.Monitor;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.capture.RegionSource;
import com.botmaker.sdk.plugin.editors.CaptureSourceEditors;

import java.util.List;

import static com.botmaker.plugin.toolkit.Types.method;

/**
 * A {@link CaptureSource} as values: the type itself, and the six calls one is written as.
 *
 * <p>{@code CaptureSource} is an interface, so the host cannot take one apart through a single
 * {@link ComponentType}. Each concrete source is its own: {@code Source.current()},
 * {@code CaptureSource.desktop()}, {@code .monitor(i)}, {@code .window("t")},
 * {@code new EmulatorSource("n")} and {@code CaptureSource.region(source, rect)}. The host reads a
 * {@code CaptureSource} slot as whichever of them the Java is, and writes a value back through the one
 * matching its class. Each call's parts are its factory's parameters ({@link Types#call}), so the two cannot
 * drift apart.
 */
public final class CaptureTypes {

    private CaptureTypes() {}

    /**
     * The declared type. A fresh one is the ambient source, which keeps following the project's source when
     * that changes later; a concrete source would freeze the declaration into what was true when it was
     * made.
     *
     * <p>Drawn as a pill opening the source tiles ({@link CaptureSourceEditors}), the same picker as the
     * toolbar's Capture Source.
     */
    public static final Types.Declared<CaptureSource> CAPTURE_SOURCE =
            Types.editable(CaptureSource.class, CurrentSource::new, () -> CaptureSourceEditors::source)
                    .preview(() -> CaptureSourceEditors::preview);

    /** {@code Source.current()}. */
    public static final ComponentType<CurrentSource> CURRENT = Types.call(CurrentSource.class,
            method(Source.class, "current"), value -> List.of(), parts -> new CurrentSource());

    /** {@code CaptureSource.desktop()}. */
    public static final ComponentType<Desktop> DESKTOP = Types.call(Desktop.class,
            method(CaptureSource.class, "desktop"), value -> List.of(), parts -> new Desktop());

    /** {@code CaptureSource.monitor(index)}. */
    public static final ComponentType<Monitor> MONITOR = Types.call(Monitor.class,
            method(CaptureSource.class, "monitor", int.class),
            value -> List.of(value.index()), parts -> new Monitor(Types.whole(parts, 0)));

    /** {@code CaptureSource.window("title")}. */
    public static final ComponentType<NamedWindow> WINDOW = Types.call(NamedWindow.class,
            method(CaptureSource.class, "window", String.class),
            value -> List.of(value.titleSubstring()), parts -> new NamedWindow(Types.text(parts, 0)));

    /**
     * {@code new EmulatorSource("name")}: a constructor, since {@code EmulatorSource} is not one of
     * {@code CaptureSource}'s factories. It is still correct Java, and the bot captures from the emulator.
     */
    public static final ComponentType<EmulatorSource> EMULATOR = Types.call(EmulatorSource.class,
            Types.constructor(EmulatorSource.class, String.class),
            value -> List.of(value.instanceName()), parts -> new EmulatorSource(Types.text(parts, 0)));

    /**
     * {@code CaptureSource.region(source, new Rect(x, y, w, h))}: a region is a part of which pixels the bot
     * reads, so it is a value too. A region of a region is written as one call inside the other.
     */
    public static final ComponentType<RegionSource> REGION = Types.call(RegionSource.class,
            method(CaptureSource.class, "region", CaptureSource.class, Rect.class),
            value -> List.of(value.parent(), value.sub()), CaptureTypes::region);

    /**
     * {@code source.region(new Rect(…))}: the chain a person writes, read as the region it builds. It is an
     * instance factory, so the host never writes it: an edited region is written as {@link #REGION}.
     */
    public static final ComponentType<RegionSource> REGION_CHAIN = Types.call(RegionSource.class,
            method(CaptureSource.class, "region", Rect.class),
            value -> List.of(value.parent(), value.sub()), CaptureTypes::region);

    private static RegionSource region(List<Object> parts) {
        return parts.size() == 2 && parts.get(0) instanceof CaptureSource of && parts.get(1) instanceof Rect sub
                ? new RegionSource(of, sub) : null;
    }

    /**
     * Every shape, in the order the host tries them: the six it writes, then the chained region, which it
     * only reads.
     */
    public static final List<ComponentType<?>> ALL =
            List.of(CURRENT, DESKTOP, MONITOR, WINDOW, EMULATOR, REGION, REGION_CHAIN);
}
