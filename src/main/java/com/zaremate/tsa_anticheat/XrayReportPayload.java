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
        Set<String> blacklistedHashes = loadBlacklistedHashes();

        if (blacklistedHashes.isEmpty()) {
            return;
        }

        List<String> detected = new ArrayList<>();

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

            if (hash.matches("[0-9a-f]{64}") && blacklistedHashes.contains(hash)) {
                detected.add(type + " " + name + " [" + hash + "]");
            }
        }

        if (!detected.isEmpty()) {
            writeReport(player, detected);
        }
    }

    private static Set<String> loadBlacklistedHashes() {
        Path directory = FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID);
        Path hashesFile = directory.resolve("blacklisted_hashes.txt");

        try {
            Files.createDirectories(directory);

            if (Files.notExists(hashesFile)) {
                Files.writeString(
                        hashesFile,
                        "# One SHA-256 hash per line. Hashes can belong to mods or resource packs.\n" +
                        "# Detection is based on content, not the filename.\n" +
                        "# Example: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW
                );
            }

            Set<String> hashes = new HashSet<>();

            for (String line : Files.readAllLines(hashesFile, StandardCharsets.UTF_8)) {
                String hash = line.trim().toLowerCase(Locale.ROOT);

                if (!hash.isEmpty() && !hash.startsWith("#") && hash.matches("[0-9a-f]{64}")) {
                    hashes.add(hash);
                }
            }

            return hashes;
        } catch (IOException exception) {
            System.err.println("Failed to load TSA Anticheat blacklisted hashes");
            exception.printStackTrace();
            return Set.of();
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
