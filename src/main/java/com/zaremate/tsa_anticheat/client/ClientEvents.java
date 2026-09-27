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
        Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath();

        List<String> reports = new ArrayList<>();

        scanDirectory(gameDirectory.resolve("resourcepacks"), "RESOURCE_PACK", reports);
        scanDirectory(gameDirectory.resolve("mods"), "MOD", reports);

        if (!reports.isEmpty()) {
            PacketDistributor.sendToServer(
                    new XrayReportPayload(String.join("\n", reports))
            );
        }
    }

    private static void scanDirectory(Path directory, String type, List<String> reports) {
        if (!Files.isDirectory(directory)) {
            return;
        }

        try (var stream = Files.list(directory)) {
            stream.forEach(entry -> {
                try {
                    // Mods are normally JARs; resource packs can be ZIP files
                    // or directories. The hasher supports both.
                    String hash = ResourcePackHasher.hash(entry);
                    String name = entry.getFileName().toString();

                    // Detection is performed by the server using only the hash.
                    // The type and name are included for readable reports.
                    reports.add(type + "\t" + name + "\t" + hash);
                } catch (Exception ignored) {
                    // Ignore unreadable/invalid entries.
                }
            });
        } catch (Exception ignored) {
            // Ignore inaccessible directories.
        }
    }
}
