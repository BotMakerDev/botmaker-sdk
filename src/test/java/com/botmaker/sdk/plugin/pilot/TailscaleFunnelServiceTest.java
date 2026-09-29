package com.botmaker.sdk.plugin.pilot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Turning the pilot's Funnel off touches only what it turned on. It ran {@code tailscale funnel reset}, which
 * cleared every serve and Funnel the user had.
 */
class TailscaleFunnelServiceTest {

    @Test
    void off_names_only_the_https_port_the_pilot_serves() {
        assertEquals(List.of("tailscale", "serve", "--https=443", "off"), TailscaleFunnelService.OFF);
        assertFalse(TailscaleFunnelService.OFF.contains("reset"));
    }

    /** Wording from tailscale 1.102 with nothing served on :443. */
    @Test
    void a_handler_already_gone_counts_as_turned_off() {
        assertTrue(TailscaleFunnelService.turnedOff(0, ""));
        assertTrue(TailscaleFunnelService.turnedOff(1, "error: failed to remove web serve: handler does not exist"));
        assertFalse(TailscaleFunnelService.turnedOff(1, "Access denied: serve config denied"));
        assertFalse(TailscaleFunnelService.turnedOff(-1, null));
    }
}
