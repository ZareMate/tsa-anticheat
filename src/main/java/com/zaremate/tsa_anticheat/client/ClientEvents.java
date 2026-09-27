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
import java.util.Locale;

public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Path resourcePacks = Minecraft.getInstance().gameDirectory.toPath().resolve("resourcepacks");

        if (!Files.isDirectory(resourcePacks)) {
            return;
        }

        List<String> suspicious = new ArrayList<>();

        try (var stream = Files.list(resourcePacks)) {
            stream.map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.toLowerCase(Locale.ROOT).contains("ray"))
                    .forEach(suspicious::add);
        } catch (Exception ignored) {
            return;
        }

        if (!suspicious.isEmpty()) {
            PacketDistributor.sendToServer(
                    new XrayReportPayload(String.join(", ", suspicious))
            );
        }
    }
}
