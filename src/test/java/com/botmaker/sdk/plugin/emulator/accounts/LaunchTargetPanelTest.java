// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.accounts;

import com.botmaker.sdk.plugin.emulator.AccountPool;
import com.botmaker.shared.emulator.config.AccountRecord;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Accounts window's side panel for a launch target's account, headless: an Add and an edit (run-places 7d). */
class LaunchTargetPanelTest extends ApplicationTest {

    static {
        // Read when the toolkit starts, so before ApplicationTest starts it: no display needed.
        System.setProperty("testfx.robot", "glass");
        System.setProperty("testfx.headless", "true");
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        System.setProperty("java.awt.headless", "true");
    }

    private static final String GAME = "emu-app:com.game";
    private static final AccountRecord GOOGLE = new AccountRecord(AccountRecord.Kind.GOOGLE, "main@gmail.com");
    private static final AccountRecord MAIN = AccountRecord.ofLaunchTarget(GAME, "main", "farm");
    private static final List<AccountRecord> BOOK = List.of(GOOGLE, MAIN);
    private static final AccountPool.Pc PC = new AccountPool.Pc(Set.of("MEmu_1"), Map.of());

    private final List<LaunchTargetPanel.Edit> saved = new ArrayList<>();
    private StackPane root;

    @Override
    public void start(Stage stage) {
        root = new StackPane();
        stage.setScene(new Scene(root, 320, 640));
        stage.show();
    }

    private void show(AccountsListModel.Row row) {
        interact(() -> root.getChildren().setAll(LaunchTargetPanel.of(GAME, row, BOOK, PC, Optional.empty(),
                new LaunchTargetPanel.Actions(saved::add, () -> {}, () -> {}, () -> {}))));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void addKeepsTheNameTypedWithThePlacePickedAndNothingBeforeAName() {
        show(null);
        Button add = lookup("#lt-save").queryButton();
        assertTrue(add.isDisabled(), "no name yet");
        clickOn("#lt-name").write("alt");
        ComboBox<String> playsAt = lookup("#lt-plays-at").query();
        interact(() -> playsAt.setValue("this-pc/android/MEmu_1"));
        clickOn(add);

        assertEquals(1, saved.size());
        assertNull(saved.getFirst().was());
        AccountRecord next = saved.getFirst().next();
        assertEquals(new AccountRecord.Key(AccountRecord.Kind.LAUNCH_TARGET, GAME, "alt"), next.key());
        assertEquals("this-pc/android/MEmu_1", next.playsAt());
        assertEquals(Optional.empty(), next.signsInWith());
    }

    @Test
    void saveKeepsTheEditOfAnAccountInPlaceOfItsKey() {
        show(AccountsListModel.rows(BOOK, Map.of(), Set.of(), Set.of()).stream()
                .filter(row -> row.account().equals(MAIN)).findFirst().orElseThrow());
        ComboBox<Optional<AccountRecord.Key>> signsInWith = lookup("#lt-signs-in-with").query();
        interact(() -> signsInWith.setValue(Optional.of(GOOGLE.key())));
        TextField name = lookup("#lt-name").query();
        interact(() -> name.setText("main2"));
        clickOn("#lt-save");

        assertEquals(1, saved.size());
        assertEquals(MAIN.key(), saved.getFirst().was());
        assertEquals("main2", saved.getFirst().next().name());
        assertEquals("farm", saved.getFirst().next().note(), "the note kept");
        assertEquals(Optional.of(GOOGLE.key()), saved.getFirst().next().signsInWith());

        interact(() -> name.setText(""));
        assertTrue(lookup("#lt-save").queryButton().isDisabled(), "a blank name is refused");
    }
}
