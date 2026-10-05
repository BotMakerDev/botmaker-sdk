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
