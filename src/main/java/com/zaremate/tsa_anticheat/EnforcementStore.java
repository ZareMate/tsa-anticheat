package com.zaremate.tsa_anticheat;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Worker-owned storage; never automatically adds a hash to the enforcement list. */
final class EnforcementStore {
    private final Path directory;
    EnforcementStore(Path directory) { this.directory = directory; }

    Set<String> hashes() throws IOException {
        return readHashes("enforcement_hashes.txt");
    }

    Set<String> allowedHashes() throws IOException {
        return readHashes("allowed_hashes.txt");
    }

    private Set<String> readHashes(String name) throws IOException {
        Path path = directory.resolve(name);
        if (Files.notExists(path)) return Set.of();
        Set<String> result = new TreeSet<>();
        for (String line : Files.readAllLines(path)) {
            String value = line.trim().toLowerCase(Locale.ROOT);
            if (value.matches("[0-9a-f]{64}")) result.add(value);
        }
        return result;
    }

    boolean editHash(String value, boolean add) throws IOException {
        String hash = EnforcementPolicy.hash(value);
        Set<String> hashes = hashes();
        boolean changed = add ? !hashes.contains(hash) : hashes.contains(hash);
        if (changed) {
            Path path = directory.resolve("enforcement_hashes.txt");
            List<String> lines = Files.exists(path) ? Files.readAllLines(path) : new ArrayList<>(List.of(
                    "# Manually managed enforcement hashes. Never populated by detection or server scanning."));
            if (add) lines.add(hash);
            else lines.removeIf(line -> line.trim().equalsIgnoreCase(hash));
            write(path, String.join("\n", lines) + "\n");
        }
        return changed;
    }

    Ban active(UUID id, long now) throws IOException {
        Properties data = loadBans();
        String value = data.getProperty(id.toString());
        if (value == null) return null;
        int separator = value.indexOf('|');
        try {
            long until = Long.parseLong(value.substring(0, separator));
            return until > now ? new Ban(until, value.substring(separator + 1)) : null;
        } catch (RuntimeException error) {
            throw new IOException("Invalid TSA tempban for " + id, error);
        }
    }

    void ban(UUID id, Ban ban) throws IOException {
        Properties data = loadBans();
        data.setProperty(id.toString(), ban.until() + "|" + ban.message());
        saveBans(data);
    }

    boolean unban(UUID id) throws IOException {
        Properties data = loadBans();
        if (data.remove(id.toString()) == null) return false;
        saveBans(data); return true;
    }

    private Properties loadBans() throws IOException {
        Properties result = new Properties();
        Path path = directory.resolve("tempbans.properties");
        if (!Files.notExists(path)) try (var reader = Files.newBufferedReader(path)) { result.load(reader); }
        return result;
    }

    private void saveBans(Properties data) throws IOException {
        StringWriter out = new StringWriter(); data.store(out, "TSA temporary bans: UUID=expiry|disconnect message");
        write(directory.resolve("tempbans.properties"), out.toString());
    }

    private void write(Path target, String contents) throws IOException {
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "tsa-enforcement-", ".tmp");
        try {
            Files.writeString(temp, contents);
            try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    record Ban(long until, String message) {}
}
