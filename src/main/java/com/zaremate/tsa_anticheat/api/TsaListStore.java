package com.zaremate.tsa_anticheat.api;

import com.zaremate.tsa_anticheat.TsaFilenameDetection;
import com.zaremate.tsa_anticheat.HashFilenameStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** All production access is serialized on TSA's worker. */
final class TsaListStore {
    private final Path directory;
    TsaListStore(Path directory) { this.directory = directory; }

    static String hash(String value) {
        String normalized = Objects.requireNonNull(value, "hash").trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected exactly 64 hexadecimal SHA-256 characters");
        }
        return normalized;
    }

    Set<String> hashes(HashList list) throws IOException {
        Set<String> result = new TreeSet<>();
        for (String line : lines(list.filename)) {
            String value = line.trim().toLowerCase(Locale.ROOT);
            if (value.matches("[0-9a-f]{64}")) result.add(value);
        }
        if (list == HashList.BLACKLISTED || list == HashList.ENFORCEMENT) {
            // Keep hash identities unchanged, but present these lists by known filename.
            // Unknown hashes sort after named entries, consistently by hash.
            HashFilenameStore names = HashFilenameStore.open(directory);
            Map<String, String> labels = new HashMap<>();
            for (String hash : result) {
                labels.put(hash, names.lookup(hash).stream()
                        .map(HashFilenameStore.Entry::filename)
                        .min(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
                        .orElse(null));
            }
            List<String> sorted = new ArrayList<>(result);
            sorted.sort(Comparator
                    .comparing((String hash) -> labels.get(hash) == null)
                    .thenComparing(hash -> labels.get(hash) == null ? hash : labels.get(hash), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Comparator.naturalOrder()));
            return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
        }
        return Collections.unmodifiableSet(result);
    }

    boolean editHash(HashList list, String value, boolean add) throws IOException {
        String hash = hash(value);
        List<String> lines = lines(list.filename);
        boolean present = lines.stream().anyMatch(line -> line.trim().equalsIgnoreCase(hash));
        if (add == present) return false;
        if (add) lines.add(hash);
        else lines.removeIf(line -> line.trim().equalsIgnoreCase(hash));
        write(list.filename, lines);
        return true;
    }

    Set<String> filenames() throws IOException {
        Set<String> result = new TreeSet<>();
        for (String line : lines("allowed_names.txt")) {
            if (!line.isBlank() && !line.stripLeading().startsWith("#")) result.add(line.trim());
        }
        return Collections.unmodifiableSet(result);
    }

    boolean editFilename(String name, boolean add) throws IOException {
        String value = Objects.requireNonNull(name, "name").trim();
        if (value.startsWith("#") || value.chars().anyMatch(Character::isISOControl)
                || TsaFilenameDetection.sanitizeFilename(value).isEmpty()) {
            throw new IllegalArgumentException("Expected a filename without control characters or a leading #");
        }
        String key = TsaFilenameDetection.sanitizeFilename(value);
        List<String> lines = lines("allowed_names.txt");
        java.util.function.Predicate<String> matches = line -> !line.stripLeading().startsWith("#")
                && TsaFilenameDetection.sanitizeFilename(line).equals(key);
        boolean present = lines.stream().anyMatch(matches);
        if (add == present) return false;
        if (add) lines.add(value);
        else lines.removeIf(matches);
        write("allowed_names.txt", lines);
        return true;
    }

    List<GeneratedHash> generated() throws IOException {
        Set<GeneratedHash> result = new LinkedHashSet<>();
        for (String line : lines("generated_hashes.txt")) {
            GeneratedHash entry = parseGenerated(line);
            if (entry != null) result.add(entry);
        }
        return List.copyOf(result);
    }

    boolean editGenerated(GeneratedHash entry, boolean add) throws IOException {
        Objects.requireNonNull(entry, "entry");
        List<String> lines = lines("generated_hashes.txt");
        boolean present = lines.stream().anyMatch(line -> entry.equals(parseGenerated(line)));
        if (add == present) return false;
        if (add) lines.add(entry.type() + " | " + entry.name() + " | " + entry.hash());
        else lines.removeIf(line -> entry.equals(parseGenerated(line)));
        write("generated_hashes.txt", lines);
        return true;
    }

    private static GeneratedHash parseGenerated(String line) {
        if (line.stripLeading().startsWith("#")) return null;
        String[] parts = line.split("\\|", 3);
        if (parts.length != 3) return null;
        try { return new GeneratedHash(parts[0], parts[1], parts[2]); }
        catch (IllegalArgumentException error) { return null; }
    }

    private List<String> lines(String filename) throws IOException {
        Path file = directory.resolve(filename);
        return Files.notExists(file) ? new ArrayList<>() : Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    private void write(String filename, List<String> lines) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "tsa-api-", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, directory.resolve(filename), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, directory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
