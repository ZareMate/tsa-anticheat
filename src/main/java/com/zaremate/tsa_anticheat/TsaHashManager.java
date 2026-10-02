package com.zaremate.tsa_anticheat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TsaHashManager {
    private static final Map<UUID, PendingRequest> PENDING =
            new ConcurrentHashMap<>();

    private TsaHashManager() {
    }

    public static void start(
            ServerPlayer player,
            String type,
            String name
    ) {
        PENDING.put(
                player.getUUID(),
                new PendingRequest(type, name, null)
        );

        PacketDistributor.sendToPlayer(
                player,
                new HashRequestPayload(type, name)
        );
    }

    public static boolean startDetection(ServerPlayer player, UUID requester) {
        if (PENDING.putIfAbsent(
                player.getUUID(),
                new PendingRequest("DETECTION", "CURRENT", requester)
        ) != null) {
            return false;
        }

        PacketDistributor.sendToPlayer(
                player,
                new HashRequestPayload("DETECTION", "CURRENT")
        );

        return true;
    }

    public static void handleResponse(
            ServerPlayer player,
            HashResponsePayload response
    ) {
        PendingRequest pending = PENDING.remove(player.getUUID());

        if (pending == null) {
            return;
        }

        if (!pending.type().equals(response.kind())
                || !pending.name().equals(response.requestedName())) {
            player.sendSystemMessage(
                    Component.literal("[TSA] Ignored invalid hash response.")
            );
            return;
        }

        if ("DETECTION".equals(pending.type())) {
            handleDetectionResponse(player, pending.requester(), response);
            return;
        }

        int saved = 0;

        for (String line : response.results().split("\\R")) {
            if (line.isBlank()) {
                continue;
            }

            int separator = line.lastIndexOf('\t');

            if (separator <= 0 || separator == line.length() - 1) {
                continue;
            }

            String name = line.substring(0, separator);
            String hash = line.substring(separator + 1).trim();

            if (!hash.matches("[0-9a-f]{64}")) {
                continue;
            }

            try {
                saveGeneratedHash(pending.type(), name, hash);
                saved++;
            } catch (Exception exception) {
                TsaAnticheat.LOGGER.warn(
                        "Failed to save generated hash for {} {}",
                        pending.type(),
                        name,
                        exception
                );
            }
        }

        if ("*".equals(pending.name())) {
            int total = saved + response.failed();

            player.sendSystemMessage(
                    Component.literal(
                            "Hashed " + saved + " " +
                            pending.type().toLowerCase() +
                            (saved == 1 ? "" : "s") +
                            (response.failed() > 0
                                    ? " (" + response.failed() + " failed)"
                                    : "") +
                            "\nProcessed " + total +
                            "\nSaved to config/tsa_anticheat/generated_hashes.txt"
                    )
            );
        } else if (saved == 1) {
            String[] parts = response.results().split("\\R", 2);
            String first = parts[0];
            int separator = first.lastIndexOf('\t');
            String hash = separator >= 0
                    ? first.substring(separator + 1).trim()
                    : "";

            player.sendSystemMessage(
                    Component.literal(
                            pending.type() + " " + pending.name() +
                            "\nSHA-256: " + hash +
                            "\nSaved to config/tsa_anticheat/generated_hashes.txt"
                    )
            );
        } else {
            player.sendSystemMessage(
                    Component.literal(
                            "Could not hash local " +
                            pending.type().toLowerCase() +
                            ": " + pending.name()
                    )
            );
        }
    }

    private static void handleDetectionResponse(
            ServerPlayer target,
            UUID requester,
            HashResponsePayload response
    ) {
        List<String> detections = new ArrayList<>();

        for (String line : response.results().split("\\R")) {
            if (line.isBlank()) {
                continue;
            }

            int firstSeparator = line.indexOf('\t');
            int lastSeparator = line.lastIndexOf('\t');

            if (firstSeparator <= 0
                    || lastSeparator <= firstSeparator
                    || lastSeparator == line.length() - 1) {
                continue;
            }

            String type = line.substring(0, firstSeparator).trim();
            String name = line.substring(firstSeparator + 1, lastSeparator);
            String hash = line.substring(lastSeparator + 1).trim().toLowerCase();

            if (!("MOD".equals(type) || "RESOURCE_PACK".equals(type))
                    || !hash.matches("[0-9a-f]{64}")) {
                continue;
            }

            if (TsaFilenameDetection.isHashAllowed(hash)) {
                continue;
            }

            boolean hashBlacklisted = isBlacklisted(hash);
            boolean rayFilename = TsaFilenameDetection.isRayFilename(name);

            if (rayFilename) {
                TsaFilenameDetection.addHashToBlacklist(hash);
            }

            if (hashBlacklisted || rayFilename) {
                detections.add(
                        type + " " + name + " [" + hash + "]"
                                + (rayFilename ? " (filename contains \\"ray\\")" : "")
                );
            }
        }

        String playerName = target.getGameProfile().getName();

        if (detections.isEmpty()) {
            notifyDetectionResult(
                    requester,
                    playerName + " is clean. No blacklisted mods or resource packs detected."
            );
            broadcastDetection(
                    playerName + " is clean. No blacklisted mods or resource packs detected."
            );
            return;
        }

        writeDetectionReport(target, detections);

        StringBuilder message = new StringBuilder()
                .append(playerName)
                .append(" has ")
                .append(detections.size())
                .append(" detection")
                .append(detections.size() == 1 ? "" : "s")
                .append(":");

        for (String detection : detections) {
            message.append("\n- ").append(detection);
        }

        String result = message.toString();

        notifyDetectionResult(requester, "[TSA Anticheat] " + result);
        broadcastDetection("[TSA Anticheat] " + result);
        DiscordWebhook.sendDetection(
                playerName,
                target.getUUID().toString(),
                detections
        );
    }

    private static boolean isBlacklisted(String hash) {
        Path directory = FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID);
        Path hashesFile = directory.resolve("blacklisted_hashes.txt");

        try {
            if (Files.notExists(hashesFile)) {
                return false;
            }

            for (String line : Files.readAllLines(
                    hashesFile,
                    StandardCharsets.UTF_8
            )) {
                String candidate = line.trim().toLowerCase();

                if (candidate.equals(hash)) {
                    return true;
                }
            }
        } catch (Exception exception) {
            TsaAnticheat.LOGGER.warn(
                    "Failed to read TSA Anticheat blacklist.",
                    exception
            );
        }

        return false;
    }

    private static void writeDetectionReport(
            ServerPlayer player,
            List<String> detections
    ) {
        Path directory =
                FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID);
        Path file = directory.resolve(player.getUUID() + ".txt");

        String line = LocalDate.now() +
                " | DETECTED | " +
                String.join(", ", detections);

        try {
            Files.createDirectories(directory);

            Files.writeString(
                    file,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception exception) {
            TsaAnticheat.LOGGER.warn(
                    "Failed to write TSA Anticheat detection report for {}",
                    player.getGameProfile().getName(),
                    exception
            );
        }
    }

    private static void notifyDetectionResult(
            UUID requester,
            String message
    ) {
        if (requester == null) {
            return;
        }

        var server =
                net.neoforged.neoforge.server.ServerLifecycleHooks
                        .getCurrentServer();

        if (server == null) {
            return;
        }

        ServerPlayer requesterPlayer =
                server.getPlayerList().getPlayer(requester);

        if (requesterPlayer != null) {
            requesterPlayer.sendSystemMessage(
                    Component.literal(message)
            );
        }
    }

    private static void broadcastDetection(String message) {
        var server =
                net.neoforged.neoforge.server.ServerLifecycleHooks
                        .getCurrentServer();

        if (server == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (TsaPermissions.hasBroadcastPermission(player)) {
                player.sendSystemMessage(
                        Component.literal(message)
                );
            }
        }
    }

    private static void saveGeneratedHash(
            String type,
            String name,
            String hash
    ) throws java.io.IOException {
        Path directory =
                FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID);
        Path file = directory.resolve("generated_hashes.txt");

        Files.createDirectories(directory);

        if (Files.notExists(file)) {
            Files.writeString(
                    file,
                    "# TSA Anticheat generated hashes\n" +
                    "# Generated from client-local mods/resource packs by /tsa hash\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW
            );
        }

        String line = type + " | " + name + " | " + hash;

        for (String existing :
                Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (existing.equals(line)) {
                return;
            }
        }

        Files.writeString(
                file,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private record PendingRequest(
            String type,
            String name,
            UUID requester
    ) {
    }
}
