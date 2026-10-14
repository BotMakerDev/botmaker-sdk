package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.PictureTag;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.vision.TemplateNames;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The pictures' tags as the bot's Java holds them (2026-10-14): a custom tag is a constant of {@code Tags}
 * ({@code PictureTag.named("UI")}), and a picture names its tags and activities on its own constant —
 * {@code .tags(List.of(Tags.UI)).activities(List.of(Activities.Collect.class))}. Nothing is stored beside the
 * pictures; {@code templates.json} is gone, and nothing reads it.
 *
 * <p>Tags are shown and named by their words ("Arena of Kings"), an activity by its label ("Daily reward"), as
 * {@link TagCatalog} lists them; this class maps those words to the constants and enums the Java holds. Every
 * method is on the FX thread, where the host's values are.
 */
public final class PictureTags {

    static final ManagedSet<PictureTag> TAGS = ManagedSet.of(SdkValues.TAGS);
    private static final ManagedSet<Outcome> ACTIVITIES = ManagedSet.of(SdkValues.ACTIVITIES);

    private PictureTags() {}

    /**
     * Every picture's tags and activities by its name, and the custom tags {@code Tags} declares; empty for a bot
     * with no values.
     */
    public static TemplateManifest read(StudioServices services) {
        PluginValues values = services == null ? null : services.pluginValues();
        if (values == null) return TemplateManifest.empty();
        Map<String, String> labels = tagLabels(values);
        Map<String, SortedSet<String>> byPicture = new LinkedHashMap<>();
        for (Member member : TemplateUses.SET.members(values)) {
            String baseName = TemplateNames.baseNameFor(member.name());
            if (baseName == null) continue;
            TemplateUses.SET.read(values, member).ifPresent(picture -> {
                SortedSet<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                for (PictureTag tag : picture.tags()) {
                    String label = labelOf(tag, labels);
                    if (label != null) tags.add(label);
                }
                picture.activities().forEach(activity -> tags.add(activity.label()));
                byPicture.put(baseName, tags);
            });
        }
        return new TemplateManifest(byPicture, new TreeSet<>(labels.values()));
    }

    /**
     * Files the picture {@code baseName} under exactly {@code labels}: each a custom tag's words or an activity's
     * label, the rest dropped. Its constant is declared first when it has none.
     *
     * @return empty when written; else why not
     */
    public static Optional<String> set(StudioServices services, String baseName, Collection<String> labels) {
        PluginValues values = services.pluginValues();
        Member member = pictureMember(baseName);
        if (member == null) return Optional.of("\"" + baseName + "\" cannot be a constant's name, so it cannot be tagged.");
        Optional<String> undeclared = TemplateUses.declare(values, baseName);
        if (undeclared.isPresent()) return undeclared;
        Optional<ValueContext> ctx = TemplateUses.SET.open(values, member);
        Optional<ImageTemplate> held = ctx.flatMap(TemplateUses.SET::read);
        if (held.isEmpty()) {
            return Optional.of(TemplateNames.CLASS_NAME + "." + member + " is written by hand; its tags were left as they are.");
        }
        Map<String, String> constants = tagConstants(values);
        Map<String, Activity> activities = activities(values);
        // A tag spelled out on the picture by hand is no constant of Tags, so nothing here names it: it stays.
        List<PictureTag> tags = new ArrayList<>(held.get().tags().stream()
                .filter(tag -> BotConstants.tagName(tag) == null).toList());
        List<Activity> filed = new ArrayList<>();
        for (String label : labels) {
            Activity activity = activities.get(label);
            if (activity != null) {
                filed.add(activity);
                continue;
            }
            String constant = constants.get(label);
            if (constant != null) tags.add(BotConstants.tag(constant));
        }
        ImageTemplate now = held.get().tags(tags).inActivities(filed);
        if (now.equals(held.get())) return Optional.empty();
        return ctx.get().write(now)
                .map(refused -> TemplateNames.CLASS_NAME + "." + member + " was not rewritten: " + refused);
    }

    /** Declares the custom tag {@code label} in {@code Tags}, the class made on the first; empty when done. */
    public static Optional<String> declare(StudioServices services, String label) {
        PluginValues values = services.pluginValues();
        if (values == null) return Optional.of("Studio has no source tree open.");
        if (tagConstants(values).containsKey(label)) return Optional.empty();
        Optional<String> missing = values.create(TAGS.value());
        if (missing.isPresent()) return missing;
        return TAGS.add(values, Member.of(freeConstant(values, label)), PictureTag.named(label));
    }

    /**
     * Renames the custom tag {@code from} to {@code to}: its words, and its constant with every picture naming it.
     * Empty when done.
     */
    public static Optional<String> rename(StudioServices services, String from, String to) {
        PluginValues values = services.pluginValues();
        Map<String, String> constants = tagConstants(values);
        String constant = constants.get(from);
        if (constant == null) return Optional.of("There is no tag called \"" + from + "\".");
        String taken = constants.get(to);
        if (taken != null && !taken.equals(constant)) return Optional.of("There is already a tag called \"" + to + "\".");
        Member member = Member.of(constant);
        // Its words first: a constant renamed and then refused its words would show under the old ones.
        Optional<String> unwritten = TAGS.open(values, member).map(ctx -> ctx.write(PictureTag.named(to)))
                .orElse(Optional.of("Tags." + constant + " could not be rewritten."));
        if (unwritten.isPresent()) return unwritten;
        if (constantFor(to).equals(constant)) return Optional.empty();
        return TAGS.rename(values, member, freeConstant(values, to))
                .map(refused -> "Tags." + constant + " now says \"" + to + "\", but kept its name: " + refused);
    }

    /** Deletes the custom tag {@code label}: off every picture, then its constant. Empty when done. */
    public static Optional<String> delete(StudioServices services, String label) {
        PluginValues values = services.pluginValues();
        String constant = tagConstants(values).get(label);
        if (constant == null) return Optional.empty();
        TemplateManifest manifest = read(services);
        for (Map.Entry<String, SortedSet<String>> picture : manifest.tagsByTemplate().entrySet()) {
            if (!picture.getValue().contains(label)) continue;
            List<String> kept = picture.getValue().stream().filter(tag -> !tag.equalsIgnoreCase(label)).toList();
            Optional<String> refused = set(services, picture.getKey(), kept);
            if (refused.isPresent()) return refused;
        }
        return TAGS.remove(values, Member.of(constant));
    }

    /**
     * {@code label} as a constant's name: {@code "Arena of Kings"} is {@code ARENA_OF_KINGS}. A name no constant
     * can start with is prefixed {@code TAG_}.
     */
    static String constantFor(String label) {
        String upper = label.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (upper.isEmpty() || !Character.isLetter(upper.charAt(0))) upper = "TAG_" + upper;
        return upper.endsWith("_") ? upper.substring(0, upper.length() - 1) : upper;
    }

    /** {@link #constantFor} of {@code label}, suffixed {@code _2}, {@code _3}… while another tag holds it. */
    private static String freeConstant(PluginValues values, String label) {
        String base = constantFor(label);
        String name = base;
        for (int i = 2; TAGS.contains(values, Member.of(name)); i++) name = base + "_" + i;
        return name;
    }

    /** Each {@code Tags} constant's words, by the constant's name, in the order the class declares them. */
    private static Map<String, String> tagLabels(PluginValues values) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (Member member : TAGS.members(values)) {
            TAGS.read(values, member).ifPresent(tag -> labels.put(member.name(), tag.name()));
        }
        return labels;
    }

    /** Each custom tag's constant, by its words, case-insensitively. */
    private static Map<String, String> tagConstants(PluginValues values) {
        Map<String, String> constants = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        tagLabels(values).forEach((constant, label) -> constants.putIfAbsent(label, constant));
        return constants;
    }

    /** Each of the bot's activities, by its label, case-insensitively. */
    private static Map<String, Activity> activities(PluginValues values) {
        Map<String, Activity> activities = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String group : ACTIVITIES.groups(values)) {
            Activity activity = new Activity(group);
            activities.putIfAbsent(activity.label(), activity);
        }
        return activities;
    }

    /**
     * The words of the tag a picture names: its constant's, or its own when it is spelled out there by hand; null
     * for a constant {@code Tags} does not declare.
     */
    private static String labelOf(PictureTag tag, Map<String, String> labels) {
        String constant = BotConstants.tagName(tag);
        return constant != null ? labels.get(constant) : tag.name();
    }

    private static Member pictureMember(String baseName) {
        String constant = TemplateNames.constantFor(baseName);
        return constant == null ? null : Member.of(constant);
    }
}
