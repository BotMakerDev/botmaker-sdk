package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.plugin.api.value.Ref;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.sdk.internal.capture.CurrentSource;
import com.botmaker.sdk.internal.capture.Desktop;
import com.botmaker.sdk.internal.capture.Monitor;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.capture.RegionSource;

import java.util.List;

/**
 * The calls a {@link CaptureSource} is written as. The type itself is {@link SdkTypes#CAPTURE_SOURCE}, declared
 * with the others and written through these ({@code writtenAsParts()}).
 *
 * <p>{@code CaptureSource} is an interface, so the host cannot take one apart through a single
 * {@link ComponentType}. Each concrete source is its own part: {@code Source.current()},
 * {@code CaptureSource.desktop()}, {@code .monitor(i)}, {@code .window("t")},
 * {@code .emulator("n")} and {@code CaptureSource.region(source, rect)}. The host reads a
 * {@code CaptureSource} slot as whichever of them the Java is, and writes a value back through the one
 * matching its class — which is what {@code writtenAsParts()} on the type says.
 */
public final class CaptureTypes {

    private CaptureTypes() {}

    /**
     * {@code Source.current()}. Built as the ambient source itself rather than by calling the factory, which
     * would resolve it to whatever the editor's own process has installed.
     */
    public static final DeclaredCall<CurrentSource> CURRENT = ComponentType.part(CurrentSource.class)
            .writtenAs(Source::current)
            .build(parts -> parts.isEmpty() ? new CurrentSource() : null);

    /** {@code CaptureSource.desktop()}. */
    public static final DeclaredCall<Desktop> DESKTOP = ComponentType.part(Desktop.class)
            .writtenAs(CaptureSource::desktop);

    /** {@code CaptureSource.monitor(index)}. */
    public static final DeclaredCall<Monitor> MONITOR = ComponentType.part(Monitor.class)
            .writtenAs(CaptureSource::monitor, Monitor::index);

    /** {@code CaptureSource.window("title")}. */
    public static final DeclaredCall<NamedWindow> WINDOW = ComponentType.part(NamedWindow.class)
            .writtenAs(CaptureSource::window, NamedWindow::titleSubstring);

    /**
     * {@code CaptureSource.emulator("name")}. It was {@code new EmulatorSource("name")} until 2026-10-01, when
     * the class moved to {@code internal}: a bot writes the factory, never the class behind it.
     */
    public static final DeclaredCall<EmulatorSource> EMULATOR = ComponentType.part(EmulatorSource.class)
            .writtenAs(CaptureSource::emulator, EmulatorSource::instanceName);

    /**
     * {@code CaptureSource.region(source, new Rect(x, y, w, h))}: a region is a part of which pixels the bot
     * reads, so it is a value too. A region of a region is written as one call inside the other.
     *
     * <p>Named with {@link Ref#member}, not a reference: {@code CaptureSource} has a static
     * {@code region(source, rect)} and an instance {@code source.region(rect)}, so {@code CaptureSource::region}
     * is ambiguous in javac. It is the one factory here that a reference cannot name.
     *
     * <p><b>It stays (decided 2026-09-28).</b> Both {@code region}s are what a bot writes, and a new-named
     * static factory would still leave {@link #REGION_CHAIN} reading the instance one through
     * {@code Ref.member}.
     */
    public static final DeclaredCall<RegionSource> REGION = ComponentType.part(RegionSource.class)
            .writtenAsMember(Ref.member(CaptureSource.class, "region", CaptureSource.class, Rect.class),
                    RegionSource::parent, RegionSource::sub);

    /**
     * {@code source.region(new Rect(…))}: the chain a person writes, read as the region it builds. It is an
     * instance factory, so the host never writes it: an edited region is written as {@link #REGION}. Named by
     * {@link Ref#member} for the reason {@link #REGION} is.
     */
    public static final DeclaredCall<RegionSource> REGION_CHAIN = ComponentType.part(RegionSource.class)
            .writtenAsMember(Ref.member(CaptureSource.class, "region", Rect.class),
                    RegionSource::parent, RegionSource::sub);

    /**
     * Every shape, in the order the host tries them: the six it writes, then the chained region, which it
     * only reads.
     */
    public static final List<ComponentType<?>> ALL =
            List.of(CURRENT, DESKTOP, MONITOR, WINDOW, EMULATOR, REGION, REGION_CHAIN);
}
