package samplebot.plugins.sdk;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.bot.SdkValue;

/** This bot's activities, one enum each, and what each can report, one constant each. */
@SdkValue(SdkValue.Id.ACTIVITIES)
public final class Activities {

    public enum Collect implements Outcome { NEXT, NOTHING_LEFT }

    public enum Battle implements Outcome { WON, LOST }

    public enum Rest implements Outcome { NEXT }

    private Activities() {}
}
