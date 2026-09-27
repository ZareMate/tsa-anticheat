package com.zaremate.tsa_anticheat;

import net.minecraft.network.chat.Component;
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
        String challenge = randomChallenge();

        PENDING.put(
                player.getUUID(),
                new PendingCheck(
                        challenge,
                        System.currentTimeMillis(),
                        player.getGameProfile().getName(),
                        requester
                )
        );

        PacketDistributor.sendToPlayer(
                player,
                new PacketIntegrityChallengePayload(challenge)
        );
    }

    public static Result handleResponse(ServerPlayer player, PacketIntegrityResponsePayload response) {
        PendingCheck pending = PENDING.remove(player.getUUID());

        if (pending == null) {
            return Result.NO_CHECK;
        }

        long elapsed = System.currentTimeMillis() - pending.createdAt();

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

        String expected = createResponse(player.getUUID(), pending.challenge());

        if (!constantTimeEquals(expected, response.response())) {
            finish(player, pending, Result.MODIFIED, "response hash was modified");
            return Result.MODIFIED;
        }

        finish(player, pending, Result.PASS, "challenge and response matched");
        return Result.PASS;
    }

    public static void expireChecks() {
        long now = System.currentTimeMillis();

        PENDING.entrySet().removeIf(entry -> {
            PendingCheck pending = entry.getValue();

            if (now - pending.createdAt() <=
                    TsaAnticheatConfig.packetCheckTimeoutMillis()) {
                return false;
            }

            String reason = "no response received within " +
                    TsaAnticheatConfig.packetCheckTimeoutText();
            writeResult(pending.playerName(), entry.getKey(), Result.TIMEOUT, reason);
            publishResult(pending.playerName(), entry.getKey(), Result.TIMEOUT, reason, pending.requester());
            return true;
        });
    }

    public static String createResponse(UUID playerUuid, String challenge) {
        return sha256(DOMAIN + "|" + playerUuid + "|" + challenge);
    }

    private static void finish(
            ServerPlayer player,
            PendingCheck pending,
            Result result,
            String reason
    ) {
        writeResult(player.getGameProfile().getName(), player.getUUID(), result, reason);

        String message = "Packet integrity check for " +
                player.getGameProfile().getName() +
                ": " + result;

        if (result == Result.MODIFIED) {
            message += " (" + reason + ")";
        }

        publishResult(player.getGameProfile().getName(), player.getUUID(), result, reason, pending.requester());
        System.out.println("[TSA Anticheat] " + message);
    }

    private static void publishResult(String playerName, UUID playerUuid, Result result, String reason, UUID requester) {
        String message = "Packet integrity check for " + playerName + ": " + result;
        if (result == Result.MODIFIED) {
            message += " (" + reason + ")";
        }

        notifyRequester(requester, message);
        broadcast(message);
        DiscordWebhook.send(playerName, playerUuid.toString(), result.name(), reason);
    }

    private static void broadcast(String message) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (hasBroadcastPermission(player)) {
                player.sendSystemMessage(Component.literal("[TSA Anticheat] " + message));
            }
        }
    }

    private static boolean hasBroadcastPermission(ServerPlayer player) {
        if (player.hasPermissions(3)) return true;
        return hasPermissionViaLuckPerms(player, TsaAnticheatConfig.BROADCAST_PERMISSION.get());
    }

    private static boolean hasPermissionViaLuckPerms(ServerPlayer player, String permission) {
        try {
            var luckPerms = net.luckperms.api.LuckPermsProvider.get();
            return luckPerms.getPlayerAdapter(ServerPlayer.class)
                    .getPermissionData(player)
                    .checkPermission(permission)
                    .asBoolean();
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private static void notifyRequester(UUID requester, String message) {
        if (requester == null) {
            return;
        }

        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();

        if (server == null) {
            return;
        }

        ServerPlayer requesterPlayer = server.getPlayerList().getPlayer(requester);

        if (requesterPlayer != null) {
            requesterPlayer.sendSystemMessage(Component.literal("[TSA] " + message));
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
            String playerName,
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
