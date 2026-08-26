package com.botmaker.sdk.internal.plugin.catalog;

import com.botmaker.plugin.api.catalog.CatalogBuilder;
import com.botmaker.plugin.api.catalog.Category;
import com.botmaker.plugin.api.catalog.FacadeRole;
import com.botmaker.plugin.api.catalog.M0;
import com.botmaker.plugin.api.catalog.M1;
import com.botmaker.plugin.api.catalog.M2;
import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.sdk.api.bot.Activity;
import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.bot.BotStuckException;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.bot.Session;
import com.botmaker.sdk.api.bot.StartMode;
import com.botmaker.sdk.api.bot.Watchdog;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.capture.Window;
import com.botmaker.sdk.api.emulator.Emulator;
import com.botmaker.sdk.api.emulator.EmulatorRef;
import com.botmaker.sdk.api.emulator.Emulators;
import com.botmaker.sdk.api.emulator.EmulatorSource;
import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.Keyboard;
import com.botmaker.sdk.api.interaction.Mouse;
import com.botmaker.sdk.api.interaction.MouseButton;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.api.launch.Game;
import com.botmaker.sdk.api.launch.LaunchTarget;
import com.botmaker.sdk.api.launch.Target;
import com.botmaker.sdk.api.util.BotMaker;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.api.util.Time;
import com.botmaker.sdk.api.vision.ColorMatch;
import com.botmaker.sdk.api.vision.ImageClicker;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.ImageTemplateGroup;
import com.botmaker.sdk.api.vision.ImageWaiter;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Matches;
import com.botmaker.sdk.api.vision.Pixel;
import com.botmaker.sdk.api.vision.Precision;
import com.botmaker.sdk.api.vision.Text;
import com.botmaker.sdk.api.vision.TextMatch;
import com.botmaker.sdk.api.vision.Vision;

import java.awt.Color;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.Month;
import java.util.List;
import java.util.function.Consumer;

/**
 * What SDK 1.1.0 offers Studio's palette — the first catalogued version, and the base every later one is
 * written as a delta on.
 *
 * <h2>This file is frozen</h2>
 *
 * <p>It describes a <b>released</b> jar. A bot that pins 1.1.0 gets exactly this palette however new the
 * editor reading it is, which is the inversion's rule stated as a file: anything touching bot code takes its
 * answer from <em>the bot's</em> SDK version. So nothing is added here — a member the SDK gains belongs in
 * the class for the version that gained it — and the only edit this file ever legitimately takes is the
 * removal of a member that no longer exists, which is why {@code release.sh} treats a diff here as a
 * deliberate API removal and refuses the release without {@code --allow-removal}.
 *
 * <h2>What it does not answer</h2>
 *
 * <p><b>Presence.</b> A catalog can only name members that compile in <em>this</em> build, so a member 1.1.0
 * genuinely had and a later version deleted cannot be written down here at all. Whether a member exists in
 * the jar a bot actually resolved stays with the editor's own scan of that jar, and the palette is the
 * intersection of the two. The failure direction is the safe one: an old pin is occasionally offered
 * <em>less</em> than it truly had, never more.
 *
 * <h2>Present means curated</h2>
 *
 * <p>A type here offers exactly the members it lists. A type with an empty member list — {@link Key},
 * {@link Direction}, {@link StartMode} — is a verdict, not an omission: it is catalogued so the editor owns
 * its simple name for imports and its constants for the pickers, and offers none of its methods. This
 * replaces the {@code @Palette} annotation deleted on 2026-08-25, whose "an uncurated jar offers everything"
 * escape hatch is gone with it: a version with no catalog class is uncurated, and a version with one is
 * curated whole.
 *
 * <h2>The one member the shapes cannot name</h2>
 *
 * <p>{@code Emulator.swipe(int, int, int, int, long)} is six-deep counted with its receiver, and the
 * reference shapes stop at five. It was never curated, so nothing is lost today; when a sixth is genuinely
 * wanted, the answer is an {@code M6} in the contract rather than a string here.
 *
 * <h2>Why some entries are cast rather than witnessed</h2>
 *
 * <p>The usual spelling is a type witness — {@code .<Point>add(Mouse::click)} — and it works because the
 * witness's <em>arity</em> picks the {@code add} overload. It fails for exactly one shape of overload set:
 * one containing a <b>no-argument or varargs</b> member, which is applicable to {@link M0} as well, and a
 * witness cannot rule {@code add(M0)} out because a non-generic method ignores explicit type arguments
 * (JLS 15.12.2.1). Those entries name their shape outright — {@code .add((M1<Key[]>) Keyboard::combo)} —
 * which is the same statement made where javac can act on it.
 */
final class V1_1_0 {

    private V1_1_0() {
    }

    static PaletteCatalog build() {
        CatalogBuilder b = PaletteCatalog.builder();
        interaction(b);
        vision(b);
        settings(b);
        launch(b);
        lifecycle(b);
        capture(b);
        time(b);
        valueTypes(b);
        return b.build();
    }

    // -------------------------------------------------------------------------------------------------
    // Facades, in menu order. Declaration order IS the order the insert menus show, so this sequence is
    // the editorial one — interaction first, then vision, then settings, launch, lifecycle, capture, time.
    // -------------------------------------------------------------------------------------------------

    private static void interaction(CatalogBuilder b) {
        b.facade(Mouse.class, Category.INTERACTION).facadeIcon("🖱")
                .<Point>add(Mouse::click)
                .<Integer, Integer>add(Mouse::click)
                .<CaptureSource, Integer, Integer>add(Mouse::click)
                .<Point>add(Mouse::move)
                .<Integer, Integer>add(Mouse::move)
                .<MouseButton>add(Mouse::down)
                .<MouseButton, Point>add(Mouse::down)
                .<MouseButton>add(Mouse::up)
                .<Point>add(Mouse::rightClick)
                .<Point>add(Mouse::middleClick)
                .<Point>add(Mouse::doubleClick)
                .<Point, Point>add(Mouse::drag)
                .<Point, Point, Long>add(Mouse::drag)
                .<Integer>add(Mouse::scrollUp)
                .<Integer>add(Mouse::scrollDown);

        b.facade(Keyboard.class, Category.INTERACTION).facadeIcon("⌨")
                .<Key>add(Keyboard::press)
                .<Key>add(Keyboard::release)
                .<Key>add(Keyboard::tap)
                .add((M1<Key[]>) Keyboard::combo)
                .<String>add(Keyboard::type)
                .<CaptureSource, Key>add(Keyboard::press)
                .<CaptureSource, Key>add(Keyboard::release)
                .<CaptureSource, Key>add(Keyboard::tap)
                .add((M2<CaptureSource, Key[]>) Keyboard::combo)
                .<CaptureSource, String>add(Keyboard::type);

        b.facade(Wait.class, Category.INTERACTION).facadeIcon("⏱")
                .<Duration>add(Wait::time)
                .<Duration, Duration>add(Wait::between)
                .<Integer>add(Wait::milliseconds)
                .<Double>add(Wait::seconds)
                .<Double>add(Wait::minutes);
    }

    private static void vision(CatalogBuilder b) {
        b.facade(ImageFinder.class, Category.VISION).facadeIcon("🔍")
                .<ImageTemplate>add(ImageFinder::find)
                .<ImageTemplate, CaptureSource>add(ImageFinder::find)
                .add((M1<ImageTemplate[]>) ImageFinder::findAny)
                .add((M2<CaptureSource, ImageTemplate[]>) ImageFinder::findAny)
                .add((M1<ImageTemplateGroup>) ImageFinder::findAny)
                .add((M2<ImageTemplateGroup, CaptureSource>) ImageFinder::findAny)
                .<ImageTemplateGroup>add(ImageFinder::findBest)
                .<ImageTemplateGroup, CaptureSource>add(ImageFinder::findBest)
                .<ImageTemplateGroup, ImageTemplateGroup>add(ImageFinder::findCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, Double>add(ImageFinder::findCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, CaptureSource>add(ImageFinder::findCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, CaptureSource, Double>add(ImageFinder::findCompare)
                .<ImageTemplateGroup, ImageTemplateGroup>add(ImageFinder::findAnyCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, Double>add(ImageFinder::findAnyCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, CaptureSource>add(ImageFinder::findAnyCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, CaptureSource, Double>add(ImageFinder::findAnyCompare)
                .<ImageTemplateGroup, ImageTemplateGroup>add(ImageFinder::findAllCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, Double>add(ImageFinder::findAllCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, CaptureSource>add(ImageFinder::findAllCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, CaptureSource, Double>add(ImageFinder::findAllCompare)
                .<ImageTemplate>add(ImageFinder::findAll)
                .<ImageTemplate, CaptureSource>add(ImageFinder::findAll)
                .<ImageTemplateGroup>add(ImageFinder::findAll)
                .<ImageTemplateGroup, CaptureSource>add(ImageFinder::findAll)
                .<ImageTemplate, Consumer<MatchResult>>add(ImageFinder::ifFind)
                .<ImageTemplate, CaptureSource, Consumer<MatchResult>>add(ImageFinder::ifFind)
                .<ImageTemplate, Consumer<MatchResult>>add(ImageFinder::whileFind)
                .<ImageTemplate, CaptureSource, Consumer<MatchResult>>add(ImageFinder::whileFind)
                .<ImageTemplate, Runnable>add(ImageFinder::untilFind)
                .<ImageTemplate, CaptureSource, Runnable>add(ImageFinder::untilFind)
                .<ImageTemplateGroup, Consumer<Matches>>add(ImageFinder::ifFindAny)
                .<ImageTemplateGroup, CaptureSource, Consumer<Matches>>add(ImageFinder::ifFindAny)
                .<ImageTemplateGroup, Consumer<Matches>>add(ImageFinder::ifFindAll)
                .<ImageTemplateGroup, CaptureSource, Consumer<Matches>>add(ImageFinder::ifFindAll)
                .<ImageTemplateGroup, Consumer<Matches>>add(ImageFinder::whileFindAny)
                .<ImageTemplateGroup, CaptureSource, Consumer<Matches>>add(ImageFinder::whileFindAny)
                .<ImageTemplateGroup, Consumer<Matches>>add(ImageFinder::whileFindAll)
                .<ImageTemplateGroup, CaptureSource, Consumer<Matches>>add(ImageFinder::whileFindAll)
                .<ImageTemplateGroup, Runnable>add(ImageFinder::untilFindAny)
                .<ImageTemplateGroup, CaptureSource, Runnable>add(ImageFinder::untilFindAny)
                .<ImageTemplateGroup, Runnable>add(ImageFinder::untilFindAll)
                .<ImageTemplateGroup, CaptureSource, Runnable>add(ImageFinder::untilFindAll);

        b.facade(ImageClicker.class, Category.VISION).facadeIcon("👆")
                .<ImageTemplate>add(ImageClicker::click)
                .<ImageTemplate, CaptureSource>add(ImageClicker::click)
                .<MatchResult>add(ImageClicker::click)
                .<MatchResult, CaptureSource>add(ImageClicker::click)
                .add(ImageClicker::clickLast)
                .add((M0) ImageClicker::clickEachLast)
                .add((M1<ImageTemplate[]>) ImageClicker::clickEachLast)
                .add((M0) ImageClicker::clickAllLast)
                .add((M1<ImageTemplate[]>) ImageClicker::clickAllLast)
                .add((M1<ImageTemplate[]>) ImageClicker::clickAny)
                .add((M2<CaptureSource, ImageTemplate[]>) ImageClicker::clickAny)
                .add((M1<ImageTemplateGroup>) ImageClicker::clickAny)
                .add((M2<ImageTemplateGroup, CaptureSource>) ImageClicker::clickAny)
                .<ImageTemplateGroup>add(ImageClicker::clickBest)
                .<ImageTemplateGroup, CaptureSource>add(ImageClicker::clickBest)
                .<ImageTemplateGroup, ImageTemplateGroup>add(ImageClicker::clickCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, Double>add(ImageClicker::clickCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, CaptureSource>add(ImageClicker::clickCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, CaptureSource, Double>add(ImageClicker::clickCompare)
                .<ImageTemplateGroup, ImageTemplateGroup>add(ImageClicker::clickAnyCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, CaptureSource>add(ImageClicker::clickAnyCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, CaptureSource, Double>add(ImageClicker::clickAnyCompare)
                .<ImageTemplateGroup, ImageTemplateGroup>add(ImageClicker::clickAllCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, CaptureSource>add(ImageClicker::clickAllCompare)
                .<ImageTemplateGroup, ImageTemplateGroup, CaptureSource, Double>add(ImageClicker::clickAllCompare)
                .<ImageTemplate>add(ImageClicker::clickAll)
                .<ImageTemplate, CaptureSource>add(ImageClicker::clickAll)
                .<ImageTemplateGroup>add(ImageClicker::clickAll)
                .<ImageTemplateGroup, CaptureSource>add(ImageClicker::clickAll);

        b.facade(ImageWaiter.class, Category.VISION).facadeIcon("⏳")
                .<ImageTemplate, Integer>add(ImageWaiter::waitFor)
                .<ImageTemplate, CaptureSource, Integer>add(ImageWaiter::waitFor)
                .<ImageTemplate, Integer>add(ImageWaiter::waitUntilGone)
                .<ImageTemplate, CaptureSource, Integer>add(ImageWaiter::waitUntilGone)
                .<ImageTemplate, Integer>add(ImageWaiter::waitAndClick)
                .<ImageTemplate, CaptureSource, Integer>add(ImageWaiter::waitAndClick);

        b.facade(Pixel.class, Category.VISION).facadeIcon("🎨")
                .<Integer, Integer>add(Pixel::colorAt)
                .<Point>add(Pixel::colorAt)
                .<Integer, Integer, CaptureSource>add(Pixel::colorAt)
                .<Integer, Integer, Color, Precision>add(Pixel::matchesAt)
                .<Integer, Integer, Color, CaptureSource, Precision>add(Pixel::matchesAt)
                .<Color, Color>add(Pixel::distance)
                .<Color>add(Pixel::find)
                .<Color, Precision>add(Pixel::find)
                .<Color, CaptureSource>add(Pixel::find)
                .<Color, CaptureSource, Precision>add(Pixel::find)
                .<Color, CaptureSource, Precision>add(Pixel::findAll)
                .<Color, Precision>add(Pixel::findAll)
                .<Color, Color, CaptureSource, Precision>add(Pixel::findInRange)
                .<Color, Color>add(Pixel::findInRange)
                .<Color, CaptureSource, Precision>add(Pixel::coverage)
                .<Color, Precision>add(Pixel::coverage)
                .<Color, CaptureSource, Precision, Long>add(Pixel::waitFor)
                .<Color, Precision, Long>add(Pixel::waitFor)
                .<Color, CaptureSource, Precision, Long>add(Pixel::waitForGone);

        b.facade(Text.class, Category.VISION).facadeIcon("🔤")
                .add((M1<CaptureSource>) Text::read)
                .add((M0) Text::read)
                .<String, CaptureSource>add(Text::find)
                .<String>add(Text::find)
                .<String, CaptureSource>add(Text::findExact)
                .<String, CaptureSource>add(Text::findMatching)
                .<String, CaptureSource>add(Text::findFuzzy)
                .<String>add(Text::findFuzzy)
                .<String, Integer, CaptureSource>add(Text::findFuzzy)
                .<String, CaptureSource>add(Text::findAll)
                .<CaptureSource>add(Text::readAll)
                .<String, CaptureSource, Long>add(Text::waitFor)
                .<String, CaptureSource, Long>add(Text::waitForGone);

        b.facade(Vision.class, Category.VISION).facadeIcon("👁")
                .add(Vision::lastMatch)
                .add(Vision::lastMatchList)
                .add(Vision::lastMatchFound)
                .add(Vision::lastMatches)
                .add(Vision::inFrame)
                .add(Vision::clearLastMatch)
                .<Consumer<MatchResult>>add(Vision::ifLastMatch)
                .add(Vision::lastColorMatch)
                .add(Vision::lastColorMatchList)
                .add(Vision::lastColorMatchFound)
                .add(Vision::clearLastColorMatch)
                .<Consumer<ColorMatch>>add(Vision::ifLastColorMatch)
                .add(Vision::lastTextMatch)
                .add(Vision::lastTextMatchList)
                .add(Vision::lastTextMatchFound)
                .add(Vision::clearLastTextMatch)
                .<Consumer<TextMatch>>add(Vision::ifLastTextMatch);
    }

    private static void settings(CatalogBuilder b) {
        b.facade(BotSettings.class, Category.BOT).facadeIcon("⚙")
                .add(BotSettings::foundDelay)
                .add(BotSettings::notFoundDelay)
                .add(BotSettings::randomizeClicks)
                .add(BotSettings::confidence)
                .add(BotSettings::compareMargin)
                .add(BotSettings::maxRetryAttempts)
                .add(BotSettings::defaultLaunchWaitTimeout)
                .add(BotSettings::defaultCaptureSource)
                .add(BotSettings::realInput)
                .<Integer>add(BotSettings::setFoundDelay)
                .<Integer>add(BotSettings::setNotFoundDelay)
                .<Boolean>add(BotSettings::enableRandomClicks)
                .<Double>add(BotSettings::setDefaultConfidence)
                .<Double>add(BotSettings::setCompareMargin)
                .<Integer>add(BotSettings::setMaxRetryAttempts)
                .<Long>add(BotSettings::setDefaultLaunchWaitTimeout)
                .<Boolean>add(BotSettings::enableDebugMode)
                .<Boolean>add(BotSettings::useRealInput)
                .add(BotSettings::resetToDefaults);

        b.facade(Debug.class, Category.UTIL, FacadeRole.HIDDEN).facadeIcon("🐞")
                .add(Debug::isEnabled)
                .add(Debug::enable)
                .add(Debug::disable)
                .<String>add(Debug::log)
                .<String>add(Debug::error)
                .<String, Throwable>add(Debug::error);

        b.facade(Session.class, Category.BOT, FacadeRole.HIDDEN)
                .add(Session::isEnabled)
                .add(Session::enable)
                .add(Session::disable)
                .<String>add(Session::useBackend);
    }

    private static void launch(CatalogBuilder b) {
        b.facade(Game.class, Category.LAUNCH).facadeIcon("🎮")
                .<String, String[]>add(Game::launch)
                .<String>add(Game::launchSteam)
                .<String>add(Game::launchEpic)
                .<String>add(Game::launchHeroic)
                .<String>add(Game::launchFaugus)
                .<String, CaptureSource>add(Game::launchEpicIfNotRunning)
                .<CaptureSource>add(Game::isRunning)
                .<CaptureSource, Long>add(Game::waitForLaunch)
                .<String, CaptureSource, String[]>add(Game::launchIfNotRunning)
                .<String, CaptureSource>add(Game::launchSteamIfNotRunning)
                .<String, CaptureSource, Long, String[]>add(Game::launchAndWait)
                .<String, String[]>add(Game::launchAndWait)
                .<Long>add(Game::waitForDefaultSource)
                .<String>add(Game::kill)
                .<String>add(Game::isRunning);

        b.facade(Target.class, Category.LAUNCH).facadeIcon("🚀")
                .<LaunchTarget>add(Target::set)
                .add(Target::start)
                .add(Target::startIfNotRunning)
                .add(Target::isRunning)
                .add(Target::restart)
                .add(Target::launchAndWait)
                .<Long>add(Target::waitForLaunch);

        b.facade(Emulators.class, Category.EMULATOR).facadeIcon("📱")
                .<String>add(Emulators::launch)
                .<String>add(Emulators::stop)
                .add((M0) Emulators::use)
                .add((M1<String>) Emulators::use);
    }

    private static void lifecycle(CatalogBuilder b) {
        b.facade(Bot.class, Category.BOT).facadeIcon("🤖")
                .add(Bot::stop);

        b.facade(Watchdog.class, Category.BOT, FacadeRole.HIDDEN).facadeIcon("🐕")
                .add(Watchdog::enable)
                .add(Watchdog::disable)
                .add(Watchdog::isEnabled)
                .add(Watchdog::checkpoint)
                .add(Watchdog::progress);

        b.facade(PopupGuard.class, Category.BOT, FacadeRole.HIDDEN)
                .<Boolean>add(PopupGuard::enabled)
                .add(PopupGuard::isEnabled)
                .add(PopupGuard::check);

        b.facade(Activity.class, Category.BOT).facadeIcon("◎")
                .<String>add(Activity::disable)
                .<String>add(Activity::enable)
                .<Activity<?>>add(Activity::name)
                .<Activity<?>>add(Activity::active)
                .<Activity<?>>add(Activity::enable)
                .<Activity<?>>add(Activity::disable);
    }

    private static void capture(CatalogBuilder b) {
        b.facade(Source.class, Category.CAPTURE).facadeIcon("🎯")
                .add(Source::current)
                .<CaptureSource>add(Source::set);

        b.facade(Window.class, Category.CAPTURE, FacadeRole.HIDDEN).facadeIcon("🪟")
                .add(Window::foreground)
                .add(Window::all)
                .<String>add(Window::find)
                .<Window>add(Window::origin)
                .<Window>add(Window::title)
                .<Window>add(Window::bounds)
                .<Window>add(Window::width)
                .<Window>add(Window::height)
                .<Window, Integer, Integer>add(Window::click)
                .<Window>add(Window::focus)
                .<Window, Integer, Integer>add(Window::move)
                .<Window, Integer, Integer>add(Window::resize);
    }

    private static void time(CatalogBuilder b) {
        b.facade(Time.class, Category.UTIL)
                .add((M0) Time::now)
                .add(Time::today)
                .add(Time::currentTime)
                .add(Time::hour)
                .add(Time::minute)
                .add(Time::second)
                .add(Time::millisecond)
                .add(Time::dayOfMonth)
                .add(Time::month)
                .add(Time::year)
                .add(Time::dayOfWeek)
                .add(Time::nowUtc)
                .add((M1<String>) Time::now)
                .<String>add(Time::setDefaultTimeZone)
                .<String>add(Time::format)
                .<Long>add(Time::elapsedMillis)
                .<Long>add(Time::elapsedSeconds)
                .<LocalTime, LocalTime>add(Time::isBetween)
                .<LocalTime, LocalTime>add(Time::isBetweenUtc)
                .add((M1<DayOfWeek[]>) Time::isDay)
                .add((M1<Month[]>) Time::isMonth)
                .add(Time::currentTimeMillis);
    }

    // -------------------------------------------------------------------------------------------------
    // Value types. Never an insert-menu entry, but in the catalog for two reasons that matter as much:
    // the editor owns their simple names for imports (Point, Window, Text all collide with java.awt), and
    // their members are reached through a variable's member submenu.
    // -------------------------------------------------------------------------------------------------

    private static void valueTypes(CatalogBuilder b) {
        b.facade(BotMaker.class, Category.UTIL, FacadeRole.VALUE)
                .<Object>add(BotMaker::print)
                .add(BotMaker::readLine)
                .add(BotMaker::readInt)
                .add(BotMaker::readDouble)
                .add(BotMaker::readBoolean);

        b.facade(Point.class, Category.GEOMETRY, FacadeRole.VALUE)
                .<Point>add(Point::x)
                .<Point>add(Point::y)
                .<Point, Rect>add(Point::inside)
                .<Point, Integer, Integer>add(Point::offset);

        b.facade(Rect.class, Category.GEOMETRY, FacadeRole.VALUE)
                .<Rect>add(Rect::x)
                .<Rect>add(Rect::y)
                .<Rect>add(Rect::width)
                .<Rect>add(Rect::height)
                .<Point, Integer, Integer>add(Rect::around)
                .<Rect>add(Rect::topLeft)
                .<Rect>add(Rect::topRight)
                .<Rect>add(Rect::bottomLeft)
                .<Rect>add(Rect::bottomRight)
                .<Rect>add(Rect::center)
                .<Rect>add(Rect::size)
                .<Rect>add(Rect::area)
                .<Rect>add(Rect::empty)
                .<Rect, Point>add(Rect::contains)
                .<Rect, Rect>add(Rect::overlaps)
                .<Rect, Rect>add(Rect::intersection)
                .<Rect, Integer>add(Rect::expand)
                .<Rect, Integer>add(Rect::shrink);

        b.facade(Size.class, Category.GEOMETRY, FacadeRole.VALUE)
                .<Size>add(Size::width)
                .<Size>add(Size::height)
                .<Size>add(Size::area)
                .<Size>add(Size::empty);

        b.facade(BotStuckException.class, Category.BOT, FacadeRole.VALUE);
        b.facade(StartMode.class, Category.BOT, FacadeRole.VALUE);

        b.facade(CaptureSource.class, Category.CAPTURE, FacadeRole.VALUE)
                .<CaptureSource>add(CaptureSource::isPresent)
                .add(CaptureSource::desktop)
                .<Integer>add(CaptureSource::monitor)
                .<String>add(CaptureSource::window)
                .add(CaptureSource::fromProjectDefault)
                .<CaptureSource, Rect>add(CaptureSource::region)
                .<CaptureSource, Integer, Integer, Integer, Integer>add(CaptureSource::region);

        b.facade(Direction.class, Category.GEOMETRY, FacadeRole.VALUE);

        b.facade(Emulator.class, Category.EMULATOR, FacadeRole.VALUE)
                .<Emulator>add(Emulator::origin)
                .<Emulator>add(Emulator::isPresent)
                .<Emulator, Point>add(Emulator::click)
                .<Emulator, Integer, Integer>add(Emulator::tap)
                .<Emulator>add(Emulator::back)
                .<Emulator>add(Emulator::home)
                .<Emulator, String>add(Emulator::text)
                .<Emulator, Integer>add(Emulator::key)
                .<Emulator, String>add(Emulator::startApp)
                .<Emulator, String>add(Emulator::stopApp)
                .<Emulator>add(Emulator::installedApps)
                .<Emulator, String>add(Emulator::isInstalled)
                .<Emulator>add(Emulator::currentApp)
                .<Emulator>add(Emulator::reboot)
                .<Emulator>add(Emulator::stop)
                .<Emulator>add(Emulator::use)
                .<Emulator>add(Emulator::name)
                .<Emulator>add(Emulator::platform)
                .<Emulator>add(Emulator::disconnect);

        b.facade(EmulatorRef.class, Category.EMULATOR, FacadeRole.VALUE)
                .<EmulatorRef>add(EmulatorRef::name)
                .<EmulatorRef>add(EmulatorRef::platform)
                .<EmulatorRef>add(EmulatorRef::endpoint)
                .<EmulatorRef>add(EmulatorRef::canLaunch)
                .<EmulatorRef>add(EmulatorRef::running)
                .<EmulatorRef>add(EmulatorRef::launch)
                .<EmulatorRef>add(EmulatorRef::stop)
                .<EmulatorRef>add(EmulatorRef::connect);

        b.facade(EmulatorSource.class, Category.EMULATOR, FacadeRole.VALUE)
                .<EmulatorSource>add(EmulatorSource::instanceName);

        b.facade(Key.class, Category.INTERACTION, FacadeRole.VALUE);
        b.facade(MouseButton.class, Category.INTERACTION, FacadeRole.VALUE);

        b.facade(LaunchTarget.class, Category.LAUNCH, FacadeRole.VALUE)
                .<LaunchTarget>add(LaunchTarget::start)
                .<LaunchTarget>add(LaunchTarget::startIfNotRunning)
                .<LaunchTarget>add(LaunchTarget::isRunning)
                .<LaunchTarget>add(LaunchTarget::restart)
                .<LaunchTarget>add(LaunchTarget::spec);

        b.facade(ColorMatch.class, Category.VISION, FacadeRole.VALUE)
                .<ColorMatch>add(ColorMatch::isFound)
                .<ColorMatch>add(ColorMatch::color)
                .<ColorMatch>add(ColorMatch::pixelCount)
                .<ColorMatch>add(ColorMatch::coverage)
                .<ColorMatch>add(ColorMatch::center)
                .<ColorMatch>add(ColorMatch::topLeft)
                .<ColorMatch>add(ColorMatch::bounds)
                .<ColorMatch>add(ColorMatch::width)
                .<ColorMatch>add(ColorMatch::height);

        b.facade(ImageTemplate.class, Category.VISION, FacadeRole.VALUE)
                .<ImageTemplate>add(ImageTemplate::id)
                .<ImageTemplate>add(ImageTemplate::filePath)
                .<ImageTemplate>add(ImageTemplate::threshold)
                .<ImageTemplate, Double>add(ImageTemplate::setThreshold)
                .<ImageTemplate>add(ImageTemplate::width)
                .<ImageTemplate>add(ImageTemplate::height);

        b.facade(ImageTemplateGroup.class, Category.VISION, FacadeRole.VALUE)
                .add((M1<ImageTemplate[]>) ImageTemplateGroup::of)
                .add((M1<List<ImageTemplate>>) ImageTemplateGroup::of)
                .<ImageTemplateGroup>add(ImageTemplateGroup::isEmpty);

        b.facade(Matches.class, Category.VISION, FacadeRole.VALUE)
                .add(Matches::none)
                .<Matches, ImageTemplate>add(Matches::has)
                .<Matches, ImageTemplate[]>add(Matches::hasAll)
                .<Matches, ImageTemplate[]>add(Matches::hasAny)
                .<Matches, ImageTemplate>add(Matches::get)
                .<Matches>add(Matches::all)
                .<Matches>add(Matches::best)
                .<Matches>add(Matches::isEmpty)
                .<Matches>add(Matches::count);

        b.facade(MatchResult.class, Category.VISION, FacadeRole.VALUE)
                .<MatchResult>add(MatchResult::isFound)
                .<MatchResult>add(MatchResult::confidence)
                .<MatchResult>add(MatchResult::center)
                .<MatchResult>add(MatchResult::randomClickPoint)
                .<MatchResult>add(MatchResult::topLeft)
                .<MatchResult>add(MatchResult::topRight)
                .<MatchResult>add(MatchResult::bottomLeft)
                .<MatchResult>add(MatchResult::bottomRight)
                .<MatchResult, Integer, Integer>add(MatchResult::pointWithOffset)
                .<MatchResult>add(MatchResult::rect)
                .<MatchResult>add(MatchResult::width)
                .<MatchResult>add(MatchResult::height)
                .<MatchResult>add(MatchResult::templateId);

        b.facade(Precision.class, Category.VISION, FacadeRole.VALUE)
                .<Double>add(Precision::of)
                .<Double, Integer, Integer>add(Precision::of)
                .<Precision, Double>add(Precision::tolerance)
                .<Precision, Integer>add(Precision::minArea)
                .<Precision, Integer>add(Precision::minCount)
                .<Precision>add(Precision::equivalentSide);

        b.facade(TextMatch.class, Category.VISION, FacadeRole.VALUE)
                .<TextMatch>add(TextMatch::isFound)
                .<TextMatch>add(TextMatch::text)
                .<TextMatch>add(TextMatch::confidence)
                .<TextMatch>add(TextMatch::bounds)
                .<TextMatch>add(TextMatch::center)
                .<TextMatch>add(TextMatch::topLeft);
    }
}
