package com.zaremate.tsa_anticheat.client;

import com.zaremate.tsa_anticheat.XrayReportPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Path resourcePacks = Minecraft.getInstance().gameDirectory.toPath().resolve("resourcepacks");

        if (!Files.isDirectory(resourcePacks)) {
            return;
        }

        List<String> reports = new ArrayList<>();

        try (var stream = Files.list(resourcePacks)) {
            stream.forEach(resourcePack -> {
                try {
                    String hash = ResourcePackHasher.hash(resourcePack);
                    String name = resourcePack.getFileName().toString();

                    // The name is sent for human-readable reporting, but the
                    // server's detection decision is based only on the hash.
                    reports.add(name + "|" + hash);
                } catch (Exception ignored) {
                    // Ignore unreadable/invalid resource packs.
                }
            });
        } catch (Exception ignored) {
            return;
        }

        if (!reports.isEmpty()) {
            PacketDistributor.sendToServer(
                    new XrayReportPayload(String.join("\n", reports))
            );
        }
    }
}
