package com.zaremate.tsa_anticheat;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EventHandlers {
    private static final int LOGIN_CHECK_DELAY_TICKS = 20;
    private static final Map<UUID, Integer> LOGIN_CHECKS = new HashMap<>();

    private EventHandlers() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && TsaAnticheatConfig.PACKET_CHECK_ON_LOGIN.get()) {
            LOGIN_CHECKS.put(player.getUUID(), LOGIN_CHECK_DELAY_TICKS);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LOGIN_CHECKS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PacketIntegrityManager.expireChecks();

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
