package com.zaremate.tsa_anticheat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Optional runtime bridge: TSA can load without Punish, but enforcement requires it. */
final class PunishIntegration {
    private static final String API = "com.zaremate.punish.api.PunishApi";
    private static final String TYPE = "com.zaremate.punish.api.PunishmentType";

    private PunishIntegration() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static String apply(PlayerSnapshot target, String mode, List<String> files) throws Exception {
        Class<?> api;
        Class<? extends Enum> typeClass;
        try {
            api = Class.forName(API);
            typeClass = (Class<? extends Enum>) Class.forName(TYPE).asSubclass(Enum.class);
        } catch (ClassNotFoundException error) {
            return "None — Punish mod is not installed; TSA enforcement requires Punish.";
        }

        String kind = mode.equals("tempban") ? "BAN" : "KICK";
        Enum<?> punishmentType = Enum.valueOf(typeClass, kind);
        // A repeated scan must not reset an existing temporary ban's duration.
        if (mode.equals("tempban")) {
            Method current = api.getMethod("getCurrentPunishment",
                    net.minecraft.server.MinecraftServer.class, UUID.class, typeClass);
            Optional<?> active = (Optional<?>) current.invoke(null, target.server(), target.uuid(), punishmentType);
            if (active.isPresent()) return "None — an active Punish ban already exists.";
        }

        String reason = "TSA Anticheat: prohibited file(s): " + String.join(", ", files.stream().limit(8).toList());
        if (files.size() > 8) reason += " and " + (files.size() - 8) + " more";
        long duration = mode.equals("tempban")
                ? TsaAnticheatConfig.TEMPBAN_SECONDS.get().longValue() * 1000L : 0L;
        Method add = api.getMethod("addPunishment",
                net.minecraft.server.MinecraftServer.class, UUID.class, String.class, typeClass,
                boolean.class, String.class, long.class, String.class, String.class, boolean.class);
        add.invoke(null, target.server(), target.uuid(), target.name(), punishmentType,
                false, null, duration, reason, "TSA Anticheat", true);
        return mode.equals("tempban") ? "Temporary ban applied through Punish." : "Kicked through Punish.";
    }
}
