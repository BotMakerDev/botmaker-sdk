package samplebot.plugins.com_github_botmakerdev.botmaker_sdk;

import samplebot.SampleBot;
import samplebot.params.bot.Progress;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.plan.Reset;
import com.botmaker.sdk.api.screens.Area;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Caption;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Custom;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Mark;
import com.botmaker.sdk.api.screens.Place;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Retry;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.TextButton;
import com.botmaker.sdk.api.text.DigitSet;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * The game's screens, one class and one constant each, kept by <i>🗺 Screens</i>: one use of each kind of input,
 * readout and setup the map has, so a change to any of them is checked against a bot.
 */
@SdkValue(SdkValue.Id.SCREEN_MAP)
public final class Screens {

    public static final class Home extends Screen {
        public final Button collect = Button.of(Pictures.COLLECT).tried().free().occasional()
                .looks(List.of(Look.of(Pictures.COLLECT_BADGE, States.Presses.BADGE)));
        public final Button battle = Button.of(Pictures.BATTLE).tried().free().gesture(Gesture.DOUBLE).marking();
        public final Button search = Button.of(Pictures.SEARCH).types(Texts.GUILD).free().clearing().submitting();
        public final Button avatar = Button.beside(Pictures.GOLD_ICON, -1.5, 0).free().hold(Duration.ofMillis(900));
        public final Custom solve = Custom.of(SampleBot::solvePuzzle).free().occasional();
        public final Do claim = Do.press(collect).serves(Goals.CLAIMED_TODAY).first()
                .daily(Reset.daily(LocalTime.of(5, 0)).in(ZoneId.of("Europe/Paris"))).checks(Condition.of(SampleBot::shopOpen)).onFail(Retry.again(2))
                .takes(Duration.ofSeconds(3));
        public final Do solved = Do.press(solve).needs(Goals.SHOP_OPEN)
                .needs(Condition.of(SampleBot::shopOpen)).last().over(() -> Screens.HOME.claim).once()
                .until(Condition.of(SampleBot::shopOpen));
        public final Do glance = Do.press(avatar).onSight().every(Duration.ofMinutes(10));
        public final TextButton shop = TextButton.of(Caption.of(Texts.SHOP, Area.of(Pictures.GOLD_ICON, 2, -0.5, 6, 2))).tried()
                .free().when(Condition.of(SampleBot::shopOpen));
        public final Readout gold = Readout.digits(Area.of(Pictures.GOLD_ICON, 1.2, 0, 3, 1),
                new DigitSet("src/main/resources/digits/gold")).subject().into(Progress::gold)
                .staleAfter(Duration.ofMinutes(30));
        public final Readout title = Readout.text(Area.of(Pictures.GOLD_ICON, 0, 1.5, 7.5, 1)).into(Progress::player);

        @Override
        public Setup setup() {
            return Setup.named("Home");
        }
    }

    public static final class Shop extends Screen {
        public final Button item = Button.of(Pictures.ITEM).dragsOnto(Pictures.BAG).dragTime(Duration.ofMillis(500))
                .free();
        public final Button buy = Button.of(Pictures.BUY).place(Place.below(Pictures.ITEM)).repeated()
                .spends(Progress::gold).when(Params.when(Progress::gold).atLeast(5))
                .looks(List.of(Look.of(Pictures.BUY_MAXED, States.Blocks.MAXED)));
        public final Button itemsTab = Button.of(Pictures.ITEMS_TAB).tried().free();
        public final Mark bag = Mark.of(Pictures.BAG);
        public final Mark title = Mark.of(Caption.of(Texts.SHOP, Area.of(Pictures.ITEMS_TAB, -3, 0, 2.5, 1)));
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
        public final Button sound = Button.of(Elements.SOUND).tried().free();
        public final Readout volume = Readout.element(Elements.VOLUME).into(Progress::soundShown);
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
