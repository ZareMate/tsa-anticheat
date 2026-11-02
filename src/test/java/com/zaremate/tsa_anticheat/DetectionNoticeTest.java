package com.zaremate.tsa_anticheat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DetectionNoticeTest {
    @Test
    void allowlistedEmptyFilesRemainAvailableForInformationalAlerts() {
        var entry = new TsaInventory.Entry("RESOURCE_PACK", "empty.zip",
                TsaInventory.EMPTY_SHA256, true, true);
        var snapshot = new TsaInventory.Snapshot(java.util.UUID.randomUUID(),
                java.time.Instant.now(), List.of(entry), 0);
        assertTrue(snapshot.unlisted().isEmpty());
        assertEquals(List.of(entry), snapshot.emptyFiles());
    }

    @Test
    void emptyFileNoticeIsInformational() {
        String description = DetectionNotice.description("Player", "uuid",
                List.of("RESOURCE_PACK empty.zip (EMPTY)"), "JOIN SCAN — EMPTY FILES",
                "None — informational only.");
        assertTrue(description.contains("Status: **EMPTY (INFORMATIONAL)**"));
        assertTrue(description.contains("RESOURCE\\_PACK empty.zip (EMPTY)"));
        assertFalse(description.contains("Status: **DETECTED**"));
    }

    @Test
    void blacklistDetectionRemainsDetection() {
        String description = DetectionNotice.description("Player", "uuid",
                List.of("MOD xray.jar"), "JOIN SCAN", "None");
        assertTrue(description.contains("Status: **DETECTED**"));
    }
}
