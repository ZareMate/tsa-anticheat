package com.zaremate.tsa_anticheat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerHashAllowlistTest {
    @TempDir Path root;
    private Path allowlist() { return root.resolve("config/tsa_anticheat/allowed_hashes.txt"); }
    private ServerHashAllowlist.Result scan() throws Exception {
        return ServerHashAllowlist.scan(root, allowlist(), (path, error) -> fail(path.toString(), error));
    }
    private Path file(String path, String contents) throws Exception {
        Path file = root.resolve(path); Files.createDirectories(file.getParent()); Files.writeString(file, contents); return file;
    }
    private Path zip(String path, boolean reverse) throws Exception {
        Path file = root.resolve(path); Files.createDirectories(file.getParent());
        try (var out = new ZipOutputStream(Files.newOutputStream(file))) {
            for (String name : reverse ? List.of("b.txt", "a.txt") : List.of("a.txt", "b.txt")) {
                var entry = new ZipEntry(name); entry.setTime(reverse ? 100000 : 200000);
                out.putNextEntry(entry); out.write(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)); out.closeEntry();
            }
        }
        return file;
    }
    @Test void allowsBothFoldersWithClientCompatibleContentHashes() throws Exception {
        Path mod = zip("mods/example.jar", false);
        Path pack = zip("resourcepacks/example.zip", true);
        assertEquals(ResourcePackHasher.hash(mod), ResourcePackHasher.hash(pack));
        assertEquals(new ServerHashAllowlist.Result(2, 1, 0), scan());
        assertTrue(Files.readString(allowlist()).contains(ResourcePackHasher.hash(mod)));
    }
    @Test void directoryPackMatchesZipAndDoesNotAllowIndividualAssets() throws Exception {
        Path pack = zip("resourcepacks/packed.zip", false);
        file("resourcepacks/unpacked/a.txt", "a.txt"); file("resourcepacks/unpacked/b.txt", "b.txt");
        assertEquals(ResourcePackHasher.hash(pack), ResourcePackHasher.hash(root.resolve("resourcepacks/unpacked")));
        assertEquals(new ServerHashAllowlist.Result(2, 1, 0), scan());
    }
    @Test void preservesManualEntriesCommentsAndMissingFinalNewline() throws Exception {
        Path mod = file("mods/raw.jar", "raw content");
        String original = "# manual entries\n" + "f".repeat(64);
        file("config/tsa_anticheat/allowed_hashes.txt", original);
        assertEquals(1, scan().added());
        assertEquals(original + "\n" + ResourcePackHasher.hash(mod) + "\n", Files.readString(allowlist()));
        assertEquals(0, scan().added());
    }
    @Test void caseInsensitiveExistingHashesAreNotDuplicated() throws Exception {
        Path mod = file("mods/example.jar", "content");
        String original = ResourcePackHasher.hash(mod).toUpperCase(Locale.ROOT);
        file("config/tsa_anticheat/allowed_hashes.txt", original);
        assertEquals(0, scan().added()); assertEquals(original, Files.readString(allowlist()));
    }
    @Test void absentFoldersDoNotCreateAllowlist() throws Exception {
        assertEquals(new ServerHashAllowlist.Result(0, 0, 0), scan()); assertFalse(Files.exists(allowlist()));
    }
    @Test void unreadableEntryDoesNotPreventOtherHashes() throws Exception {
        file("mods/good.jar", "good");
        Files.createSymbolicLink(root.resolve("mods/broken.jar"), root.resolve("missing.jar"));
        List<Path> failed = new ArrayList<>();
        assertEquals(new ServerHashAllowlist.Result(1, 1, 1), ServerHashAllowlist.scan(root, allowlist(), (path, error) -> failed.add(path)));
        assertEquals(List.of(root.resolve("mods/broken.jar")), failed);
    }
    @Test void removedServerFilesStayAllowedAndNewFilesAreAddedOnNextScan() throws Exception {
        Path mod = file("mods/first.jar", "first"); scan(); String old = Files.readString(allowlist()); Files.delete(mod);
        file("resourcepacks/second.zip", "second"); assertEquals(1, scan().added());
        assertTrue(Files.readString(allowlist()).startsWith(old));
    }
}
