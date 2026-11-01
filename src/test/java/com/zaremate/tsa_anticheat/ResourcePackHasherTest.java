package com.zaremate.tsa_anticheat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ResourcePackHasherTest {
    @TempDir Path temp;

    @Test
    void zipAndJarHashEntireRawFile() throws Exception {
        byte[] data = {0x50, 0x4b, 3, 4, 1, 2, 3, 4, 5};
        for (String extension : new String[] {"zip", "jar"}) {
            Path file = temp.resolve("pack." + extension);
            Files.write(file, data);
            String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
            assertEquals(expected, ResourcePackHasher.hash(file));
        }
    }

    @Test
    void folderMatchesPythonDeterministicZip() throws Exception {
        Path folder = temp.resolve("folder");
        Files.createDirectories(folder.resolve("sub"));
        Files.writeString(folder.resolve("file.txt"), "Hello TSA");
        Files.write(folder.resolve("sub/ö.txt"), new byte[]{0, 1});
        // Generated with tsa_hash_raw.py: ZIP_STORED, Unix 0644, fixed timestamp, force_zip64.
        assertEquals("f8d68dfa786b1f2c75ca0ad25e116a09de9abcc2a49d785a741f28bb0f73f4bd",
                ResourcePackHasher.hash(folder));
    }

    @Test
    void emptyFolderHashesEmptyZipNotEmptyData() throws Exception {
        Path folder = Files.createDirectory(temp.resolve("empty"));
        assertEquals("8739c76e681f900923b900c9df0ef75cf421d39cabb54650c4b9ad19b6a76d85",
                ResourcePackHasher.hash(folder));
        assertNotEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                ResourcePackHasher.hash(folder));
    }

    @Test
    void folderHashDoesNotDependOnCreationOrderOrFilesystemTimestamp() throws Exception {
        Path a = Files.createDirectory(temp.resolve("a"));
        Path b = Files.createDirectory(temp.resolve("b"));
        Files.writeString(a.resolve("z.txt"), "Z");
        Files.writeString(a.resolve("a.txt"), "A");
        Files.writeString(b.resolve("a.txt"), "A");
        Files.writeString(b.resolve("z.txt"), "Z");
        assertEquals(ResourcePackHasher.hash(a), ResourcePackHasher.hash(b));
        Files.writeString(b.resolve("z.txt"), "changed");
        assertNotEquals(ResourcePackHasher.hash(a), ResourcePackHasher.hash(b));
    }
}
