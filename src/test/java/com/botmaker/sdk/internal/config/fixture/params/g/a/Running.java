package com.botmaker.sdk.internal.config.fixture.params.g.a;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.bot.SdkValue;

/** One of the groups beside {@code fixture.plugins.g.a.Sdk}: this computer's run values, one method each. */
@Param
public class Running {

    @SdkValue(SdkValue.Id.DO_SETUP)
    public boolean doSetup() {
        return false;
    }

    public int attempts() {
        return 3;
    }
}
