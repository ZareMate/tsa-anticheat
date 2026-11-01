package com.zaremate.tsa_anticheat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DetectionNoticeTest {
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
