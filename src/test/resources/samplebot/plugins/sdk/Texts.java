package samplebot.plugins.sdk;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.text.Words;

/** The texts this bot types or reads, one constant each, as {@link Pictures} holds its pictures. */
@SdkValue(SdkValue.Id.TEXTS)
public final class Texts {

    public static final Words GUILD = Words.of("Guild");
    public static final Words SHOP = Words.of("Shop");

    private Texts() {}
}
