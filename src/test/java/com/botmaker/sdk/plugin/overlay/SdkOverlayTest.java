package com.botmaker.sdk.plugin.overlay;

import com.botmaker.plugin.api.overlay.OverlayPart;
import com.botmaker.plugin.api.overlay.Watched;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.ImageClicker;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdkOverlayTest {

    @Test
    void theWatchedScreenIsTheCaptureSourcesWindow() {
        assertEquals(Optional.of(Watched.window("Game")), SdkOverlay.of(CaptureSource.window("Game")));
        assertEquals(Optional.of(Watched.window("Game")),
                SdkOverlay.of(CaptureSource.window("Game").region(new Rect(10, 10, 100, 50))),
                "a region docks beside the window it narrows");
        assertEquals(Optional.empty(), SdkOverlay.of(CaptureSource.desktop()), "Studio asks instead");
        assertEquals(Optional.empty(), SdkOverlay.of(null));
    }

    /** A source that captures {@code image} (null: a failed capture) at {@code 40,60}. */
    private static CaptureSource fake(java.awt.image.BufferedImage image) {
        return new CaptureSource() {
            @Override public java.awt.image.BufferedImage capture() { return image; }
            @Override public com.botmaker.sdk.api.geometry.Point origin() {
                return new com.botmaker.sdk.api.geometry.Point(40, 60);
            }
        };
    }

    @Test
    void theFrameIsTheSourcesOwnCaptureAtItsOrigin() {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(8, 6,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        OverlayPart.FrameSource frames = SdkOverlay.framesOf(fake(image)).orElseThrow();
        com.botmaker.plugin.api.overlay.OverlayFrame frame = frames.grab().orElseThrow();
        assertEquals(image, frame.image());
        assertEquals(new com.botmaker.plugin.api.toolbar.ActionContext.Area(40, 60, 8, 6), frame.area());

        assertEquals(Optional.empty(), SdkOverlay.framesOf(fake(null)).orElseThrow().grab(), "a failed capture");
        assertEquals(Optional.empty(), SdkOverlay.framesOf(null), "no source: Studio grabs the window");
        assertTrue(SdkOverlay.framesOf(CaptureSource.desktop().region(new Rect(0, 0, 10, 10))).isPresent(),
                "a region is grabbed as the region");
        assertEquals(Optional.empty(), SdkOverlay.framesOf(CaptureSource.emulator("Pixel")),
                "an emulator is never grabbed from the editor: that connects adb, or launches it");
    }

    @Test
    void activitiesAreTargetsByTypeAndClicksAreActing() throws NoSuchMethodException {
        OverlayPart part = SdkOverlay.PART;
        assertEquals(List.of(new OverlayPart.TargetType(ActivityBody.class.getName(), "Activities")), part.targets());
        assertEquals(List.of("picture", "point", "flow"), part.tools().stream().map(t -> t.id()).toList());
        assertTrue(part.changeWatched().isPresent());

        assertTrue(part.probeFor(ImageFinder.class.getMethod("find", ImageTemplate.class)).orElseThrow().readsOnly());
        assertFalse(part.probeFor(ImageClicker.class.getMethod("click", ImageTemplate.class)).orElseThrow()
                .readsOnly(), "a click is never computed by Try");
        assertEquals(5, part.probes().size());
    }
}
