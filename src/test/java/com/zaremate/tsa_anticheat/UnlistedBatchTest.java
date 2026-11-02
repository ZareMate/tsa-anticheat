package com.zaremate.tsa_anticheat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class UnlistedBatchTest {
    private static final String HASH = "a".repeat(64), OTHER = "b".repeat(64);
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();

    private UnlistedBatch batch() {
        Map<UUID, String> names = new LinkedHashMap<>();
        names.put(alice, "Alice"); names.put(bob, "Bob");
        return new UnlistedBatch(names);
    }

    private TsaInventory.Entry entry(String type, String name, String hash, boolean allowed) {
        return new TsaInventory.Entry(type, name, hash, TsaInventory.isEmptyHash(hash), allowed);
    }

    private TsaInventory.Snapshot inventory(UUID player, int failures, TsaInventory.Entry... entries) {
        return new TsaInventory.Snapshot(player, Instant.now(), List.of(entries), failures);
    }

    @Test void matchingEntriesAcrossPlayersArePrintedOnceWithBothNames() {
        var batch = batch();
        var file = entry("MOD", "example.jar", HASH, false);
        batch.accept(bob, inventory(bob, 0, file, file));
        assertFalse(batch.complete());
        batch.accept(alice, inventory(alice, 0, file));
        assertTrue(batch.complete());
        String text = batch.format();
        assertTrue(text.contains("1 unique entries from 2/2 players"));
        assertEquals(1, text.split("example.jar", -1).length - 1);
        assertTrue(text.contains("Players: Alice, Bob"));
    }

    @Test void distinctTypesNamesAndHashesAreNotCollapsed() {
        var batch = batch();
        batch.accept(alice, inventory(alice, 0,
                entry("MOD", "same.jar", HASH, false),
                entry("MOD", "same.jar", OTHER, false),
                entry("RESOURCE_PACK", "same.jar", HASH, false),
                entry("MOD", "renamed.jar", HASH, false)));
        batch.accept(bob, inventory(bob, 0));
        assertTrue(batch.format().contains("4 unique entries from 2/2 players"));
    }

    @Test void allowedEntriesAreExcludedPerPlayerAndEmptyEntriesRemainLabeled() {
        var batch = batch();
        batch.accept(alice, inventory(alice, 0,
                entry("MOD", "allowed.jar", HASH, true),
                entry("RESOURCE_PACK", "empty.zip", TsaInventory.EMPTY_SHA256, false)));
        batch.accept(bob, inventory(bob, 0, entry("MOD", "allowed.jar", HASH, false)));
        String text = batch.format();
        assertTrue(text.contains("2 unique entries"));
        assertTrue(text.contains("(EMPTY) — Players: Alice"));
        assertTrue(text.contains("allowed.jar [" + HASH + "] — Players: Bob"));
    }

    @Test void failedBusyDisconnectedAndMissingPlayersAreExplicitlyIncomplete() {
        Map<UUID, String> names = new LinkedHashMap<>();
        UUID busy = UUID.randomUUID(), disconnected = UUID.randomUUID(), invalid = UUID.randomUUID();
        names.put(alice, "Alice"); names.put(bob, "Bob"); names.put(busy, "Busy");
        names.put(disconnected, "Gone"); names.put(invalid, "Invalid");
        var batch = new UnlistedBatch(names);
        batch.accept(alice, inventory(alice, 3));
        batch.skip(busy, "already scanning"); batch.skip(disconnected, "disconnected");
        batch.skip(invalid, "invalid response");
        assertFalse(batch.complete());
        batch.timeout();
        assertTrue(batch.complete());
        String text = batch.format();
        assertTrue(text.contains("0 unique entries from 1/5 players"));
        assertTrue(text.contains("3 hash failures; incomplete"));
        assertTrue(text.contains("Skipped Bob: timed out"));
        assertTrue(text.contains("Skipped Busy: already scanning"));
        assertTrue(text.contains("Skipped Gone: disconnected"));
        assertTrue(text.contains("Skipped Invalid: invalid response"));
    }

    @Test void duplicateUnknownAndLateResponsesDoNotChangeCompletedResults() {
        var batch = batch();
        batch.accept(alice, inventory(alice, 0));
        batch.skip(bob, "disconnected");
        String text = batch.format();
        batch.accept(alice, inventory(alice, 5, entry("MOD", "late.jar", HASH, false)));
        batch.accept(bob, inventory(bob, 0, entry("MOD", "late.jar", HASH, false)));
        batch.accept(UUID.randomUUID(), inventory(UUID.randomUUID(), 0));
        batch.timeout(); batch.skip(alice, "timed out");
        assertEquals(text, batch.format());
    }

    @Test void concurrentCommandsKeepSeparateResults() {
        var first = batch(); var second = batch();
        first.accept(alice, inventory(alice, 0, entry("MOD", "first.jar", HASH, false)));
        second.accept(alice, inventory(alice, 0, entry("MOD", "second.jar", OTHER, false)));
        first.timeout(); second.timeout();
        assertFalse(first.format().contains("second.jar"));
        assertFalse(second.format().contains("first.jar"));
    }
}
