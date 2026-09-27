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

public final class PacketIntegrityClient {
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

        Path directory = switch (payload.type()) {
            case "MOD" -> gameDirectory.resolve("mods");
            case "RESOURCE_PACK" -> gameDirectory.resolve("resourcepacks");
            default -> null;
        };

        if (directory == null || !Files.isDirectory(directory)) {
            sendHashResponse(payload, List.of(), 1);
            return;
        }

        List<String> results = new ArrayList<>();
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

        sendHashResponse(payload, results, failed);
    }

    private static void sendHashResponse(
            HashRequestPayload payload,
            List<String> results,
            int failed
    ) {
        PacketDistributor.sendToServer(
                new HashResponsePayload(
                        payload.type(),
                        payload.name(),
                        String.join("\n", results),
                        failed
                )
        );
    }
}
