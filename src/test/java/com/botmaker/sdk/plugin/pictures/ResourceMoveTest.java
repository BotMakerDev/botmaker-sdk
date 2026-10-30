package com.botmaker.sdk.plugin.pictures;

import com.botmaker.sdk.internal.vision.TemplateNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceMoveTest {

    @Test
    void theRootIsAboveAPluginsFolderOnly(@TempDir Path project) {
        Path resources = project.resolve("src/main/resources");
        assertEquals(Optional.of(resources),
                ResourceMove.root(resources.resolve("plugins/com_github_botmakerdev/botmaker_sdk")));
        assertEquals(Optional.empty(), ResourceMove.root(resources), "a Studio that gives every plugin the root");
    }

    @Test
    void theSdksFilesMoveIntoItsFolderAndOneThereAlreadyStays(@TempDir Path project) throws Exception {
        Path resources = project.resolve("src/main/resources");
        Path folder = resources.resolve("plugins/com_github_botmakerdev/botmaker_sdk");
        write(resources.resolve("images/ore.png"), 1);
        write(resources.resolve("digits/gold/0.png"), 2);
        write(resources.resolve("screens/HOME.png"), 3);
        write(resources.resolve("screens/BAG.png"), 4);
        write(folder.resolve("screens/BAG.png"), 5);
        write(resources.resolve("sounds/beep.wav"), 6);
        write(resources.resolve("images/logo.svg"), 7);
        write(resources.resolve("images/icons/app.png"), 8);

        assertEquals(4, ResourceMove.waiting(folder), "only the pictures the SDK writes");
        ResourceMove.Moved moved = ResourceMove.moveFiles(folder);
        assertEquals(3, moved.files());
        assertEquals(Set.of("screens/BAG.png"), moved.left());
        assertTrue(Files.exists(resources.resolve("images/logo.svg")), "the bot's own file stays");
        assertTrue(Files.exists(resources.resolve("images/icons/app.png")), "the bot's own file stays");

        assertArrayEquals(new byte[]{1}, Files.readAllBytes(folder.resolve("images/ore.png")));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(folder.resolve("digits/gold/0.png")));
        assertArrayEquals(new byte[]{5}, Files.readAllBytes(folder.resolve("screens/BAG.png")), "never written over");
        assertTrue(Files.exists(resources.resolve("screens/BAG.png")), "left where it still loads from");
        assertFalse(Files.exists(resources.resolve("digits")), "an emptied folder goes");
        assertTrue(Files.exists(resources.resolve("sounds/beep.wav")));
        assertEquals(1, ResourceMove.waiting(folder));
    }

    @Test
    void aPathWhoseFileStayedAtTheRootIsNotRewritten() {
        Set<String> left = Set.of("images/ore.png", "digits/gold/3.png");
        assertTrue(ResourceMove.stayed("images/ore.png", left));
        assertTrue(ResourceMove.stayed("digits/gold", left), "a digit set with one digit left");
        assertFalse(ResourceMove.stayed("images/iron.png", left));
        assertFalse(ResourceMove.stayed("digits/go", left));
    }

    @Test
    void aPathWrittenFromTheRootIsTheSamePictureShort() {
        assertEquals("images/ore.png", TemplateNames.current("src/main/resources/images/ore.png"));
        assertEquals("images/ore.png", TemplateNames.current("images/ore.png"));
        assertTrue(TemplateNames.isPathFor("ore", "src/main/resources/images/ore.png"));
        assertTrue(TemplateNames.isPathFor("ore", "images/ore.png"));
        assertFalse(TemplateNames.isPathFor("ore", "pics/ore.png"));
        assertEquals("ORE", TemplateNames.constantForPath("src/main/resources/images/ore.png"));
        assertEquals("ORE", TemplateNames.constantForPath("images/ore.png"));
    }

    private static void write(Path file, int b) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{(byte) b});
    }
}
