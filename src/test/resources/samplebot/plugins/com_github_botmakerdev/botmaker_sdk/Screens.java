package samplebot.plugins.com_github_botmakerdev.botmaker_sdk;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Caption;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Mark;
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
        public final Button collect = Button.of(Pictures.COLLECT).tried().occasional()
                .looks(List.of(Look.of(Pictures.COLLECT_BADGE, States.Presses.BADGE)));
        public final Button battle = Button.of(Pictures.BATTLE).tried().gesture(Gesture.DOUBLE).marking();
        public final Button search = Button.of(Pictures.SEARCH).types(Texts.GUILD).clearing().submitting();
        public final Button avatar = Button.beside(Pictures.GOLD_ICON, -1.5, 0).hold(Duration.ofMillis(900));
        public final TextButton shop = TextButton.of(Caption.of(Texts.SHOP, new Zone(0, 0, 400, 80))).tried();
        public final Readout gold = Readout.digits(new Zone(40, 0, 120, 30),
                new DigitSet("src/main/resources/digits/gold")).subject();
        public final Readout title = Readout.text(new Zone(0, 40, 300, 40));

        @Override
        public Setup setup() {
            return Setup.named("Home");
        }
    }

    public static final class Shop extends Screen {
        public final Button item = Button.of(Pictures.ITEM).dragsOnto(Pictures.BAG).dragTime(Duration.ofMillis(500));
        public final Button buy = Button.of(Pictures.BUY).place(Place.below(Pictures.ITEM)).repeated()
                .looks(List.of(Look.of(Pictures.BUY_MAXED, States.Blocks.MAXED)));
        public final Button itemsTab = Button.of(Pictures.ITEMS_TAB).tried();
        public final Mark bag = Mark.of(Pictures.BAG);
        public final Mark title = Mark.of(Caption.of(Texts.SHOP, new Zone(0, 0, 400, 80)));
        public final Mark noBattle = Mark.of(Pictures.BATTLE).absent();

        @Override
        public Setup setup() {
            return Setup.named("Shop")
                    .from(HOME.shop)
                    .takes(Duration.ofSeconds(2));
        }
    }

    public static final class Items extends Screen {
        public final Mark item = Mark.of(Pictures.ITEM);

        @Override
        public Setup setup() {
            return Setup.named("Items").from(SHOP.itemsTab).tab();
        }
    }

    public static final class Settings extends Screen {
        public final Button sound = Button.of(Elements.SOUND).tried();
        public final Readout volume = Readout.element(Elements.VOLUME);
        public final Mark list = Mark.of(Elements.SETTINGS_LIST);

        @Override
        public Setup setup() {
            return Setup.named("Settings").from(HOME.collect);
        }
    }

    public static final Home HOME = new Home();
    public static final Shop SHOP = new Shop();
    public static final Items ITEMS = new Items();
    public static final Settings SETTINGS = new Settings();

    private Screens() {}
}
