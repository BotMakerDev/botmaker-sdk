package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.ManagedHandle;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.session.display.SessionBackends;

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
        ImageTemplate picture = cut.place() == null ? plain : plain.at(cut.place());
        Size frame = cut.frame();
        if (frame == null || frame.empty()) return picture;
        Optional<Size> screen = read(services);
        if (screen.isPresent()) return screen.get().equals(frame) ? picture : picture.in(frame);
        // A screen() written by hand, which the grammar cannot read, is the user's: never overwritten.
        boolean handWritten = services != null && SCREEN.open(services).filter(ctx -> !SCREEN.readable(ctx)).isPresent();
        if (handWritten) return picture.in(frame);
        try {
            return SCREEN.write(services, frame) == null ? picture : picture.in(frame);
        } catch (RuntimeException refused) {
            return picture.in(frame);
        }
    }
}
