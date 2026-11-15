package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScreenCheckTest {

    /** Two pages alike but for the field: the email page holds a text field and no password's. */
    public static final class Map {
        public static final class Email extends Screen {
            public final Mark forgot = Mark.of(Element.byText(Words.of("Forgot email")));
            public final Mark noPassword = Mark.of(Element.passwordField()).absent();

            @Override
            public Setup setup() {
                return Setup.named("Email");
            }
        }

        public static final class Password extends Screen {
            public final Mark field = Mark.of(Element.passwordField());

            @Override
            public Setup setup() {
                return Setup.named("Password");
            }
        }

        public static final class Nothing extends Screen {
            @Override
            public Setup setup() {
                return Setup.named("Nothing");
            }
        }

        public static final Email EMAIL = new Email();
        public static final Password PASSWORD = new Password();
        public static final Nothing NOTHING = new Nothing();
    }

    public static final class Pictured {
        public static final class Home extends Screen {
            public final Mark title = Mark.of(new ImageTemplate("images/title.png"));

            @Override
            public Setup setup() {
                return Setup.named("Home");
            }
        }

        public static final Home HOME = new Home();
    }

    private static String tree(String... nodes) {
        return "<hierarchy rotation=\"0\"><node text=\"\" class=\"android.widget.FrameLayout\" package=\"app\""
                + " bounds=\"[0,0][1000,900]\">" + String.join("", nodes) + "</node></hierarchy>";
    }

    private static final String FORGOT = "<node text=\"Forgot email?\" class=\"android.widget.Button\" package=\"app\""
            + " bounds=\"[0,0][100,50]\"/>";
    private static final String PASSWORD = "<node text=\"\" class=\"android.widget.EditText\" package=\"app\""
            + " password=\"true\" bounds=\"[0,100][100,150]\"/>";

    @Test
    void namesTheScreensTheTreeShows() {
        assertEquals(List.of("EMAIL"), ScreenCheck.which(Map.class, tree(FORGOT)));
        assertEquals(List.of("PASSWORD"), ScreenCheck.which(Map.class, tree(FORGOT, PASSWORD)),
                "an absent element tells the email page apart");
        assertEquals(List.of(), ScreenCheck.which(Map.class, tree()), "nothing tells NOTHING");
    }

    @Test
    void aScreenToldByAPictureCannotBeChecked() {
        assertThrows(IllegalArgumentException.class, () -> ScreenCheck.which(Pictured.class, tree(FORGOT)));
    }
}
