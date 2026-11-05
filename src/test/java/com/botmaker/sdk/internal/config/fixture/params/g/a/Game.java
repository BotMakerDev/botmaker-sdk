package com.botmaker.sdk.internal.config.fixture.params.g.a;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.bot.SdkValue;

/** Another group beside {@code fixture.plugins.g.a.Sdk}, named apart from it: a plugin declares several. */
@Param
public class Game {

    @SdkValue(SdkValue.Id.LAUNCH_TARGET)
    public LaunchChoice launchTarget() {
        return LaunchChoice.of(" steam:570 ");
    }
}
