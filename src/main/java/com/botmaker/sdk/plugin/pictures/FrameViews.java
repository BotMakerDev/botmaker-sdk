package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.nio.file.Path;

/**
 * A picture's file in a JavaFX view, frame 1 of a moving one ({@link FrameStrip}): a thumbnail, a preview, a card's
 * marker shows the picture, not its strip squeezed into the box of one.
 */
public final class FrameViews {

    private FrameViews() {}

    /**
     * How many frames {@code name}'s file holds, as its constant says; 1 when unknown. Throws nothing: a view is being
     * built. On the FX thread.
     */
    public static int frames(StudioServices services, String name) {
        try {
            return services == null || name == null ? 1 : TemplateUses.frames(services.pluginValues(), name);
        } catch (RuntimeException unread) {
            return 1;
        }
    }

    /**
     * {@code file} read so that one frame fits {@code width}×{@code height}, ratio kept (0: its own size): a strip of
     * {@code frames} is read that many times as wide, so its frame comes out as crisp as a still picture's.
     */
    public static Image load(Path file, double width, double height, int frames, boolean background) {
        return new Image(file.toUri().toString(), width * Math.max(1, frames), height, true, true, background);
    }

    /**
     * {@code view} showing frame 1 of its image, a strip of {@code frames}, now or once a background read has its
     * size; the whole image for a still one.
     */
    public static ImageView firstFrame(ImageView view, int frames) {
        Image image = view.getImage();
        view.setViewport(null);
        if (frames <= 1 || image == null) return view;
        if (image.getWidth() > 0) {
            view.setViewport(new Rectangle2D(0, 0, image.getWidth() / frames, image.getHeight()));
            return view;
        }
        // Still reading in the background: cut once its size is known, then let go, since cached images outlive views.
        image.widthProperty().addListener(new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Number> width, Number was, Number is) {
                if (is.doubleValue() <= 0) return;
                width.removeListener(this);
                if (view.getImage() == image) {
                    view.setViewport(new Rectangle2D(0, 0, image.getWidth() / frames, image.getHeight()));
                }
            }
        });
        return view;
    }
}
