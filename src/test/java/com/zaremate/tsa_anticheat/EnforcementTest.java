package com.zaremate.tsa_anticheat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EnforcementTest {
    @TempDir Path directory;
    private static final String HASH = "a".repeat(64), OTHER = "b".repeat(64);
    private String report() { return "MOD\tCheat.jar\t" + HASH + "\nRESOURCE_PACK\tPack.zip\t" + OTHER; }

    @Test void onlyExplicitHashesQualifyEvenIfFilenameLooksSuspicious() {
        assertTrue(EnforcementPolicy.matchingFiles(report(), Set.of(), Set.of()).isEmpty());
        assertEquals(List.of("Cheat.jar"), EnforcementPolicy.matchingFiles(report(), Set.of(HASH), Set.of()));
    }
    @Test void allowlistAlwaysOverridesEnforcement() {
        assertEquals(List.of("Pack.zip"), EnforcementPolicy.matchingFiles(report(), Set.of(HASH, OTHER), Set.of(HASH)));
    }
    @Test void modeIsExclusiveAndRequiresAMatchingFile() {
        for (String mode : List.of("off", "kick", "tempban")) {
            assertEquals("off", EnforcementPolicy.action(mode, List.of()));
            assertEquals(mode, EnforcementPolicy.action(mode, List.of("Cheat.jar")));
        }
        assertThrows(IllegalArgumentException.class, () -> EnforcementPolicy.action("ban", List.of("file")));
    }
    @Test void invalidTypesAndMalformedReportsCannotTriggerAction() {
        assertTrue(EnforcementPolicy.matchingFiles("OTHER\tx\t" + HASH + "\nMOD\tx\tshort\nmalformed", Set.of(HASH), Set.of()).isEmpty());
    }
    @Test void caseNormalizationAndDuplicateFilesMatchBothDetectionPaths() {
        String report = "mod\tCheat.jar\t" + HASH.toUpperCase(Locale.ROOT) + "\nMOD\tCheat.jar\t" + HASH;
        assertEquals(List.of("Cheat.jar"), EnforcementPolicy.matchingFiles(report, Set.of(HASH), Set.of()));
    }
    @Test void disconnectMessagesContainFilenamesNotHashesOrClientPaths() {
        var files = EnforcementPolicy.matchingFiles("MOD\tC:\\mods\\Cheat.jar\t" + HASH, Set.of(HASH), Set.of());
        for (String mode : List.of("kick", "tempban")) {
            String message = EnforcementPolicy.message(mode, files, 1000);
            assertTrue(message.contains("Cheat.jar")); assertFalse(message.contains(HASH)); assertFalse(message.contains("C:"));
        }
        assertTrue(EnforcementPolicy.message("tempban", files, 1000).contains("1970-01-01T00:00:01Z"));
    }
    @Test void filenamesAreSanitizedAndMessagesBounded() {
        assertEquals("[file].jar", EnforcementPolicy.filename(HASH + ".jar"));
        assertEquals("evil.jar", EnforcementPolicy.filename("§evil\u0001.jar"));
        List<String> files = java.util.stream.IntStream.range(0, 100).mapToObj(i -> EnforcementPolicy.filename("x".repeat(500) + i)).toList();
        assertTrue(EnforcementPolicy.message("kick", files, 0).length() < 1500);
        assertTrue(EnforcementPolicy.message("kick", files, 0).contains("92 more"));
    }
    @Test void durationUnitsAndBoundaries() {
        assertEquals(1, EnforcementPolicy.seconds("1")); assertEquals(30, EnforcementPolicy.seconds("30s"));
        assertEquals(1800, EnforcementPolicy.seconds("30m")); assertEquals(3600, EnforcementPolicy.seconds("1H"));
        assertEquals(604800, EnforcementPolicy.seconds("7d")); assertEquals(31536000, EnforcementPolicy.seconds("365d"));
        for (String invalid : List.of("0", "-1", "366d", "0h", "1.5h", "999999999999999999999999h", "infinite")) {
            assertThrows(IllegalArgumentException.class, () -> EnforcementPolicy.seconds(invalid));
        }
    }
    @Test void manualHashListPersistsWithoutTouchingOtherLists() throws Exception {
        Files.writeString(directory.resolve("blacklisted_hashes.txt"), OTHER);
        Files.writeString(directory.resolve("allowed_hashes.txt"), OTHER);
        var store = new EnforcementStore(directory);
        assertTrue(store.hashes().isEmpty()); assertTrue(store.editHash(HASH.toUpperCase(Locale.ROOT), true));
        assertFalse(store.editHash(HASH, true)); assertEquals(Set.of(HASH), new EnforcementStore(directory).hashes());
        assertEquals(OTHER, Files.readString(directory.resolve("allowed_hashes.txt")));
        assertEquals(OTHER, Files.readString(directory.resolve("blacklisted_hashes.txt")));
        assertTrue(store.editHash(HASH, false)); assertFalse(store.editHash(HASH, false));
        assertTrue(store.hashes().isEmpty());
    }
    @Test void invalidHashNeverModifiesStorage() {
        var store = new EnforcementStore(directory);
        assertThrows(IllegalArgumentException.class, () -> store.editHash("g".repeat(64), true));
        assertFalse(Files.exists(directory.resolve("enforcement_hashes.txt")));
    }
    @Test void handEditedListSupportsCommentsAndCase() throws Exception {
        Files.writeString(directory.resolve("enforcement_hashes.txt"), "# comment\ninvalid\n" + HASH.toUpperCase(Locale.ROOT) + "\n" + HASH);
        assertEquals(Set.of(HASH), new EnforcementStore(directory).hashes());
    }
    @Test void unreadableAllowlistIsAnErrorRatherThanAnEmptyExemptionList() throws Exception {
        Files.createDirectory(directory.resolve("allowed_hashes.txt"));
        assertThrows(java.io.IOException.class, () -> new EnforcementStore(directory).allowedHashes());
    }
    @Test void hashEditsPreserveAdminCommentsAndRemoveAllCaseVariants() throws Exception {
        String prefix = "# keep this comment\ninvalid-but-preserved\n";
        Files.writeString(directory.resolve("enforcement_hashes.txt"), prefix + HASH + "\n" + HASH.toUpperCase(Locale.ROOT) + "\n");
        var store = new EnforcementStore(directory);
        store.editHash(OTHER, true); store.editHash(HASH, false);
        assertEquals(prefix + OTHER + "\n", Files.readString(directory.resolve("enforcement_hashes.txt")));
    }
    @Test void tempbansSurviveRestartAndExpireAtExactDeadline() throws Exception {
        UUID id = UUID.randomUUID(); var ban = new EnforcementStore.Ban(5000, "Prohibited file: test.jar");
        new EnforcementStore(directory).ban(id, ban);
        var restarted = new EnforcementStore(directory);
        assertEquals(ban, restarted.active(id, 4999)); assertNull(restarted.active(id, 5000));
        assertNull(restarted.active(UUID.randomUUID(), 0));
    }
    @Test void unbanOnlyRemovesTargetAndPreservesUnicodeReason() throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(); var store = new EnforcementStore(directory);
        var ban = new EnforcementStore.Ban(5000, "żółć|pack.zip: test");
        store.ban(first, ban); store.ban(second, ban);
        assertTrue(store.unban(first)); assertFalse(store.unban(first)); assertEquals(ban, store.active(second, 1));
    }
    @Test void corruptedBanIsReportedAsFailureInsteadOfSilentlyUnbanning() throws Exception {
        UUID id = UUID.randomUUID(); Files.writeString(directory.resolve("tempbans.properties"), id + "=broken");
        assertThrows(java.io.IOException.class, () -> new EnforcementStore(directory).active(id, 0));
    }
    @Test void webhookContainsContextAndActualActionEvenWhenTruncated() {
        for (String action : List.of("Kicked.", "Tempban active until tomorrow.", "None — player already disconnected.", "None — enforcement is disabled.")) {
            String message = DetectionNotice.description("Player", "uuid", List.of("x".repeat(9000)), "JOIN SCAN", action);
            assertTrue(message.contains(action)); assertTrue(message.contains("JOIN SCAN")); assertTrue(message.length() <= 4096);
        }
        String clean = DetectionNotice.description("Player", "uuid", List.of(), "MANUAL DETECTION SCAN", "None");
        assertTrue(clean.contains("CLEAN")); assertTrue(clean.contains("Action taken: **None**"));
    }
}
