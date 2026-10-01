package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.input.MouseButton;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The drawn mouse: which SDK button a real click is, and that every button has a part to click. */
class MouseButtonsTest {

    @Test
    void a_real_click_is_the_sdks_button() {
        assertEquals(Optional.of(MouseButton.LEFT), MouseButtons.of(javafx.scene.input.MouseButton.PRIMARY));
        assertEquals(Optional.of(MouseButton.RIGHT), MouseButtons.of(javafx.scene.input.MouseButton.SECONDARY));
        assertEquals(Optional.of(MouseButton.MIDDLE), MouseButtons.of(javafx.scene.input.MouseButton.MIDDLE));
        assertEquals(Optional.of(MouseButton.BACK), MouseButtons.of(javafx.scene.input.MouseButton.BACK));
        assertEquals(Optional.of(MouseButton.FORWARD), MouseButtons.of(javafx.scene.input.MouseButton.FORWARD));
        assertEquals(Optional.empty(), MouseButtons.of(javafx.scene.input.MouseButton.NONE));
        assertEquals(Optional.empty(), MouseButtons.of(null));
    }

    @Test
    void every_button_has_a_part_on_the_drawing() {
        EnumSet<MouseButton> drawn = EnumSet.noneOf(MouseButton.class);
        MouseButtons.parts().forEach(p -> drawn.add(p.button()));
        assertEquals(EnumSet.allOf(MouseButton.class), drawn);
    }
}
