package com.zaremate.tsa_anticheat.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TsaListStoreTest {
    @TempDir Path directory;
    private static final String HASH = "abcdef0123456789".repeat(4), OTHER = "b".repeat(64);
    private TsaListStore store() { return new TsaListStore(directory); }

    @Test void everyOperationalListSupportsPersistentNormalizedCrudAndIsIndependent() throws Exception {
        for (HashList list : HashList.values()) {
            assertTrue(store().hashes(list).isEmpty());
            assertTrue(store().editHash(list, " " + HASH.toUpperCase(Locale.ROOT) + " ", true));
            assertFalse(store().editHash(list, HASH, true));
            assertEquals(Set.of(HASH), store().hashes(list));
            assertThrows(UnsupportedOperationException.class, () -> store().hashes(list).add(OTHER));
        }
        for (HashList list : HashList.values()) {
            assertTrue(store().editHash(list, HASH, false));
            assertFalse(store().editHash(list, HASH, false));
            assertTrue(store().hashes(list).isEmpty());
        }
    }

    @Test void blacklistAndEnforcementSortByKnownNameNotHash() throws Exception {
        String first = "f".repeat(64);
        String second = "1".repeat(64);
        String unknown = "0".repeat(64);
        Files.writeString(directory.resolve("hash_filenames.tsv"),
                first + "\\tMOD\\talpha.jar\\n" + second + "\\tMOD\\tZeta.jar\\n");
        for (HashList list : List.of(HashList.BLACKLISTED, HashList.ENFORCEMENT)) {
            store().editHash(list, second, true);
            store().editHash(list, unknown, true);
            store().editHash(list, first, true);
            assertEquals(List.of(first, second, unknown), new ArrayList<>(store().hashes(list)));
        }
        store().editHash(HashList.ALLOWED, first, true);
        store().editHash(HashList.ALLOWED, second, true);
        assertEquals(List.of(second, first), new ArrayList<>(store().hashes(HashList.ALLOWED)));
    }

    @Test void removingAllowlistHashNeverDeletesBlacklistOrEnforcement() throws Exception {
        for (HashList list : HashList.values()) store().editHash(list, HASH, true);
        store().editHash(HashList.ALLOWED, HASH, false);
        assertEquals(Set.of(HASH), store().hashes(HashList.BLACKLISTED));
        assertEquals(Set.of(HASH), store().hashes(HashList.ENFORCEMENT));
    }

    @Test void hashEditsPreserveCommentsInvalidLinesAndUnrelatedEntries() throws Exception {
        for (HashList list : HashList.values()) {
            Path file = directory.resolve(list.filename);
            Files.writeString(file, "# comment\ninvalid\n" + HASH + "\n" + HASH.toUpperCase(Locale.ROOT) + "\n" + OTHER);
            assertTrue(store().editHash(list, HASH, false));
            assertEquals("# comment\ninvalid\n" + OTHER + "\n", Files.readString(file));
            assertTrue(store().editHash(list, HASH, true));
            assertEquals(Set.of(HASH, OTHER), store().hashes(list));
        }
    }

    @Test void invalidHashesNeverCreateOrModifyFiles() throws Exception {
        for (HashList list : HashList.values()) {
            for (String invalid : List.of("", "g".repeat(64), HASH + "\n" + OTHER, "a".repeat(63))) {
                assertThrows(IllegalArgumentException.class, () -> store().editHash(list, invalid, true));
            }
            assertThrows(NullPointerException.class, () -> store().editHash(list, null, true));
            assertFalse(Files.exists(directory.resolve(list.filename)));
        }
    }

    @Test void filenameEditsUseDetectionNormalizationWithoutRemovingComments() throws Exception {
        Files.writeString(directory.resolve("allowed_names.txt"), "# My-Ray.zip\nMy-Ray.zip\nmy_ray.zip\nOther.jar\n");
        assertFalse(store().editFilename("MY RAY.ZIP", true));
        assertTrue(store().editFilename("MY RAY.ZIP", false));
        assertEquals("# My-Ray.zip\nOther.jar\n", Files.readString(directory.resolve("allowed_names.txt")));
        assertTrue(store().editFilename(" Żółć.jar ", true));
        assertEquals(Set.of("Other.jar", "Żółć.jar"), store().filenames());
        assertThrows(UnsupportedOperationException.class, () -> store().filenames().clear());
        for (String invalid : List.of("", "---", "#comment", "file.jar\nother.jar")) {
            assertThrows(IllegalArgumentException.class, () -> store().editFilename(invalid, true));
        }
    }

    @Test void generatedHistoryIsStructuredAndNeverChangesRules() throws Exception {
        GeneratedHash entry = new GeneratedHash("mod", " example.jar ", HASH.toUpperCase(Locale.ROOT));
        assertEquals(new GeneratedHash("MOD", "example.jar", HASH), entry);
        assertTrue(store().generated().isEmpty());
        assertTrue(store().editGenerated(entry, true));
        assertFalse(store().editGenerated(entry, true));
        assertEquals(List.of(entry), store().generated());
        for (HashList list : HashList.values()) assertTrue(store().hashes(list).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> store().generated().clear());
        assertTrue(store().editGenerated(entry, false));
        assertFalse(store().editGenerated(entry, false));
    }

    @Test void generatedRemovalPreservesCommentsMalformedAndUnrelatedEntries() throws Exception {
        Path file = directory.resolve("generated_hashes.txt");
        String keep = "# generated\ninvalid\nRESOURCE_PACK | pack.zip | " + OTHER + "\n";
        Files.writeString(file, keep + "MOD | mod.jar | " + HASH + "\nmod | mod.jar | " + HASH.toUpperCase(Locale.ROOT) + "\n");
        assertEquals(2, store().generated().size());
        store().editGenerated(new GeneratedHash("MOD", "mod.jar", HASH), false);
        assertEquals(keep, Files.readString(file));
    }

    @Test void generatedValidationRejectsUnrepresentableEntries() {
        for (String type : List.of("OTHER", "")) {
            assertThrows(IllegalArgumentException.class, () -> new GeneratedHash(type, "file", HASH));
        }
        for (String name : List.of("", "file|other", "file\nother")) {
            assertThrows(IllegalArgumentException.class, () -> new GeneratedHash("MOD", name, HASH));
        }
        assertThrows(IllegalArgumentException.class, () -> new GeneratedHash("MOD", "file", "invalid"));
    }

    @Test void inaccessibleFilesAreFailuresRatherThanEmptyListsOrSuccessfulEdits() throws Exception {
        for (HashList list : HashList.values()) {
            Files.createDirectory(directory.resolve(list.filename));
            assertThrows(IOException.class, () -> store().hashes(list));
            assertThrows(IOException.class, () -> store().editHash(list, HASH, true));
        }
        Files.createDirectory(directory.resolve("allowed_names.txt"));
        Files.createDirectory(directory.resolve("generated_hashes.txt"));
        assertThrows(IOException.class, () -> store().filenames());
        assertThrows(IOException.class, () -> store().editFilename("file.jar", true));
        assertThrows(IOException.class, () -> store().generated());
        assertThrows(IOException.class, () -> store().editGenerated(new GeneratedHash("MOD", "file", HASH), true));
    }
}
