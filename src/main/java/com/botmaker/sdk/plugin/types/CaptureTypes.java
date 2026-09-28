package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.plugin.api.value.DeclaredType;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.plugin.api.value.Ref;
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

/**
 * A {@link CaptureSource} as values: the type itself, and the calls one is written as.
 *
 * <p>{@code CaptureSource} is an interface, so the host cannot take one apart through a single
 * {@link ComponentType}. Each concrete source is its own part: {@code Source.current()},
 * {@code CaptureSource.desktop()}, {@code .monitor(i)}, {@code .window("t")},
 * {@code new EmulatorSource("n")} and {@code CaptureSource.region(source, rect)}. The host reads a
 * {@code CaptureSource} slot as whichever of them the Java is, and writes a value back through the one
 * matching its class — which is what {@code writtenAsParts()} on the type says.
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
    public static final DeclaredType<CaptureSource> CAPTURE_SOURCE = PluginType.value(CaptureSource.class)
            .fresh(CurrentSource::new)
            .editor(() -> CaptureSourceEditors::source)
            .preview(() -> CaptureSourceEditors::preview)
            .writtenAsParts();

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
     * {@code new EmulatorSource("name")}: a constructor, since {@code EmulatorSource} is not one of
     * {@code CaptureSource}'s factories. It is still correct Java, and the bot captures from the emulator.
     */
    public static final DeclaredCall<EmulatorSource> EMULATOR = ComponentType.part(EmulatorSource.class)
            .writtenAs(EmulatorSource::new, EmulatorSource::instanceName);

    /**
     * {@code CaptureSource.region(source, new Rect(x, y, w, h))}: a region is a part of which pixels the bot
     * reads, so it is a value too. A region of a region is written as one call inside the other.
     *
     * <p>Named with {@link Ref#member}, not a reference: {@code CaptureSource} has a static
     * {@code region(source, rect)} and an instance {@code source.region(rect)}, so {@code CaptureSource::region}
     * is ambiguous in javac. It is the one factory here that a reference cannot name.
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
