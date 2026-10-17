package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Reached;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;

import javax.lang.model.SourceVersion;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * One screen as the map holds it: its {@link Setup} and its inputs by field name, in the order the class declares
 * them. A run reads it off the bot's {@link Screen} class ({@link #of(Screen)}); Studio's tools off the class's
 * values as the host reads them, where an input another screen names is a reference ({@code BotConstants.input}).
 *
 * @param setup  everything but its inputs
 * @param inputs its inputs, field name to input
 */
public record MapScreen(Setup setup, Map<String, Input> inputs) {

    public MapScreen {
        setup = setup == null ? Setup.named("") : setup;
        inputs = inputs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
    }

    /** A screen of {@code setup} with no input yet. */
    public static MapScreen of(Setup setup) {
        return new MapScreen(setup, Map.of());
    }

    /**
     * {@code screen} as the bot's class holds it: its {@code public final} input fields, in the order declared, and its
     * setup, read now that every constant is set.
     *
     * @throws IllegalStateException when its setup throws, so the bot's author sees why rather than a screen missing
     */
    public static MapScreen of(Screen screen) {
        Map<String, Input> inputs = new LinkedHashMap<>();
        for (Field field : screen.getClass().getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)
                    || !Input.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                if (field.get(screen) instanceof Input input) inputs.put(field.getName(), input);
            } catch (IllegalAccessException | LinkageError unreadable) {
                // Not the bot's to read here: the screen lacks that input, and a walk through it says so.
            }
        }
        Setup setup;
        try {
            setup = screen.setup();
        } catch (RuntimeException failed) {
            throw new IllegalStateException(screen.getClass().getSimpleName() + ".setup() failed: " + failed, failed);
        }
        return new MapScreen(setup, inputs);
    }

    public String name() {
        return setup.name();
    }

    public List<ImageTemplate> markers() {
        return setup.markers();
    }

    public List<ImageTemplate> absent() {
        return setup.absent();
    }

    public Way from() {
        return setup.from();
    }

    /** The input its way in goes through; null for none. */
    public Input fromInput() {
        return setup.fromInput();
    }

    public List<Way> alsoFrom() {
        return setup.alsoFrom();
    }

    public Input back() {
        return setup.back();
    }

    public List<ScreenVariant> variants() {
        return setup.variants();
    }

    public boolean isPopup() {
        return setup.isPopup();
    }

    public String notes() {
        return setup.notes();
    }

    public Reached reached() {
        return setup.reached();
    }

    public List<String> owes() {
        return setup.owes();
    }

    public Point at() {
        return setup.at();
    }

    public boolean hasMarker() {
        return setup.hasMarker();
    }

    public ImageTemplate firstMarker() {
        return setup.firstMarker();
    }

    /** Its ways in: its {@code from}, then each {@code alsoFrom}. */
    public List<Way> ways() {
        return setup.ways();
    }

    /** This screen, its setup as {@code change} makes it. */
    public MapScreen setup(UnaryOperator<Setup> change) {
        return new MapScreen(change.apply(setup), inputs);
    }

    /** This screen with {@code input} as its field {@code field}: in its place, or added last. */
    public MapScreen withInput(String field, Input input) {
        Map<String, Input> now = new LinkedHashMap<>(inputs);
        now.put(field, input);
        return new MapScreen(setup, now);
    }

    /**
     * This screen without its field {@code field}. A button taken off is off its strip too, and a strip left with no
     * member is none.
     */
    public MapScreen withoutInput(String field) {
        Map<String, Input> now = new LinkedHashMap<>(inputs);
        Input gone = now.remove(field);
        if (gone instanceof Button button && button.picture() != null) {
            String id = button.picture().id();
            for (Map.Entry<String, Input> e : inputs.entrySet()) {
                if (!(e.getValue() instanceof Strip strip) || !strip.has(button.picture())) continue;
                List<ImageTemplate> members = strip.members().stream().filter(m -> !m.id().equals(id)).toList();
                if (members.isEmpty()) now.remove(e.getKey());
                else now.put(e.getKey(), strip.members(members));
            }
        }
        return new MapScreen(setup, now);
    }

    /**
     * A free field name for an input called {@code words} ({@code beer_buy_tavern}, {@code Beer buy}): its words in
     * lowerCamelCase, {@code beerBuyTavern}, numbered when taken, {@code input} when no word is left.
     */
    public String fieldFor(String words) {
        StringBuilder name = new StringBuilder();
        for (String word : (words == null ? "" : words).split("[^A-Za-z0-9]+")) {
            if (word.isEmpty()) continue;
            String lower = word.toLowerCase(Locale.ROOT);
            name.append(name.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
        }
        String base = name.isEmpty() ? "input" : Character.isDigit(name.charAt(0)) ? "n" + name : name.toString();
        if (SourceVersion.isKeyword(base)) base = base + "Input";
        String field = base;
        for (int n = 2; inputs.containsKey(field); n++) field = base + n;
        return field;
    }

    /** Its field {@code field}; empty when it has none of that name. */
    public Optional<Input> input(String field) {
        return Optional.ofNullable(field == null ? null : inputs.get(field));
    }

    /** The field holding {@code input}: this very value, else one equal to it; empty for none. */
    public Optional<String> fieldOf(Input input) {
        if (input == null) return Optional.empty();
        for (Map.Entry<String, Input> e : inputs.entrySet()) if (e.getValue() == input) return Optional.of(e.getKey());
        for (Map.Entry<String, Input> e : inputs.entrySet()) if (e.getValue().equals(input)) return Optional.of(e.getKey());
        return Optional.empty();
    }

    /** Its strips, by field name. */
    public Map<String, Strip> strips() {
        Map<String, Strip> strips = new LinkedHashMap<>();
        inputs.forEach((field, input) -> {
            if (input instanceof Strip strip) strips.put(field, strip);
        });
        return strips;
    }

    /** The strip {@code input}'s picture is a member of; empty for a button at its place, a key or a strip. */
    public Optional<Strip> stripOf(Input input) {
        if (!(input instanceof Button button) || button.picture() == null) return Optional.empty();
        return strips().values().stream().filter(strip -> strip.has(button.picture())).findFirst();
    }

    /** The field of the strip {@code input}'s picture is a member of; empty as for {@link #stripOf}. */
    public Optional<String> stripFieldOf(Input input) {
        if (!(input instanceof Button button) || button.picture() == null) return Optional.empty();
        return strips().entrySet().stream().filter(e -> e.getValue().has(button.picture())).map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Its field {@code field} with what its strip says of its members put on it where it says nothing itself: a strip
     * that comes and goes makes each member come and go. Empty for no such field.
     */
    public Optional<Input> effective(String field) {
        return input(field).map(this::effective);
    }

    /** {@code input}, one of its fields, with its strip's defaults ({@link #effective(String)}). */
    public Input effective(Input input) {
        Optional<Strip> strip = stripOf(input);
        if (strip.isEmpty()) return input;
        Strip s = strip.get();
        Input now = input;
        if (s.comesAndGoes() && !now.comesAndGoes()) now = now.sometimes();
        if (now.when() == null && s.when() != null) now = now.when(s.when());
        if (!now.isInfo() && s.isInfo()) now = now.info();
        if (now.never() == null && s.never() != null) now = now.never(s.never());
        if (now.lock() == null && s.lock() != null) now = now.locked(s.lock());
        return now;
    }
}
