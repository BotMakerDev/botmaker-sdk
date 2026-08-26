package com.botmaker.sdk.api.vision;

import com.botmaker.plugin.api.palette.Facade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a group of templates looked like in <em>one</em> frame: for each {@link ImageTemplate} that cleared the
 * confidence threshold, its best {@link MatchResult}.
 *
 * <p>This is the value the group lambda helpers hand your action — {@link ImageFinder#ifFindAny},
 * {@link ImageFinder#whileFindAny}, {@link ImageFinder#ifFindAll}, {@link ImageFinder#whileFindAll}. It exists
 * because "which of these are on screen right now" is the question a bot actually asks, and neither a single
 * {@code MatchResult} (the old {@code *Any} signature — it only told you the first hit, so a second template
 * present in the same frame was invisible) nor a bare {@code Runnable} (the old {@code *All} signature — it told
 * you nothing at all) could answer it. Branching on a <em>combination</em> is the whole point:
 *
 * <pre>{@code
 * ImageFinder.whileFindAny(POPUPS, found -> {
 *     if (found.has(mail)) {
 *         if (found.has(claimAll)) ImageClicker.click(found.get(claimAll));
 *         else                     ImageClicker.click(found.get(mail));
 *     } else if (found.has(tapToClose)) {
 *         while (ImageClicker.click(tapToClose));
 *     }
 * });
 * }</pre>
 *
 * <p><b>One frame, one answer.</b> Every {@code has}/{@code get} on the same {@code Matches} reads the same
 * capture, so two questions about the same instant can't disagree — which is exactly what a chain of separate
 * {@code find} calls could not promise. The next loop iteration re-captures and hands you a fresh instance.
 *
 * <p><b>Keyed by {@link MatchResult#templateId() template id}</b>, not by template identity: a template
 * reloaded from the same image file answers {@code has} for a match found with its twin. When two templates in a
 * group share an id, the higher-confidence match wins the slot — a lookup can only return one.
 *
 * <p>Immutable and iteration-ordered by the group's declaration order.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): everything a bot asks a frame is offered. The
 * verdict covers the instance methods as well as {@link #none()}, because this is a value a bot holds — the
 * lambda parameter of {@code ifFindAny}/{@code whileFindAny} — and the questions it answers are asked through
 * that value, not through a static facade submenu.
 */
@Facade(category = "vision", categoryLabel = "Vision", role = "VALUE", order = 97)
public final class Matches {

    private static final Matches NONE = new Matches(Collections.emptyMap());

    private final Map<String, MatchResult> byTemplateId;

    private Matches(Map<String, MatchResult> byTemplateId) {
        this.byTemplateId = byTemplateId;
    }

    /** The empty result — nothing in the group was visible. */
    public static Matches none() {
        return NONE;
    }

    /**
     * Internal: builds a {@code Matches} from the found results of a single capture, keeping the
     * highest-confidence result per template id and dropping anything not found.
     */
    static Matches of(List<MatchResult> results) {
        Map<String, MatchResult> map = new LinkedHashMap<>();
        for (MatchResult result : results) {
            if (result == null || !result.isFound() || result.templateId() == null) continue;
            map.merge(result.templateId(), result,
                    (a, b) -> b.confidence() > a.confidence() ? b : a);
        }
        return map.isEmpty() ? NONE : new Matches(Collections.unmodifiableMap(map));
    }

    /** Whether {@code template} was visible in this frame. */
    public boolean has(ImageTemplate template) {
        return template != null && byTemplateId.containsKey(template.id());
    }

    /** Whether <em>every</em> given template was visible. Vacuously true for no arguments. */
    public boolean hasAll(ImageTemplate... templates) {
        if (templates == null) return true;
        for (ImageTemplate template : templates) {
            if (!has(template)) return false;
        }
        return true;
    }

    /** Whether <em>at least one</em> of the given templates was visible. False for no arguments. */
    public boolean hasAny(ImageTemplate... templates) {
        if (templates == null) return false;
        for (ImageTemplate template : templates) {
            if (has(template)) return true;
        }
        return false;
    }

    /**
     * The match for {@code template}, or {@link MatchResult#notFound()} when it wasn't visible — never null, so
     * it is safe to pass straight to {@link ImageClicker#click(MatchResult)}, which no-ops on a miss.
     */
    public MatchResult get(ImageTemplate template) {
        if (template == null) return MatchResult.notFound();
        MatchResult result = byTemplateId.get(template.id());
        return result != null ? result : MatchResult.notFound();
    }

    /** Every match found in this frame, in the group's declaration order. Never null; possibly empty. */
    public List<MatchResult> all() {
        return List.copyOf(byTemplateId.values());
    }

    /**
     * The highest-confidence match in this frame, or {@link MatchResult#notFound()} when nothing was visible.
     * Ties keep the earlier template — the group's order is the caller's priority order.
     */
    public MatchResult best() {
        MatchResult best = null;
        for (MatchResult result : byTemplateId.values()) {
            if (best == null || result.confidence() > best.confidence()) best = result;
        }
        return best != null ? best : MatchResult.notFound();
    }

    /** True when no template in the group was visible. */
    public boolean isEmpty() {
        return byTemplateId.isEmpty();
    }

    /** How many distinct templates were visible. */
    public int count() {
        return byTemplateId.size();
    }

    @Override
    public String toString() {
        if (byTemplateId.isEmpty()) return "Matches[none]";
        List<String> ids = new ArrayList<>(byTemplateId.keySet());
        return "Matches" + ids;
    }
}
