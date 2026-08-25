package com.botmaker.sdk.api.authoring;

import com.botmaker.sdk.api.meta.Since;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The declared range of a number variable — <b>both ends optional and independent</b>, both stored as text so
 * a {@link ValueType#DURATION} bound can be written the way a duration is ({@code "30s"}) rather than as the
 * millisecond count nobody means.
 *
 * <p>Independent is the point: "at most 10" is a sentence a person says, and it was once unsayable because
 * the widget only appeared when both ends were filled in. A missing end is the type's own limit, not a reason
 * to fall back to an unguided text field.
 *
 * <p>Both ends are advice to the editor's widget and a clamp when a value is normalised, never a validation
 * that can fail — a value outside the range is pulled to the nearest bound, because the alternative is a
 * project that refuses to save because of a limit somebody tightened after the fact.
 *
 * <p>To the SDK itself this is inert: the generator stores it and emits nothing from it. It is here because
 * the file has one owner, and splitting the file by who reads which field is how two authors get created.
 */
@Since("1.2.0")
@JsonIgnoreProperties(ignoreUnknown = true)
public record Range(String min, String max) {

    /** No range declared — the state every number variable starts in. */
    public static final Range NONE = new Range(null, null);

    public Range {
        min = blankToNull(min);
        max = blankToNull(max);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return min == null && max == null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
