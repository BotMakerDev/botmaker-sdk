package samplebot.plugins.sdk;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.vision.ImageTemplate;

/** The pictures this bot looks for, one constant each, kept by <i>🖼 Manage Pictures</i>. */
@SdkValue(SdkValue.Id.PICTURES)
public final class Pictures {

    public static final ImageTemplate COLLECT = new ImageTemplate("src/main/resources/images/collect.png");
    public static final ImageTemplate BATTLE = new ImageTemplate("src/main/resources/images/battle.png");
    public static final ImageTemplate VICTORY = new ImageTemplate("src/main/resources/images/victory.png");
    public static final ImageTemplate DEFEAT = new ImageTemplate("src/main/resources/images/defeat.png");
    public static final ImageTemplate HOME = new ImageTemplate("src/main/resources/images/home.png");
    public static final ImageTemplate SEARCH = new ImageTemplate("src/main/resources/images/search.png");
    public static final ImageTemplate GOLD_ICON = new ImageTemplate("src/main/resources/images/gold_icon.png");
    public static final ImageTemplate BAG = new ImageTemplate("src/main/resources/images/bag.png");
    public static final ImageTemplate ITEM = new ImageTemplate("src/main/resources/images/item.png");
    public static final ImageTemplate BUY = new ImageTemplate("src/main/resources/images/buy.png");
    public static final ImageTemplate ITEMS_TAB = new ImageTemplate("src/main/resources/images/items_tab.png");

    private Pictures() {}
}
