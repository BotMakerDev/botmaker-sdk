// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.signin;

import com.botmaker.plugin.api.value.Link;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.api.secrets.Secret;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * An account of this PC's account book (2026-11-27, spec {@code 2026-11-27-claimed-accounts-design.md}): a Google,
 * Facebook or mail account, or a user's account in a game, kept for its launch target. {@code Sdk.account()} is a
 * parameter, and several runs at once are each dealt one of the book's accounts for the bot's launch targets, the
 * account following the place the run plays at. BotMaker keeps the accounts, never the bot: one run plays the bot's own
 * value.
 *
 * <pre>{@code
 * Account.of("emu-app:com.game", "main")
 *         .signsInWith(Account.google("main@gmail.com"))
 *         .playsAt(Place.android("MEmu_1"))
 * }</pre>
 *
 * <p>Its password is never in it: {@link #password()} names the secret this PC keeps for it.
 */
public final class Account {

    /** What kind of account it is, as the book files it. */
    public enum Kind {
        /** A Google account, the one kind BotMaker signs in. */
        GOOGLE("google", "Google"),
        /** A Facebook account. */
        FACEBOOK("facebook", "Facebook"),
        /** A mail account. */
        MAIL("mail", "Mail"),
        /** A user's account in a game, kept for its launch target. */
        LAUNCH_TARGET("launch-target", "Launch target"),
        /** A kind this release does not know. */
        UNKNOWN("unknown", "Account");

        private final String id;
        private final String displayName;

        Kind(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        /** The kind as the book spells it. */
        public String id() {
            return id;
        }

        /** The kind for a person. */
        public String displayName() {
            return displayName;
        }

        /** The kind whose {@link #id()} is {@code id}; {@link #UNKNOWN} for any other. */
        public static Kind fromId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) return kind;
            }
            return UNKNOWN;
        }
    }

    private static final Account NONE = new Account(Kind.LAUNCH_TARGET, "", "", "", null, null);

    private final Kind kind;
    private final String target;
    private final String login;
    private final String note;
    private final Account signsInWith;
    private final Place playsAt;

    private Account(Kind kind, String target, String login, String note, Account signsInWith, Place playsAt) {
        this.kind = kind;
        this.target = target == null || kind != Kind.LAUNCH_TARGET ? "" : target.strip();
        this.login = login == null ? "" : login.strip();
        this.note = note == null ? "" : note.strip();
        this.signsInWith = signsInWith;
        this.playsAt = playsAt;
    }

    /**
     * The account of the launch target {@code target} ({@code emu-app:com.game}) that a player logs in as
     * {@code login}.
     */
    public static Account of(String target, String login) {
        return new Account(Kind.LAUNCH_TARGET, target, login, "", null, null);
    }

    /** The Google account whose email address is {@code login}. */
    public static Account google(String login) {
        return new Account(Kind.GOOGLE, "", login, "", null, null);
    }

    /** The Facebook account {@code login} names. */
    public static Account facebook(String login) {
        return new Account(Kind.FACEBOOK, "", login, "", null, null);
    }

    /** The mail account whose address is {@code login}. */
    public static Account mail(String login) {
        return new Account(Kind.MAIL, "", login, "", null, null);
    }

    /** No account: what a bot's own value starts as, and what a run is dealt when the book has none for its games. */
    public static Account none() {
        return NONE;
    }

    /** This account, with {@code note} said about it; blank for none. */
    @Link
    public Account note(String note) {
        return new Account(kind, target, login, note, signsInWith, playsAt);
    }

    /**
     * This launch target's account, signing in to its game with {@code account}, a Google, Facebook or mail account
     * of the book; {@code null} for none.
     *
     * @throws IllegalArgumentException for an account of another launch target, or on an account that is no launch
     *                                  target's
     */
    public Account signsInWith(Account account) {
        if (account != null && (kind != Kind.LAUNCH_TARGET || account.kind == Kind.LAUNCH_TARGET)) {
            throw new IllegalArgumentException("only a launch target's account signs in with a Google, Facebook or"
                    + " mail account: " + this + " with " + account);
        }
        return new Account(kind, target, login, note, account, playsAt);
    }

    /**
     * This launch target's account, played at {@code place}; {@code null} for any place.
     *
     * @throws IllegalArgumentException on an account that is no launch target's
     */
    public Account playsAt(Place place) {
        if (place != null && kind != Kind.LAUNCH_TARGET) {
            throw new IllegalArgumentException("only a launch target's account plays at a place: " + this);
        }
        return new Account(kind, target, login, note, signsInWith, place);
    }

    /** What kind of account this is. */
    public Kind kind() {
        return kind;
    }

    /** A launch target's account's launch target, stripped; {@code ""} for any other kind. */
    public String target() {
        return target;
    }

    /** What the account logs in as, stripped: an email address, or a name in a game; {@code ""} for none. */
    public String login() {
        return login;
    }

    /** What the user said about it; {@code ""} for nothing. */
    public String note() {
        return note;
    }

    /** The Google, Facebook or mail account a launch target's account signs in with; empty for none. */
    public Optional<Account> signsInWith() {
        return Optional.ofNullable(signsInWith);
    }

    /** The place a launch target's account is played at; empty for any. */
    public Optional<Place> playsAt() {
        return Optional.ofNullable(playsAt);
    }

    /** Whether this names no account. */
    public boolean isNone() {
        return login.isEmpty();
    }

    /**
     * The secret this PC keeps the account's password as, by the account's key
     * ({@code launch-target:emu-app:com.game:main}): the host types it, and its value never reaches the run.
     */
    public Secret password() {
        return Secret.of(isNone() ? "" : toString());
    }

    /** Equal by every part, the login and the launch target as written. */
    @Override
    public boolean equals(Object other) {
        return other instanceof Account that && kind == that.kind && target.equals(that.target)
                && login.equals(that.login) && note.equals(that.note) && Objects.equals(signsInWith, that.signsInWith)
                && Objects.equals(playsAt, that.playsAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, target, login, note, signsInWith, playsAt);
    }

    /** The account's key: its kind, its launch target if any, and its login lower-cased ({@code google:a@b.c}). */
    @Override
    public String toString() {
        String name = login.toLowerCase(Locale.ROOT);
        return kind == Kind.LAUNCH_TARGET ? kind.id() + ":" + target + ":" + name : kind.id() + ":" + name;
    }
}
