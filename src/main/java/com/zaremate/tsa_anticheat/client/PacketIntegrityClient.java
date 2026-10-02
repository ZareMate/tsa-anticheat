package com.zaremate.tsa_anticheat.client;

import com.zaremate.tsa_anticheat.HashRequestPayload;
import com.zaremate.tsa_anticheat.HashResponsePayload;
import com.zaremate.tsa_anticheat.PacketIntegrityChallengePayload;
import com.zaremate.tsa_anticheat.PacketIntegrityManager;
import com.zaremate.tsa_anticheat.PacketIntegrityResponsePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PacketIntegrityClient {
    private static final ExecutorService HASH_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "TSA Anticheat Hash Worker");
                thread.setDaemon(true);
                return thread;
            });

    private PacketIntegrityClient() {}

    public static void handleChallenge(PacketIntegrityChallengePayload payload) {
        var minecraft = Minecraft.getInstance();
        var gameProfile = minecraft.getGameProfile();

        if (gameProfile == null || minecraft.player == null) {
            return;
        }

        String response = PacketIntegrityManager.createResponse(
                gameProfile.getId(),
                payload.challenge()
        );

        PacketDistributor.sendToServer(
                new PacketIntegrityResponsePayload(
                        payload.challenge(),
                        response
                )
        );
    }

    public static void handleHashRequest(HashRequestPayload payload) {
        var minecraft = Minecraft.getInstance();
        Path gameDirectory = minecraft.gameDirectory.toPath();

        HASH_EXECUTOR.execute(() -> processHashRequest(
                minecraft,
                gameDirectory,
                payload
        ));
    }

    private static void processHashRequest(
            Minecraft minecraft,
            Path gameDirectory,
            HashRequestPayload payload
    ) {
        List<String> results = new ArrayList<>();
        int failed;

        if ("DETECTION".equals(payload.kind())) {
            failed = handleDetectionRequest(gameDirectory, results);
        } else {
            failed = handleSingleHashRequest(gameDirectory, payload, results);
        }

        int finalFailed = failed;
        minecraft.execute(() -> {
            if (minecraft.getConnection() != null) {
                sendHashResponse(payload, results, finalFailed);
            }
        });
    }

    private static int handleSingleHashRequest(
            Path gameDirectory,
            HashRequestPayload payload,
            List<String> results
    ) {
        Path directory = switch (payload.kind()) {
            case "MOD" -> gameDirectory.resolve("mods");
            case "RESOURCE_PACK" -> gameDirectory.resolve("resourcepacks");
            default -> null;
        };

        if (directory == null || !Files.isDirectory(directory)) {
            return 1;
        }

        int failed = 0;

        try {
            if ("*".equals(payload.name())) {
                try (var stream = Files.list(directory)) {
                    var entries = stream
                            .sorted(Comparator.comparing(path ->
                                    path.getFileName().toString()))
                            .toList();

                    for (Path entry : entries) {
                        try {
                            if (!Files.exists(entry)) {
                                failed++;
                                continue;
                            }

                            String hash = ResourcePackHasher.hash(entry);
                            results.add(
                                    entry.getFileName().toString() +
                                    "\t" +
                                    hash
                            );
                        } catch (Exception exception) {
                            failed++;
                        }
                    }
                }
            } else {
                Path entry = directory.resolve(payload.name());

                if (!entry.normalize().startsWith(directory.normalize())
                        || !Files.exists(entry)) {
                    failed++;
                } else {
                    try {
                        results.add(
                                entry.getFileName().toString() +
                                "\t" +
                                ResourcePackHasher.hash(entry)
                        );
                    } catch (Exception exception) {
                        failed++;
                    }
                }
            }
        } catch (Exception exception) {
            failed++;
        }

        return failed;
    }

    private static int handleDetectionRequest(
            Path gameDirectory,
            List<String> results
    ) {
        int failed = 0;

        failed += scanDetectionDirectory(
                gameDirectory.resolve("mods"),
                "MOD",
                results
        );

        failed += scanDetectionDirectory(
                gameDirectory.resolve("resourcepacks"),
                "RESOURCE_PACK",
                results
        );

        return failed;
    }

    private static int scanDetectionDirectory(
            Path directory,
            String type,
            List<String> results
    ) {
        if (!Files.isDirectory(directory)) {
            return 0;
        }

        int failed = 0;

        try (var stream = Files.list(directory)) {
            var entries = stream
                    .sorted(Comparator.comparing(path ->
                            path.getFileName().toString()))
                    .toList();

            for (Path entry : entries) {
                try {
                    if (!Files.exists(entry)) {
                        failed++;
                        continue;
                    }

                    String hash = ResourcePackHasher.hash(entry);

                    results.add(
                            type + "\t" +
                            entry.getFileName() + "\t" +
                            hash
                    );
                } catch (Exception exception) {
                    failed++;
                }
            }
        } catch (Exception exception) {
            failed++;
        }

        return failed;
    }

    private static void sendHashResponse(
            HashRequestPayload payload,
            List<String> results,
            int failed
    ) {
        PacketDistributor.sendToServer(
                new HashResponsePayload(
                        payload.kind(),
                        payload.name(),
                        String.join("\n", results),
                        failed
                )
        );
    }
}
