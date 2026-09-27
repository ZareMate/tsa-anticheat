package com.zaremate.tsa_anticheat;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TsaAnticheatConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue WEBHOOK_ENABLED =
            BUILDER.comment("Send packet integrity results to Discord.")
                    .define("webhook_enabled", false);

    public static final ModConfigSpec.ConfigValue<String> WEBHOOK_URL =
            BUILDER.comment("Discord webhook URL. Keep this private.")
                    .define("webhook_url", "");

    public static final ModConfigSpec.ConfigValue<String> BROADCAST_PERMISSION =
            BUILDER.comment("LuckPerms permission for players who receive packet integrity result broadcasts.")
                    .define("broadcast_permission", "tsa_anticheat.alerts");

    public static final ModConfigSpec SPEC = BUILDER.build();

    private TsaAnticheatConfig() {
    }

    public static String webhookUrl() {
        return WEBHOOK_URL.get().trim();
    }
}
