package com.zaremate.tsa_anticheat.api;

import com.zaremate.tsa_anticheat.TsaAnticheat;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class TsaAnticheatDataAccess {
    private static final Path DIRECTORY =
            FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID);

    private TsaAnticheatDataAccess() {}

    static TsaAnticheatAPI.PlayerRecord getPlayer(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        return loadAll().get(uuid);
    }

    static List<TsaAnticheatAPI.PlayerRecord> getPlayers() {
        return List.copyOf(loadAll().values());
    }

    private static Map<UUID, TsaAnticheatAPI.PlayerRecord> loadAll() {
        Map<UUID, MutableRecord> mutable = new LinkedHashMap<>();

        if (Files.notExists(DIRECTORY)) {
            return Map.of();
        }

        try (var stream = Files.list(DIRECTORY)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> parseFile(path, mutable));
        } catch (Exception exception) {
            System.err.println("[TSA Anticheat] Failed to read API data.");
            exception.printStackTrace();
        }

        Map<UUID, TsaAnticheatAPI.PlayerRecord> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, MutableRecord> entry : mutable.entrySet()) {
            result.put(entry.getKey(), entry.getValue().freeze(entry.getKey()));
        }

        return result;
    }

    private static void parseFile(
            Path path,
            Map<UUID, MutableRecord> records
    ) {
        String filename = path.getFileName().toString();
        String uuidText = filename.substring(0, filename.length() - 4);

        UUID uuid;
        try {
            uuid = UUID.fromString(uuidText);
        } catch (IllegalArgumentException ignored) {
            return;
        }

        MutableRecord record =
                records.computeIfAbsent(uuid, MutableRecord::new);

        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|", 4);

                if (parts.length < 3) {
                    continue;
                }

                String timestamp = parts[0].trim();
                String kind = parts[1].trim();

                if (kind.equalsIgnoreCase("PACKET_CHECK")) {
                    String status = parts[2].trim().toUpperCase();

                    record.packetChecks++;

                    switch (status) {
                        case "PASS" -> record.packetPasses++;
                        case "MODIFIED" -> record.packetModified++;
                        case "TIMEOUT" -> record.packetTimeout++;
                        default -> {
                        }
                    }

                    record.lastPacketStatus = status;
                    record.lastPacketDate = timestamp;
                    continue;
                }

                if (kind.equalsIgnoreCase("DETECTED")) {
                    String details = parts.length >= 4
                            ? parts[2].trim() + " | " + parts[3].trim()
                            : parts[2].trim();

                    java.util.regex.Matcher matcher =
                            DETECTION_PATTERN.matcher(details);

                    while (matcher.find()) {
                        String detectionType = matcher.group(1);
                        String name = matcher.group(2).trim();
                        String hash = matcher.group(3);

                        record.detections.add(
                                timestamp + " | DETECTED | "
                                        + detectionType + " "
                                        + name + " [" + hash + "]"
                        );
                    }
                }
            }
        } catch (Exception exception) {
            System.err.println(
                    "[TSA Anticheat] Failed to parse " + filename
            );
            exception.printStackTrace();
        }
    }

    private static final class MutableRecord {
        private long packetChecks;
        private long packetPasses;
        private long packetModified;
        private long packetTimeout;
        private String lastPacketStatus;
        private String lastPacketDate;
        private final List<String> detections = new ArrayList<>();

        private MutableRecord(UUID ignored) {
        }

        private TsaAnticheatAPI.PlayerRecord freeze(UUID playerUuid) {
            return new TsaAnticheatAPI.PlayerRecord(
                    playerUuid,
                    resolveName(playerUuid),
                    packetChecks,
                    packetPasses,
                    packetModified,
                    packetTimeout,
                    lastPacketStatus,
                    lastPacketDate,
                    detections
            );
        }
    }

    private static String resolveName(UUID uuid) {
        var server = ServerLifecycleHooks.getCurrentServer();

        if (server != null) {
            var player = server.getPlayerList().getPlayer(uuid);

            if (player != null) {
                return player.getGameProfile().getName();
            }
        }

        return uuid.toString();
    }
}
