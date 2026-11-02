package com.zaremate.tsa_anticheat.api;

import com.zaremate.tsa_anticheat.TsaAnticheatConfig;
import com.zaremate.tsa_anticheat.TsaFilenameDetection;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.*;

/** Worker-owned access to the loaded common config. */
final class TsaConfiguration {
    private TsaConfiguration() {}

    static Map<TsaSetting, Object> read() {
        Map<TsaSetting, Object> result = new EnumMap<>(TsaSetting.class);
        for (TsaSetting setting : TsaSetting.values()) {
            Object value = config(setting).get();
            result.put(setting, value instanceof List<?> entries ? List.copyOf(entries) : value);
        }
        return Collections.unmodifiableMap(result);
    }

    static boolean set(TsaSetting setting, Object value) {
        return setValue(config(setting), setting.validate(value));
    }

    private static <T> boolean setValue(ModConfigSpec.ConfigValue<T> config, Object value) {
        T previous = config.get();
        if (previous.equals(value)) return false;
        // The exhaustive mapping and TsaSetting.validate establish the config's value type.
        @SuppressWarnings("unchecked") T typed = (T) value;
        config.set(typed);
        try { config.save(); }
        catch (RuntimeException error) {
            config.set(previous);
            throw error;
        }
        return true;
    }

    static boolean editEntry(TsaSetting setting, String entry, boolean add) {
        if (setting != TsaSetting.FILENAME_DETECTION_KEYWORDS && setting != TsaSetting.RAY_FILENAME_ALLOWLIST) {
            throw new IllegalArgumentException("Expected a filename/keyword list setting");
        }
        setting.validate(List.of(entry));
        String key = TsaFilenameDetection.sanitizeFilename(entry);
        List<String> values = new ArrayList<>();
        for (Object value : (List<?>) config(setting).get()) values.add((String) value);
        boolean present = values.stream().anyMatch(value -> TsaFilenameDetection.sanitizeFilename(value).equals(key));
        if (add == present) return false;
        if (add) values.add(setting == TsaSetting.FILENAME_DETECTION_KEYWORDS ? key : entry.trim());
        else values.removeIf(value -> TsaFilenameDetection.sanitizeFilename(value).equals(key));
        return set(setting, values);
    }

    private static ModConfigSpec.ConfigValue<?> config(TsaSetting setting) {
        return switch (setting) {
            case WEBHOOK_ENABLED -> TsaAnticheatConfig.WEBHOOK_ENABLED;
            case WEBHOOK_URL -> TsaAnticheatConfig.WEBHOOK_URL;
            case COMMAND_PERMISSION -> TsaAnticheatConfig.COMMAND_PERMISSION;
            case BROADCAST_PERMISSION -> TsaAnticheatConfig.BROADCAST_PERMISSION;
            case BYPASS_PERMISSION -> TsaAnticheatConfig.BYPASS_PERMISSION;
            case FILENAME_DETECTION_KEYWORDS -> TsaAnticheatConfig.FILENAME_DETECTION_KEYWORDS;
            case RAY_FILENAME_ALLOWLIST -> TsaAnticheatConfig.RAY_FILENAME_ALLOWLIST;
            case BYPASS_DISABLED_JARS -> TsaAnticheatConfig.BYPASS_DISABLED_JARS;
            case PACKET_CHECK_ON_LOGIN -> TsaAnticheatConfig.PACKET_CHECK_ON_LOGIN;
            case LOGIN_CHECK_DELAY_SECONDS -> TsaAnticheatConfig.LOGIN_CHECK_DELAY_SECONDS;
            case PACKET_CHECK_TIMEOUT_SECONDS -> TsaAnticheatConfig.PACKET_CHECK_TIMEOUT_SECONDS;
            case ENFORCEMENT_MODE -> TsaAnticheatConfig.ENFORCEMENT_MODE;
            case TEMPBAN_SECONDS -> TsaAnticheatConfig.TEMPBAN_SECONDS;
        };
    }
}
