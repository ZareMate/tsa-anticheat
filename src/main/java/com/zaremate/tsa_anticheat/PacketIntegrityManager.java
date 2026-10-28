package com.zaremate.tsa_anticheat;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.neoforged.fml.loading.FMLPaths;

public final class PacketIntegrityManager {
    private static final String DOMAIN = "tsa-anticheat:packet-integrity:v1";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private static final Map<UUID, PendingCheck> PENDING = new ConcurrentHashMap<>();

    private PacketIntegrityManager() {
    }

    public static void start(ServerPlayer player, UUID requester) {
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        TsaWorker.execute(() -> {
            String challenge = randomChallenge();
            snapshot.onServer(() -> {
                PENDING.put(snapshot.uuid(), new PendingCheck(challenge,
                        System.currentTimeMillis(), snapshot, requester));
                PacketDistributor.sendToPlayer(player, new PacketIntegrityChallengePayload(challenge));
            });
        });
    }

    public static void handleResponse(ServerPlayer player, PacketIntegrityResponsePayload response) {
        PendingCheck pending = PENDING.remove(player.getUUID());
        if (pending == null) return;
        long receivedAt = System.currentTimeMillis();
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        TsaWorker.execute(() -> processResponse(snapshot, pending, response, receivedAt));
    }

    private static Result processResponse(PlayerSnapshot player, PendingCheck pending,
                                         PacketIntegrityResponsePayload response, long receivedAt) {
        if (pending == null) {
            return Result.NO_CHECK;
        }

        long elapsed = receivedAt - pending.createdAt();

        if (elapsed > TsaAnticheatConfig.packetCheckTimeoutMillis()) {
            finish(player, pending, Result.TIMEOUT,
                    "response arrived after " +
                    TsaAnticheatConfig.packetCheckTimeoutText());
            return Result.TIMEOUT;
        }

        if (!constantTimeEquals(pending.challenge(), response.challenge())) {
            finish(player, pending, Result.MODIFIED, "challenge was modified");
            return Result.MODIFIED;
        }

        String expected = createResponse(player.uuid(), pending.challenge());

        if (!constantTimeEquals(expected, response.response())) {
            finish(player, pending, Result.MODIFIED, "response hash was modified");
            return Result.MODIFIED;
        }

        finish(player, pending, Result.PASS, "challenge and response matched");
        return Result.PASS;
    }

    private static final java.util.concurrent.atomic.AtomicBoolean EXPIRING =
            new java.util.concurrent.atomic.AtomicBoolean();

    public static void expireChecks() {
        if (!EXPIRING.compareAndSet(false, true)) return;
        long now = System.currentTimeMillis();
        TsaWorker.submit(() -> {
            PENDING.forEach((uuid, pending) -> {
                if (now - pending.createdAt() > TsaAnticheatConfig.packetCheckTimeoutMillis()
                        && PENDING.remove(uuid, pending)) {
                    String reason = "no response received within " + TsaAnticheatConfig.packetCheckTimeoutText();
                    finish(pending.player(), pending, Result.TIMEOUT, reason);
                }
            });
            return null;
        }).whenComplete((ignored, error) -> {
            EXPIRING.set(false);
            if (error != null) TsaAnticheat.LOGGER.error("Failed to expire packet checks", error);
        });
    }

    static void clear() {
        PENDING.clear();
        EXPIRING.set(false);
    }

    public static String createResponse(UUID playerUuid, String challenge) {
        return sha256(DOMAIN + "|" + playerUuid + "|" + challenge);
    }

    private static void finish(
            PlayerSnapshot player,
            PendingCheck pending,
            Result result,
            String reason
    ) {
        writeResult(player.name(), player.uuid(), result, reason);

        String message = "Packet integrity check for " +
                player.name() +
                ": " + result;

        if (result == Result.MODIFIED) {
            message += " (" + reason + ")";
        }

        publishResult(player, result, reason, pending.requester());
        System.out.println("[TSA Anticheat] " + message);
    }

    private static void publishResult(PlayerSnapshot player, Result result, String reason, UUID requester) {
        String playerName = player.name();
        UUID playerUuid = player.uuid();
        String message = "Packet integrity check for " + playerName + ": " + result;
        if (result == Result.MODIFIED) {
            message += " (" + reason + ")";
        }

        // Automatic login packet checks stay quiet for bypassed staff.
        // Explicit /tsa packet checks always publish their result.
        boolean broadcast = requester != null || !player.bypass();
        player.publish(requester, "[TSA] " + message, "[TSA Anticheat] " + message, broadcast);

        if (result == Result.MODIFIED || result == Result.TIMEOUT) {
            DiscordWebhook.send(
                    playerName,
                    playerUuid.toString(),
                    result.name(),
                    reason
            );
        }
    }

    private static void writeResult(
            String playerName,
            UUID playerUuid,
            Result result,
            String reason
    ) {
        Path directory = FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID);
        Path file = directory.resolve(playerUuid + ".txt");

        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String line = timestamp + " | PACKET_CHECK | " + result +
                " | " + reason;

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
            System.err.println(
                    "Failed to write packet integrity result for " + playerName
            );
            exception.printStackTrace();
        }
    }

    private static String randomChallenge() {
        byte[] challenge = new byte[32];
        RANDOM.nextBytes(challenge);
        return HEX.formatHex(challenge);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HEX.formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8)
        );
    }

    public record PendingCheck(
            String challenge,
            long createdAt,
            PlayerSnapshot player,
            UUID requester
    ) {
    }

    public enum Result {
        PASS,
        MODIFIED,
        TIMEOUT,
        NO_CHECK
    }
}
