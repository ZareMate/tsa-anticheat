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

    public static Optional<PlayerRecord> getPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(TsaAnticheatDataAccess.getPlayer(playerUuid));
    }

    public static List<PlayerRecord> getPlayers() {
        return TsaAnticheatDataAccess.getPlayers();
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
