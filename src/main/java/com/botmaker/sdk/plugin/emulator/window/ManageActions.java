package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.sdk.plugin.emulator.host.InstanceRenames;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Operation;
import com.botmaker.shared.emulator.manage.InstanceManager;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Clone, Rename and Delete on an instance's row, through shared's {@link InstanceManager}: a console's work is queued
 * as an operation, and BlueStacks' and MSI's manager is opened instead. Each moves the SDK's values of this PC with the
 * instance: a clone gets its source's configuration, a rename takes its rows and is recorded for the bots that name it
 * ({@link InstanceRenames}), a deleted instance's rows go.
 */
final class ManageActions {

    private final EmulatorsWindow window;

    ManageActions(EmulatorsWindow window) {
        this.window = window;
    }

    void cloneOf(EmulatorsModel.InstanceRow row) {
        withConsole(row, console -> {
            List<EmulatorInstance> siblings = ManageModel.siblings(window.snapshot(), row);
            Optional<String> name = askName("Clone " + row.instance().name(),
                    "A copy of " + row.instance().name() + ", stopped, with new device IDs. The Google accounts it copied"
                            + " are signed out on its first start, held from bots; its configuration is copied too.",
                    ManageModel.cloneName(row.instance().name(), siblings), siblings, null);
            if (name.isEmpty()) return;
            StudioServices services = window.services();
            InstanceKey source = row.key();
            window.submit("Clone " + row.instance().name() + " as " + name.get(), row.instance(),
                    console.clone(row.instance(), name.get(), InstanceManager.AccountRemoval.SIGN_OUT,
                            InstanceManager.Prepare.NOTHING, clone -> copied(services, source, clone)));
        });
    }

    void rename(EmulatorsModel.InstanceRow row) {
        withConsole(row, console -> {
            List<EmulatorInstance> siblings = ManageModel.siblings(window.snapshot(), row);
            Optional<String> name = askName("Rename " + row.instance().name(), ManageModel.renameNote(row),
                    row.instance().name(), siblings, row.instance());
            if (name.isEmpty() || name.get().equals(row.instance().name())) return;
            StudioServices services = window.services();
            Set<String> others = InstanceRenames.names(window.snapshot().instances().stream()
                    .filter(one -> !one.key().equals(row.key())).map(EmulatorsModel.InstanceRow::instance).toList());
            window.submit("Rename " + row.instance().name() + " to " + name.get(), row.instance(),
                    console.rename(row.instance(), name.get(), renamed -> moved(services, row.key(), renamed, others)));
        });
    }

    void delete(EmulatorsModel.InstanceRow row) {
        withConsole(row, console -> {
            Alert ask = window.alert(Alert.AlertType.CONFIRMATION, ManageModel.deleteQuestion(row));
            ask.setHeaderText("Delete " + row.instance().name() + "?");
            ask.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
            // it cannot be undone: Enter cancels
            ((Button) ask.getDialogPane().lookupButton(ButtonType.OK)).setDefaultButton(false);
            ((Button) ask.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
            if (ask.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;
            StudioServices services = window.services();
            InstanceKey key = row.key();
            Operation.Body delete = console.delete(row.instance());
            window.submit("Delete " + row.instance().name(), row.instance(), context -> {
                String failed = delete.run(context);
                if (failed == null) {
                    HostConfig.forget(services, key).ifPresent(why -> said("It is deleted, but BotMaker could not forget"
                            + " all of its configuration: " + why));
                }
                return failed;
            });
        });
    }

    /**
     * Runs {@code then} with the instance's console, found off the UI thread; BlueStacks' and MSI's manager is opened
     * instead, and a product with neither says so.
     */
    private void withConsole(EmulatorsModel.InstanceRow row, Consumer<InstanceManager.Console> then) {
        var product = row.key().platform();
        Async.load("emulators-manager", () -> Optional.ofNullable(InstanceManager.forProduct(product)), found -> {
            if (found.isEmpty()) {
                window.say(product.displayName() + " offers BotMaker no way to manage its instances.");
            } else if (found.get() instanceof InstanceManager.OpensManager manager) {
                Async.load("emulators-open-manager", () -> {
                    try {
                        return manager.open();
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                }, window::say, window::say);
            } else if (found.get() instanceof InstanceManager.Console console) {
                then.accept(console);
            }
        }, failed -> window.say("BotMaker could not find " + product.displayName() + "'s console: " + failed));
    }

    /** The name typed, checked as the console will check it; empty when cancelled or refused, the refusal said. */
    private Optional<String> askName(String title, String note, String offered, List<EmulatorInstance> siblings,
                                     EmulatorInstance except) {
        TextInputDialog ask = new TextInputDialog(offered);
        ask.initOwner(window.stage());
        ask.setTitle(title);
        ask.setHeaderText(note);
        ask.setContentText("Name:");
        window.services().theme().apply(ask);
        Optional<String> typed = ask.showAndWait().map(String::strip);
        if (typed.isEmpty()) return typed;
        String problem = InstanceManager.Console.nameProblem(typed.get(), siblings, except);
        if (problem != null) {
            window.say(problem);
            return Optional.empty();
        }
        return typed;
    }

    /** The clone's configuration is its source's, keyed by the clone's name. On the operation's thread. */
    private void copied(StudioServices services, InstanceKey source, EmulatorInstance clone) {
        InstanceKey key;
        try {
            key = InstanceKey.of(clone);
        } catch (IllegalArgumentException unnamed) {
            return;
        }
        HostConfig.update(services, config -> config.withInstance(key, config.instance(source)))
                .ifPresent(why -> said("The clone's configuration was not copied: " + why));
    }

    /**
     * The renamed instance's rows move to its new name and the rename is recorded; the open bot, when it names the old
     * name, is pointed at the new one now. On the operation's thread; {@code others} are the other instances' names.
     */
    private void moved(StudioServices services, InstanceKey from, EmulatorInstance renamed, Set<String> others) {
        InstanceKey to;
        try {
            to = InstanceKey.of(renamed);
        } catch (IllegalArgumentException unnamed) {
            return;
        }
        Optional<String> refused = HostConfig.rename(services, from, to);
        if (refused.isPresent()) {
            said("It is renamed, but BotMaker could not move its configuration or note the rename for its bots: "
                    + refused.get());
            return;
        }
        Set<String> known = new LinkedHashSet<>(others);
        known.add(to.reference());
        Map<String, String> moves = InstanceRenames.moves(HostConfig.renames(services), known);
        if (services.projectDir() == null || moves.isEmpty()) return;
        Platform.runLater(() -> InstanceRenames.handOff(services, moves).ifPresent(window::say));
    }

    private void said(String words) {
        Platform.runLater(() -> window.say(words));
    }
}
