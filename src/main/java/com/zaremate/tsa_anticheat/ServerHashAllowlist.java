package com.zaremate.tsa_anticheat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;

/** Runs on the ordered TSA worker before incoming detection reports. */
final class ServerHashAllowlist {
    private ServerHashAllowlist() {}

    static Result scan(Path serverDirectory, Path allowlist, BiConsumer<Path, IOException> failure) throws IOException {
        Set<String> hashes = new LinkedHashSet<>();
        int hashed = 0, failed = 0;
        for (String folder : List.of("mods", "resourcepacks")) {
            Path directory = serverDirectory.resolve(folder);
            if (Files.notExists(directory)) continue;
            try (var entries = Files.list(directory)) {
                for (Path entry : entries.sorted().toList()) {
                    try {
                        hashes.add(ResourcePackHasher.hash(entry));
                        hashed++;
                    } catch (IOException error) {
                        failed++;
                        failure.accept(entry, error);
                    }
                }
            } catch (IOException error) {
                failed++;
                failure.accept(directory, error);
            }
        }
        if (hashes.isEmpty()) return new Result(hashed, 0, failed);
        String original = Files.exists(allowlist) ? Files.readString(allowlist, StandardCharsets.UTF_8)
                : "# Allowed hashes override TSA blacklist and filename detection.\n";
        original.lines().map(String::trim).map(value -> value.toLowerCase(Locale.ROOT)).forEach(hashes::remove);
        if (hashes.isEmpty()) return new Result(hashed, 0, failed);
        String updated = original + (original.isEmpty() || original.endsWith("\n") || original.endsWith("\r") ? "" : "\n")
                + String.join("\n", hashes) + "\n";
        Files.createDirectories(allowlist.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(allowlist.toAbsolutePath().getParent(), "tsa-server-hashes-", ".tmp");
        try {
            Files.writeString(temp, updated, StandardCharsets.UTF_8);
            try {
                Files.move(temp, allowlist, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, allowlist, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return new Result(hashed, hashes.size(), failed);
    }

    record Result(int hashed, int added, int failed) {}
}
