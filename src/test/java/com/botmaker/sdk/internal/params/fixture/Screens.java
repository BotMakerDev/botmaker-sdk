package com.botmaker.sdk.internal.params.fixture;

import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.params.fixture.params.bot.Wallet;

import java.time.Duration;

/** A bot's map, for {@code RunFactsTest} and {@code ReadoutIntoTest}. */
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
        public final Readout coins = Readout.text(new Zone(0, 0, 40, 20)).into(Wallet::coins)
                .staleAfter(Duration.ofMinutes(10));
        public final Readout owner = Readout.text(new Zone(0, 20, 80, 20)).into(Wallet::owner);
        public final Readout rank = Readout.text(new Zone(0, 40, 80, 20)).into(Wallet::rank);
        public final Readout sale = Readout.text(new Zone(0, 60, 80, 20)).into(Wallet::sale);
        public final Readout saleBadge = Readout.text(new Zone(0, 80, 80, 20)).into(Wallet::sale);
        public final Readout tips = Readout.text(new Zone(0, 100, 80, 20)).into(Wallet::tips);

        @Override
        public Setup setup() {
            return Setup.named("Shop");
        }
    }
}
