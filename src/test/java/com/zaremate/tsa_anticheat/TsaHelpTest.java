package com.zaremate.tsa_anticheat;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TsaHelpTest {
    @Test void pagesCoverAllCommandFamilies() {
        String all = String.join("\n", Arrays.stream(new int[]{1, 2, 3})
                .mapToObj(TsaHelpPages::lines).flatMap(Arrays::stream).toList());
        for (String command : new String[]{
                "/tsa help", "/tsa detect", "/tsa packetcheck", "/tsa checkpacket",
                "/tsa logindelay", "/tsa disabledjars", "/tsa detection remove name",
                "/tsa detection remove hash", "/tsa allow", "/tsa keyword",
                "/tsa hash mod", "/tsa hash resourcepack", "/tsa enforcement",
                "/tsa enforcement hash add", "/tsa enforcement hash remove"
        }) {
            assertTrue(all.contains(command), "Missing command: " + command);
        }
        for (int page = 1; page <= 3; page++) {
            assertTrue(TsaHelpPages.lines(page).length > 0);
        }
    }

    @Test void invalidPageFails() {
        assertThrows(IllegalArgumentException.class, () -> TsaHelpPages.lines(0));
        assertThrows(IllegalArgumentException.class, () -> TsaHelpPages.lines(4));
    }
}
