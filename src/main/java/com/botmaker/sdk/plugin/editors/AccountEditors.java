// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.sdk.api.signin.Account;
import com.botmaker.sdk.internal.config.PlaceKeys;
import com.botmaker.sdk.plugin.emulator.SdkPools;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The editor of the bot's {@code Account account()} (2026-11-27): a pill offering no account and every account of this
 * PC's book for the bot's games ({@link SdkPools#accounts}). Several runs at once are each dealt one instead of this
 * value; the book's accounts are kept in the Emulators window's Accounts.
 */
public final class AccountEditors {

    private static final String NONE = "No account";

    private AccountEditors() {}

    public static Node account(ValueContext ctx) {
        MenuButton pill = Pills.bare(label(ctx.value(Account.class).orElse(null)));
        Pills.onOpen(pill, () -> {
            List<MenuItem> items = new ArrayList<>();
            items.add(Pills.item(NONE, () -> choose(ctx, pill, Account.none())));
            for (Account each : SdkPools.accounts(ctx.services())) {
                items.add(Pills.item(label(each), () -> choose(ctx, pill, each)));
            }
            return items;
        });
        return pill;
    }

    /** The account beside a declared value, as the pill says it. */
    public static Node preview(ValueContext ctx) {
        return ctx.value(Account.class).map(account -> (Node) new Label(label(account))).orElse(null);
    }

    private static void choose(ValueContext ctx, MenuButton pill, Account picked) {
        Optional<String> refused = ctx.write(picked);
        if (refused.isPresent()) {
            if (ctx.services() != null) ctx.services().status(refused.get());
            return;
        }
        pill.setText(label(picked));
    }

    /** An account for a person: its login, and where it plays when it is kept for one place. */
    static String label(Account account) {
        if (account == null || account.isNone()) return NONE;
        return account.playsAt().map(place -> account.login() + " at " + PlaceKeys.said(place))
                .orElse(account.login());
    }
}
