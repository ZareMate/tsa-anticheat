package com.zaremate.tsa_anticheat;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class DetectionRemoval {
    private static final Pattern DETECTION = Pattern.compile(
            "^(?:MOD|RESOURCE_PACK) (.*?) \\[([0-9a-fA-F]{64})\\](?: \\(.*)?$"
    );

    private DetectionRemoval() {}

    public static Result remove(String selector, String value) throws IOException {
        Path directory = FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID);
        return removeFrom(directory, selector, value);
    }

    static Result removeFrom(Path directory, String selector, String value) throws IOException {
        if (!"name".equals(selector) && !"hash".equals(selector)) {
            throw new IllegalArgumentException("Unknown detection selector: " + selector);
        }
        if (value == null) {
            throw new IllegalArgumentException("Detection name/hash cannot be empty.");
        }
        String target = value.trim();
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Detection name/hash cannot be empty.");
        }
        if ("hash".equals(selector) && !target.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected a 64-character SHA-256 hash.");
        }

        if ("name".equals(selector) && TsaFilenameDetection.sanitizeFilename(target).isEmpty()) {
            throw new IllegalArgumentException("Detection filename is invalid.");
        }
        if (!Files.isDirectory(directory)) return new Result(0, 0);

        int affectedPlayers = 0;
        int removed = 0;
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString()
                    .matches("[0-9a-fA-F-]{36}\\.txt")).toList()) {
                List<String> original = Files.readAllLines(file, StandardCharsets.UTF_8);
                List<String> updated = new ArrayList<>();
                int fileRemoved = 0;
                for (String line : original) {
                    int marker = line.indexOf(" | DETECTED | ");
                    if (marker < 0) {
                        updated.add(line);
                        continue;
                    }
                    String prefix = line.substring(0, marker + " | DETECTED | ".length());
                    String details = line.substring(marker + " | DETECTED | ".length());
                    String[] entries = details.split(", (?=(?:MOD|RESOURCE_PACK) )");
                    List<String> retained = new ArrayList<>();
                    for (String entry : entries) {
                        Matcher match = DETECTION.matcher(entry);
                        if (match.matches() && (("hash".equals(selector)
                                && match.group(2).equalsIgnoreCase(target))
                                || ("name".equals(selector)
                                && TsaFilenameDetection.sanitizeFilename(match.group(1))
                                        .equals(TsaFilenameDetection.sanitizeFilename(target))))) {
                            fileRemoved++;
                        } else {
                            retained.add(entry);
                        }
                    }
                    if (!retained.isEmpty()) updated.add(prefix + String.join(", ", retained));
                }
                if (fileRemoved == 0) continue;
                Path temp = Files.createTempFile(directory, "tsa-detection-", ".tmp");
                try {
                    Files.write(temp, updated, StandardCharsets.UTF_8);
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temp);
                }
                affectedPlayers++;
                removed += fileRemoved;
            }
        }
        return new Result(affectedPlayers, removed);
    }

    public record Result(int players, int detections) {}
}
