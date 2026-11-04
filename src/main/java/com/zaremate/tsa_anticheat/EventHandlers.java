package com.zaremate.tsa_anticheat;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EventHandlers {
    private static final Map<UUID, Integer> LOGIN_CHECKS = new HashMap<>();
    private static final Map<UUID, Integer> INVENTORY_CHECKS = new HashMap<>();

    private static final Map<UUID, String> PLAYER_NAMES = new java.util.concurrent.ConcurrentHashMap<>();
    private static int historyRefreshTicks;

    public static String playerName(UUID uuid) {
        return PLAYER_NAMES.getOrDefault(uuid, uuid.toString());
    }

    private static void refreshHistory() {
        com.zaremate.tsa_anticheat.api.TsaAnticheatAPI.getPlayersAsync()
                .exceptionally(error -> {
                    TsaAnticheat.LOGGER.error("Failed to refresh TSA history", error);
                    return java.util.List.of();
                });
    }

    private EventHandlers() {
    }

    @SubscribeEvent
    public static void onServerStarting(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        if (!event.getServer().isDedicatedServer()) return;
        TsaWorker.start();
        TsaWorker.execute(() -> {
            try {
                int added = HashFilenameStore.open(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                        .resolve(TsaAnticheat.MOD_ID)).importHistory();
                TsaAnticheat.LOGGER.info("Imported {} historical hash-to-filename associations", added);
            } catch (java.io.IOException error) {
                TsaAnticheat.LOGGER.error("Could not import historical hash-to-filename associations", error);
            }
        });
        var directory = event.getServer().getServerDirectory();
        var allowlist = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve(TsaAnticheat.MOD_ID).resolve("allowed_hashes.txt");
        TsaWorker.execute(() -> {
            try {
                var result = ServerHashAllowlist.scan(directory, allowlist,
                        (path, error) -> TsaAnticheat.LOGGER.warn("Could not auto-allow server file {}", path, error));
                TsaAnticheat.LOGGER.info("Server hash allowlist: {} entries hashed, {} new hashes allowed, {} failures",
                        result.hashed(), result.added(), result.failed());
            } catch (java.io.IOException error) {
                TsaAnticheat.LOGGER.error("Could not save server hash allowlist", error);
            }
        });
        refreshHistory();
    }

    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        if (!event.getServer().isDedicatedServer()) return;
        TsaWorker.stop();
        HashFilenameStore.clearCache();
        LOGIN_CHECKS.clear();
        INVENTORY_CHECKS.clear();
        PacketIntegrityManager.clear();
        TsaHashManager.clear();
        PLAYER_NAMES.clear();
        historyRefreshTicks = 0;
        com.zaremate.tsa_anticheat.api.TsaAnticheatAPI.clearCache();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.server.isDedicatedServer()) {
            PLAYER_NAMES.put(player.getUUID(), player.getGameProfile().getName());
            INVENTORY_CHECKS.put(player.getUUID(), TsaAnticheatConfig.LOGIN_CHECK_DELAY_SECONDS.get() * 20);
            if (TsaAnticheatConfig.PACKET_CHECK_ON_LOGIN.get()) {
                LOGIN_CHECKS.put(player.getUUID(), TsaAnticheatConfig.LOGIN_CHECK_DELAY_SECONDS.get() * 20);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.server.isDedicatedServer()) return;
        LOGIN_CHECKS.remove(event.getEntity().getUUID());
        INVENTORY_CHECKS.remove(event.getEntity().getUUID());
        PLAYER_NAMES.remove(event.getEntity().getUUID());
        TsaHashManager.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!event.getServer().isDedicatedServer()) return;
        PacketIntegrityManager.expireChecks();
        TsaHashManager.expireBatches();
        if (++historyRefreshTicks >= 20) {
            historyRefreshTicks = 0;
            refreshHistory();
        }

        var inventoryIterator = INVENTORY_CHECKS.entrySet().iterator();
        while (inventoryIterator.hasNext()) {
            var entry = inventoryIterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining > 0) {
                entry.setValue(remaining);
                continue;
            }
            inventoryIterator.remove();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) TsaHashManager.startJoinScan(player);
        }
        if (LOGIN_CHECKS.isEmpty()) {
            return;
        }

        var server = event.getServer();
        var iterator = LOGIN_CHECKS.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining > 0) {
                entry.setValue(remaining);
                continue;
            }

            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && TsaAnticheatConfig.PACKET_CHECK_ON_LOGIN.get()) {
                PacketIntegrityManager.start(player, null);
            }
        }
    }
}
