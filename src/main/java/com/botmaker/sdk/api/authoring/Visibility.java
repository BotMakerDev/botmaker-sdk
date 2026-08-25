package com.botmaker.sdk.api.authoring;

import com.botmaker.sdk.api.meta.Since;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Who a variable is for: everyone who runs the bot, or only the person building it.
 *
 * <p>Every variable is a knob, but they are not all the same kind of knob. "How many ore before going home"
 * is a setting the bot's user should be handed; "retry delay after a failed swipe" is a number the author
 * tuned once and does not want reopened. Both are emitted identically, so the difference cannot be read off
 * the generated field — it has to be declared, and this is where.
 *
 * <p><b>Two different defaults, both deliberate.</b> A <em>new</em> variable is {@link #PUBLIC} — a variable
 * exists to be configured. An <em>unrecognised</em> id, from a newer SDK, reads as {@link #EDITOR_ONLY}:
 * "I don't know what this says" must not publish something to the bot's user.
 */
@Since("1.2.0")
public enum Visibility {

    /** Offered to the bot's user. The author is saying "this is yours to set". */
    PUBLIC("public"),

    /** Hidden from the user. Also how an unrecognised id reads — see above. */
    EDITOR_ONLY("editor");

    private final String id;

    Visibility(String id) {
        this.id = id;
    }

    /** The stable value written to {@code activities.json}. Persisted — do not change. */
    @JsonValue
    public String id() {
        return id;
    }

    /** Total: anything unrecognised, {@code null} included, reads as {@link #EDITOR_ONLY}. */
    @JsonCreator
    public static Visibility fromId(String id) {
        if (id == null) return EDITOR_ONLY;
        for (Visibility v : values()) {
            if (v.id.equalsIgnoreCase(id) || v.name().equalsIgnoreCase(id)) return v;
        }
        return EDITOR_ONLY;
    }
}
