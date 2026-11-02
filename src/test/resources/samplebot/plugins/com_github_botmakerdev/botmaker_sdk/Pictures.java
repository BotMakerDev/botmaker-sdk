package samplebot.plugins.com_github_botmakerdev.botmaker_sdk;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.vision.ImageTemplate;

/** The pictures this bot looks for, one constant each, kept by <i>🖼 Manage Pictures</i>. */
@SdkValue(SdkValue.Id.PICTURES)
public final class Pictures {

    public static final ImageTemplate COLLECT = new ImageTemplate("images/collect.png");
    public static final ImageTemplate BATTLE = new ImageTemplate("images/battle.png");
    public static final ImageTemplate VICTORY = new ImageTemplate("images/victory.png");
    public static final ImageTemplate DEFEAT = new ImageTemplate("images/defeat.png");
    public static final ImageTemplate HOME = new ImageTemplate("images/home.png");
    public static final ImageTemplate SEARCH = new ImageTemplate("images/search.png");
    public static final ImageTemplate GOLD_ICON = new ImageTemplate("images/gold_icon.png");
    public static final ImageTemplate BAG = new ImageTemplate("images/bag.png");
    public static final ImageTemplate ITEM = new ImageTemplate("images/item.png");
    public static final ImageTemplate BUY = new ImageTemplate("images/buy.png");
    public static final ImageTemplate ITEMS_TAB = new ImageTemplate("images/items_tab.png");
    public static final ImageTemplate SLIME = new ImageTemplate("images/slime.png").frames(3);

    private Pictures() {}
}
