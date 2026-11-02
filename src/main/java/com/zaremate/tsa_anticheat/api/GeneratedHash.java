package com.zaremate.tsa_anticheat.api;

import java.util.Locale;
import java.util.Objects;

/** A generated-hash history entry, not a detection or enforcement rule. */
public record GeneratedHash(String type, String name, String hash) {
    public GeneratedHash {
        type = Objects.requireNonNull(type, "type").trim().toUpperCase(Locale.ROOT);
        if (!type.equals("MOD") && !type.equals("RESOURCE_PACK")) {
            throw new IllegalArgumentException("Expected MOD or RESOURCE_PACK");
        }
        name = Objects.requireNonNull(name, "name").trim();
        if (name.isEmpty() || name.contains("|") || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Expected a filename without control characters or pipes");
        }
        hash = TsaListStore.hash(hash);
    }
}
