// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.signin;

import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Condition;

import java.util.Objects;
import java.util.Optional;

/**
 * What {@code Accounts.signedIn(Login::email)} answers: whether Android on the run's instance holds the account the
 * parameter names, as this PC's sign-in door reads it. No door, no instance, no address dealt, or a door that cannot
 * say answers unknown, which never closes a way.
 *
 * <p>A value, equal by the parameter it reads, so the editor reads it and writes it back ({@code ScreenTypes}).
 *
 * @param email the parameter holding the account's address: a record's part ({@code Login::email}) or a String
 */
public record SignedInCheck(Params.Of<?, ?> email) implements Condition {

    public SignedInCheck {
        Objects.requireNonNull(email, "email");
    }

    @Override
    public Answer answer() {
        return answer(address(), SignInClient.door(), SignInClient.instance());
    }

    /** What the door says of {@code email} on {@code instance}; unknown when any of the three is missing. */
    static Answer answer(Optional<String> email, Optional<SignInClient> door, Optional<String> instance) {
        if (email.isEmpty() || door.isEmpty() || instance.isEmpty()) return Answer.UNKNOWN;
        return door.get().signedIn(email.get(), instance.get()).map(Answer::of).orElse(Answer.UNKNOWN);
    }

    @SuppressWarnings("unchecked")
    private Optional<String> address() {
        try {
            return Params.known((Params.Of<Object, Object>) email).map(String::valueOf).map(String::strip)
                    .filter(address -> !address.isEmpty());
        } catch (RuntimeException unread) {
            return Optional.empty();
        }
    }
}
