package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.vm.Hypervisor;
import com.botmaker.shared.vm.VmAutoStop;
import com.botmaker.shared.vm.VmInventory;
import com.botmaker.shared.vm.VmRecord;
import com.botmaker.shared.vm.VmSetup;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A game VM's power in ⚙ Bot Settings: whether it runs, a Shut down button, and when BotMaker shuts it down by
 * itself ({@link VmAutoStop}: with Studio, or once unused). A started VM otherwise runs until its Windows shuts
 * down, so the next run doesn't wait for it to start.
 */
final class VmPower {

    private static final int DEFAULT_IDLE_MINUTES = 30;

    private final StudioServices services;
    private final Label state = new Label();
    private final Button shutDown = new Button("Shut down VM");
    private final CheckBox withStudio = new CheckBox("Shut it down when Studio closes");
    private final CheckBox whenIdle = new CheckBox("Shut it down when nothing has used it for");
    private final Spinner<Integer> minutes = new Spinner<>(5, 24 * 60, DEFAULT_IDLE_MINUTES, 5);
    private final Label idleNote = new Label();
    private final VBox node;
    private VmRecord vm;
    /** The settings chosen for each VM shown before the one shown now, until {@link #save()}. */
    private final Map<VmRecord, VmAutoStop> chosen = new HashMap<>();

    VmPower(StudioServices services) {
        this.services = services;
        minutes.setEditable(true);
        minutes.setPrefWidth(90);
        minutes.disableProperty().bind(whenIdle.selectedProperty().not().or(whenIdle.disabledProperty()));
        Styles.on(idleNote, Styles.DIALOG_HINT);
        idleNote.setWrapText(true);
        shutDown.setOnAction(e -> {
            VmRecord target = vm;
            if (target == null) return;
            shutDown.setDisable(true);
            state.setText("Shutting down…");
            shutDown(services, target, done -> {
                if (target == vm) state.setText(done);
            }, failed -> {
                if (target != vm) return;
                state.setText(failed);
                shutDown.setDisable(false);
            });
        });
        HBox power = new HBox(8, state, shutDown);
        power.setAlignment(Pos.CENTER_LEFT);
        HBox idle = new HBox(8, whenIdle, minutes, new Label("minutes"));
        idle.setAlignment(Pos.CENTER_LEFT);
        node = new VBox(6, power, withStudio, idle, idleNote);
    }

    VBox node() {
        return node;
    }

    /**
     * Shows {@code next}'s state and settings; nothing to show for none, or one not set up yet. What was chosen
     * for the VM shown until now is kept, for {@link #save()}.
     */
    void show(VmRecord next) {
        if (vm != null && vm.stage() == VmRecord.Stage.READY) chosen.put(vm, shown());
        vm = next;
        boolean ready = next != null && next.stage() == VmRecord.Stage.READY;
        node.setVisible(ready);
        node.setManaged(ready);
        if (!ready) return;
        VmAutoStop settings = chosen.getOrDefault(next, VmAutoStop.load(next));
        withStudio.setSelected(settings.withStudio());
        whenIdle.setSelected(settings.idleMinutes() > 0);
        minutes.getValueFactory().setValue(settings.idleMinutes() > 0 ? settings.idleMinutes() : DEFAULT_IDLE_MINUTES);
        boolean counts = next.hypervisor() == Hypervisor.QEMU;
        whenIdle.setDisable(!counts);
        idleNote.setText(counts ? "Used means a bot running in it, or its screen open in Studio."
                : "VMware doesn't say whether anything uses the VM, so it isn't shut down when unused.");
        look(next);
    }

    /** Writes the settings chosen for each VM shown, and has the keeper apply them now. */
    void save() {
        if (vm != null && vm.stage() == VmRecord.Stage.READY) chosen.put(vm, shown());
        boolean changed = false;
        for (Map.Entry<VmRecord, VmAutoStop> e : chosen.entrySet()) {
            if (e.getValue().equals(VmAutoStop.load(e.getKey()))) continue;
            try {
                e.getValue().save(e.getKey());
                changed = true;
            } catch (java.io.IOException failed) {
                services.status("The game VM " + e.getKey().name() + "'s shutdown settings weren't saved: "
                        + failed.getMessage());
            }
        }
        if (changed) VmKeeper.checkNow();
    }

    /** What the controls say now. The minutes as typed: a spinner takes typing only on Enter or leaving it. */
    private VmAutoStop shown() {
        try {
            minutes.getValueFactory().setValue(
                    minutes.getValueFactory().getConverter().fromString(minutes.getEditor().getText()));
        } catch (RuntimeException e) {
            minutes.getEditor().setText(Integer.toString(minutes.getValue())); // not a number: the last one stands
        }
        return new VmAutoStop(withStudio.isSelected(),
                whenIdle.isSelected() && !whenIdle.isDisabled() ? minutes.getValue() : 0);
    }

    /** Whether {@code target} runs, off the JavaFX thread. */
    private void look(VmRecord target) {
        state.setText("…");
        shutDown.setDisable(true);
        Async.load("vm-power-" + target.name(), () -> VmInventory.running(target), running -> {
            if (target != vm) return;
            state.setText(running ? "Running." : "Shut down.");
            shutDown.setDisable(!running);
        });
    }

    /**
     * Shuts {@code vm} down off the JavaFX thread, then tells {@code done} or {@code failed} one sentence, which
     * also goes to the status bar. A bot running in it ends.
     */
    static void shutDown(StudioServices services, VmRecord vm, Consumer<String> done, Consumer<String> failed) {
        services.status("Shutting the game VM " + vm.name() + " down…");
        Async.load("vm-shut-down-" + vm.name(), () -> GuestCalls.unchecked(() -> VmSetup.shutDown(vm)), clean -> {
            String sentence = clean ? "Shut down." : "Shut down: Windows didn't finish in time, so it was powered off.";
            services.status("The game VM " + vm.name() + ": " + sentence);
            done.accept(sentence);
        }, why -> {
            String sentence = "Didn't shut down: " + why;
            services.status("The game VM " + vm.name() + ": " + sentence);
            failed.accept(sentence);
        });
    }
}
