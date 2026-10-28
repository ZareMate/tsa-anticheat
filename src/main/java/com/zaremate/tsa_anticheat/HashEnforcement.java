package com.zaremate.tsa_anticheat;

import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import java.util.*;
import java.io.IOException;

final class HashEnforcement {
    private HashEnforcement() {}

    static EnforcementStore store() {
        return new EnforcementStore(FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID));
    }

    static Set<String> hashes() {
        try { return store().hashes(); }
        catch (IOException error) {
            TsaAnticheat.LOGGER.error("Could not read enforcement hashes", error);
            return Set.of();
        }
    }

    /** Called on the worker after verification, including clean scans. */
    static void finish(PlayerSnapshot player, String report, List<String> detections, String context) {
        try {
            List<String> files = EnforcementPolicy.matchingFiles(report, store().hashes(), store().allowedHashes());
            String mode = EnforcementPolicy.action(TsaAnticheatConfig.ENFORCEMENT_MODE.get(), files);
            if (files.isEmpty() || mode.equals("off")) {
                notify(player, detections, context, files.isEmpty()
                        ? "None — no non-allowlisted enforcement hash matched."
                        : "None — enforcement is disabled.");
                return;
            }
            EnforcementStore.Ban ban = null;
            if (mode.equals("tempban")) {
                long now = System.currentTimeMillis();
                ban = store().active(player.uuid(), now);
                if (ban == null) {
                    long until = now + TsaAnticheatConfig.TEMPBAN_SECONDS.get().longValue() * 1000L;
                    ban = new EnforcementStore.Ban(until, EnforcementPolicy.message(mode, files, until));
                    store().ban(player.uuid(), ban);
                }
            }
            String message = ban == null ? EnforcementPolicy.message(mode, files, 0) : ban.message();
            String outcome = ban == null ? "Kicked." : "Tempban active until " + java.time.Instant.ofEpochMilli(ban.until()) + ".";
            boolean persistedBan = ban != null;
            long expiresAt = ban == null ? Long.MAX_VALUE : ban.until();
            player.server().execute(() -> {
                String result;
                if (!player.server().isRunning() || player.server().getPlayerList().getPlayer(player.uuid()) != player.connection()
                        || player.connection().hasDisconnected()) {
                    result = persistedBan ? outcome + " Player already disconnected; no kick sent."
                            : "None — player already disconnected.";
                } else if (persistedBan && expiresAt <= System.currentTimeMillis()) {
                    result = "None — tempban expired before disconnection.";
                } else {
                    try {
                        player.connection().connection.disconnect(Component.literal(message));
                        result = outcome;
                    } catch (Exception error) {
                        TsaAnticheat.LOGGER.error("Could not disconnect detected player {}", player.uuid(), error);
                        result = persistedBan ? outcome + " Disconnect failed." : "None — kick failed.";
                    }
                }
                String actual = result;
                TsaWorker.execute(() -> notify(player, detections, context, actual));
            });
        } catch (Exception error) {
            TsaAnticheat.LOGGER.error("Hash enforcement failed for {}", player.uuid(), error);
            notify(player, detections, context, "None — enforcement check or persistence failed; see server log.");
        }
    }

    /** NeoForge waits for this future before admitting the player to the world. */
    static void negotiate(net.neoforged.neoforge.event.entity.player.PlayerNegotiationEvent event) {
        var profile = event.getProfile();
        var connection = event.getConnection();
        java.util.concurrent.CompletableFuture<Void> check = TsaWorker.submit(() -> {
            try {
                EnforcementStore.Ban ban = store().active(profile.getId(), System.currentTimeMillis());
                if (ban != null && connection.isConnected()) {
                    disconnectLogin(connection, ban.message());
                    DiscordWebhook.sendDetection(profile.getName(), profile.getId().toString(), List.of(ban.message()),
                            "JOIN — EXISTING TEMPBAN", "Login refused — tempban active until "
                                    + java.time.Instant.ofEpochMilli(ban.until()) + ".");
                }
                return null;
            } catch (IOException error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        });
        event.enqueueWork(check.exceptionally(error -> {
            TsaAnticheat.LOGGER.error("Could not check TSA tempban for {}", profile.getId(), error);
            if (connection.isConnected()) disconnectLogin(connection, "[TSA Anticheat] Cannot verify temporary ban status. Please retry shortly.");
            return null;
        }));
    }

    private static void disconnectLogin(net.minecraft.network.Connection connection, String message) {
        Component reason = Component.literal(message);
        if (connection.getPacketListener() instanceof net.minecraft.server.network.ServerLoginPacketListenerImpl login) {
            login.disconnect(reason);
        } else {
            connection.disconnect(reason);
        }
    }

    private static void notify(PlayerSnapshot player, List<String> detections, String context, String action) {
        TsaAnticheat.LOGGER.info("{} for {}: {}", context, player.name(), action);
        DiscordWebhook.sendDetection(player.name(), player.uuid().toString(), detections, context, action);
    }
}
