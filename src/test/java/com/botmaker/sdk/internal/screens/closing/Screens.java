package com.botmaker.sdk.internal.screens.closing;

import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Setup;

/** A bot's {@code Screens} class, its {@code Pictures} beside it holding the game's usual close button. */
public final class Screens {

    private Screens() {}

    public static final Home HOME = new Home();

    public static final class Home extends Screen {
        @Override
        public Setup setup() {
            return Setup.named("Home");
        }
    }
}
