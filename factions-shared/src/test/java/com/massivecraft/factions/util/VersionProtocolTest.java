package com.massivecraft.factions.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionProtocolTest {

    @Test
    void parsesLegacyMinecraftVersionScheme() {
        assertEquals(21, VersionProtocol.parseMajorCompatibilityVersion("1.21.11-R0.1-SNAPSHOT"));
    }

    @Test
    void parsesModernMinecraftVersionScheme() {
        assertEquals(26, VersionProtocol.parseMajorCompatibilityVersion("26.1.2-build.8"));
    }

    @Test
    void rejectsUnparseableVersionScheme() {
        assertEquals(-1, VersionProtocol.parseMajorCompatibilityVersion("not-a-version"));
    }
}
