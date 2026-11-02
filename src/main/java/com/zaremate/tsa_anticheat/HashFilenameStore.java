package com.zaremate.tsa_anticheat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Persistent client-reported associations. Call on the TSA worker, never the server thread. */
public final class HashFilenameStore {
    private static final java.util.Map<Path, HashFilenameStore> STORES = new java.util.HashMap<>();
    private final Path file;
    private java.util.Map<String, LinkedHashSet<Entry>> index;

    /** Share one index across scans, commands and API calls for the same directory. */
    public static synchronized HashFilenameStore open(Path directory) {
        return STORES.computeIfAbsent(directory.toAbsolutePath().normalize(), HashFilenameStore::new);
    }

    /** Called only after the server worker has drained. */
    public static synchronized void clearCache() { STORES.clear(); }

    public HashFilenameStore(Path directory) { file = directory.resolve("hash_filenames.tsv"); }

    public record Entry(String hash, String type, String filename) {
        public Entry {
            hash = normalizeHash(hash);
            type = type.trim().toUpperCase(Locale.ROOT);
            if (!type.equals("MOD") && !type.equals("RESOURCE_PACK"))
                throw new IllegalArgumentException("Expected MOD or RESOURCE_PACK");
            if (filename.isBlank() || filename.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Expected a filename without control characters");
        }
    }

    public static String normalizeHash(String hash) {
        String normalized = hash.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid SHA-256 hash. Expected exactly 64 hexadecimal characters.");
        return normalized;
    }

    private void load() throws IOException {
        if (index != null) return;
        java.util.Map<String, LinkedHashSet<Entry>> loaded = new java.util.HashMap<>();
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\t", 3);
                    if (parts.length != 3) continue;
                    try {
                        Entry entry = new Entry(parts[0], parts[1], parts[2]);
                        loaded.computeIfAbsent(entry.hash(), ignored -> new LinkedHashSet<>()).add(entry);
                    }
                    catch (IllegalArgumentException ignored) { /* Ignore malformed history rows. */ }
                }
            }
        }
        index = loaded;
    }

    public synchronized int remember(Collection<Entry> observed) throws IOException {
        load();
        var fresh = new LinkedHashSet<Entry>();
        StringBuilder added = new StringBuilder();
        for (Entry entry : observed) {
            var known = index.get(entry.hash());
            if ((known == null || !known.contains(entry)) && fresh.add(entry))
                added.append(entry.hash()).append('\t').append(entry.type())
                    .append('\t').append(entry.filename()).append('\n');
        }
        if (added.isEmpty()) return 0;
        Files.createDirectories(file.getParent());
        Files.writeString(file, added, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        for (Entry entry : fresh) index.computeIfAbsent(entry.hash(), ignored -> new LinkedHashSet<>()).add(entry);
        return fresh.size();
    }

    /** Import only history containing explicit filename/hash pairs; leave all source files untouched. */
    public synchronized int importHistory() throws IOException {
        load();
        Path directory = file.getParent();
        if (!Files.isDirectory(directory)) return 0;
        var observed = new LinkedHashSet<Entry>();
        Path generated = directory.resolve("generated_hashes.txt");
        if (Files.isRegularFile(generated)) {
            for (String line : Files.readAllLines(generated, StandardCharsets.UTF_8)) {
                if (line.stripLeading().startsWith("#")) continue;
                int first = line.indexOf('|');
                int last = line.lastIndexOf('|');
                if (first < 0 || last <= first) continue;
                addHistorical(observed, line.substring(last + 1).trim(),
                        line.substring(0, first).trim(), line.substring(first + 1, last).trim());
            }
        }
        var detection = java.util.regex.Pattern.compile(
                "(?:^|,\\s*)(MOD|RESOURCE_PACK)\\s+(.+?)\\s+\\[([0-9a-fA-F]{64})\\]");
        try (var paths = Files.list(directory)) {
            for (Path report : paths.filter(Files::isRegularFile).toList()) {
                String name = report.getFileName().toString();
                if (!name.endsWith(".txt")) continue;
                try { java.util.UUID.fromString(name.substring(0, name.length() - 4)); }
                catch (IllegalArgumentException ignored) { continue; }
                for (String line : Files.readAllLines(report, StandardCharsets.UTF_8)) {
                    String[] parts = line.split("\\|", 3);
                    if (parts.length != 3 || !parts[1].trim().equalsIgnoreCase("DETECTED")) continue;
                    var matcher = detection.matcher(parts[2].trim());
                    while (matcher.find()) addHistorical(observed, matcher.group(3), matcher.group(1), matcher.group(2).trim());
                }
            }
        }
        return remember(observed);
    }

    private static void addHistorical(Collection<Entry> entries, String hash, String type, String filename) {
        try { entries.add(new Entry(hash, type, filename)); }
        catch (IllegalArgumentException ignored) { /* Skip malformed legacy entries. */ }
    }

    public synchronized List<Entry> lookup(String hash) throws IOException {
        String normalized = normalizeHash(hash);
        load();
        var entries = index.get(normalized);
        if (entries == null) return List.of();
        return entries.stream()
                .sorted(java.util.Comparator.comparing(Entry::type).thenComparing(Entry::filename)).toList();
    }
}
