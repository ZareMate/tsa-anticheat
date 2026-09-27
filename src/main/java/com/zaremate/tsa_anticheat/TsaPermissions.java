package com.zaremate.tsa_anticheat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class TsaPermissions {
    private TsaPermissions() {
    }

    public static boolean canUseCommands(CommandSourceStack source) {
        if (source.hasPermission(3)) {
            return true;
        }

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return false;
        }

        return hasLuckPermsPermission(
                player,
                TsaAnticheatConfig.COMMAND_PERMISSION.get()
        );
    }

    public static boolean hasBroadcastPermission(ServerPlayer player) {
        if (player.hasPermissions(3)) {
            return true;
        }

        return hasLuckPermsPermission(
                player,
                TsaAnticheatConfig.BROADCAST_PERMISSION.get()
        );
    }

    private static boolean hasLuckPermsPermission(
            ServerPlayer player,
            String permission
    ) {
        if (permission == null || permission.isBlank()) {
            return false;
        }

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
}
