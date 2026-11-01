package com.zaremate.tsa_anticheat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackHasherTest {
    @TempDir Path temp;

    @Test
    void archivesIgnoreCompressionAndEntryOrder() throws Exception {
        byte[] alpha = "alpha".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] beta = "beta".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path a = temp.resolve("a.zip"), b = temp.resolve("b.jar");
        writeZip(a, 1, new String[]{"sub/b.txt", "a.txt"}, Map.of("a.txt", alpha, "sub/b.txt", beta));
        writeZip(b, 9, new String[]{"a.txt", "sub/b.txt"}, Map.of("a.txt", alpha, "sub/b.txt", beta));
        assertEquals(ResourcePackHasher.hash(a), ResourcePackHasher.hash(b));
        assertNotEquals(rawSha256(a), rawSha256(b));
        assertFalse(ResourcePackHasher.hashWithInfo(a).empty());
    }

    @Test
    void matchingFolderAndZipProduceSameContentHash() throws Exception {
        Path folder = Files.createDirectory(temp.resolve("folder"));
        Files.createDirectories(folder.resolve("sub"));
        Files.writeString(folder.resolve("a.txt"), "alpha");
        Files.writeString(folder.resolve("sub/b.txt"), "beta");
        Path zip = temp.resolve("pack.zip");
        writeZip(zip, 6, new String[]{"sub/b.txt", "a.txt"},
                Map.of("a.txt", "alpha".getBytes(), "sub/b.txt", "beta".getBytes()));
        assertEquals(ResourcePackHasher.hash(folder), ResourcePackHasher.hash(zip));
        assertFalse(ResourcePackHasher.hashWithInfo(folder).empty());
    }

    @Test
    void emptyDirectoryAndEmptyZipAreExplicitlyMarkedEmpty() throws Exception {
        Path folder = Files.createDirectory(temp.resolve("empty-folder"));
        Path zip = temp.resolve("empty.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {}
        String emptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        assertEquals(emptyHash, ResourcePackHasher.hashWithInfo(folder).hash());
        assertEquals(emptyHash, ResourcePackHasher.hashWithInfo(zip).hash());
        assertTrue(ResourcePackHasher.hashWithInfo(folder).empty());
        assertTrue(ResourcePackHasher.hashWithInfo(zip).empty());
    }

    @Test
    void changingActualContentChangesHash() throws Exception {
        Path folder = Files.createDirectory(temp.resolve("pack"));
        Files.writeString(folder.resolve("a.txt"), "one");
        String initial = ResourcePackHasher.hash(folder);
        Files.writeString(folder.resolve("a.txt"), "two");
        assertNotEquals(initial, ResourcePackHasher.hash(folder));
    }

    private static String rawSha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private static void writeZip(Path file, int compression, String[] names, Map<String, byte[]> files) throws Exception {
        try (OutputStream stream = Files.newOutputStream(file);
             ZipOutputStream zip = new ZipOutputStream(stream)) {
            zip.setLevel(compression);
            for (String name : names) {
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(1700000000000L);
                zip.putNextEntry(entry);
                zip.write(files.get(name));
                zip.closeEntry();
            }
        }
    }
}
