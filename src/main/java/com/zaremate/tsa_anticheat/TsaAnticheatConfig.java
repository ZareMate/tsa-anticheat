package com.zaremate.tsa_anticheat;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

@SuppressWarnings("null")
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

    public static final ModConfigSpec.IntValue PACKET_CHECK_TIMEOUT_SECONDS =
            BUILDER.comment(
                    "Maximum time to wait for a packet integrity response.",
                    "Default: 5 seconds."
            ).defineInRange("packet_check_timeout_seconds", 5, 1, 300);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private TsaAnticheatConfig() {
    }

    public static String webhookUrl() {
        return WEBHOOK_URL.get().trim();
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
