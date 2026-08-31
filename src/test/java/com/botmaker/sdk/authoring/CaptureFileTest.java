package com.botmaker.sdk.authoring;

import com.botmaker.shared.config.ProjectProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code capture.json}'s reader and its two writers, both of which became this module's on 2026-08-31 when the
 * targets manager stopped being the editor's.
 *
 * <p>What is held here is the migration and the projection — the two things that are invisible when they break.
 * A project written by an older editor still has its targets in that editor's {@code settings.json}, and
 * nothing moves them across any more, so this reader has to recognise the old shape or a configured project
 * silently reads as having no targets at all. And the {@code capture.source} key is what a <em>running</em>
 * bot resolves, so a default target that is not projected onto it means the editor and the bot look at two
 * different windows and say nothing about it.
 */
class CaptureFileTest {

    private static final SdkVersion VERSION = SdkVersion.latest();

    private static final String LEGACY_SETTINGS = """
            {
              "schemaVersion": 1,
              "captureTargets": [
                { "type": "window", "titleSubstring": "Diablo IV" },
                { "type": "screen", "index": 1 },
                { "type": "emulator", "instanceName": "MuMu Player 12" },
                { "type": "something-newer" }
              ],
              "defaultTargetIndex": 2,
              "referenceResolution": { "width": 1600, "height": 900 }
            }
            """;

    @Test
    void anOlderEditorsSettingsFileIsReadWhenThereIsNoCaptureFile(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("settings.json"), LEGACY_SETTINGS);

        CaptureModel model = Authoring.readCapture(VERSION, dir);

        // Three of four: an entry whose type nothing recognises is skipped rather than refused.
        assertEquals(List.of(CaptureTargetModel.window("Diablo IV"), CaptureTargetModel.monitor(1),
                CaptureTargetModel.emulator("MuMu Player 12")), model.targets());
        assertEquals(2, model.defaultIndex());
        assertEquals(new CaptureModel.Resolution(1600, 900), model.reference());
    }

    @Test
    void theCaptureFileWinsOverTheOlderShapeEvenWhenItIsEmpty(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("settings.json"), LEGACY_SETTINGS);
        Files.writeString(dir.resolve(CaptureModel.FILE_NAME), "{ \"targets\": [], \"defaultIndex\": null }");

        CaptureModel model = Authoring.readCapture(VERSION, dir);

        assertTrue(model.targets().isEmpty());
        // The size is still taken from the older file: it arrived in this one a day after the targets did.
        assertEquals(new CaptureModel.Resolution(1600, 900), model.reference());
    }

    @Test
    void aSizeInTheCaptureFileWinsOverTheOlderOne(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("settings.json"), LEGACY_SETTINGS);
        Files.writeString(dir.resolve(CaptureModel.FILE_NAME),
                "{ \"targets\": [], \"reference\": { \"width\": 1280, \"height\": 720 } }");

        assertEquals(new CaptureModel.Resolution(1280, 720),
                Authoring.readCapture(VERSION, dir).reference());
    }

    @Test
    void aDefaultTargetIsProjectedOntoTheSpecARunningBotReads(@TempDir Path dir) throws IOException {
        Authoring.writeCaptureSource(VERSION, dir, CaptureTargetModel.window("Diablo IV").spec());

        assertEquals("window:Diablo IV", captureSource(dir));
    }

    @Test
    void aProjectWithNoDefaultLeavesThatSpecAlone(@TempDir Path dir) throws IOException {
        Properties existing = new Properties();
        existing.setProperty(ProjectProperties.KEY_CAPTURE_SOURCE, "emulator:Waydroid");
        try (var out = Files.newOutputStream(dir.resolve(ProjectProperties.FILE_NAME))) {
            existing.store(out, null);
        }

        Authoring.writeCaptureSource(VERSION, dir, null);

        assertEquals("emulator:Waydroid", captureSource(dir));
    }

    @Test
    void writingTheSpecKeepsEveryOtherKeyInThatFile(@TempDir Path dir) throws IOException {
        Properties existing = new Properties();
        existing.setProperty(ProjectProperties.KEY_LAUNCH_TARGET, "steam:12345");
        existing.setProperty("schemaVersion", "3");
        try (var out = Files.newOutputStream(dir.resolve(ProjectProperties.FILE_NAME))) {
            existing.store(out, null);
        }

        Authoring.writeCaptureSource(VERSION, dir, "desktop");

        Properties after = read(dir);
        assertEquals("steam:12345", after.getProperty(ProjectProperties.KEY_LAUNCH_TARGET));
        assertEquals("3", after.getProperty("schemaVersion"), "the editor's stamp is carried, never restamped");
        assertEquals("desktop", after.getProperty(ProjectProperties.KEY_CAPTURE_SOURCE));
    }

    @Test
    void aProjectWithNothingStoredReadsAsNoTargets(@TempDir Path dir) throws IOException {
        CaptureModel model = Authoring.readCapture(VERSION, dir);

        assertTrue(model.targets().isEmpty());
        assertNull(model.defaultIndex());
        assertNull(model.reference());
    }

    private static String captureSource(Path dir) throws IOException {
        return read(dir).getProperty(ProjectProperties.KEY_CAPTURE_SOURCE);
    }

    private static Properties read(Path dir) throws IOException {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(dir.resolve(ProjectProperties.FILE_NAME))) {
            properties.load(in);
        }
        return properties;
    }
}
