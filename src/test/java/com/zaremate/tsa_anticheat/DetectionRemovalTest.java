package com.zaremate.tsa_anticheat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DetectionRemovalTest {
    @TempDir Path directory;
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String A = "MOD X-Ray.zip [" + HASH_A + "] (filename contains \"ray\")";
    private static final String B = "RESOURCE_PACK Normal.zip [" + HASH_B + "] (hash already known to server)";
    private static final String PREFIX = "2026-10-24 | DETECTED | ";

    private Path report(String contents) throws Exception {
        Path file = directory.resolve(UUID.randomUUID() + ".txt");
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }

    @Test void removeByNameAcrossAllPlayerFilesIncludingOffline() throws Exception {
        Path first = report(PREFIX + A + "\n");
        Path second = report(PREFIX + A + "\n");
        var result = DetectionRemoval.removeFrom(directory, "name", "x ray.ZIP");
        assertEquals(2, result.players());
        assertEquals(2, result.detections());
        assertEquals("", Files.readString(first));
        assertEquals("", Files.readString(second));
    }

    @Test void removeHashIgnoresCaseAndRemovesBothTypes() throws Exception {
        Path file = report(PREFIX + A + ", RESOURCE_PACK Other.zip [" + HASH_A + "]\n");
        var result = DetectionRemoval.removeFrom(directory, "hash", HASH_A.toUpperCase());
        assertEquals(1, result.players());
        assertEquals(2, result.detections());
        assertEquals("", Files.readString(file));
    }

    @Test void retainsUnrelatedDetectionsInSameLine() throws Exception {
        Path file = report(PREFIX + A + ", " + B + "\n");
        var result = DetectionRemoval.removeFrom(directory, "name", "x-ray.zip");
        assertEquals(1, result.detections());
        assertEquals(PREFIX + B + "\n", Files.readString(file));
    }

    @Test void preservesPacketChecksAndOtherLogs() throws Exception {
        String unaffected = "2026-10-24 | PACKET_CHECK | PASS | challenge and response matched\n" +
                "2026-10-24 | OTHER | value\n";
        Path file = report(unaffected + PREFIX + A + "\n" + PREFIX + B + "\n");
        DetectionRemoval.removeFrom(directory, "hash", HASH_A);
        assertEquals(unaffected + PREFIX + B + "\n", Files.readString(file));
    }

    @Test void preservesUnmatchedOrMalformedEntries() throws Exception {
        String line = PREFIX + "not parseable, " + B + "\n";
        Path file = report(line);
        var result = DetectionRemoval.removeFrom(directory, "name", "x-ray.zip");
        assertEquals(0, result.players());
        assertEquals(0, result.detections());
        assertEquals(line, Files.readString(file));
    }

    @Test void doesNotTouchNonPlayerFiles() throws Exception {
        Path other = directory.resolve("blacklisted_hashes.txt");
        Files.writeString(other, HASH_A + "\n");
        Path notes = directory.resolve("allowed_names.txt");
        Files.writeString(notes, A);
        DetectionRemoval.removeFrom(directory, "hash", HASH_A);
        assertEquals(HASH_A + "\n", Files.readString(other));
        assertEquals(A, Files.readString(notes));
    }

    @Test void absentDirectoryReturnsZero() throws Exception {
        assertEquals(new DetectionRemoval.Result(0, 0),
                DetectionRemoval.removeFrom(directory.resolve("missing"), "name", "xray"));
    }

    @Test void nonexistentTargetLeavesFilesUnchanged() throws Exception {
        Path file = report(PREFIX + B + "\n");
        assertEquals(new DetectionRemoval.Result(0, 0),
                DetectionRemoval.removeFrom(directory, "hash", HASH_A));
        assertEquals(PREFIX + B + "\n", Files.readString(file));
    }

    @Test void invalidHashRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DetectionRemoval.removeFrom(directory, "hash", "short"));
        assertThrows(IllegalArgumentException.class,
                () -> DetectionRemoval.removeFrom(directory, "hash", "g".repeat(64)));
    }

    @Test void invalidNameAndSelectorRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DetectionRemoval.removeFrom(directory, "name", "!?"));
        assertThrows(IllegalArgumentException.class,
                () -> DetectionRemoval.removeFrom(directory, "name", " "));
        assertThrows(IllegalArgumentException.class,
                () -> DetectionRemoval.removeFrom(directory, "hash", null));
        assertThrows(IllegalArgumentException.class,
                () -> DetectionRemoval.removeFrom(directory, "other", "anything"));
    }

    @Test void onlyExactNormalizedFilenameMatches() throws Exception {
        Path file = report(PREFIX + A + ", MOD X-Ray-Extra.zip [" + HASH_B + "]\n");
        var result = DetectionRemoval.removeFrom(directory, "name", "xray.zip");
        assertEquals(1, result.detections());
        assertTrue(Files.readString(file).contains("X-Ray-Extra.zip"));
    }

    @Test void preservesOtherReportDataAcrossMultipleLines() throws Exception {
        Path file = report(PREFIX + A + "\n" + PREFIX + B + "\n" + PREFIX + A + "\n");
        var result = DetectionRemoval.removeFrom(directory, "hash", HASH_A);
        assertEquals(2, result.detections());
        assertEquals(1, result.players());
        assertEquals(PREFIX + B + "\n", Files.readString(file));
    }
}
