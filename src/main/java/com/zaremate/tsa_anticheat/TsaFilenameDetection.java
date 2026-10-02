package com.zaremate.tsa_anticheat;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TsaFilenameDetection {
    private static final String TRIGGER = "ray";
    private static final String HASH_PATTERN = "[0-9a-f]{64}";

    private TsaFilenameDetection() {
    }

    /**
     * Normalizes a filename before rule matching:
     * Unicode compatibility normalization, lowercase, then removal of
     * everything except ASCII letters and digits.
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "";
        }

        String normalized = Normalizer.normalize(
                filename,
                Normalizer.Form.NFKC
        ).toLowerCase(Locale.ROOT);

        return normalized.replaceAll("[^a-z0-9]", "");
    }

    public static boolean isRayFilename(String filename) {
        String sanitized = sanitizeFilename(filename);
        return sanitized.contains(TRIGGER) && !isAllowlisted(sanitized);
    }

    public static boolean isAllowlisted(String filename) {
        String sanitized = sanitizeFilename(filename);

        if (sanitized.isEmpty()) {
            return false;
        }

        for (String allowed : TsaAnticheatConfig.rayFilenameAllowlist()) {
            if (sanitizeFilename(allowed).equals(sanitized)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Adds a valid SHA-256 hash to the persistent blacklist if it is not
     * already present.
     */
    public static boolean addHashToBlacklist(String hash) {
        String normalizedHash = hash == null
                ? ""
                : hash.trim().toLowerCase(Locale.ROOT);

        if (!normalizedHash.matches(HASH_PATTERN)) {
            return false;
        }

        Path directory = FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID);
        Path hashesFile = directory.resolve("blacklisted_hashes.txt");

        try {
            Files.createDirectories(directory);

            if (Files.notExists(hashesFile)) {
                Files.writeString(
                        hashesFile,
                        "# One SHA-256 hash per line. Hashes can belong to mods or resource packs.\\n" +
                        "# Detection can be based on hash or on a sanitized filename containing \\"ray\\".\\n" +
                        "# Example: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW
                );
            }

            Set<String> hashes = new HashSet<>();

            for (String line : Files.readAllLines(
                    hashesFile,
                    StandardCharsets.UTF_8
            )) {
                String candidate = line.trim().toLowerCase(Locale.ROOT);

                if (candidate.matches(HASH_PATTERN)) {
                    hashes.add(candidate);
                }
            }

            if (!hashes.add(normalizedHash)) {
                return false;
            }

            Files.writeString(
                    hashesFile,
                    normalizedHash + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

            return true;
        } catch (IOException exception) {
            TsaAnticheat.LOGGER.warn(
                    "Failed to add hash to TSA Anticheat blacklist.",
                    exception
            );
            return false;
        }
    }

    public static boolean isHashAllowed(String hash) {
        String normalizedHash = hash == null
                ? ""
                : hash.trim().toLowerCase(Locale.ROOT);

        if (!normalizedHash.matches(HASH_PATTERN)) {
            return false;
        }

        return loadAllowedHashes().contains(normalizedHash);
    }

    /**
     * Loads hashes that are explicitly exempt from TSA hash and filename
     * detection. This file is separate from the blacklist.
     */
    public static Set<String> loadAllowedHashes() {
        Path directory = FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID);
        Path hashesFile = directory.resolve("allowed_hashes.txt");

        try {
            Files.createDirectories(directory);

            if (Files.notExists(hashesFile)) {
                Files.writeString(
                        hashesFile,
                        "# One SHA-256 hash per line. Allowed hashes override TSA blacklist and filename detection.\\n" +
                        "# Example: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW
                );
            }

            Set<String> hashes = new HashSet<>();

            for (String line : Files.readAllLines(hashesFile, StandardCharsets.UTF_8)) {
                String hash = line.trim().toLowerCase(Locale.ROOT);

                if (hash.matches(HASH_PATTERN)) {
                    hashes.add(hash);
                }
            }

            return hashes;
        } catch (IOException exception) {
            TsaAnticheat.LOGGER.warn(
                    "Failed to read TSA Anticheat allowed hash list.",
                    exception
            );
            return Set.of();
        }
    }

    /**
     * Loads the current persistent blacklist using the same normalization as
     * other TSA hash checks.
     */
    public static Set<String> loadBlacklistedHashes() {
        Path directory = FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID);
        Path hashesFile = directory.resolve("blacklisted_hashes.txt");

        try {
            if (Files.notExists(hashesFile)) {
                return Set.of();
            }

            Set<String> hashes = new HashSet<>();

            for (String line : Files.readAllLines(
                    hashesFile,
                    StandardCharsets.UTF_8
            )) {
                String hash = line.trim().toLowerCase(Locale.ROOT);

                if (hash.matches(HASH_PATTERN)) {
                    hashes.add(hash);
                }
            }

            return hashes;
        } catch (IOException exception) {
            TsaAnticheat.LOGGER.warn(
                    "Failed to read TSA Anticheat blacklist.",
                    exception
            );
            return Set.of();
        }
    }
}
