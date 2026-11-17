// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.signin;

import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.internal.signin.SignedInCheck;

/**
 * The accounts an instance holds, asked of BotMaker, which reads them off the device; the run hands it only the
 * address its parameter holds.
 *
 * <pre>{@code
 * public static final Goal SIGNED_IN = Goal.of(Accounts.signedIn(Login::email));
 * }</pre>
 */
@Palette(category = "signin", categoryLabel = "Sign-in", icon = "🔑")
public final class Accounts {

    private Accounts() {}

    /**
     * Whether the instance this run reads holds the Google account {@code email} names: yes or no as Android says,
     * unknown when BotMaker cannot ask (no door, no instance, no address dealt), which never closes a way.
     */
    public static <G> Condition signedIn(Params.Of<G, String> email) {
        return new SignedInCheck(email);
    }
}
