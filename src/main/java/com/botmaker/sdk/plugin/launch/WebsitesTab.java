// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.launch.LaunchSpec;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * The Game window's Websites tab: an address, shown in the strip to try and use. A target of another kind typed as
 * {@code kind:id} ({@code steam:620}) is taken too.
 */
final class WebsitesTab {

    private final VBox box;

    WebsitesTab(Consumer<GameCatalog.Game> onPick, QuickLaunch.Report report) {
        TextField address = new TextField();
        address.setPromptText("https://example.com");
        HBox.setHgrow(address, Priority.ALWAYS);
        Button show = new Button("Show");
        Runnable typed = () -> GameCatalog.typed(address.getText()).ifPresentOrElse(
                spec -> onPick.accept(PickStrip.single(null,
                        new GameCatalog.Item(spec, LaunchSpec.describe(spec), null))),
                () -> report.accept(false, "\"" + address.getText().trim() + "\" is not a website's address: write "
                        + "it as https://…, or a target as kind:id, where kind is one of " + GameCatalog.kinds() + "."));
        show.setOnAction(e -> typed.run());
        address.setOnAction(e -> typed.run());
        HBox row = new HBox(8, address, show);
        row.setAlignment(Pos.CENTER_LEFT);
        Label hint = Styles.on(new Label("The site opens in BotMaker's own browser under the read-only guard, a fresh "
                + "one each run. Signing in is the bot's goal, not the target's."), Styles.DIALOG_HINT);
        hint.setWrapText(true);
        box = new VBox(8, row, hint);
        box.setPadding(new Insets(8, 0, 0, 0));
    }

    Node node() {
        return box;
    }
}
