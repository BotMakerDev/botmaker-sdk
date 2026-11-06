package com.botmaker.sdk.internal.params;

import com.botmaker.java.grammar.ParamsFitter;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.internal.params.fixture.Screens;
import com.botmaker.sdk.internal.params.fixture.params.bot.Wallet;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A readout that names a fact, {@code Readout.into(Wallet::coins)}, is read into it as the fact's type says. */
class ReadoutIntoTest {

    private static final String GROUP = "com/botmaker/sdk/internal/params/fixture/params/bot/Wallet.java";

    @TempDir
    Path bot;

    @BeforeEach
    void aBotWithItsFacts() throws IOException {
        String source = Files.readString(Path.of("src/test/java", GROUP));
        for (Path file : List.of(bot.resolve("src/main/java").resolve(GROUP),
                bot.resolve(ParamsFitter.STARTING).resolve("bot/Wallet.java"))) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, source);
        }
        RunParams.startOver(bot);
        RunParams.clock(() -> Instant.parse("2026-11-06T10:00:00Z").toEpochMilli());
        ScreenMaps.use(ScreenGraph.ofScreens(Map.of("SHOP", new Screens.Shop())));
    }

    @AfterEach
    void noMapLeft() {
        RunFacts.use(List.of(), (constant, fields) -> Optional.empty());
        ScreenMaps.use(null);
    }

    @Test
    void readsTheFactByItsType() {
        RunFacts.use(List.of(Wallet.class), (constant, fields) -> Optional.of(
                Map.of("coins", "1 234 gold", "owner", "  Ann ", "rank", "Silver plus")));

        RunFacts.seen("SHOP");

        assertEquals(Optional.of(1234), Params.known(Wallet::coins), "an int takes the digits shown");
        assertEquals(Optional.of("Ann"), Params.known(Wallet::owner), "a String the text");
        assertEquals(Optional.of(Wallet.Rank.SILVER_PLUS), Params.known(Wallet::rank), "an enum the constant it names");
        assertEquals(Optional.of(false), Params.known(Wallet::sale), "a boolean whether anything shows");
    }
}
