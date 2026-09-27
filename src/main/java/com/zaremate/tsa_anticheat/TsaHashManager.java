package com.zaremate.tsa_anticheat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TsaHashManager {
    private static final Map<UUID, PendingRequest> PENDING =
            new ConcurrentHashMap<>();

    private TsaHashManager() {
    }

    public static void start(
            ServerPlayer player,
            String type,
            String name
    ) {
        PENDING.put(
                player.getUUID(),
                new PendingRequest(type, name)
        );

        PacketDistributor.sendToPlayer(
                player,
                new HashRequestPayload(type, name)
        );
    }

    public static void handleResponse(
            ServerPlayer player,
            HashResponsePayload response
    ) {
        PendingRequest pending = PENDING.remove(player.getUUID());

        if (pending == null) {
            return;
        }

        if (!pending.type().equals(response.kind())
                || !pending.name().equals(response.requestedName())) {
            player.sendSystemMessage(
                    Component.literal("[TSA] Ignored invalid hash response.")
            );
            return;
        }

        int saved = 0;

        for (String line : response.results().split("\\R")) {
            if (line.isBlank()) {
                continue;
            }

            int separator = line.lastIndexOf('\t');

            if (separator <= 0 || separator == line.length() - 1) {
                continue;
            }

            String name = line.substring(0, separator);
            String hash = line.substring(separator + 1).trim();

            if (!hash.matches("[0-9a-f]{64}")) {
                continue;
            }

            try {
                saveGeneratedHash(pending.type(), name, hash);
                saved++;
            } catch (Exception exception) {
                TsaAnticheat.LOGGER.warn(
                        "Failed to save generated hash for {} {}",
                        pending.type(),
                        name,
                        exception
                );
            }
        }

        if ("*".equals(pending.name())) {
            int total = saved + response.failed();

            player.sendSystemMessage(
                    Component.literal(
                            "Hashed " + saved + " " +
                            pending.type().toLowerCase() +
                            (saved == 1 ? "" : "s") +
                            (response.failed() > 0
                                    ? " (" + response.failed() + " failed)"
                                    : "") +
                            "\nProcessed " + total +
                            "\nSaved to config/tsa_anticheat/generated_hashes.txt"
                    )
            );
        } else if (saved == 1) {
            String[] parts = response.results().split("\\R", 2);
            String first = parts[0];
            int separator = first.lastIndexOf('\t');
            String hash = separator >= 0
                    ? first.substring(separator + 1).trim()
                    : "";

            player.sendSystemMessage(
                    Component.literal(
                            pending.type() + " " + pending.name() +
                            "\nSHA-256: " + hash +
                            "\nSaved to config/tsa_anticheat/generated_hashes.txt"
                    )
            );
        } else {
            player.sendSystemMessage(
                    Component.literal(
                            "Could not hash local " +
                            pending.type().toLowerCase() +
                            ": " + pending.name()
                    )
            );
        }
    }

    private static void saveGeneratedHash(
            String type,
            String name,
            String hash
    ) throws java.io.IOException {
        Path directory =
                FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID);
        Path file = directory.resolve("generated_hashes.txt");

        Files.createDirectories(directory);

        if (Files.notExists(file)) {
            Files.writeString(
                    file,
                    "# TSA Anticheat generated hashes\n" +
                    "# Generated from client-local mods/resource packs by /tsa hash\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW
            );
        }

        String line = type + " | " + name + " | " + hash;

        for (String existing :
                Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (existing.equals(line)) {
                return;
            }
        }

        Files.writeString(
                file,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private record PendingRequest(String type, String name) {
    }
}
