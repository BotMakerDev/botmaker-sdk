package samplebot.plugins.com_github_botmakerdev.botmaker_sdk;

import com.botmaker.sdk.api.state.Fact;

import java.time.Duration;

/** What the game showed of this account, kept between runs: each kind of fact once. */
public final class Facts {

    public static final Fact<Integer> GOLD = Fact.number(Screens.Home.class, home -> home.gold)
            .staleAfter(Duration.ofMinutes(30));
    public static final Fact<String> PLAYER = Fact.read(Screens.Home.class, home -> home.title).account();
    public static final Fact<Boolean> SOUND_SHOWN = Fact.shown(Screens.Settings.class, s -> s.volume);
    public static final Fact<Boolean> RESTED = Fact.set().assume(false);

    private Facts() {}
}
