package com.zaremate.tsa_anticheat.api;

import com.zaremate.tsa_anticheat.TsaFilenameDetection;

import java.util.List;
import java.util.Objects;

/** Supported common-config keys. Values use Boolean, Integer, String or List<String>. */
public enum TsaSetting {
    WEBHOOK_ENABLED(Boolean.class),
    WEBHOOK_URL(String.class),
    COMMAND_PERMISSION(String.class),
    BROADCAST_PERMISSION(String.class),
    BYPASS_PERMISSION(String.class),
    FILENAME_DETECTION_KEYWORDS(List.class),
    RAY_FILENAME_ALLOWLIST(List.class),
    BYPASS_DISABLED_JARS(Boolean.class),
    PACKET_CHECK_ON_LOGIN(Boolean.class),
    LOGIN_CHECK_DELAY_SECONDS(Integer.class, 0, 300),
    PACKET_CHECK_TIMEOUT_SECONDS(Integer.class, 1, 300),
    ENFORCEMENT_MODE(String.class),
    TEMPBAN_SECONDS(Integer.class, 1, 31_536_000);

    private final Class<?> valueType;
    private final int minimum, maximum;
    TsaSetting(Class<?> type) { this(type, 0, 0); }
    TsaSetting(Class<?> type, int minimum, int maximum) {
        this.valueType = type; this.minimum = minimum; this.maximum = maximum;
    }

    public Class<?> valueType() { return valueType; }

    /** Validate and defensively copy before queuing a mutation. */
    public Object validate(Object value) {
        Objects.requireNonNull(value, "value");
        if (!valueType.isInstance(value)) throw new IllegalArgumentException(name() + " expects " + valueType.getSimpleName());
        if (value instanceof Integer number && (number < minimum || number > maximum)) {
            throw new IllegalArgumentException(name() + " must be between " + minimum + " and " + maximum);
        }
        if (this == ENFORCEMENT_MODE && !List.of("off", "kick", "tempban").contains(value)) {
            throw new IllegalArgumentException("Enforcement mode must be off, kick, or tempban");
        }
        if (value instanceof String text && text.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Config strings cannot contain control characters");
        }
        if (value instanceof List<?> entries) {
            if (entries.stream().anyMatch(entry -> !(entry instanceof String text) || text.isBlank()
                    || text.chars().anyMatch(Character::isISOControl)
                    || TsaFilenameDetection.sanitizeFilename(text).isEmpty())) {
                throw new IllegalArgumentException("Expected nonempty filename/keyword strings");
            }
            return List.copyOf(entries);
        }
        return value;
    }
}
