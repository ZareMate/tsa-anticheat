package com.zaremate.tsa_anticheat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Capture on the server thread; dereference the player only in server callbacks. */
record PlayerSnapshot(UUID uuid, String name, MinecraftServer server, ServerPlayer connection, boolean bypass) {
    static PlayerSnapshot capture(ServerPlayer player) {
        return new PlayerSnapshot(player.getUUID(), player.getGameProfile().getName(),
                player.getServer(), player, TsaPermissions.hasBypassPermission(player));
    }

    void onServer(Runnable action) {
        server.execute(() -> {
            if (server.isRunning() && server.getPlayerList().getPlayer(uuid) == connection) {
                action.run();
            }
        });
    }

    void sendSystemMessage(Component message) {
        onServer(() -> connection.sendSystemMessage(message));
    }

    void publish(UUID requester, String message, String broadcastMessage) {
        publish(requester, message, broadcastMessage, true);
    }

    void publish(UUID requester, String message, String broadcastMessage, boolean broadcast) {
        server.execute(() -> {
            if (!server.isRunning()) return;
            if (requester != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(requester);
                if (player != null) player.sendSystemMessage(Component.literal(message));
            }
            if (!broadcast) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (TsaPermissions.hasBroadcastPermission(player)) {
                    player.sendSystemMessage(Component.literal(broadcastMessage));
                }
            }
        });
    }
}
