package com.botmaker.sdk.internal.config.fixture.params.g.a;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.bot.SdkValue;

/** The group beside {@code fixture.plugins.g.a.Sdk}: this computer's values, one method each. */
@Param
public class Sdk {

    @SdkValue(SdkValue.Id.LAUNCH_TARGET)
    public LaunchChoice launchTarget() {
        return LaunchChoice.of(" steam:570 ");
    }

    @SdkValue(SdkValue.Id.DO_SETUP)
    public boolean doSetup() {
        return false;
    }

    public int attempts() {
        return 3;
    }
}
