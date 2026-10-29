package com.zaremate.tsa_anticheat;

/** Plain Java help entries, independent of Minecraft runtime classes. */
final class TsaHelpPages {
    private TsaHelpPages() {}

    static String[] lines(int page) {
        return switch (page) {
            case 1 -> new String[]{
                    "/tsa help [page]|Show this guide (pages 1-3)",
                    "/tsa detect <player>|Scan player's mods and resource packs",
                    "/tsa packetcheck <player>|Run packet integrity check now",
                    "/tsa checkpacket <player>|Alias for packetcheck",
                    "/tsa logindelay [seconds]|View/set auto-login check delay (0-300s; default 5s)",
                    "/tsa disabledjars [true|false]|Exempt .jar.disabled mods from punishment only",
                    "/tsa detection remove name <name>|Remove matching detections for all players",
                    "/tsa detection remove hash <sha256>|Remove detections by hash for all players"
            };
            case 2 -> new String[]{
                    "/tsa allow <name-or-hash>|Allow an exact filename or SHA-256 hash",
                    "/tsa allow name <filename>|Allow a filename from keyword detection",
                    "/tsa allow hash <sha256>|Allow a SHA-256 hash",
                    "/tsa keyword list|List filename-detection keywords",
                    "/tsa keyword add <keyword>|Add a filename keyword",
                    "/tsa keyword remove <keyword>|Remove a filename keyword",
                    "/tsa hash mod <name|*>|Hash local mod or all local mods",
                    "/tsa hash resourcepack <name|*>|Hash local resource pack or all packs"
            };
            case 3 -> new String[]{
                    "/tsa enforcement [status]|Show enforcement mode, duration, hash count",
                    "/tsa enforcement mode <off|kick|tempban>|Choose punishment mode",
                    "/tsa enforcement kick <true|false>|Enable/disable kick mode",
                    "/tsa enforcement tempban <true|false>|Enable/disable tempban mode",
                    "/tsa enforcement duration <duration>|Set tempban length (e.g. 30m, 1h, 7d)",
                    "/tsa enforcement hash list|List hashes eligible for punishment",
                    "/tsa enforcement hash add <sha256>|Add enforcement hash",
                    "/tsa enforcement hash remove <sha256>|Remove enforcement hash",
                    "Permissions|tsa_anticheat.command for /tsa; tsa_anticheat.alerts for alerts",
                    "Bypass|tsa.bypass prevents TSA punishment; Punish mod handles bans"
            };
            default -> throw new IllegalArgumentException("Help page must be between 1 and 3.");
        };
    }
}
