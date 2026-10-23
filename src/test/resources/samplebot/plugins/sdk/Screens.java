package samplebot.plugins.sdk;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Caption;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Place;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.TextButton;
import com.botmaker.sdk.api.text.DigitSet;

import java.time.Duration;
import java.util.List;

/**
 * The game's screens, one class and one constant each, kept by <i>🗺 Screens</i>: one use of each kind of input,
 * readout and setup the map has, so a change to any of them is checked against a bot.
 */
@SdkValue(SdkValue.Id.SCREEN_MAP)
public final class Screens {

    public static final class Home extends Screen {
        public final Button collect = Button.of(Pictures.COLLECT).tried().sometimes();
        public final Button battle = Button.of(Pictures.BATTLE).tried().gesture(Gesture.DOUBLE);
        public final Button search = Button.of(Pictures.SEARCH).types(Texts.GUILD).clears().submits();
        public final Button avatar = Button.beside(Pictures.GOLD_ICON, -1.5, 0).hold(Duration.ofMillis(900));
        public final TextButton shop = TextButton.of(Caption.of(Texts.SHOP, new Zone(0, 0, 400, 80))).tried();
        public final Readout gold = Readout.digits(new Zone(40, 0, 120, 30),
                new DigitSet("src/main/resources/digits/gold")).subject();
        public final Readout title = Readout.text(new Zone(0, 40, 300, 40));

        @Override
        public Setup setup() {
            return Setup.named("Home").markers(List.of(Pictures.BATTLE));
        }
    }

    public static final class Shop extends Screen {
        public final Button item = Button.of(Pictures.ITEM).dragsOnto(Pictures.BAG).dragTime(Duration.ofMillis(500));
        public final Button buy = Button.of(Pictures.BUY).place(Place.below(Pictures.ITEM)).repeated();
        public final Button itemsTab = Button.of(Pictures.ITEMS_TAB).tried();

        @Override
        public Setup setup() {
            return Setup.named("Shop")
                    .markers(List.of(Pictures.BAG))
                    .from(HOME.shop)
                    .takes(Duration.ofSeconds(2));
        }
    }

    public static final class Items extends Screen {
        @Override
        public Setup setup() {
            return Setup.named("Items").markers(List.of(Pictures.ITEM)).from(SHOP.itemsTab).tab();
        }
    }

    public static final Home HOME = new Home();
    public static final Shop SHOP = new Shop();
    public static final Items ITEMS = new Items();

    private Screens() {}
}
