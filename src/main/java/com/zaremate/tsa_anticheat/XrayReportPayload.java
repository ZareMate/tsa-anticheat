package com.zaremate.tsa_anticheat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record XrayReportPayload(String data) implements CustomPacketPayload {
    public static final Type<XrayReportPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TsaAnticheat.MOD_ID, "xray_report"));

    public static final StreamCodec<ByteBuf, XrayReportPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    XrayReportPayload::data,
                    XrayReportPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(XrayReportPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                verifyAndWriteReports(player, payload.data());
            }
        });
    }

    private static void verifyAndWriteReports(ServerPlayer player, String data) {
        Set<String> blacklistedHashes = TsaFilenameDetection.loadBlacklistedHashes();

        List<String> detected = new ArrayList<>();
        List<String> detectedResourcePacks = new ArrayList<>();

        for (String line : data.split("\\R")) {
            int lastSeparator = line.lastIndexOf('\t');

            if (lastSeparator <= 0 || lastSeparator == line.length() - 1) {
                continue;
            }

            int firstSeparator = line.indexOf('\t');

            if (firstSeparator <= 0 || firstSeparator >= lastSeparator) {
                continue;
            }

            String type = line.substring(0, firstSeparator).trim().toUpperCase(Locale.ROOT);
            String name = line.substring(firstSeparator + 1, lastSeparator);
            String hash = line.substring(lastSeparator + 1).trim().toLowerCase(Locale.ROOT);

            if (!type.equals("MOD") && !type.equals("RESOURCE_PACK")) {
                continue;
            }

            if (!hash.matches("[0-9a-f]{64}")) {
                continue;
            }

            boolean hashBlacklisted = blacklistedHashes.contains(hash);
            boolean rayFilename = TsaFilenameDetection.isRayFilename(name);

            if (rayFilename) {
                TsaFilenameDetection.addHashToBlacklist(hash);
                blacklistedHashes.add(hash);
            }

            if (hashBlacklisted || rayFilename) {
                String detection = type + " " + name + " [" + hash + "]"
                        + (rayFilename ? " (filename contains \\"ray\\")" : "");
                detected.add(detection);

                if (type.equals("RESOURCE_PACK")) {
                    detectedResourcePacks.add(name + " [" + hash + "]"
                            + (rayFilename ? " (filename contains \\"ray\\")" : ""));
                }
            }
        }

        if (!detected.isEmpty()) {
            writeReport(player, detected);

            if (!detectedResourcePacks.isEmpty()) {
                DiscordWebhook.sendResourcePackDetection(
                        player.getGameProfile().getName(),
                        player.getUUID().toString(),
                        detectedResourcePacks
                );
            }
        }
    }

    private static void writeReport(ServerPlayer player, List<String> detected) {
        Path directory = FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID);
        Path file = directory.resolve(player.getUUID() + ".txt");
        String line = LocalDate.now() + " | DETECTED | " + String.join(", ", detected);

        try {
            Files.createDirectories(directory);

            if (Files.exists(file)) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

                if (lines.contains(line)) {
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
        } catch (IOException exception) {
            System.err.println(
                    "Failed to write TSA Anticheat report for " +
                    player.getGameProfile().getName()
            );
            exception.printStackTrace();
        }
    }
}
