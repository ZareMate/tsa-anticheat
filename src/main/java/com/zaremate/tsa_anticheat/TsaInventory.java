package com.zaremate.tsa_anticheat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Latest client-reported inventory. Entries are untrusted client claims, not
 * server-verified proof that a mod or resource pack is installed.
 */
public final class TsaInventory {
    private static final Map<UUID, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private TsaInventory() {}

    public record Entry(String type, String name, String hash, boolean empty, boolean allowlisted) {}
    public record Snapshot(UUID playerUuid, Instant scannedAt, List<Entry> files, int failures) {
        public Snapshot { files = List.copyOf(files); }
        public List<Entry> unallowlisted() {
            return files.stream().filter(entry -> !entry.allowlisted()).toList();
        }
        public List<Entry> emptyFiles() {
            return files.stream().filter(Entry::empty).toList();
        }
    }

    public static Snapshot get(UUID uuid) { return SNAPSHOTS.get(uuid); }

    public static void clear() { SNAPSHOTS.clear(); }

    public static Snapshot capture(UUID uuid, String raw, int failures) {
        List<Entry> entries = new ArrayList<>();
        for (String line : raw.split("\\R")) {
            int first = line.indexOf('\t');
            int last = line.lastIndexOf('\t');
            if (first <= 0 || last <= first || last == line.length() - 1) continue;
            String type = line.substring(0, first).trim().toUpperCase(Locale.ROOT);
            if (!type.equals("MOD") && !type.equals("RESOURCE_PACK")) continue;
            String name = line.substring(first + 1, last);
            String hash = line.substring(last + 1).trim().toLowerCase(Locale.ROOT);
            boolean empty = hash.equals("empty");
            if (!empty && !hash.matches("[0-9a-f]{64}")) continue;
            boolean allowed = !empty && (TsaFilenameDetection.isHashAllowed(hash)
                    || TsaFilenameDetection.isAllowlisted(name));
            entries.add(new Entry(type, name, empty ? "" : hash, empty, allowed));
        }
        Snapshot snapshot = new Snapshot(uuid, Instant.now(), entries, failures);
        SNAPSHOTS.put(uuid, snapshot);
        return snapshot;
    }

    public static String formatUnallowlisted(Snapshot snapshot, String name) {
        List<Entry> entries = snapshot.unallowlisted();
        StringBuilder text = new StringBuilder("[TSA] Unallowlisted files for ").append(name)
                .append(": ").append(entries.size());
        if (snapshot.failures() > 0) text.append(" (").append(snapshot.failures()).append(" hash failures; incomplete)");
        if (entries.isEmpty()) text.append("\nNo unallowlisted files reported.");
        for (Entry entry : entries) {
            text.append("\n- ").append(entry.type()).append(" ")
                .append(entry.name()).append(" [")
                .append(entry.empty() ? "EMPTY" : entry.hash()).append("]");
        }
        return text.toString();
    }
}
