package com.zaremate.tsa_anticheat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zaremate.tsa_anticheat.client.ResourcePackHasher;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PacketIntegrityCommands {
    private PacketIntegrityCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("tsa")
                        .requires(source -> source.hasPermission(3))
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
                        .then(Commands.literal("hash")
                                .then(Commands.literal("mod")
                                        .then(Commands.literal("*")
                                        .executes(context -> hashAllEntries(
                                                context.getSource(),
                                                "MOD",
                                                FMLPaths.GAMEDIR.get().resolve("mods")
                                        )))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) ->
                                                suggestEntries(builder, FMLPaths.GAMEDIR.get().resolve("mods")))
                                        .executes(context -> hashEntry(
                                                context.getSource(),
                                                "MOD",
                                                FMLPaths.GAMEDIR.get().resolve("mods"),
                                                StringArgumentType.getString(context, "name")
                                        )))
                                )
                                .then(Commands.literal("resourcepack")
                                        .then(Commands.literal("*")
                                        .executes(context -> hashAllEntries(
                                                context.getSource(),
                                                "RESOURCE_PACK",
                                                FMLPaths.GAMEDIR.get().resolve("resourcepacks")
                                        )))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests((context, builder) ->
                                                suggestEntries(
                                                        builder,
                                                        FMLPaths.GAMEDIR.get().resolve("resourcepacks")
                                                ))
                                        .executes(context -> hashEntry(
                                                context.getSource(),
                                                "RESOURCE_PACK",
                                                FMLPaths.GAMEDIR.get().resolve("resourcepacks"),
                                                StringArgumentType.getString(context, "name")
                                        )))
                                )
                        )
        );
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

    private static int hashAllEntries(
            CommandSourceStack source,
            String type,
            Path directory
    ) {
        if (!Files.isDirectory(directory)) {
            source.sendFailure(
                    Component.literal(
                            "Directory not found: " + directory
                    )
            );
            return 0;
        }

        int hashed = 0;
        int failed = 0;

        try (var stream = Files.list(directory)) {
            var entries = stream
                    .sorted()
                    .toList();

            for (Path entry : entries) {
                if (!Files.exists(entry)) {
                    continue;
                }

                try {
                    String hash = ResourcePackHasher.hash(entry);
                    saveGeneratedHash(
                            type,
                            entry.getFileName().toString(),
                            hash
                    );
                    hashed++;
                } catch (Exception exception) {
                    failed++;
                    TsaAnticheat.LOGGER.warn(
                            "Failed to hash {} {}",
                            type,
                            entry,
                            exception
                    );
                }
            }
        } catch (Exception exception) {
            TsaAnticheat.LOGGER.warn(
                    "Failed to list {} directory {}",
                    type,
                    directory,
                    exception
            );
            source.sendFailure(
                    Component.literal(
                            "Failed to read " + type.toLowerCase() + " directory."
                    )
            );
            return 0;
        }

        int finalHashed = hashed;
        int finalFailed = failed;

        source.sendSuccess(
                () -> Component.literal(
                        "Hashed " + finalHashed + " " + type.toLowerCase()
                                + (finalHashed == 1 ? "" : "s")
                                + (finalFailed > 0
                                ? " (" + finalFailed + " failed)"
                                : "")
                                + "\nSaved to config/tsa_anticheat/generated_hashes.txt"
                ),
                false
        );

        return hashed;
    }

    private static int hashEntry(
            CommandSourceStack source,
            String type,
            Path directory,
            String name
    ) {
        Path entry = directory.resolve(name);

        if (!entry.normalize().startsWith(directory.normalize())
                || !Files.exists(entry)) {
            source.sendFailure(
                    Component.literal(
                            "Could not find " + type.toLowerCase() + ": " + name
                    )
            );
            return 0;
        }

        try {
            String hash = ResourcePackHasher.hash(entry);
            saveGeneratedHash(type, name, hash);

            source.sendSuccess(
                    () -> Component.literal(
                            type + " " + name
                                    + "\nSHA-256: " + hash
                                    + "\nSaved to config/tsa_anticheat/generated_hashes.txt"
                    ),
                    false
            );

            return 1;
        } catch (Exception exception) {
            TsaAnticheat.LOGGER.warn(
                    "Failed to hash {} {}",
                    type,
                    entry,
                    exception
            );

            source.sendFailure(
                    Component.literal(
                            "Failed to hash " + type.toLowerCase()
                                    + " '" + name + "'."
                    )
            );
            return 0;
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
                    "# TSA Anticheat generated hashes\n"
                            + "# Generated by /tsa hash\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW
            );
        }

        String line = type + " | " + name + " | " + hash;

        for (String existing : Files.readAllLines(file, StandardCharsets.UTF_8)) {
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

    private static CompletableFuture<Suggestions> suggestEntries(
            SuggestionsBuilder builder,
            Path directory
    ) {
        try (var stream = Files.list(directory)) {
            var suggestions = new java.util.ArrayList<String>();
            suggestions.add("*");
            suggestions.addAll(
                    stream
                            .map(path -> path.getFileName().toString())
                            .sorted()
                            .toList()
            );

            return SharedSuggestionProvider.suggest(suggestions, builder);
        } catch (Exception exception) {
            return builder.buildFuture();
        }
    }
}
