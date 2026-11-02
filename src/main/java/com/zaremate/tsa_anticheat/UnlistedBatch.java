package com.zaremate.tsa_anticheat;

import java.util.*;

/** One command's fresh results; never mixes cached inventories or concurrent commands. */
final class UnlistedBatch {
    private final Map<UUID, String> players;
    private final Set<UUID> waiting;
    private final Map<UUID, TsaInventory.Snapshot> results = new LinkedHashMap<>();
    private final Map<UUID, String> skipped = new LinkedHashMap<>();

    UnlistedBatch(Map<UUID, String> players) {
        this.players = new LinkedHashMap<>(players);
        waiting = new LinkedHashSet<>(players.keySet());
    }

    synchronized void accept(UUID player, TsaInventory.Snapshot inventory) {
        if (waiting.remove(player)) results.put(player, inventory);
    }

    synchronized void skip(UUID player, String reason) {
        if (waiting.remove(player)) skipped.put(player, reason);
    }

    synchronized boolean complete() { return waiting.isEmpty(); }

    synchronized void timeout() {
        for (UUID player : List.copyOf(waiting)) skip(player, "timed out");
    }

    synchronized String format() {
        record Key(String type, String name, String hash) {}
        Map<Key, Set<String>> grouped = new LinkedHashMap<>();
        int failures = 0;
        // Follow selection order, not response arrival order.
        for (UUID player : players.keySet()) {
            TsaInventory.Snapshot inventory = results.get(player);
            if (inventory == null) continue;
            failures += inventory.failures();
            for (TsaInventory.Entry entry : inventory.unlisted()) {
                Key key = new Key(entry.type(), entry.name(), entry.hash());
                grouped.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(players.get(player));
            }
        }
        StringBuilder text = new StringBuilder("[TSA] Combined unlisted files: ")
                .append(grouped.size()).append(" unique entries from ")
                .append(results.size()).append("/").append(players.size()).append(" players.");
        if (failures > 0) text.append("\n").append(failures).append(" hash failures; incomplete.");
        if (grouped.isEmpty()) text.append("\nNo unlisted files reported by responding players.");
        grouped.forEach((key, names) -> text.append("\n- ").append(key.type()).append(" ")
                .append(key.name()).append(" [").append(key.hash()).append("]")
                .append(TsaInventory.isEmptyHash(key.hash()) ? " (EMPTY)" : "")
                .append(" — Players: ").append(String.join(", ", names)));
        for (UUID player : players.keySet()) {
            if (skipped.containsKey(player)) text.append("\nSkipped ").append(players.get(player))
                    .append(": ").append(skipped.get(player)).append("; incomplete.");
        }
        return text.toString();
    }
}
