package com.botmaker.sdk.api.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenTest {

    static final class Options extends Screen {
        @Override
        public Setup setup() {
            throw new IllegalStateException("toString must not build the setup");
        }
    }

    @Test
    void aScreenReadsAsItsClassWithoutBuildingItsSetup() {
        assertEquals("Options", new Options().toString());
    }
}
