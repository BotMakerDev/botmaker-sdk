package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.toolkit.AbstractPluginType;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.emulator.EmulatorSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.capture.CurrentSource;
import com.botmaker.sdk.internal.capture.Desktop;
import com.botmaker.sdk.internal.capture.Monitor;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.capture.RegionSource;
import javafx.scene.Node;

import java.lang.reflect.Executable;
import java.util.List;

/**
 * A {@link CaptureSource} as values: the type itself, and the six calls one is written as.
 *
 * <p>{@code CaptureSource} is an interface, so the host cannot take one apart through a single
 * {@link ComponentType}. Each concrete source is its own: {@code Source.current()},
 * {@code CaptureSource.desktop()}, {@code .monitor(i)}, {@code .window("t")},
 * {@code new EmulatorSource("n")} and {@code CaptureSource.region(source, rect)}. The host reads a
 * {@code CaptureSource} slot as whichever of them the Java is, and writes a value back through the one
 * matching its class.
 */
public final class CaptureTypes {

    private CaptureTypes() {}

    /**
     * The declared type. A fresh one is the ambient source, which keeps following the project's source when
     * that changes later; a concrete source would freeze the declaration into what was true when it was
     * made.
     *
     * <p>No editor: the host draws the value read-only, and the project's own source is picked in 🎯 Capture
     * Targets.
     */
    public static final class CaptureSourceType extends AbstractPluginType<CaptureSource> {
        public CaptureSourceType() { super(CaptureSource.class); }
        @Override public CaptureSource fresh() { return new CurrentSource(); }
        @Override public Node editor(ValueContext ctx) { return null; }
    }

    /** {@code Source.current()}. */
    public static final ComponentType<CurrentSource> CURRENT =
            new Shape<>(CurrentSource.class, SdkTypes.method(Source.class, "current")) {
                @Override public List<Object> components(CurrentSource value) { return List.of(); }
                @Override public CurrentSource build(List<Object> parts) { return new CurrentSource(); }
            };

    /** {@code CaptureSource.desktop()}. */
    public static final ComponentType<Desktop> DESKTOP =
            new Shape<>(Desktop.class, SdkTypes.method(CaptureSource.class, "desktop")) {
                @Override public List<Object> components(Desktop value) { return List.of(); }
                @Override public Desktop build(List<Object> parts) { return new Desktop(); }
            };

    /** {@code CaptureSource.monitor(index)}. */
    public static final ComponentType<Monitor> MONITOR =
            new Shape<>(Monitor.class, SdkTypes.method(CaptureSource.class, "monitor", int.class)) {
                @Override public List<Object> components(Monitor value) { return List.of(value.index()); }
                @Override public Monitor build(List<Object> parts) {
                    return new Monitor(parts.getFirst() instanceof Number n ? n.intValue() : 0);
                }
            };

    /** {@code CaptureSource.window("title")}. */
    public static final ComponentType<NamedWindow> WINDOW =
            new Shape<>(NamedWindow.class, SdkTypes.method(CaptureSource.class, "window", String.class)) {
                @Override public List<Object> components(NamedWindow value) {
                    return List.of(value.titleSubstring());
                }
                @Override public NamedWindow build(List<Object> parts) {
                    return new NamedWindow(parts.getFirst() instanceof String s ? s : "");
                }
            };

    /**
     * {@code new EmulatorSource("name")}: a constructor, since {@code EmulatorSource} is not one of
     * {@code CaptureSource}'s factories. It is still correct Java, and the bot captures from the emulator.
     */
    public static final ComponentType<EmulatorSource> EMULATOR =
            new Shape<>(EmulatorSource.class, SdkTypes.constructor(EmulatorSource.class, String.class)) {
                @Override public List<Object> components(EmulatorSource value) {
                    return List.of(value.instanceName());
                }
                @Override public EmulatorSource build(List<Object> parts) {
                    return new EmulatorSource(parts.getFirst() instanceof String s ? s : "");
                }
            };

    /**
     * {@code CaptureSource.region(source, new Rect(x, y, w, h))}: a region is a part of which pixels the bot
     * reads, so it is a value too. A region of a region is written as one call inside the other.
     */
    public static final ComponentType<RegionSource> REGION =
            new Shape<>(RegionSource.class,
                    SdkTypes.method(CaptureSource.class, "region", CaptureSource.class, Rect.class)) {
                @Override public List<Object> components(RegionSource value) {
                    return List.of(value.parent(), value.sub());
                }
                @Override public RegionSource build(List<Object> parts) {
                    return parts.size() == 2 && parts.get(0) instanceof CaptureSource of
                                   && parts.get(1) instanceof Rect sub ? new RegionSource(of, sub) : null;
                }
            };

    /** Every shape, in the order the host tries them. */
    public static final List<ComponentType<?>> ALL = List.of(CURRENT, DESKTOP, MONITOR, WINDOW, EMULATOR, REGION);

    /**
     * What each of the six shares: the class, and the call that writes it. The types of its parts are the
     * call's parameters, so the two cannot drift apart.
     */
    private abstract static class Shape<T> implements ComponentType<T> {

        private final Class<T> type;
        private final Executable factory;

        Shape(Class<T> type, Executable factory) {
            this.type = type;
            this.factory = factory;
        }

        @Override public final Class<T> type() { return type; }
        @Override public final Executable factory() { return factory; }
        @Override public final List<Class<?>> componentTypes() { return SdkTypes.parts(factory); }
    }
}
