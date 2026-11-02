package com.zaremate.tsa_anticheat.api;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TsaSettingTest {
    @Test void settingsUseExactTypesAndRejectNulls() {
        for (TsaSetting setting : TsaSetting.values()) {
            assertThrows(NullPointerException.class, () -> setting.validate(null));
            assertThrows(IllegalArgumentException.class, () -> setting.validate(new Object()));
            Object valid = switch (setting) {
                case WEBHOOK_ENABLED, BYPASS_DISABLED_JARS, PACKET_CHECK_ON_LOGIN -> true;
                case LOGIN_CHECK_DELAY_SECONDS, PACKET_CHECK_TIMEOUT_SECONDS, TEMPBAN_SECONDS -> 5;
                case ENFORCEMENT_MODE -> "off";
                case FILENAME_DETECTION_KEYWORDS, RAY_FILENAME_ALLOWLIST -> List.of("example");
                default -> "example";
            };
            assertEquals(valid, setting.validate(valid));
            assertTrue(setting.valueType().isInstance(valid));
        }
        assertThrows(IllegalArgumentException.class, () -> TsaSetting.WEBHOOK_ENABLED.validate("true"));
        assertThrows(IllegalArgumentException.class, () -> TsaSetting.TEMPBAN_SECONDS.validate(5L));
    }

    @Test void integerBoundariesMatchCommonConfig() {
        checkRange(TsaSetting.LOGIN_CHECK_DELAY_SECONDS, 0, 300);
        checkRange(TsaSetting.PACKET_CHECK_TIMEOUT_SECONDS, 1, 300);
        checkRange(TsaSetting.TEMPBAN_SECONDS, 1, 31_536_000);
    }

    private void checkRange(TsaSetting setting, int min, int max) {
        assertEquals(min, setting.validate(min)); assertEquals(max, setting.validate(max));
        assertThrows(IllegalArgumentException.class, () -> setting.validate(min - 1));
        assertThrows(IllegalArgumentException.class, () -> setting.validate(max + 1));
    }

    @Test void enforcementModeRemainsExclusive() {
        for (String mode : List.of("off", "kick", "tempban")) assertEquals(mode, TsaSetting.ENFORCEMENT_MODE.validate(mode));
        for (String mode : List.of("ban", "KICK", "kick,tempban", "")) {
            assertThrows(IllegalArgumentException.class, () -> TsaSetting.ENFORCEMENT_MODE.validate(mode));
        }
    }

    @Test void listInputsAreCopiedAndInvalidEntriesRejected() {
        for (TsaSetting setting : List.of(TsaSetting.FILENAME_DETECTION_KEYWORDS, TsaSetting.RAY_FILENAME_ALLOWLIST)) {
            List<String> input = new ArrayList<>(List.of("x-ray"));
            Object value = setting.validate(input);
            input.clear();
            assertEquals(List.of("x-ray"), value);
            assertThrows(UnsupportedOperationException.class, () -> ((List<?>) value).clear());
            assertEquals(List.of(), setting.validate(List.of()));
            for (List<?> invalid : List.of(List.of(5), List.of(""), List.of("---"), List.of("line\nline"), Arrays.asList((String) null))) {
                assertThrows(IllegalArgumentException.class, () -> setting.validate(invalid));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> TsaSetting.WEBHOOK_URL.validate("url\nother"));
    }
}
