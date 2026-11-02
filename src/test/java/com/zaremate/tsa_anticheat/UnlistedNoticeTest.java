package com.zaremate.tsa_anticheat;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class UnlistedNoticeTest {
    private String description(String payload) {
        var root = JsonParser.parseString(payload).getAsJsonObject();
        assertEquals(0, root.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
        var embed = root.getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertEquals("TSA Anticheat unlisted inventory", embed.get("title").getAsString());
        return embed.get("description").getAsString();
    }

    @Test void singlePlayerReportIsInformationalAndPreservesNamesAndHashes() {
        String hash = "a".repeat(64);
        String text = description(UnlistedNotice.payloads("[TSA] Unlisted files for Player: 1\n- MOD example.jar [" + hash + "]").getFirst());
        assertTrue(text.contains("Status: **INFORMATIONAL**"));
        assertTrue(text.contains("None — inventory only, not evidence of cheating."));
        assertTrue(text.contains("Unlisted files for Player"));
        assertTrue(text.contains("MOD example.jar [" + hash + "]"));
        assertFalse(text.contains("**DETECTED**"));
    }

    @Test void combinedReportKeepsGroupingPlayersAndIncompleteStatus() {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), missing = UUID.randomUUID();
        Map<UUID, String> players = new LinkedHashMap<>();
        players.put(alice, "Alice"); players.put(bob, "Bob"); players.put(missing, "Missing");
        var batch = new UnlistedBatch(players);
        var entry = new TsaInventory.Entry("MOD", "same.jar", "a".repeat(64), false, false);
        batch.accept(alice, new TsaInventory.Snapshot(alice, Instant.now(), List.of(entry), 1));
        batch.accept(bob, new TsaInventory.Snapshot(bob, Instant.now(), List.of(entry), 0));
        batch.timeout();
        var payloads = UnlistedNotice.payloads(batch.format());
        assertEquals(1, payloads.size());
        String text = description(payloads.getFirst());
        assertEquals(1, text.split("same.jar", -1).length - 1);
        assertTrue(text.contains("Players: Alice, Bob"));
        assertTrue(text.contains("hash failures; incomplete"));
        assertTrue(text.contains("Skipped Missing: timed out"));
    }

    @Test void emptyReportsStillProduceWebhookPayloads() {
        UUID player = UUID.randomUUID();
        String report = TsaInventory.formatUnlisted(new TsaInventory.Snapshot(player, Instant.now(), List.of(), 0), "Player");
        var payloads = UnlistedNotice.payloads(report);
        assertEquals(1, payloads.size());
        assertTrue(description(payloads.getFirst()).contains("No unlisted files reported."));
    }

    @Test void largeReportsRetainEveryEntryWithinDiscordLimits() {
        StringBuilder report = new StringBuilder("Combined unlisted files\n");
        for (int i = 0; i < 200; i++) report.append("MOD file").append(i).append(".jar [")
                .append("a".repeat(64)).append("] — Players: Alice, Bob\n");
        var payloads = UnlistedNotice.payloads(report.toString());
        assertTrue(payloads.size() > 1);
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < payloads.size(); i++) {
            String text = description(payloads.get(i));
            assertTrue(text.length() <= 4096);
            assertTrue(text.contains("Page " + (i + 1) + "/" + payloads.size()));
            assertTrue(text.contains("Status: **INFORMATIONAL**"));
            all.append(text);
        }
        for (int i = 0; i < 200; i++) assertTrue(all.toString().contains("file" + i + ".jar"));
        assertFalse(all.toString().contains("truncated"));
    }

    @Test void clientTextIsEscapedAndLongLinesAndUnicodeArePreserved() {
        String text = description(UnlistedNotice.payloads("quote\" slash\\ star* under_score `code`\n\t\u0001 Żółć 😀").getFirst());
        assertTrue(text.contains("quote\" slash\\\\ star\\* under\\_score \\`code\\`"));
        assertTrue(text.contains("\t\u0001 Żółć 😀"));
        String longLine = "😀".repeat(5000);
        StringBuilder rebuilt = new StringBuilder();
        for (String payload : UnlistedNotice.payloads(longLine)) {
            String page = description(payload);
            assertTrue(page.length() <= 4096);
            rebuilt.append(page.substring(page.indexOf("\n\n") + 2));
        }
        assertEquals(longLine, rebuilt.toString());
    }
}
