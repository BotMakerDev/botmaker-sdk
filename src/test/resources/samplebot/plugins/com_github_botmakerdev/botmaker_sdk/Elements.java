package samplebot.plugins.com_github_botmakerdev.botmaker_sdk;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.screens.Element;

/** The app's inputs found in its screen's tree, one constant each, as {@link Pictures} holds its pictures. */
@SdkValue(SdkValue.Id.ELEMENTS)
public final class Elements {

    public static final Element SETTINGS_LIST = Element.byId("settings_list");
    public static final Element SOUND = Element.byId("title").text(Texts.SOUND).inside(SETTINGS_LIST)
            .shot("elements/sound.png");
    public static final Element VOLUME = Element.byId("volume").below(SOUND);

    private Elements() {}
}
