package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.bot.GameVm;
import com.botmaker.shared.platform.Os;
import com.botmaker.shared.vm.GuestOs;
import com.botmaker.shared.vm.VmInventory;
import com.botmaker.shared.vm.VmRecord;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;

/**
 * The editor of the bot's {@link GameVm}, {@code Sdk.vm()}: this computer's game VMs to pick from, ▶ Set up a game
 * VM (or go on setting one up), its screen, and its power. What ⚙ Bot Settings showed under "Run the game in ▸ A
 * virtual machine" until 2026-11-09, moved beside the value it sets.
 */
public final class VmEditors {

    private VmEditors() {}

    /** The VM picker, its setup and its screen; a pick is written as {@code GameVm.of(name)}. */
    public static Node vm(ValueContext ctx) {
        StudioServices services = ctx.services();
        String named = ctx.value(GameVm.class).map(GameVm::name).orElse("");
        ComboBox<VmRecord> vms = new ComboBox<>();
        vms.setConverter(new StringConverter<>() {
            @Override
            public String toString(VmRecord vm) {
                return vm == null ? "" : vm.name() + " (" + vm.guestOs().displayName() + ")"
                        + (vm.stage() == VmRecord.Stage.READY ? "" : " — " + vm.stage().displayName());
            }

            @Override
            public VmRecord fromString(String text) {
                return null;
            }
        });
        vms.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(vms, Priority.ALWAYS);
        List<VmRecord> all = VmInventory.list();
        vms.getItems().setAll(all);
        // None picked when the Java names none, or a VM that is gone: a pick is then always a change.
        vms.setValue(all.stream().filter(vm -> vm.name().equals(named)).findFirst().orElse(null));
        vms.setPromptText(named.isEmpty() ? "Pick a game VM" : named + " is not on this computer");

        Button setUp = new Button();
        Button screen = new Button("Open VM screen");
        Label status = new Label();
        status.setWrapText(true);
        VmPower power = new VmPower(services);
        Runnable refresh = () -> {
            VmRecord picked = vms.getValue();
            boolean ready = picked != null && picked.stage() == VmRecord.Stage.READY;
            screen.setDisable(!ready);
            power.show(picked);
            setUp.setText(picked != null && !ready ? "Go on setting " + picked.name() + " up…" : "Set up a game VM…");
            status.setText(all.isEmpty() ? "No game VM on this computer yet."
                    : picked != null && picked.guestOs() == GuestOs.LINUX
                    ? "Each bot gets a screen of its own in this VM, so several can share it." : "");
        };
        vms.valueProperty().addListener((o, was, now) -> {
            refresh.run();
            // Only a VM that is set up becomes the bot's: one still installing would start nothing.
            // Against what the slot holds now, not when the editor opened: picking back the first one writes it.
            String holds = ctx.value(GameVm.class).map(GameVm::name).orElse("");
            if (now != null && now.stage() == VmRecord.Stage.READY && !now.name().equals(holds)) {
                ctx.set(GameVm.of(now.name()));
            }
        });
        setUp.setOnAction(e -> {
            // The setup takes 20–40 minutes and outlives this editor: its "Use this VM" writes the choice itself.
            VmRecord picked = vms.getValue();
            if (picked != null && picked.stage() != VmRecord.Stage.READY) {
                VmSetupWindow.resume(services, Modals.owner(services), picked, vm -> VmChoice.use(services, vm));
            } else {
                VmSetupWindow.open(services, Modals.owner(services), vm -> VmChoice.use(services, vm));
            }
        });
        screen.setOnAction(e -> {
            if (vms.getValue() != null) VmScreen.open(services, Modals.owner(services), vms.getValue());
        });
        refresh.run();

        Label hint = new Label("The game runs in a computer of its own, "
                + (Os.current().isMac() ? "macOS or Linux" : "Windows or Linux") + ", which Studio sets up, when "
                + "gameRunsIn() says a virtual machine.");
        hint.setWrapText(true);
        HBox row = new HBox(8, vms, screen);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox setUpRow = new HBox(8, setUp, status);
        setUpRow.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, row, setUpRow, power.node(), hint);
    }

    /** The VM's name beside a declared value, {@code the one VM} for none. */
    public static Node preview(ValueContext ctx) {
        return new Label(ctx.value(GameVm.class).map(GameVm::name).filter(name -> !name.isEmpty())
                .orElse("the one VM there is"));
    }
}
