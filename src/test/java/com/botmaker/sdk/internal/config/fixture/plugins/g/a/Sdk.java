package com.botmaker.sdk.internal.config.fixture.plugins.g.a;

import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.api.bot.SdkValue;

import java.util.List;

/** A bot's plugin holder, as {@code main} names it: the author's data, its parameters in the group beside it. */
public final class Sdk {

    @SdkValue(SdkValue.Id.LAUNCH_TARGETS)
    public static List<Launcher> games() {
        return List.of(Launcher.of("faugus:abc"), Launcher.of("steam:570"));
    }

    private Sdk() {}
}
