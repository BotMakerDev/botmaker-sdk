package com.botmaker.sdk.internal.params.fixture;

import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Setup;

/** A bot's map, for {@code GameFactsTest}. */
public final class Screens {

    private Screens() {}

    public static final class Home extends Screen {
        public final Readout level = Readout.text(new Zone(0, 0, 40, 20));
        public final Readout player = Readout.text(new Zone(0, 20, 80, 20));

        @Override
        public Setup setup() {
            return Setup.named("Home");
        }
    }

    public static final class Shop extends Screen {
        @Override
        public Setup setup() {
            return Setup.named("Shop");
        }
    }
}
