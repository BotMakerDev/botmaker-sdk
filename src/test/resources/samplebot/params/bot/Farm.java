package samplebot.params.bot;

import com.botmaker.plugin.api.params.Param;

/** The game account a run plays: one run alone plays this one, several are each dealt one by {@link FarmPools}. */
@Param
public class Farm {

    public String account() { return "account-1@example.com"; }
}
