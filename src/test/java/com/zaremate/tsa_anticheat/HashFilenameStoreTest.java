package com.zaremate.tsa_anticheat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HashFilenameStoreTest {
    @TempDir Path directory;
    private static final String HASH = "ab".repeat(32);

    @Test void remembersAliasesTypesAndUnicodeAcrossRestartsWithoutDuplicates() throws Exception {
        var store = new HashFilenameStore(directory);
        var first = new HashFilenameStore.Entry(HASH.toUpperCase(), "mod", "żółć | first.jar");
        var renamed = new HashFilenameStore.Entry(HASH, "MOD", "renamed.jar");
        var pack = new HashFilenameStore.Entry(HASH, "RESOURCE_PACK", "pack.zip");
        store.remember(List.of(first, renamed, pack, first));
        store.remember(List.of(first));
        var reopened = new HashFilenameStore(directory);
        assertEquals(List.of(renamed, first, pack), reopened.lookup(HASH.toUpperCase()));
        assertEquals(3, Files.readAllLines(directory.resolve("hash_filenames.tsv")).size());
        assertTrue(reopened.lookup("c".repeat(64)).isEmpty());
    }

    @Test void rejectsInvalidInputAndIgnoresDamagedRows() throws Exception {
        var store = new HashFilenameStore(directory);
        assertThrows(IllegalArgumentException.class, () -> store.lookup("bad"));
        assertThrows(IllegalArgumentException.class, () -> new HashFilenameStore.Entry(HASH, "MOD", "bad\nname"));
        assertThrows(IllegalArgumentException.class, () -> new HashFilenameStore.Entry(HASH, "OTHER", "file"));
        Files.writeString(directory.resolve("hash_filenames.tsv"), "broken\nnohash\tMOD\tfile\n");
        var valid = new HashFilenameStore.Entry(HASH, "MOD", "good.jar");
        store.remember(List.of(valid));
        assertEquals(List.of(valid), store.lookup(HASH));
    }

    @Test void importsGeneratedAndPlayerHistoryIdempotentlyWithoutChangingSources() throws Exception {
        String generated = "# history\nMOD | old.jar | " + HASH.toUpperCase()
                + "\nMOD | old.jar | " + HASH + "\nMOD | broken.jar | invalid\n"
                + "RESOURCE_PACK | pack | name.zip | " + HASH + "\n";
        String report = "2026-01-01 | DETECTED | MOD renamed.jar [" + HASH
                + "] (hash already known), RESOURCE_PACK pack.zip [" + HASH + "]\n"
                + "2026-01-01 | PACKET_CHECK | MOD fake.jar [" + HASH + "]\n";
        Path generatedFile = directory.resolve("generated_hashes.txt");
        Path reportFile = directory.resolve("00000000-0000-0000-0000-000000000001.txt");
        Files.writeString(generatedFile, generated);
        Files.writeString(reportFile, report);
        Files.writeString(directory.resolve("blacklisted_hashes.txt"), HASH + "\n");
        Files.writeString(directory.resolve("other.txt"), report);
        var store = new HashFilenameStore(directory);
        store.remember(List.of(new HashFilenameStore.Entry(HASH, "MOD", "old.jar")));
        assertEquals(3, store.importHistory());
        assertEquals(4, new HashFilenameStore(directory).lookup(HASH).size());
        assertEquals(0, store.importHistory());
        assertEquals(generated, Files.readString(generatedFile));
        assertEquals(report, Files.readString(reportFile));
        assertFalse(store.lookup(HASH).stream().anyMatch(entry -> entry.filename().equals("fake.jar")));
        Files.writeString(generatedFile, "MOD | later.jar | " + HASH + "\n", java.nio.file.StandardOpenOption.APPEND);
        assertEquals(1, store.importHistory());
    }

    @Test void absentHistoryDoesNotCreateFiles() throws Exception {
        Path missing = directory.resolve("missing");
        assertEquals(0, new HashFilenameStore(missing).importHistory());
        assertFalse(Files.exists(missing));
    }

    @Test void sharedIndexServesLookupsAndDuplicatesWithoutReadingHistoryAgain() throws Exception {
        var store = HashFilenameStore.open(directory);
        assertSame(store, HashFilenameStore.open(directory.resolve(".")));
        var entry = new HashFilenameStore.Entry(HASH, "MOD", "cached.jar");
        store.remember(List.of(entry));
        Path file = directory.resolve("hash_filenames.tsv");
        // Make disk reads and appends fail: cached reads and duplicate observations must still work.
        Files.delete(file);
        Files.createDirectory(file);
        assertEquals(List.of(entry), HashFilenameStore.open(directory).lookup(HASH));
        assertEquals(0, store.remember(List.of(entry)));
        var fresh = new HashFilenameStore.Entry(HASH, "MOD", "new.jar");
        assertThrows(java.io.IOException.class, () -> store.remember(List.of(fresh)));
        assertEquals(List.of(entry), store.lookup(HASH));
        Files.delete(file);
        assertEquals(1, store.remember(List.of(fresh)));
        assertEquals(2, store.lookup(HASH).size());
        HashFilenameStore.clearCache();
        // A new server session reloads persisted data instead of retaining the prior index.
        assertEquals(List.of(fresh), HashFilenameStore.open(directory).lookup(HASH));
    }

    @Test void largeHistoryKeepsHashResultsSeparate() throws Exception {
        var entries = new java.util.ArrayList<HashFilenameStore.Entry>();
        for (int i = 0; i < 10000; i++) entries.add(new HashFilenameStore.Entry(
                String.format("%064x", i), "MOD", "file-" + i + ".jar"));
        var store = new HashFilenameStore(directory);
        assertEquals(10000, store.remember(entries));
        var reopened = new HashFilenameStore(directory);
        assertEquals(List.of(entries.get(5000)), reopened.lookup(entries.get(5000).hash()));
        assertEquals(0, reopened.remember(entries));
        assertEquals(10000, Files.readAllLines(directory.resolve("hash_filenames.tsv")).size());
    }
}
