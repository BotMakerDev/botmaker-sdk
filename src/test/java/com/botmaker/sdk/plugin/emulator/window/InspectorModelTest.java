// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.sdk.plugin.emulator.window.EmulatorsModel.InstanceRow;
import com.botmaker.shared.emulator.AdbEndpoint;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AndroidState;
import com.botmaker.shared.emulator.config.ConfigDiff;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.ConfigValue;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.GameRecord;
import com.botmaker.shared.emulator.config.InstanceFacts;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Layer;
import com.botmaker.shared.emulator.config.Wanted;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectorModelTest {

    private static final EmulatorInstance FARM = new EmulatorInstance(PlatformId.LDPLAYER, "Farm", "127.0.0.1", 5555);
    private static final ConfigValue.Screen TALL = new ConfigValue.Screen(1080, 1920, 320);
    private static final ConfigValue.Screen WIDE = new ConfigValue.Screen(1600, 900, 240);
    private static final GameRecord GAME = new GameRecord("com.zjcs.android.us", "Sword x Staff");

    private static Wanted wanted(Layer layer) {
        return EmulatorConfig.EMPTY.withInstance(InstanceKey.of(FARM), layer).wanted(InstanceKey.of(FARM));
    }

    private static InstanceRow row(Wanted wanted, InstanceFacts facts, boolean running) {
        return new InstanceRow(FARM, InstanceKey.of(FARM), running, wanted, facts,
                facts == null ? null : ConfigDiff.of(wanted, facts), List.of());
    }

    private static InstanceFacts facts(Optional<AndroidState> android) {
        return new InstanceFacts(InstanceKey.of(FARM), Map.of(ConfigField.SCREEN, WIDE,
                ConfigField.MEMORY, new ConfigValue.Megabytes(4096)), Instant.EPOCH, android, false);
    }

    @Test
    void applySaysEachChangeWithItsSign() {
        Wanted wanted = wanted(Layer.EMPTY.with(ConfigField.SCREEN, TALL).withGame(GAME)
                .withAccount(new AccountRecord(AccountRecord.Kind.GOOGLE, "user@example.com"))
                .withAccount(new AccountRecord(AccountRecord.Kind.FACEBOOK, "fb@example.com")));
        assertEquals(List.of("~ Screen " + WIDE.said() + " → " + TALL.said(), "+ sign in user@example.com",
                        "+ install Sword x Staff"), InspectorModel.applyLines(row(wanted, facts(Optional.empty()), true)),
                "a Facebook account is a record only: nothing to sign in");
    }

    @Test
    void anInstanceNotReadOrAsConfiguredChangesNothing() {
        assertEquals(List.of(), InspectorModel.applyLines(row(wanted(Layer.EMPTY.with(ConfigField.SCREEN, TALL)),
                null, false)));
        assertEquals(List.of(), InspectorModel.applyLines(row(wanted(Layer.EMPTY.with(ConfigField.SCREEN, WIDE)),
                facts(Optional.empty()), true)));
    }

    @Test
    void theDeviceSaysWhatWasNotRead() {
        List<InspectorModel.Fact> device = InspectorModel.device(row(wanted(Layer.EMPTY), facts(Optional.empty()),
                true));
        assertEquals(List.of("Android", "Screen", "Memory", "Root", "ADB address"),
                device.stream().map(InspectorModel.Fact::label).toList());
        assertEquals("Not read", device.getFirst().value());
        assertEquals(WIDE.said(), device.get(1).value());
        assertEquals("Not read", device.get(3).value());
        assertTrue(device.getLast().value().contains("5555"));
    }

    @Test
    void severalAreCountedByTheirPills() {
        Wanted toApply = wanted(Layer.EMPTY.with(ConfigField.SCREEN, TALL));
        InspectorModel.Several counted = InspectorModel.several(List.of(row(toApply, facts(Optional.empty()), true),
                row(wanted(Layer.EMPTY), facts(Optional.empty()), false), row(toApply, null, true)));
        assertEquals(new InspectorModel.Several(3, 1, 1, 1, 1), counted);
        assertEquals("3 instances", counted.title());
        assertEquals("1 running, 1 stopped, 1 not read", counted.states());
        assertEquals("0 running, 0 stopped", InspectorModel.several(List.of()).states());
        assertEquals("1 instance", InspectorModel.several(List.of(row(toApply, null, false))).title());
    }

    private static InstanceRow phone(AdbEndpoint adb, boolean connected) {
        EmulatorInstance pixel = new EmulatorInstance(PlatformId.PHYSICAL, "Pixel 10", adb);
        InstanceKey key = InstanceKey.of(pixel);
        return new InstanceRow(pixel, key, connected, EmulatorConfig.EMPTY.wanted(key), null, null, List.of());
    }

    @Test
    void aPhoneSaysHowItIsConnected() {
        assertEquals("connected · USB", InspectorModel.phoneState(phone(new AdbEndpoint.Server("4A1B2C3D"), true)));
        assertEquals("connected · Wi-Fi",
                InspectorModel.phoneState(phone(new AdbEndpoint.Server("192.168.1.20:41234"), true)));
        assertEquals("connected · Wi-Fi", InspectorModel.phoneState(
                phone(new AdbEndpoint.Server("adb-4A1B2C3D-x1y2z3._adb-tls-connect._tcp"), true)));
        assertEquals("connected · Wi-Fi", InspectorModel.phoneState(phone(new AdbEndpoint.Tcp("192.168.1.20", 5555),
                true)));
        assertEquals("not connected", InspectorModel.phoneState(phone(new AdbEndpoint.Server("4A1B2C3D"), false)));
    }

    @Test
    void aPhoneRowSaysPhoneAndItsConnection() {
        InstanceRow usb = phone(new AdbEndpoint.Server("4A1B2C3D"), true);
        assertEquals("Phone", EmulatorsModel.Column.EMULATOR.text(usb));
        assertEquals("connected · USB", EmulatorsModel.Column.STATE.text(usb));
        assertEquals("—", EmulatorsModel.Column.DIFFERS.text(usb));
        assertEquals(new Look.Pill(Look.Tone.OK, "connected · USB"), InspectorModel.statePill(usb));
        assertEquals(new Look.Pill(Look.Tone.MUTED, "not connected"),
                InspectorModel.statePill(phone(new AdbEndpoint.Server("4A1B2C3D"), false)));
        assertTrue(usb.said().contains("connected · USB"), usb.said());
        InstanceRow farm = row(wanted(Layer.EMPTY), facts(Optional.empty()), true);
        assertEquals(PlatformId.LDPLAYER.displayName(), EmulatorsModel.Column.EMULATOR.text(farm));
        assertEquals("Running", EmulatorsModel.Column.STATE.text(farm));
        assertEquals(Look.pill(true, true), InspectorModel.statePill(farm));
    }

    @Test
    void startAndStopLeaveThePhonesOut() {
        InstanceRow pixel = phone(new AdbEndpoint.Server("4A1B2C3D"), false);
        InstanceRow farm = row(wanted(Layer.EMPTY), facts(Optional.empty()), false);
        assertEquals(List.of(farm), InspectorModel.startable(List.of(pixel, farm)));
        assertEquals(List.of(), InspectorModel.startable(List.of(pixel)));
    }
}
