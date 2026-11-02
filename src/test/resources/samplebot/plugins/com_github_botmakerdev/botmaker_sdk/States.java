package samplebot.plugins.com_github_botmakerdev.botmaker_sdk;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.screens.ButtonState;

/**
 * What a button's look means beside normal, selected and disabled, kept by <i>🗺 Screens</i>: a state of
 * {@code Blocks} is never pressed, one of {@code Presses} is.
 */
@SdkValue(SdkValue.Id.STATES)
public final class States {

    public enum Blocks implements ButtonState { MAXED }

    public enum Presses implements ButtonState { BADGE }

    private States() {}
}
