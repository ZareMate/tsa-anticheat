package com.zaremate.tsa_anticheat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisabledJarBypassTest {
    @Test void onlyDisabledModJarsAreEligible() {
        assertTrue(DisabledJarBypass.isDisabledJar("MOD", "Cheat.jar.disabled"));
        assertTrue(DisabledJarBypass.isDisabledJar("mod", "CHEAT.JAR.DISABLED"));
        assertFalse(DisabledJarBypass.isDisabledJar("MOD", "Cheat.jar"));
        assertFalse(DisabledJarBypass.isDisabledJar("RESOURCE_PACK", "Cheat.jar.disabled"));
        assertFalse(DisabledJarBypass.isDisabledJar("MOD", "Cheat.zip.disabled"));
    }

    @Test void toggleControlsBypass() {
        assertTrue(DisabledJarBypass.ignore("MOD", "Cheat.jar.disabled", true));
        assertFalse(DisabledJarBypass.ignore("MOD", "Cheat.jar.disabled", false));
        assertFalse(DisabledJarBypass.ignore("MOD", "Cheat.jar", true));
    }

    @Test void enforcementReportFiltersOnlyDisabledModsWhenEnabled() {
        String hash = "a".repeat(64);
        String disabled = "MOD\tCheat.jar.disabled\t" + hash;
        String enabled = "MOD\tCheat.jar\t" + hash;
        String pack = "RESOURCE_PACK\tTexture.jar.disabled\t" + hash;
        String input = String.join("\n", disabled, enabled, pack);
        assertEquals(String.join("\n", enabled, pack), DisabledJarBypass.filterReport(input, true));
        assertEquals(input, DisabledJarBypass.filterReport(input, false));
    }
}
