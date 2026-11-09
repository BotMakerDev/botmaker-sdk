package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.ManagedHandle;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.session.display.SessionBackends;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The bot's screen size as the plugin's windows read and write it: {@code @SdkValue(SdkValue.Id.SCREEN)} in
 * {@code Sdk.java} (2026-10-11). The first picture cut sets it; every later one is written with {@code .in(Size)}
 * only when its frame differs; a private display Studio brings up is made at it.
 */
public final class ScreenSize {

    public static final ManagedHandle<Size> SCREEN = ManagedHandle.of(SdkValues.SCREEN);

    private ScreenSize() {}

    /** The size the bot names, when it names one ({@code 0×0} is none). On the FX thread. */
    public static Optional<Size> read(StudioServices services) {
        return services == null ? Optional.empty() : SCREEN.read(services).filter(size -> !size.empty());
    }

    /** The size a private display for this bot is made at, and whether it is the bot's or the default. */
    public static SessionBackends.DisplaySize display(StudioServices services) {
        return read(services).map(size -> SessionBackends.sizeFor(size.width(), size.height()))
                .orElseGet(() -> SessionBackends.sizeFor(0, 0));
    }

    /**
     * {@code plain} written as cut at {@code cut}: {@code .at(place)} when the box is known, and {@code .in(frame)}
     * when the frame is not the bot's size. A bot that names no size yet takes this frame's, so its first picture
     * needs no {@code .in}. On the FX thread, since it may write {@code Sdk.java}.
     */
    public static ImageTemplate cut(StudioServices services, ImageTemplate plain, FrameCut cut) {
        ImageTemplate picture = (cut.place() == null ? plain : plain.at(cut.place())).frames(cut.frames());
        Size frame = cut.frame();
        if (frame == null || frame.empty()) return picture;
        return adopt(services, frame).filter(frame::equals).isPresent() ? picture : picture.in(frame);
    }

    private static final ManagedSet<ImageTemplate> PICTURES = ManagedSet.of(SdkValues.PICTURES);
    private static final ManagedSet<DigitSet> DIGITS = ManagedSet.of(SdkValues.DIGITS);

    /**
     * What {@link #change} did: the size before, and the constants written by hand, which it could not mark as cut
     * at that size.
     */
    public record Changed(Optional<Size> before, List<String> unmarked) {}

    /**
     * Sets the bot's screen size to {@code size} (the user's call, 2026-11-02: a size is set, not only taken from the
     * first cut). Every picture and digit set cut at the old size is written {@code .in(old)} first, so it still
     * scales from the frame it was cut from. On the FX thread.
     *
     * @return what changed, or the host's sentence when {@code screen()} was not written
     */
    public static Changed change(StudioServices services, Size size) {
        if (size == null || size.empty()) throw new IllegalArgumentException("a size is a width and a height above 0");
        if (handWritten(services)) {
            throw new IllegalStateException("Sdk.screen() is written by hand, so it is left as you wrote it.");
        }
        Optional<Size> before = read(services);
        List<String> unmarked = new ArrayList<>();
        if (before.isPresent() && !before.get().equals(size)) {
            PluginValues values = services.pluginValues();
            for (Member member : PICTURES.members(values)) {
                Optional<ValueContext> ctx = PICTURES.open(values, member);
                Optional<ImageTemplate> held = ctx.flatMap(PICTURES::read);
                if (held.isEmpty()) unmarked.add("Pictures." + member.name());
                else if (held.get().frame() == null) ctx.get().set(held.get().in(before.get()));
            }
            for (Member member : DIGITS.members(values)) {
                Optional<ValueContext> ctx = DIGITS.open(values, member);
                Optional<DigitSet> held = ctx.flatMap(DIGITS::read);
                if (held.isEmpty()) unmarked.add("Digits." + member.name());
                else if (held.get().frame() == null) ctx.get().set(held.get().in(before.get()));
            }
        }
        Optional<String> refused = SCREEN.write(services, size);
        if (refused.isPresent()) throw new IllegalStateException(refused.get());
        return new Changed(before, unmarked);
    }

    /** Whether {@code screen()} is written by hand, so its size cannot be read and it is never written. */
    public static boolean handWritten(StudioServices services) {
        return services != null && SCREEN.open(services).filter(ctx -> !SCREEN.readable(ctx)).isPresent();
    }

    /**
     * The bot's screen size, which becomes {@code frame} when the bot names none yet: what a picture cut from
     * {@code frame} is written against. Empty when the bot names none and it could not be written. On
     * the FX thread.
     */
    public static Optional<Size> adopt(StudioServices services, Size frame) {
        Optional<Size> screen = read(services);
        if (screen.isPresent() || frame == null || frame.empty()) return screen;
        // A screen() written by hand, which the grammar cannot read, is the user's: never overwritten.
        if (handWritten(services)) return Optional.empty();
        try {
            return SCREEN.write(services, frame).isEmpty() ? Optional.of(frame) : Optional.empty();
        } catch (RuntimeException refused) {
            return Optional.empty();
        }
    }
}
