package com.zaremate.tsa_anticheat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

public final class PacketIntegrityCommands {
    private PacketIntegrityCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("tsa")
                        .requires(TsaPermissions::canUseCommands)
                        .then(Commands.literal("packetcheck")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> startCheck(
                                                context.getSource(),
                                                EntityArgument.getPlayer(
                                                        context,
                                                        "player"
                                                )
                                        )))
                        )
                        .then(Commands.literal("checkpacket")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> startCheck(
                                                context.getSource(),
                                                EntityArgument.getPlayer(
                                                        context,
                                                        "player"
                                                )
                                        )))
                        )
                        .then(Commands.literal("detect")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> startDetection(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")
                                        )))
                        )
                        .then(Commands.literal("allow")
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(context -> allowValue(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "value")
                                        )))
                                .then(Commands.literal("name")
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(context -> allowName(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name")
                                                )))
                                )
                                .then(Commands.literal("hash")
                                        .then(Commands.argument("hash", StringArgumentType.word())
                                                .executes(context -> allowHash(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "hash")
                                                )))
                                )
                        )
                        .then(Commands.literal("hash")
                                .then(Commands.literal("mod")
                                        .then(Commands.literal("*")
                                                .executes(context -> startHash(
                                                        context.getSource(),
                                                        "MOD",
                                                        "*"
                                                )))
                                        .then(Commands.argument(
                                                "name",
                                                StringArgumentType.string()
                                        )
                                                .executes(context -> startHash(
                                                        context.getSource(),
                                                        "MOD",
                                                        StringArgumentType.getString(
                                                                context,
                                                                "name"
                                                        )
                                                )))
                                )
                                .then(Commands.literal("resourcepack")
                                        .then(Commands.literal("*")
                                                .executes(context -> startHash(
                                                        context.getSource(),
                                                        "RESOURCE_PACK",
                                                        "*"
                                                )))
                                        .then(Commands.argument(
                                                "name",
                                                StringArgumentType.string()
                                        )
                                                .executes(context -> startHash(
                                                        context.getSource(),
                                                        "RESOURCE_PACK",
                                                        StringArgumentType.getString(
                                                                context,
                                                                "name"
                                                        )
                                                )))
                                )
                        )
        );
    }

    private static int allowValue(
            CommandSourceStack source,
            String value
    ) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);

        if (normalized.matches("[0-9a-f]{64}")) {
            return allowHash(source, value);
        }

        return allowName(source, value);
    }

    private static int allowName(
            CommandSourceStack source,
            String name
    ) {
        String cleanName = name.trim();

        if (cleanName.isEmpty()) {
            source.sendFailure(Component.literal("Filename cannot be empty."));
            return 0;
        }

        boolean added = TsaFilenameDetection.addFilenameToAllowlist(cleanName);
        String sanitized = TsaFilenameDetection.sanitizeFilename(cleanName);

        if (!added) {
            source.sendFailure(
                    Component.literal(
                            "Filename is already allowed or invalid: " + cleanName
                    )
            );
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Allowed filename: " + cleanName +
                        " (sanitized: " + sanitized + ")"
                ),
                true
        );
        return 1;
    }

    private static int allowHash(
            CommandSourceStack source,
            String hash
    ) {
        String normalized = hash.trim().toLowerCase(java.util.Locale.ROOT);

        if (!normalized.matches("[0-9a-f]{64}")) {
            source.sendFailure(Component.literal(
                    "Invalid SHA-256 hash. Expected exactly 64 hexadecimal characters."
            ));
            return 0;
        }

        if (TsaFilenameDetection.loadAllowedHashes().contains(normalized)) {
            source.sendFailure(Component.literal("Hash is already allowed."));
            return 0;
        }

        Path directory = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve(TsaAnticheat.MOD_ID);
        Path hashesFile = directory.resolve("allowed_hashes.txt");

        try {
            java.nio.file.Files.createDirectories(directory);

            if (java.nio.file.Files.notExists(hashesFile)) {
                java.nio.file.Files.writeString(
                        hashesFile,
                        "# One SHA-256 hash per line. Allowed hashes override TSA blacklist and filename detection.\\n" +
                        "# Example: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\\n",
                        java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE_NEW
                );
            }

            java.nio.file.Files.writeString(
                    hashesFile,
                    normalized + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (java.io.IOException exception) {
            TsaAnticheat.LOGGER.warn("Failed to add allowed TSA hash.", exception);
            source.sendFailure(Component.literal("Failed to save allowed hash."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Allowed hash: " + normalized),
                true
        );
        return 1;
    }

    private static int startCheck(CommandSourceStack source, ServerPlayer target) {
        UUID requester = source.getEntity() instanceof ServerPlayer player
                ? player.getUUID()
                : null;

        PacketIntegrityManager.start(target, requester);

        source.sendSuccess(
                () -> Component.literal(
                        "Started packet integrity check for " +
                        target.getGameProfile().getName()
                ),
                true
        );

        return 1;
    }

    private static int startDetection(CommandSourceStack source, ServerPlayer target) {
        UUID requester = source.getEntity() instanceof ServerPlayer player
                ? player.getUUID()
                : null;

        if (!TsaHashManager.startDetection(target, requester)) {
            source.sendFailure(
                    Component.literal(
                            "A detection scan is already pending for " +
                            target.getGameProfile().getName() + "."
                    )
            );
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Started detection scan for " +
                        target.getGameProfile().getName()
                ),
                false
        );

        return 1;
    }

    private static int startHash(
            CommandSourceStack source,
            String type,
            String name
    ) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(
                    Component.literal(
                            "This command must be run by a player with TSA Anticheat installed."
                    )
            );
            return 0;
        }

        TsaHashManager.start(player, type, name);

        source.sendSuccess(
                () -> Component.literal(
                        "Requested local " + type.toLowerCase() +
                        ("*".equals(name)
                                ? " hash scan."
                                : " hash for " + name + ".")
                ),
                false
        );

        return 1;
    }
}
