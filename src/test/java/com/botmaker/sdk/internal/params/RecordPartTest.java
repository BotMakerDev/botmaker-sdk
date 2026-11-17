// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.params;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.secrets.Secret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code Login::email} reads the part of the bot's one {@code Login} parameter; none or two are refused with words. */
public class RecordPartTest {

    /** A pooled login, as the sign-in bot keeps one. */
    public record Login(String email, Secret password, Secret code) {}

    /** The bot's one login; what a pool dealt this run. */
    @Param
    public static class SignInParams {
        public static Login dealt;

        public Login login() {
            return dealt;
        }
    }

    /** A second group with a login of its own: a part cannot say which. */
    @Param
    public static class Other {
        public Login spare() {
            return null;
        }
    }

    @AfterEach
    void forget() {
        SignInParams.dealt = null;
        RunParams.groups(List.of());
    }

    @Test
    void aPartReadsTheBotsOneParameterOfItsRecord() {
        RunParams.groups(List.of(SignInParams.class));
        SignInParams.dealt = new Login("farm@example.com", Secret.of("farm password"), Secret.of(""));
        assertEquals("farm@example.com", Params.get(Login::email));
        assertEquals(Secret.of("farm password"), Params.get(Login::password));
        assertEquals("farm@example.com", Params.known(Login::email).orElseThrow());
        SignInParams.dealt = null;
        assertNull(Params.get(Login::email), "no login dealt: no part");
    }

    @Test
    void noneOrTwoParametersOfTheRecordAreRefusedWithWords() {
        IllegalArgumentException none = assertThrows(IllegalArgumentException.class, () -> Params.get(Login::email));
        assertTrue(none.getMessage().contains("no parameter of that type"), none.getMessage());
        assertTrue(none.getMessage().contains("Login login()"), none.getMessage());

        RunParams.groups(List.of(SignInParams.class, Other.class));
        IllegalArgumentException two = assertThrows(IllegalArgumentException.class, () -> Params.get(Login::email));
        assertTrue(two.getMessage().contains("2 parameters"), two.getMessage());
        assertTrue(two.getMessage().contains("SignInParams.login"), two.getMessage());
    }

    @Test
    void aPartIsNeverSetAlone() {
        RunParams.groups(List.of(SignInParams.class));
        IllegalArgumentException set = assertThrows(IllegalArgumentException.class,
                () -> Params.set(Login::email, "x@example.com"));
        assertTrue(set.getMessage().contains("set or forget the whole parameter"), set.getMessage());
    }

    @Test
    void isSetHoldsOnlyForAValueNotBlank() {
        RunParams.groups(List.of(SignInParams.class));
        SignInParams.dealt = new Login("farm@example.com", Secret.of("farm password"), Secret.of(""));
        assertEquals(Answer.YES, Params.when(Login::password).isSet().answer());
        assertEquals(Answer.NO, Params.when(Login::code).isSet().answer(), "a blank secret is no code");
        SignInParams.dealt = null;
        assertEquals(Answer.NO, Params.when(Login::email).isSet().answer(), "no login dealt");
    }
}
