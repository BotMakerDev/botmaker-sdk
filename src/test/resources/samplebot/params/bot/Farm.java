package samplebot.params.bot;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.secrets.Secret;
import samplebot.Login;

/** The game account a run plays: one run alone plays this one, several are each dealt one by {@link FarmPools}. */
@Param
public class Farm {

    public String account() { return "account-1@example.com"; }

    /** The game's password, a secret this PC keeps: the bot names it, BotMaker types it. */
    public Secret password() { return Secret.of("farm password"); }

    /** The login a sign-in screen types, its parts named by the record's methods. */
    public Login login() { return new Login("farm@example.com", Secret.of("farm password"), Secret.of("")); }
}
