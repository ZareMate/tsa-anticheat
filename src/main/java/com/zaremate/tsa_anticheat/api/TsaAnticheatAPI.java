package com.zaremate.tsa_anticheat.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API for TSA Anticheat.
 *
 * <p>Other server-side mods can use this API to read TSA's per-player
 * detection and packet-integrity history without depending on the storage
 * implementation.</p>
 */
public final class TsaAnticheatAPI {
    private TsaAnticheatAPI() {}

    private static volatile List<PlayerRecord> snapshot = List.of();
    private static java.util.concurrent.CompletableFuture<List<PlayerRecord>> refresh;

    /** Nonblocking view of the most recent background refresh. */
    public static Optional<PlayerRecord> getPlayer(UUID playerUuid) {
        return snapshot.stream().filter(record -> record.playerUuid().equals(playerUuid)).findFirst();
    }

    /** Nonblocking view; initially empty until the first background refresh completes. */
    public static List<PlayerRecord> getPlayers() {
        return snapshot;
    }

    /** Fresh disk data, serialized with TSA report writes and removals. Never join on the server thread. */
    public static synchronized java.util.concurrent.CompletableFuture<List<PlayerRecord>> getPlayersAsync() {
        if (refresh == null || refresh.isDone()) {
            refresh = com.zaremate.tsa_anticheat.TsaWorker.submit(() -> {
                List<PlayerRecord> records = TsaAnticheatDataAccess.getPlayers();
                snapshot = records;
                return records;
            });
        }
        return refresh.copy();
    }

    public static java.util.concurrent.CompletableFuture<Optional<PlayerRecord>> getPlayerAsync(UUID uuid) {
        return getPlayersAsync().thenApply(records -> records.stream()
                .filter(record -> record.playerUuid().equals(uuid)).findFirst());
    }

    /** Lifecycle hook; called after the worker has drained. */
    public static synchronized void clearCache() {
        snapshot = List.of();
        refresh = null;
    }

    public record PlayerRecord(
            UUID playerUuid,
            String playerName,
            long packetChecks,
            long packetPasses,
            long packetModified,
            long packetTimeout,
            String lastPacketStatus,
            String lastPacketDate,
            List<String> detections
    ) {
        public PlayerRecord {
            detections = List.copyOf(detections);
        }
    }
}
