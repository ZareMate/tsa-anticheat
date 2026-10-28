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

    public static boolean hasBypassPermission(ServerPlayer player) {
        return hasLuckPermsPermission(player, TsaAnticheatConfig.BYPASS_PERMISSION.get());
    }

    /** Check stored LuckPerms permissions before a tempbanned player enters the world. */
    public static boolean hasBypassPermission(java.util.UUID uuid) {
        try {
            var luckPerms = net.luckperms.api.LuckPermsProvider.get();
            var user = luckPerms.getUserManager().loadUser(uuid).join();
            return user != null && user.getCachedData().getPermissionData()
                    .checkPermission(TsaAnticheatConfig.BYPASS_PERMISSION.get()).asBoolean();
        } catch (IllegalStateException exception) {
            return false;
        }
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
