package com.zaremate.tsa_anticheat;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class TsaAnticheatConfig {
    private static final ModConfigSpec.Builder BUILDER =
            new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue WEBHOOK_ENABLED =
            BUILDER.comment(
                    "Whether TSA security alerts are sent to Discord.",
                    "Alerts include failed packet checks and blacklisted resource-pack detections.",
                    "Default: false."
            ).define("webhook_enabled", false);

    public static final ModConfigSpec.ConfigValue<String> WEBHOOK_URL =
            BUILDER.comment(
                    "Discord webhook URL.",
                    "Default: empty (webhook disabled until configured).",
                    "Keep this URL private."
            ).define("webhook_url", "");

    public static final ModConfigSpec.ConfigValue<String> COMMAND_PERMISSION =
            BUILDER.comment(
                    "LuckPerms permission required to use /tsa commands.",
                    "Operators with permission level 3 always have access.",
                    "Default: tsa_anticheat.command."
            ).define("command_permission", "tsa_anticheat.command");

    public static final ModConfigSpec.ConfigValue<String> BROADCAST_PERMISSION =
            BUILDER.comment(
                    "LuckPerms permission for players who receive packet integrity broadcasts.",
                    "Operators with permission level 3 always receive them.",
                    "Default: tsa_anticheat.alerts."
            ).define("broadcast_permission", "tsa_anticheat.alerts");

    public static final ModConfigSpec.ConfigValue<String> BYPASS_PERMISSION =
            BUILDER.comment(
                    "LuckPerms permission exempting players from TSA kicks and temporary bans.",
                    "Also suppresses broadcast of successful automatic login packet checks.",
                    "Manual /tsa detection checks still show their results.",
                    "Default: tsa.bypass. OP status alone does not grant this permission."
            ).define("bypass_permission", "tsa.bypass");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FILENAME_DETECTION_KEYWORDS =
            BUILDER.comment(
                    "Sanitized filename keywords that trigger a filename-based detection.",
                    "Matching is case-insensitive and ignores punctuation, spaces, and other non-alphanumeric characters.",
                    "Example: [\"ray\", \"xray\", \"cheat\"] detects any sanitized filename containing one of these keywords.",
                    "Add or remove keywords here without changing the mod."
            ).defineListAllowEmpty(
                    "filename_detection_keywords",
                    List.of("ray", "freecam"),
                    () -> "",
                    value -> value instanceof String s && !s.isBlank()
            );

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RAY_FILENAME_ALLOWLIST =
            BUILDER.comment(
                    "Sanitized filenames in this list are exempt from the \"ray\" filename detector.",
                    "Matching is case-insensitive and ignores punctuation, spaces, and other non-alphanumeric characters.",
                    "Example: My-Ray-Texture-Pack.zip becomes myraytexturepack."
            ).defineListAllowEmpty(
                    "ray_filename_allowlist",
                    List.of(),
                    () -> "",
                    value -> value instanceof String s && !s.isBlank()
            );

    public static final ModConfigSpec.BooleanValue PACKET_CHECK_ON_LOGIN =
            BUILDER.comment(
                    "Automatically check packet integrity shortly after a player logs in.",
                    "Default: true."
            ).define("packet_check_on_login", true);

    public static final ModConfigSpec.IntValue PACKET_CHECK_TIMEOUT_SECONDS =
            BUILDER.comment(
                    "Maximum time to wait for a packet integrity response.",
                    "Default: 5 seconds."
            ).defineInRange("packet_check_timeout_seconds", 5, 1, 300);

    public static final ModConfigSpec.ConfigValue<String> ENFORCEMENT_MODE = BUILDER
            .comment("Manual enforcement-hash action: off, kick, or tempban. Hash allowlists take precedence.")
            .define("enforcement_mode", "off", value -> value instanceof String mode
                    && List.of("off", "kick", "tempban").contains(mode));
    public static final ModConfigSpec.IntValue TEMPBAN_SECONDS = BUILDER
            .comment("Duration of new TSA tempbans, in seconds. Existing bans keep their original expiry.")
            .defineInRange("tempban_seconds", 3600, 1, 31_536_000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private TsaAnticheatConfig() {
    }

    public static String webhookUrl() {
        return WEBHOOK_URL.get().trim();
    }

    public static List<String> filenameDetectionKeywords() {
        return FILENAME_DETECTION_KEYWORDS.get().stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(TsaFilenameDetection::sanitizeFilename)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    public static boolean addFilenameDetectionKeyword(String keyword) {
        String sanitized = TsaFilenameDetection.sanitizeFilename(keyword);
        if (sanitized.isEmpty()) return false;
        List<String> keywords = new java.util.ArrayList<>(filenameDetectionKeywords());
        if (keywords.contains(sanitized)) return false;
        keywords.add(sanitized);
        FILENAME_DETECTION_KEYWORDS.set(keywords);
        FILENAME_DETECTION_KEYWORDS.save();
        return true;
    }

    public static boolean removeFilenameDetectionKeyword(String keyword) {
        String sanitized = TsaFilenameDetection.sanitizeFilename(keyword);
        if (sanitized.isEmpty()) return false;
        List<String> keywords = new java.util.ArrayList<>(filenameDetectionKeywords());
        if (!keywords.remove(sanitized)) return false;
        FILENAME_DETECTION_KEYWORDS.set(keywords);
        FILENAME_DETECTION_KEYWORDS.save();
        return true;
    }

    public static List<String> rayFilenameAllowlist() {
        return RAY_FILENAME_ALLOWLIST.get().stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public static long packetCheckTimeoutMillis() {
        return PACKET_CHECK_TIMEOUT_SECONDS.get().longValue() * 1_000L;
    }

    public static String packetCheckTimeoutText() {
        long seconds = PACKET_CHECK_TIMEOUT_SECONDS.get();
        return seconds + " second" + (seconds == 1 ? "" : "s");
    }
}
