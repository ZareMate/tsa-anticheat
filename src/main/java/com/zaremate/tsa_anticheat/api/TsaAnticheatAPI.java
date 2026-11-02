package com.zaremate.tsa_anticheat.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import com.zaremate.tsa_anticheat.TsaWorker;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Public API for TSA Anticheat.
 *
 * <p>Other server-side mods can use this API to read TSA's per-player
 * detection and packet-integrity history and manage lists and common settings
 * without depending on the storage implementation.
 * Management methods require a running TSA worker and loaded common config.
 * They run asynchronously; never join their futures on the server thread.
 * This trusted in-process API does not perform command permission checks.</p>
 */
public final class TsaAnticheatAPI {
    private TsaAnticheatAPI() {}

    private static volatile List<PlayerRecord> snapshot = List.of();
    private static java.util.concurrent.CompletableFuture<List<PlayerRecord>> refresh;

    /** Nonblocking view of the most recent background refresh. */
    public static Optional<PlayerRecord> getPlayer(UUID playerUuid) {
        return snapshot.stream().filter(record -> record.playerUuid().equals(playerUuid)).findFirst();
    }

    /** Nonblocking view; initially empty until the first background refresh completes. */
    public static List<PlayerRecord> getPlayers() {
        return snapshot;
    }

    /** Fresh disk data, serialized with TSA report writes and removals. Never join on the server thread. */
    public static synchronized java.util.concurrent.CompletableFuture<List<PlayerRecord>> getPlayersAsync() {
        if (refresh == null || refresh.isDone()) {
            refresh = com.zaremate.tsa_anticheat.TsaWorker.submit(() -> {
                List<PlayerRecord> records = TsaAnticheatDataAccess.getPlayers();
                snapshot = records;
                return records;
            });
        }
        return refresh.copy();
    }

    public static java.util.concurrent.CompletableFuture<Optional<PlayerRecord>> getPlayerAsync(UUID uuid) {
        return getPlayersAsync().thenApply(records -> records.stream()
                .filter(record -> record.playerUuid().equals(uuid)).findFirst());
    }

    /** Lifecycle hook; called after the worker has drained. */
    public static synchronized void clearCache() {
        snapshot = List.of();
        refresh = null;
        com.zaremate.tsa_anticheat.TsaInventory.clear();
    }

    /** Latest client-reported inventory from a successful scan. */
    public static java.util.Optional<com.zaremate.tsa_anticheat.TsaInventory.Snapshot> getInventory(UUID uuid) {
        return java.util.Optional.ofNullable(com.zaremate.tsa_anticheat.TsaInventory.get(uuid));
    }

    /** Request a fresh client inventory scan. Result is delivered asynchronously into getInventory(). */
    public static boolean requestUnlistedScan(net.minecraft.server.level.ServerPlayer player) {
        return com.zaremate.tsa_anticheat.TsaHashManager.startUnlisted(player, null);
    }

    /** Cached inventories from all players scanned since server start. */
    public static java.util.Map<UUID, com.zaremate.tsa_anticheat.TsaInventory.Snapshot> getInventories() {
        return com.zaremate.tsa_anticheat.TsaInventory.all();
    }

    /** Cached unallowlisted entries grouped by player UUID. */
    public static java.util.Map<UUID, List<com.zaremate.tsa_anticheat.TsaInventory.Entry>> getAllUnlisted() {
        java.util.Map<UUID, List<com.zaremate.tsa_anticheat.TsaInventory.Entry>> result = new java.util.LinkedHashMap<>();
        getInventories().forEach((uuid, snapshot) -> result.put(uuid, snapshot.unlisted()));
        return java.util.Map.copyOf(result);
    }

    /** Non-allowlisted mods and resource packs from the latest client scan. */
    public static List<com.zaremate.tsa_anticheat.TsaInventory.Entry> getUnlisted(UUID uuid) {
        return getInventory(uuid).map(com.zaremate.tsa_anticheat.TsaInventory.Snapshot::unlisted)
                .orElseGet(List::of);
    }

    /** @deprecated Use requestUnlistedScan(ServerPlayer). */
    @Deprecated
    public static boolean requestUnallowlistedScan(net.minecraft.server.level.ServerPlayer player) {
        return requestUnlistedScan(player);
    }

    /** @deprecated Use getUnlisted(UUID). */
    @Deprecated
    public static List<com.zaremate.tsa_anticheat.TsaInventory.Entry> getUnallowlisted(UUID uuid) {
        return getUnlisted(uuid);
    }

    /** @deprecated Use getAllUnlisted(). */
    @Deprecated
    public static java.util.Map<UUID, List<com.zaremate.tsa_anticheat.TsaInventory.Entry>> getAllUnallowlisted() {
        return getAllUnlisted();
    }

    /** Fresh normalized hashes from one independent operational list. */
    public static CompletableFuture<Set<String>> getHashesAsync(HashList list) {
        return manage(() -> lists().hashes(java.util.Objects.requireNonNull(list, "list")));
    }

    /** Returns true only when the selected list changed. Does not alter other lists. */
    public static CompletableFuture<Boolean> addHashAsync(HashList list, String hash) {
        return manage(() -> lists().editHash(java.util.Objects.requireNonNull(list, "list"), hash, true));
    }

    /** Removes all case variants. Filename detection may later re-add a blacklisted hash. */
    public static CompletableFuture<Boolean> removeHashAsync(HashList list, String hash) {
        return manage(() -> lists().editHash(java.util.Objects.requireNonNull(list, "list"), hash, false));
    }

    /** File-based filename exemptions; the config filename allowlist is exposed separately. */
    public static CompletableFuture<Set<String>> getAllowedFilenamesAsync() {
        return manage(() -> lists().filenames());
    }

    public static CompletableFuture<Boolean> addAllowedFilenameAsync(String filename) {
        return manage(() -> lists().editFilename(filename, true));
    }

    public static CompletableFuture<Boolean> removeAllowedFilenameAsync(String filename) {
        return manage(() -> lists().editFilename(filename, false));
    }

    /** Generated hashes are history only; editing them never changes detection rules. */
    public static CompletableFuture<List<GeneratedHash>> getGeneratedHashesAsync() {
        return manage(() -> lists().generated());
    }

    public static CompletableFuture<Boolean> addGeneratedHashAsync(GeneratedHash entry) {
        return manage(() -> lists().editGenerated(entry, true));
    }

    public static CompletableFuture<Boolean> removeGeneratedHashAsync(GeneratedHash entry) {
        return manage(() -> lists().editGenerated(entry, false));
    }

    /** Immutable snapshot of every common setting, including the private webhook URL. */
    public static CompletableFuture<Map<TsaSetting, Object>> getSettingsAsync() {
        return manage(TsaConfiguration::read);
    }

    /** Validate, apply and persist one setting. Existing checks and Punish bans retain their state. */
    public static CompletableFuture<Boolean> setSettingAsync(TsaSetting setting, Object value) {
        final Object validated;
        try { validated = java.util.Objects.requireNonNull(setting, "setting").validate(value); }
        catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
        return manage(() -> TsaConfiguration.set(setting, validated));
    }

    /** Add one sanitized-unique filename/keyword to a list-valued config setting. */
    public static CompletableFuture<Boolean> addSettingEntryAsync(TsaSetting setting, String entry) {
        return manage(() -> TsaConfiguration.editEntry(setting, entry, true));
    }

    /** Remove every matching sanitized filename/keyword from a list-valued config setting. */
    public static CompletableFuture<Boolean> removeSettingEntryAsync(TsaSetting setting, String entry) {
        return manage(() -> TsaConfiguration.editEntry(setting, entry, false));
    }

    private static TsaListStore lists() {
        return new TsaListStore(FMLPaths.CONFIGDIR.get().resolve("tsa_anticheat"));
    }

    /** Persistent client-reported filenames for a hash; associations are not enforcement rules. */
    public static CompletableFuture<List<com.zaremate.tsa_anticheat.HashFilenameStore.Entry>> lookupHashAsync(String hash) {
        return manage(() -> com.zaremate.tsa_anticheat.HashFilenameStore.open(
                FMLPaths.CONFIGDIR.get().resolve("tsa_anticheat")).lookup(hash));
    }

    private interface ManagementTask<T> { T run() throws Exception; }

    private static <T> CompletableFuture<T> manage(ManagementTask<T> task) {
        return TsaWorker.submit(() -> {
            try { return task.run(); }
            catch (Exception error) { throw new CompletionException(error); }
        });
    }

    public record PlayerRecord(
            UUID playerUuid,
            String playerName,
            long packetChecks,
            long packetPasses,
            long packetModified,
            long packetTimeout,
            String lastPacketStatus,
            String lastPacketDate,
            List<String> detections
    ) {
        public PlayerRecord {
            detections = List.copyOf(detections);
        }
    }
}
