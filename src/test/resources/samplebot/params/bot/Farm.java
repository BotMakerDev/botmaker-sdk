package samplebot.params.bot;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.secrets.Secret;

/** The game account a run plays: one run alone plays this one, several are each dealt one by {@link FarmPools}. */
@Param
public class Farm {

    public String account() { return "account-1@example.com"; }

    /** The game's password, a secret this PC keeps: the bot names it, BotMaker types it. */
    public Secret password() { return Secret.of("farm password"); }
}
