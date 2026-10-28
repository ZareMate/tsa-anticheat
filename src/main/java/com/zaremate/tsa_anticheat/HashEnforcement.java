package com.zaremate.tsa_anticheat;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/** Determines TSA hash violations on the worker, then delegates punishment to Punish on the server thread. */
final class HashEnforcement {
    private HashEnforcement() {}

    static EnforcementStore store() {
        return new EnforcementStore(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve(TsaAnticheat.MOD_ID));
    }

    static Set<String> hashes() {
        try { return store().hashes(); }
        catch (IOException error) {
            TsaAnticheat.LOGGER.error("Could not read enforcement hashes", error);
            return Set.of();
        }
    }

    static void finish(PlayerSnapshot player, String report, List<String> detections, String context) {
        if (player.bypass()) {
            if (!"JOIN SCAN".equals(context)) {
                notify(player, detections, context, "None — tsa.bypass permission granted.");
            }
            return;
        }
        try {
            List<String> files = EnforcementPolicy.matchingFiles(report, store().hashes(), store().allowedHashes());
            String mode = EnforcementPolicy.action(TsaAnticheatConfig.ENFORCEMENT_MODE.get(), files);
            if (mode.equals("off")) {
                notify(player, detections, context, files.isEmpty()
                        ? "None — no non-allowlisted enforcement hash matched."
                        : "None — enforcement is disabled.");
                return;
            }
            player.onServer(() -> {
                // Punish reads and writes Minecraft state; never invoke its API from the TSA worker.
                String result;
                try {
                    if (TsaPermissions.hasBypassPermission(player.connection())) {
                        result = "None — tsa.bypass permission granted.";
                    } else {
                        result = PunishIntegration.apply(player, mode, files);
                    }
                } catch (Exception error) {
                    TsaAnticheat.LOGGER.error("Could not apply Punish enforcement for {}", player.uuid(), error);
                    result = "None — Punish integration failed; see server log.";
                }
                String action = result;
                TsaWorker.execute(() -> notify(player, detections, context, action));
            });
        } catch (Exception error) {
            TsaAnticheat.LOGGER.error("Hash enforcement failed for {}", player.uuid(), error);
            notify(player, detections, context, "None — enforcement check failed; see server log.");
        }
    }

    private static void notify(PlayerSnapshot player, List<String> detections, String context, String action) {
        TsaAnticheat.LOGGER.info("{} for {}: {}", context, player.name(), action);
        DiscordWebhook.sendDetection(player.name(), player.uuid().toString(), detections, context, action);
    }
}
