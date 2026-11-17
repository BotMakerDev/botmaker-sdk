package samplebot.params.bot;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.signin.GoogleAccount;

/** The game account a run plays: one run alone plays this one, several are each dealt one by {@link FarmPools}. */
@Param
public class Farm {

    public String account() { return "account-1@example.com"; }

    /** The Google account the game signs in with, picked from this PC's book. */
    public GoogleAccount google() { return GoogleAccount.of("farm@example.com"); }
}
