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

    private static final Map<UUID, BatchRequest> BATCHES = new ConcurrentHashMap<>();
    private static final long BATCH_TIMEOUT_MILLIS = 60_000;

    private record BatchRequest(UnlistedBatch results, java.util.function.Consumer<String> reply,
                                long deadline, java.util.concurrent.atomic.AtomicBoolean expiring) {}

    static void forget(UUID uuid) {
        PendingRequest pending = PENDING.remove(uuid);
        if (pending != null) failBatch(pending, uuid, "disconnected");
    }

    static void clear() { PENDING.clear(); BATCHES.clear(); }

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

    public static boolean startJoinScan(ServerPlayer player) {
        return startInventoryScan(player, null, "JOIN_SCAN");
    }

    public static boolean startUnlisted(ServerPlayer player, UUID requester) {
        return startInventoryScan(player, requester, "UNLISTED");
    }

    /** Server-thread command entry point for one combined multi-player report. */
    static int startUnlistedBatch(java.util.Collection<ServerPlayer> targets, UUID requester,
                                  java.util.function.Consumer<String> reply) {
        Map<UUID, String> names = new java.util.LinkedHashMap<>();
        for (ServerPlayer player : targets) names.put(player.getUUID(), player.getGameProfile().getName());
        UUID id = UUID.randomUUID();
        UnlistedBatch results = new UnlistedBatch(names);
        BATCHES.put(id, new BatchRequest(results, reply, System.currentTimeMillis() + BATCH_TIMEOUT_MILLIS,
                new java.util.concurrent.atomic.AtomicBoolean()));
        int started = 0;
        for (ServerPlayer player : targets) {
            if (PENDING.putIfAbsent(player.getUUID(), new PendingRequest("UNLISTED", "CURRENT", requester, id)) != null) {
                results.skip(player.getUUID(), "already scanning");
                continue;
            }
            PacketDistributor.sendToPlayer(player, new HashRequestPayload("DETECTION", "CURRENT"));
            started++;
        }
        TsaWorker.execute(() -> finishBatch(id));
        return started;
    }

    static void expireBatches() {
        long now = System.currentTimeMillis();
        BATCHES.forEach((id, batch) -> {
            if (now >= batch.deadline() && batch.expiring().compareAndSet(false, true)) {
                TsaWorker.execute(() -> {
                    // Remove only requests belonging to this command; never cancel a newer scan.
                    PENDING.entrySet().removeIf(entry -> id.equals(entry.getValue().batch()));
                    batch.results().timeout();
                    finishBatch(id);
                });
            }
        });
    }

    private static void failBatch(PendingRequest pending, UUID player, String reason) {
        if (pending.batch() == null) return;
        TsaWorker.execute(() -> {
            BatchRequest batch = BATCHES.get(pending.batch());
            if (batch == null) return;
            batch.results().skip(player, reason);
            finishBatch(pending.batch());
        });
    }

    private static void finishBatch(UUID id) {
        BatchRequest batch = BATCHES.get(id);
        if (batch != null && batch.results().complete() && BATCHES.remove(id, batch)) {
            String message = batch.results().format();
            DiscordWebhook.sendUnlisted(message);
            batch.reply().accept(message);
        }
    }

    public static boolean startDetection(ServerPlayer player, UUID requester) {
        return startInventoryScan(player, requester, "DETECTION");
    }

    private static boolean startInventoryScan(ServerPlayer player, UUID requester, String kind) {
        if (PENDING.putIfAbsent(
                player.getUUID(),
                new PendingRequest(kind, "CURRENT", requester)
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
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        PendingRequest pending = PENDING.remove(player.getUUID());
        if (pending == null) return;
        TsaWorker.execute(() -> processResponse(snapshot, pending, response));
    }

    private static void processResponse(PlayerSnapshot player, PendingRequest pending, HashResponsePayload response) {

        if (pending == null) {
            return;
        }

        String expectedKind = ("JOIN_SCAN".equals(pending.type()) || "UNLISTED".equals(pending.type()))
                ? "DETECTION" : pending.type();
        if (!expectedKind.equals(response.kind())
                || !pending.name().equals(response.requestedName())) {
            failBatch(pending, player.uuid(), "invalid response");
            player.sendSystemMessage(
                    Component.literal("[TSA] Ignored invalid hash response.")
            );
            return;
        }

        if ("JOIN_SCAN".equals(pending.type())) {
            XrayReportPayload.verifyAndWriteReports(player, response.results(), response.failed());
            return;
        }
        if ("DETECTION".equals(pending.type()) || "UNLISTED".equals(pending.type())) {
            TsaInventory.Snapshot inventory = TsaInventory.capture(player.uuid(), response.results(), response.failed());
            if ("UNLISTED".equals(pending.type())) {
                reportEmptyFiles(player, inventory, "UNLISTED INVENTORY SCAN");
                if (pending.batch() != null) {
                    BatchRequest batch = BATCHES.get(pending.batch());
                    if (batch != null) {
                        batch.results().accept(player.uuid(), inventory);
                        finishBatch(pending.batch());
                    }
                    return;
                }
                String message = TsaInventory.formatUnlisted(inventory, player.name());
                DiscordWebhook.sendUnlisted(message);
                if (pending.requester() == null) TsaAnticheat.LOGGER.info(message);
                else player.publish(pending.requester(), message, "", false);
                return;
            }
            handleDetectionResponse(player, pending.requester(), response);
            return;
        }

        int saved = 0;
        List<TsaInventory.Entry> observed = new ArrayList<>();
        java.util.List<String> emptyFiles = new java.util.ArrayList<>();

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

            if (TsaInventory.isEmptyHash(hash)) {
                emptyFiles.add(pending.type() + " " + name + " [" + hash + "] (EMPTY)");
                player.sendSystemMessage(Component.literal("[TSA] Empty " + pending.type()
                        + ": " + name + " [" + hash + "]"));
            }

            observed.add(new TsaInventory.Entry(pending.type(), name, hash, false, false));

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

        TsaInventory.rememberFiles(observed);
        reportEmptyFiles(player, emptyFiles, "MANUAL HASH REQUEST");

        if ("*".equals(pending.name())) {
            int total = saved + response.failed();

            player.sendSystemMessage(
                    Component.literal(
                            "Hashed " + saved + " " +
                            pending.type().toLowerCase() +
                            (saved == 1 ? "" : "s") +
                            (emptyFiles.isEmpty() ? "" : " (" + emptyFiles.size() + " empty)") +
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
            PlayerSnapshot target,
            UUID requester,
            HashResponsePayload response
    ) {
        var inventory = TsaInventory.get(target.uuid());
        reportEmptyFiles(target, inventory, "MANUAL DETECTION SCAN");
        var enforcementHashes = HashEnforcement.hashes();
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

            String type = line.substring(0, firstSeparator).trim().toUpperCase(java.util.Locale.ROOT);
            String name = line.substring(firstSeparator + 1, lastSeparator);
            String hash = line.substring(lastSeparator + 1).trim().toLowerCase(java.util.Locale.ROOT);

            if (!("MOD".equals(type) || "RESOURCE_PACK".equals(type))
                    || !hash.matches("[0-9a-f]{64}")) {
                continue;
            }

            if (TsaInventory.isEmptyHash(hash) || TsaFilenameDetection.isHashAllowed(hash)) {
                continue;
            }

            boolean hashBlacklistedBeforeFilenameCheck = isBlacklisted(hash);
            String filenameKeyword = TsaFilenameDetection.findFilenameKeyword(name);
            boolean filenameMatched = !filenameKeyword.isEmpty();
            boolean hashAddedByFilename = false;

            if (filenameMatched && !hashBlacklistedBeforeFilenameCheck) {
                hashAddedByFilename =
                        TsaFilenameDetection.addHashToBlacklist(hash);
            }

            if (hashBlacklistedBeforeFilenameCheck || filenameMatched || enforcementHashes.contains(hash)) {
                String detection = type + " " + name + " [" + hash + "]";

                if (enforcementHashes.contains(hash)) {
                    detection += " (manually listed enforcement hash)";
                } else if (hashBlacklistedBeforeFilenameCheck && filenameMatched) {
                    detection += " (hash already known to server; filename contains \""
                            + filenameKeyword + "\")";
                } else if (hashBlacklistedBeforeFilenameCheck) {
                    detection += " (hash already known to server)";
                } else if (hashAddedByFilename) {
                    detection += " (filename contains \"" + filenameKeyword
                            + "\"; hash newly added to blacklist)";
                } else {
                    detection += " (filename contains \"" + filenameKeyword
                            + "\"; hash could not be added to blacklist)";
                }

                detections.add(detection);
            }
        }

        String playerName = target.name();

        if (detections.isEmpty()) {
            String clean = playerName + " is clean. No blacklisted mods or resource packs detected.";
            target.publish(requester, clean, clean);
            HashEnforcement.finish(target, response.results(), detections, "MANUAL DETECTION SCAN");
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

        target.publish(requester, "[TSA Anticheat] " + result, "[TSA Anticheat] " + result);
        HashEnforcement.finish(target, response.results(), detections, "MANUAL DETECTION SCAN");
    }

    static void reportEmptyFiles(PlayerSnapshot player, TsaInventory.Snapshot inventory, String context) {
        if (inventory == null) return;
        reportEmptyFiles(player, inventory.emptyFiles().stream()
                .map(entry -> entry.type() + " " + entry.name() + " [" + entry.hash() + "] (EMPTY)")
                .toList(), context);
    }

    static void reportEmptyFiles(PlayerSnapshot player, java.util.List<String> descriptions, String context) {
        if (descriptions.isEmpty()) return;
        String message = "[TSA] " + player.name() + " reported empty files:" + System.lineSeparator()
                + "- " + String.join(System.lineSeparator() + "- ", descriptions);
        player.publish(null, message, message);
        DiscordWebhook.sendDetection(player.name(), player.uuid().toString(), descriptions,
                context + " — EMPTY FILES", "None — informational only.");
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
                String candidate = line.trim().toLowerCase(java.util.Locale.ROOT);

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
            PlayerSnapshot player,
            List<String> detections
    ) {
        Path directory =
                FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID);
        Path file = directory.resolve(player.uuid() + ".txt");

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
                    player.name(),
                    exception
            );
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
            UUID requester,
            UUID batch
    ) {
        PendingRequest(String type, String name, UUID requester) { this(type, name, requester, null); }
    }
}
